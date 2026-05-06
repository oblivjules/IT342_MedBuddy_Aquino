package com.medbuddy.features.user;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.medbuddy.features.auth.AuthResponse;
import com.medbuddy.features.auth.LoginRequest;
import com.medbuddy.features.auth.RegisterRequest;
import com.medbuddy.features.medicalrecords.FileStorageService;
import com.medbuddy.features.medicalrecords.FileUploadValidationService;
import com.medbuddy.features.medicalrecords.StorageUploadResult;
import com.medbuddy.features.specialization.SpecializationRepository;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.Provider;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.Specialization;
import com.medbuddy.shared.model.User;
import com.medbuddy.shared.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Set<String> ALLOWED_PROFILE_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_PROFILE_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    @Value("${app.storage.max-profile-image-size-bytes:2097152}")
    private long maxProfileImageSizeBytes;


    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final SpecializationRepository specializationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;
    private final FileUploadValidationService fileUploadValidationService;
    
    // ================= REGISTER =================
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("[REGISTER] role={} email={} specializationIds={}",
                request.getRole(), request.getEmail(), request.getSpecializationIds());
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // Prevent duplicate emails across local + Google registrations.
        Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            if (existingUser.get().getProvider() == Provider.GOOGLE) {
                throw new IllegalArgumentException(
                        "This email is already associated with a Google account. Please sign in with Google instead.");
            }
            throw new IllegalArgumentException(
                    "An account with this email already exists. Please log in instead.");
        }

        // 🔹 Resolve specializations FIRST (important)
        Set<Specialization> doctorSpecializations = resolveDoctorSpecializations(request);

        // 🔹 Create User
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .provider(Provider.LOCAL)
                .build();

        user = userRepository.save(user);

        // ================= PATIENT =================
        if (request.getRole() == Role.PATIENT) {

            Patient patient = Patient.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .user(user)
                    .build();

            patientRepository.save(patient);

            emailService.sendWelcomeEmail(user.getEmail(), request.getFirstName());
        }

        // ================= DOCTOR =================
        else if (request.getRole() == Role.DOCTOR) {

            Doctor doctor = Doctor.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .user(user)
                    .build();

            // ✅ CRITICAL: assign specializations AFTER build
            doctor.setSpecializations(doctorSpecializations);

            log.debug("[REGISTER][DOCTOR][PRE_SAVE] userId={} specializationCount={} specializationNames={}",
                    user.getId(),
                    doctorSpecializations.size(),
                    toSpecializationNames(doctorSpecializations));

            Doctor savedDoctor = doctorRepository.saveAndFlush(doctor);

            log.debug("[REGISTER][DOCTOR][POST_SAVE] doctorId={} joinSpecializationCount={} summaryColumn='{}'",
                    savedDoctor.getId(),
                    savedDoctor.getSpecializations().size(),
                    savedDoctor.getSpecializationsSummary());

            String persistedSummary = doctorRepository.findSpecializationsSummaryRawByDoctorId(savedDoctor.getId());
            log.debug("[REGISTER][DOCTOR][POST_SAVE][DB_RAW] doctorId={} doctors.specializations='{}'",
                    savedDoctor.getId(), persistedSummary);

            emailService.sendDoctorWelcomeEmail(user.getEmail(), request.getFirstName());
        }

        // 🔹 Generate JWT
        String token = jwtUtil.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(buildUserDto(user))
                .build();
    }

    // ================= LOGIN =================
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password", ex);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + request.getEmail()));

        if (request.getRole() != null && request.getRole() != user.getRole()) {
            throw new BadCredentialsException(
                    "This account is not registered as a " + request.getRole().name().toLowerCase(Locale.ROOT) + ".");
        }

        String token = jwtUtil.generateToken(user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(buildUserDto(user))
                .build();
    }

    // ================= GET CURRENT USER =================
    @Transactional(readOnly = true)
    public UserDto getMe(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));

        UserDto dto = buildUserDto(user);
        log.debug("[PROFILE_IMAGE][GETME] userId={} profileImageUrl={}", 
                user.getId(), dto.getProfileImageUrl() != null ? "present" : "null");
        
        return dto;
    }

    // ================= UPDATE CURRENT USER =================
    @Transactional
    public AuthResponse updateMe(String currentEmail, UpdateProfileRequest request) {

        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + currentEmail));

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!user.getEmail().equalsIgnoreCase(normalizedEmail)
                && userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException(
                    "An account already exists with email: " + normalizedEmail);
        }

        user.setEmail(normalizedEmail);

        boolean hasCurrentPassword = request.getCurrentPassword() != null
                && !request.getCurrentPassword().isBlank();
        boolean hasNewPassword = request.getNewPassword() != null
                && !request.getNewPassword().isBlank();

        if (hasCurrentPassword || hasNewPassword) {
            if (!hasCurrentPassword || !hasNewPassword) {
                throw new IllegalArgumentException(
                        "Both currentPassword and newPassword are required to change password.");
            }

            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }

            if (Objects.equals(request.getCurrentPassword(), request.getNewPassword())) {
                throw new IllegalArgumentException("New password must be different from current password.");
            }

            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);

        if (user.getRole() == Role.PATIENT) {
            Patient patient = patientRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Patient profile not found for user: " + currentEmail));

            patient.setFirstName(request.getFirstName().trim());
            patient.setLastName(request.getLastName().trim());
            patient.setPhoneNumber(request.getPhoneNumber().trim());
            patientRepository.save(patient);
        } else if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Doctor profile not found for user: " + currentEmail));

            doctor.setFirstName(request.getFirstName().trim());
            doctor.setLastName(request.getLastName().trim());
            doctor.setPhoneNumber(request.getPhoneNumber().trim());

            if (request.getSpecializationIds() != null) {
                doctor.setSpecializations(resolveDoctorSpecializationsForUpdate(request.getSpecializationIds()));
            }

            doctorRepository.save(doctor);
        }

        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail()))
                .user(buildUserDto(user))
                .build();
    }

    private Set<Specialization> resolveDoctorSpecializationsForUpdate(List<Long> specializationIds) {
        if (specializationIds.isEmpty()) {
            throw new IllegalArgumentException("At least one specialization is required for doctors.");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(specializationIds);
        List<Specialization> specializations = specializationRepository.findAllById(uniqueIds);

        if (specializations.size() != uniqueIds.size()) {
            Set<Long> foundIds = specializations.stream()
                    .map(Specialization::getId)
                    .collect(Collectors.toSet());

            List<Long> missingIds = uniqueIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());

            throw new IllegalArgumentException("Invalid specialization IDs: " + missingIds);
        }

        return new LinkedHashSet<>(specializations);
    }

    // ================= GET DOCTORS =================
    @Transactional(readOnly = true)
    public List<DoctorDto> getDoctors() {

        return doctorRepository.findAll().stream()
                .map(d -> DoctorDto.builder()
                        .id(d.getId())
                        .userId(d.getUser().getId())
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .phoneNumber(d.getPhoneNumber())
                        .specializations(toSpecializationNames(d.getSpecializations()))
                        .profileImageUrl(d.getUser().getProfileImageUrl() != null
                                ? fileStorageService.createSignedUrl(d.getUser().getProfileImageUrl())
                                : null)
                        .email(d.getUser().getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DoctorDto getDoctorById(Long doctorId) {
        Doctor d = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found with id: " + doctorId));

        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phoneNumber(d.getPhoneNumber())
                .specializations(toSpecializationNames(d.getSpecializations()))
                .profileImageUrl(d.getUser().getProfileImageUrl() != null
                        ? fileStorageService.createSignedUrl(d.getUser().getProfileImageUrl())
                        : null)
                .email(d.getUser().getEmail())
                .build();
    }

    @Transactional
    public UserDto updateDoctorProfileImage(String email, MultipartFile file) {
        log.info("[PROFILE_IMAGE][DOCTOR] request email={} fileName={} size={} bytes",
                email,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));

        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can update profile images.");
        }

        validateProfileImage(file);

        StorageUploadResult storageResult = fileStorageService.store(file, "profile-images/doctor-" + user.getId());
        user.setProfileImageUrl(storageResult.storagePath());
        user = userRepository.save(user);

        log.info("[PROFILE_IMAGE][DOCTOR] updated userId={} storagePath={}", user.getId(), storageResult.storagePath());
        
        UserDto response = buildUserDto(user);
        log.info("[PROFILE_IMAGE][DOCTOR][RESPONSE] userId={} returnedProfileImageUrl={}", 
                user.getId(), 
                response.getProfileImageUrl() != null ? "present" : "null");
        
        return response;
    }

    // ================= DTO BUILDER =================
    private UserDto buildUserDto(User user) {

        String signedUrl = user.getProfileImageUrl() != null
                ? fileStorageService.createSignedUrl(user.getProfileImageUrl())
                : null;
        
        if (signedUrl != null) {
            log.debug("[PROFILE_IMAGE][RESPONSE] userId={} storagePath={} signedUrl={}", 
                    user.getId(), user.getProfileImageUrl(), signedUrl);
        } else if (user.getProfileImageUrl() != null) {
            log.warn("[PROFILE_IMAGE][RESPONSE] userId={} storagePath={} signedUrl=null (generation failed)", 
                    user.getId(), user.getProfileImageUrl());
        }
        
        UserDto.UserDtoBuilder builder = UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .profileImageUrl(signedUrl);

        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser_Id(user.getId()).ifPresent(p -> {
                builder.profileId(p.getId())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .phoneNumber(p.getPhoneNumber());
            });
        }

        else if (user.getRole() == Role.DOCTOR) {
            doctorRepository.findByUser_Id(user.getId()).ifPresent(d -> {
                builder.profileId(d.getId())
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .phoneNumber(d.getPhoneNumber())
                        .specializations(toSpecializationNames(d.getSpecializations()));
            });
        }

        return builder.build();
    }

    // ================= SPECIALIZATION RESOLVER =================
    private Set<Specialization> resolveDoctorSpecializations(RegisterRequest request) {

        if (request.getRole() != Role.DOCTOR) {
            return Set.of();
        }

        List<Long> specializationIds = request.getSpecializationIds();

        log.debug("[REGISTER][DOCTOR][RESOLVE] rawSpecializationIds={}", specializationIds);

        if (specializationIds == null || specializationIds.isEmpty()) {
            log.debug("[REGISTER][DOCTOR][RESOLVE] failed: specializationIds empty/null");
            throw new IllegalArgumentException(
                    "At least one specialization is required for doctors.");
        }

        // Remove duplicates but preserve order
        Set<Long> uniqueIds = new LinkedHashSet<>(specializationIds);
        log.debug("[REGISTER][DOCTOR][RESOLVE] uniqueSpecializationIds={}", uniqueIds);

        List<Specialization> specializations =
                specializationRepository.findAllById(uniqueIds);

        log.debug("[REGISTER][DOCTOR][RESOLVE] foundSpecializationIds={} foundSpecializationNames={}",
                specializations.stream().map(Specialization::getId).collect(Collectors.toList()),
                specializations.stream().map(Specialization::getName).collect(Collectors.toList()));

        // 🔥 VALIDATION: ensure all IDs exist
        if (specializations.size() != uniqueIds.size()) {

            Set<Long> foundIds = specializations.stream()
                    .map(Specialization::getId)
                    .collect(Collectors.toSet());

            List<Long> missingIds = uniqueIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());

            log.debug("[REGISTER][DOCTOR][RESOLVE] failed: missingSpecializationIds={}", missingIds);

            throw new IllegalArgumentException(
                    "Invalid specialization IDs: " + missingIds);
        }

        return new LinkedHashSet<>(specializations);
    }

    // ================= HELPER =================
    private List<String> toSpecializationNames(Set<Specialization> specializations) {
        return specializations.stream()
                .map(Specialization::getName)
                .collect(Collectors.toList());
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Profile image is required.");
        }
        if (file.getSize() > maxProfileImageSizeBytes) {
            throw new IllegalArgumentException("Profile image exceeds maximum allowed size.");
        }

        fileUploadValidationService.validate(
                file,
                ALLOWED_PROFILE_IMAGE_TYPES,
                ALLOWED_PROFILE_IMAGE_EXTENSIONS);
    }
}