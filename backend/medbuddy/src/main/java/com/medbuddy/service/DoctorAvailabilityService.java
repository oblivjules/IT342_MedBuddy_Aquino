package com.medbuddy.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.DoctorAvailabilityRequest;
import com.medbuddy.dto.DoctorAvailabilityResponse;
import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.AppointmentSlotStatus;
import com.medbuddy.model.AvailabilityStatus;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.repository.AppointmentSlotRepository;
import com.medbuddy.repository.DoctorAvailabilityRepository;
import com.medbuddy.service.facade.UserAccessFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorAvailabilityService {

    private static final int SLOT_DURATION_MINUTES = 30;
    private static final int CLINIC_CUTOFF_MINUTES = 30;
    private static final LocalTime UNAVAILABLE_DEFAULT_START = LocalTime.MIDNIGHT;
    private static final LocalTime UNAVAILABLE_DEFAULT_END = LocalTime.of(0, 30);

    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final UserAccessFacade userAccessFacade;

    @Transactional
    public DoctorAvailabilityResponse upsert(String doctorEmail,
                                             DoctorAvailabilityRequest request) {
        Doctor doctor = getDoctorProfile(doctorEmail);

        if (request.getAvailableDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot set availability for a past date.");
        }

        AvailabilityStatus resolvedStatus = request.getStatus() != null
                ? request.getStatus()
                : AvailabilityStatus.AVAILABLE;

        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();

        if (resolvedStatus == AvailabilityStatus.UNAVAILABLE && (startTime == null || endTime == null)) {
            startTime = UNAVAILABLE_DEFAULT_START;
            endTime = UNAVAILABLE_DEFAULT_END;
        }

        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required for available slots.");
        }

        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("End time must be after start time.");
        }

        DoctorAvailability saved = upsertSingleDate(
                doctor,
                request.getAvailableDate(),
                startTime,
                endTime,
                resolvedStatus);

        return toResponse(saved);
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
        return userAccessFacade.getDoctorByEmail(email);
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
        AvailabilityStatus resolvedStatus = status != null ? status : AvailabilityStatus.AVAILABLE;

        DoctorAvailability saved = availabilityRepository
                .findByDoctor_IdAndAvailableDate(doctor.getId(), date)
                .orElseGet(() -> DoctorAvailability.builder()
                        .doctor(doctor)
                        .availableDate(date)
                        .build());

        saved.setDoctor(doctor);
        saved.setAvailableDate(date);
        saved.setStartTime(startTime);
        saved.setEndTime(endTime);
        saved.setStatus(resolvedStatus);

        saved = availabilityRepository.saveAndFlush(saved);
        syncThirtyMinuteSlots(saved);
        return saved;
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
