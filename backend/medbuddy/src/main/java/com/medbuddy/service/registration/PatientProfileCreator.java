package com.medbuddy.service.registration;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.medbuddy.features.auth.RegisterRequest;
import com.medbuddy.features.user.PatientRepository;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.Specialization;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PatientProfileCreator implements UserProfileCreator {

    private final PatientRepository patientRepository;

    @Override
    public boolean supports(Role role) {
        return role == Role.PATIENT;
    }

    @Override
    public void createProfile(User user, RegisterRequest request, Set<Specialization> doctorSpecializations) {
        Patient patient = Patient.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .user(user)
                .build();
        patientRepository.save(patient);
    }
}
