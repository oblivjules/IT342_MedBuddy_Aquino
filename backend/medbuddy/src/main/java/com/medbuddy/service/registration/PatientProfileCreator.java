package com.medbuddy.service.registration;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;
import com.medbuddy.repository.PatientRepository;

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
