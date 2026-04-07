package com.medbuddy.service.registration;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.medbuddy.dto.RegisterRequest;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DoctorProfileCreator implements UserProfileCreator {

    private final DoctorRepository doctorRepository;

    @Override
    public boolean supports(Role role) {
        return role == Role.DOCTOR;
    }

    @Override
    public void createProfile(User user, RegisterRequest request, Set<Specialization> doctorSpecializations) {
        Doctor doctor = Doctor.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .user(user)
                .build();

        doctor.setSpecializations(doctorSpecializations);
        doctorRepository.save(doctor);
    }
}
