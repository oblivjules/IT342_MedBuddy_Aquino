package com.medbuddy.service.facade;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserAccessFacade {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + email));
    }

    public Patient getPatientByEmail(String email) {
        User user = findUserByEmail(email);
        requireRole(user, Role.PATIENT, "Only patients can perform this action.");

        return patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + email));
    }

    public Doctor getDoctorByEmail(String email) {
        User user = findUserByEmail(email);
        requireRole(user, Role.DOCTOR, "Only doctors can perform this action.");

        return doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + email));
    }

    public void requireRole(User user, Role role, String message) {
        if (user.getRole() != role) {
            throw new AccessDeniedException(message);
        }
    }
}
