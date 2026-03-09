package com.medbuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
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

        if (request.getRole() == Role.DOCTOR && (request.getSpecialization() == null
                || request.getSpecialization().isBlank())) {
            throw new IllegalArgumentException("Specialization is required for doctors.");
        }

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
                    .specialization(request.getSpecialization())
                    .user(user)
                    .build();
            doctorRepository.save(doctor);
        }

        String token = jwtUtil.generateToken(user.getEmail());
        return AuthResponse.builder()
                .token(token)
                .user(buildUserDto(user))
                .build();
    }

    // ── Login ────────────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + request.getEmail()));

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
                        .specialization(d.getSpecialization())
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
                .role(user.getRole());

        if (user.getRole() == Role.PATIENT) {
            patientRepository.findByUser_Id(user.getId()).ifPresent(p -> {
                builder.profileId(p.getId())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .phoneNumber(p.getPhoneNumber());
            });
        } else if (user.getRole() == Role.DOCTOR) {
            doctorRepository.findByUser_Id(user.getId()).ifPresent(d -> {
                builder.profileId(d.getId())
                        .firstName(d.getFirstName())
                        .lastName(d.getLastName())
                        .phoneNumber(d.getPhoneNumber())
                        .specialization(d.getSpecialization());
            });
        }

        return builder.build();
    }
}
