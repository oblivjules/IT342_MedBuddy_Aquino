package com.medbuddy.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.AppointmentSlotRequest;
import com.medbuddy.dto.AppointmentSlotResponse;
import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.AppointmentSlotStatus;
import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.AppointmentSlotRepository;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentSlotService {

    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorAvailabilityRepository doctorAvailabilityRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional
    public AppointmentSlotResponse create(String doctorEmail, AppointmentSlotRequest request) {
        User doctorUser = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + doctorEmail));

        if (doctorUser.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can create appointment slots.");
        }

        DoctorAvailability availability = doctorAvailabilityRepository.findById(request.getDoctorAvailabilityId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Doctor availability not found with id: " + request.getDoctorAvailabilityId()));

        Long ownerDoctorId = availability.getDoctor().getUser().getId();
        if (!ownerDoctorId.equals(doctorUser.getId())) {
            throw new AccessDeniedException("You can only create slots for your own availability.");
        }

        if (!request.getSlotDate().equals(availability.getAvailableDate())) {
            throw new IllegalArgumentException("slotDate must match doctor availability date.");
        }

        if (!request.getSlotStartTime().isBefore(request.getSlotEndTime())) {
            throw new IllegalArgumentException("slotStartTime must be before slotEndTime.");
        }

        AppointmentSlot slot = AppointmentSlot.builder()
                .doctorAvailability(availability)
            .doctorId(availability.getDoctor().getId())
                .slotDate(request.getSlotDate())
                .slotStartTime(request.getSlotStartTime())
                .slotEndTime(request.getSlotEndTime())
                .status(request.getStatus() == null ? AppointmentSlotStatus.AVAILABLE : request.getStatus())
                .build();

        return toResponse(appointmentSlotRepository.save(slot));
    }

    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> getByAvailability(Long doctorAvailabilityId) {
        return appointmentSlotRepository.findByDoctorAvailability_IdOrderBySlotDateAscSlotStartTimeAsc(doctorAvailabilityId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentSlotResponse> getByDoctorAndDate(Long doctorId, LocalDate slotDate) {
        return appointmentSlotRepository
                .findByDoctorIdAndSlotDateAndStatus(doctorId, slotDate, AppointmentSlotStatus.AVAILABLE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppointmentSlotResponse updateStatus(String doctorEmail, Long slotId, AppointmentSlotStatus status) {
        User doctorUser = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + doctorEmail));
        if (doctorUser.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can update appointment slots.");
        }

        AppointmentSlot slot = appointmentSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment slot not found with id: " + slotId));

        Long authenticatedDoctorProfileId = doctorRepository.findByUser_Id(doctorUser.getId())
            .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + doctorEmail))
            .getId();

        Long ownerDoctorId = slot.getDoctorId();
        if (ownerDoctorId == null && slot.getDoctorAvailability() != null && slot.getDoctorAvailability().getDoctor() != null) {
            ownerDoctorId = slot.getDoctorAvailability().getDoctor().getId();
        }
        if (ownerDoctorId == null) {
            throw new IllegalStateException("Appointment slot has no associated doctor.");
        }
        if (!ownerDoctorId.equals(authenticatedDoctorProfileId)) {
            throw new AccessDeniedException("You can only update your own appointment slots.");
        }

        slot.setStatus(status);
        return toResponse(appointmentSlotRepository.save(slot));
    }

    private AppointmentSlotResponse toResponse(AppointmentSlot slot) {
        Long resolvedDoctorAvailabilityId =
                slot.getDoctorAvailability() != null ? slot.getDoctorAvailability().getId() : null;
        Long resolvedDoctorId = slot.getDoctorId();
        if (resolvedDoctorId == null && slot.getDoctorAvailability() != null) {
            resolvedDoctorId = slot.getDoctorAvailability().getDoctor().getId();
        }

        return AppointmentSlotResponse.builder()
                .id(slot.getId())
                .doctorAvailabilityId(resolvedDoctorAvailabilityId)
                .doctorId(resolvedDoctorId)
                .slotDate(slot.getSlotDate())
                .slotStartTime(slot.getSlotStartTime())
                .slotEndTime(slot.getSlotEndTime())
                .status(slot.getStatus())
                .build();
    }

}
