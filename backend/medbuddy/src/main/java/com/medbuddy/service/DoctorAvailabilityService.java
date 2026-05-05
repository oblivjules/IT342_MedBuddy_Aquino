package com.medbuddy.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.DoctorAvailabilityRequest;
import com.medbuddy.dto.DoctorAvailabilityResponse;
import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.AppointmentSlotStatus;
import com.medbuddy.model.AvailabilityStatus;
import com.medbuddy.model.Doctor;
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
public class DoctorAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(DoctorAvailabilityService.class);
    private static final int SLOT_DURATION_MINUTES = 30;
    private static final int CLINIC_CUTOFF_MINUTES = 30;

    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    @Transactional
    public DoctorAvailabilityResponse upsert(String doctorEmail,
                                             DoctorAvailabilityRequest request) {
        try {
            log.debug("[SERVICE] upsert called for doctor email: {}", doctorEmail);
            log.debug("[SERVICE] Request - availableDate: {}, startTime: {}, endTime: {}, status: {}",
                    request.getAvailableDate(), request.getStartTime(), request.getEndTime(), request.getStatus());
            
            Doctor doctor = getDoctorProfile(doctorEmail);
            log.debug("[SERVICE] Doctor profile retrieved - id: {}, name: {} {}", 
                    doctor.getId(), doctor.getFirstName(), doctor.getLastName());

            if (request.getAvailableDate().isBefore(LocalDate.now())) {
                log.warn("[SERVICE] Attempted to set availability for past date: {}", request.getAvailableDate());
                throw new IllegalArgumentException("Cannot set availability for a past date.");
            }

            if (!request.getStartTime().isBefore(request.getEndTime())) {
                log.warn("[SERVICE] Invalid time range - start: {}, end: {}", request.getStartTime(), request.getEndTime());
                throw new IllegalArgumentException("End time must be after start time.");
            }

            DoctorAvailability saved = upsertSingleDate(
                    doctor,
                    request.getAvailableDate(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getStatus());
            
            log.debug("[SERVICE] upsert completed - availability id: {}", saved.getId());
            return toResponse(saved);
        } catch (Exception e) {
            log.error("[SERVICE] Exception in upsert for doctor: {}", doctorEmail, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getByDoctor(Long doctorId) {
        return availabilityRepository
                .findByDoctor_IdOrderByAvailableDateAsc(doctorId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponse> getByDoctorAndDate(Long doctorId, LocalDate date) {
        return availabilityRepository
            .findByDoctor_IdAndAvailableDate(doctorId, date)
            .map(List::of)
            .orElseGet(List::of)
            .stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public void delete(String doctorEmail, LocalDate date) {
        Doctor doctor = getDoctorProfile(doctorEmail);
        DoctorAvailability availability = availabilityRepository.findByDoctor_IdAndAvailableDate(doctor.getId(), date).orElse(null);

        if (availability == null) {
            throw new IllegalArgumentException("No availability slot found for date: " + date);
        }

        List<AppointmentSlot> slots = appointmentSlotRepository
            .findByDoctorAvailability_IdOrderBySlotDateAscSlotStartTimeAsc(availability.getId());

        boolean hasBookedSlots = slots.stream().anyMatch(slot -> slot.getStatus() == AppointmentSlotStatus.BOOKED);
        if (hasBookedSlots) {
            throw new IllegalStateException("Cannot delete availability with existing booked appointments.");
        }

        if (!slots.isEmpty()) {
            appointmentSlotRepository.deleteAll(slots);
        }

        availabilityRepository.delete(availability);
    }

    @Transactional(readOnly = true)
    public Long getAuthenticatedDoctorId(String doctorEmail) {
        return getDoctorProfile(doctorEmail).getId();
    }

    private Doctor getDoctorProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can manage availability slots.");
        }
        return doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Doctor profile not found for user: " + email));
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailability slot) {
        Doctor d = slot.getDoctor();
        return DoctorAvailabilityResponse.builder()
                .doctorId(d.getId())
                .availableDate(slot.getAvailableDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .doctorName(d.getFirstName() + " " + d.getLastName())
                .build();
    }

    private DoctorAvailability upsertSingleDate(Doctor doctor,
                                                LocalDate date,
                                                LocalTime startTime,
                                                LocalTime endTime,
                                                AvailabilityStatus status) {
        try {
            log.debug("[SERVICE.upsertSingleDate] Called for doctor: {}, date: {}", doctor.getId(), date);
            
            AvailabilityStatus resolvedStatus = status != null ? status : AvailabilityStatus.AVAILABLE;
            log.debug("[SERVICE.upsertSingleDate] Resolved status: {}", resolvedStatus);

            DoctorAvailability saved = availabilityRepository
                    .findByDoctor_IdAndAvailableDate(doctor.getId(), date)
                    .orElseGet(() -> {
                        log.debug("[SERVICE.upsertSingleDate] No existing availability, creating new entity");
                        return DoctorAvailability.builder()
                                .doctor(doctor)
                                .availableDate(date)
                                .build();
                    });

            log.debug("[SERVICE.upsertSingleDate] Setting entity fields - id: {}, isNew: {}", 
                    saved.getId(), saved.getId() == null);

            saved.setDoctor(doctor);
            saved.setAvailableDate(date);
            saved.setStartTime(startTime);
            saved.setEndTime(endTime);
            saved.setStatus(resolvedStatus);

            log.debug("[SERVICE.upsertSingleDate] Entity prepared - id: {}, date: {}, start: {}, end: {}, status: {}",
                    saved.getId(), saved.getAvailableDate(), saved.getStartTime(), saved.getEndTime(), saved.getStatus());

            saved = availabilityRepository.saveAndFlush(saved);
            log.debug("[SERVICE.upsertSingleDate] Entity persisted - id: {}", saved.getId());

            syncThirtyMinuteSlots(saved);
            log.debug("[SERVICE.upsertSingleDate] Slot sync completed for availability id: {}", saved.getId());
            
            return saved;
        } catch (Exception e) {
            log.error("[SERVICE.upsertSingleDate] Exception occurred", e);
            throw e;
        }
    }

    private void syncThirtyMinuteSlots(DoctorAvailability availability) {
        if (availability.getId() == null) {
            return;
        }

        List<AppointmentSlot> existingSlots = appointmentSlotRepository
            .findByDoctorAvailability_IdOrderBySlotDateAscSlotStartTimeAsc(
                availability.getId());

        if (availability.getStatus() != AvailabilityStatus.AVAILABLE) {
            removeUnbookedSlots(existingSlots);
            return;
        }

        Map<String, AppointmentSlot> existingByRange = new HashMap<>();
        for (AppointmentSlot slot : existingSlots) {
            existingByRange.put(slotKey(slot.getSlotStartTime(), slot.getSlotEndTime()), slot);
        }

        List<AppointmentSlot> toCreate = new ArrayList<>();
        LocalTime pointer = availability.getStartTime();
        LocalTime latestSlotEnd = availability.getEndTime().minusMinutes(CLINIC_CUTOFF_MINUTES);

        while (!pointer.plusMinutes(SLOT_DURATION_MINUTES).isAfter(latestSlotEnd)) {
            LocalTime end = pointer.plusMinutes(SLOT_DURATION_MINUTES);
            String key = slotKey(pointer, end);

            if (existingByRange.remove(key) == null) {
                toCreate.add(AppointmentSlot.builder()
                        .doctorAvailability(availability)
                        .doctorId(availability.getDoctor().getId())
                    .slotDate(availability.getAvailableDate())
                        .slotStartTime(pointer)
                        .slotEndTime(end)
                        .status(AppointmentSlotStatus.AVAILABLE)
                        .build());
            }

            pointer = end;
        }

        if (!toCreate.isEmpty()) {
            appointmentSlotRepository.saveAll(toCreate);
        }

        List<AppointmentSlot> staleSlots = existingByRange.values().stream()
                .filter(slot -> slot.getStatus() != AppointmentSlotStatus.BOOKED)
                .toList();

        if (!staleSlots.isEmpty()) {
            appointmentSlotRepository.deleteAll(staleSlots);
        }
    }

    private void removeUnbookedSlots(List<AppointmentSlot> slots) {
        List<AppointmentSlot> removable = slots.stream()
                .filter(slot -> slot.getStatus() != AppointmentSlotStatus.BOOKED)
                .toList();

        if (!removable.isEmpty()) {
            appointmentSlotRepository.deleteAll(removable);
        }
    }

    private String slotKey(LocalTime start, LocalTime end) {
        return start + "|" + end;
    }

}
