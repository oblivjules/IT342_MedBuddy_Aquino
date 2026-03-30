package com.medbuddy.service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.AuthResponse;
import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.LoginRequest;
import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.dto.UserDto;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Provider;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.SpecializationRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final SpecializationRepository specializationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ── Register ────────────────────────────────────────────────────────
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalStateException(
                    "An account already exists with email: " + request.getEmail());
        }

        Set<Specialization> doctorSpecializations = resolveDoctorSpecializations(request);

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .provider(Provider.LOCAL)
                .build();

        user = userRepository.save(user);

        // Create corresponding profile entity
        if (request.getRole() == Role.PATIENT) {
            Patient patient = Patient.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .user(user)
                    .build();
            patientRepository.save(patient);
        } else {
            Doctor doctor = Doctor.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .phoneNumber(request.getPhoneNumber())
                    .user(user)
                    .build();
            doctor.setSpecializations(doctorSpecializations);
            doctorRepository.save(doctor);
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .user(buildUserDto(user))
                .build();
    }

    // ── Login ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password", ex);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + request.getEmail()));

        if (request.getRole() != null && request.getRole() != user.getRole()) {
            throw new BadCredentialsException(
                    "This account is not registered as a "
                            + request.getRole().name().toLowerCase(Locale.ROOT) + ".");
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .user(buildUserDto(user))
                .build();
    }

    // ── Get current user (for /me) ────────────────────────────────────
    @Transactional(readOnly = true)
    public UserDto getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
        return buildUserDto(user);
    }

    // ── List all doctors (for Find Doctor page) ────────────────────────
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
                        .specialization(d.getSpecializationsSummary())
                        .profileImageUrl(d.getUser().getProfileImageUrl())
                        .email(d.getUser().getEmail())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Internal helper ────────────────────────────────────────────────
    /**
     * Builds a UserDto by combining the User with its Patient or Doctor profile.
     * No password is included at any path.
     */
    UserDto buildUserDto(User user) {
        UserDto.UserDtoBuilder builder = UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .profileImageUrl(user.getProfileImageUrl());

        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser_Id(user.getId()).ifPresent(p -> {
                builder.profileId(p.getId())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .phoneNumber(p.getPhoneNumber());
            });
        } else if (user.getRole() == Role.DOCTOR) {
            doctorRepository.findByUser_Id(user.getId()).ifPresent(d -> {
                List<String> specNames = toSpecializationNames(d.getSpecializations());
                builder.profileId(d.getId())
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .phoneNumber(d.getPhoneNumber())
                        .specializations(specNames)
                        .specialization(d.getSpecializationsSummary());
            });
        }

        return builder.build();
    }

    private Set<Specialization> resolveDoctorSpecializations(RegisterRequest request) {
        if (request.getRole() != Role.DOCTOR) {
            return Set.of();
        }

        if (request.getSpecializationIds() != null && !request.getSpecializationIds().isEmpty()) {
            return resolveByIds(request.getSpecializationIds());
        }

        if (request.getSpecializations() != null && !request.getSpecializations().isEmpty()) {
            return resolveByNames(request.getSpecializations());
        }

        if (request.getSpecialization() != null && !request.getSpecialization().isBlank()) {
            return resolveByNames(List.of(request.getSpecialization()));
        }

        throw new IllegalArgumentException("At least one specialization is required for doctors.");
    }

    private Set<Specialization> resolveByIds(List<Long> specializationIds) {
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

    private Set<Specialization> resolveByNames(List<String> names) {
        Set<Specialization> resolved = new LinkedHashSet<>();

        for (String rawName : names) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }

            String cleanName = rawName.trim();
            Specialization specialization = specializationRepository
                    .findByNameIgnoreCase(cleanName)
                    .orElseGet(() -> specializationRepository.save(
                            Specialization.builder().name(cleanName).build()));

            resolved.add(specialization);
        }

        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("At least one specialization is required for doctors.");
        }

        return resolved;
    }

    private List<String> toSpecializationNames(Set<Specialization> specializations) {
        return specializations.stream()
                .map(Specialization::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
