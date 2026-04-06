package com.medbuddy.service;

import com.medbuddy.dto.DoctorAvailabilityRequest;
import com.medbuddy.dto.DoctorAvailabilityResponse;
import com.medbuddy.model.AvailabilityStatus;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailability(Long doctorId) {
        return availabilityRepository.findByDoctor_IdOrderByAvailableDateAsc(doctorId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getDoctorAvailabilityByDate(Long doctorId, LocalDate date) {
        return availabilityRepository.findByDoctor_IdAndAvailableDate(doctorId, date)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public DoctorAvailabilityResponse createAvailability(String userEmail, DoctorAvailabilityRequest request) {
        User user = findUserByEmail(userEmail);
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can create availability.");
        }

        Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + userEmail));

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new IllegalArgumentException("endTime must be after startTime.");
        }

        List<DoctorAvailability> daySlots = availabilityRepository
                .findByDoctor_IdAndAvailableDateBetweenOrderByAvailableDateAsc(
                        doctor.getId(),
                        request.getAvailableDate(),
                        request.getAvailableDate());

        boolean hasOverlap = daySlots.stream().anyMatch(slot ->
                request.getStartTime().isBefore(slot.getEndTime()) && slot.getStartTime().isBefore(request.getEndTime()));
        if (hasOverlap) {
            throw new IllegalArgumentException("Availability overlaps with an existing time slot.");
        }

        DoctorAvailability availability = DoctorAvailability.builder()
            .availableDate(request.getAvailableDate())
                .doctor(doctor)
            .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(request.getStatus() != null ? request.getStatus() : AvailabilityStatus.AVAILABLE)
                .build();

        return toResponse(availabilityRepository.save(availability));
    }

    @Transactional
    public void deleteAvailabilityByDate(String userEmail, LocalDate date) {
        User user = findUserByEmail(userEmail);
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can delete availability.");
        }

        Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + userEmail));

        availabilityRepository.deleteByDoctor_IdAndAvailableDate(doctor.getId(), date);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + email));
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailability availability) {
        return DoctorAvailabilityResponse.builder()
                .doctorId(availability.getDoctor().getId())
                .availableDate(availability.getAvailableDate())
                .startTime(availability.getStartTime())
                .endTime(availability.getEndTime())
                .status(availability.getStatus())
                .build();
    }
}

