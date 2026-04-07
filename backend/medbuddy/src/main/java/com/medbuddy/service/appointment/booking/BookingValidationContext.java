package com.medbuddy.service.appointment.booking;

import java.time.LocalDateTime;

import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.Doctor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BookingValidationContext {

    private final Doctor doctor;
    private final AppointmentSlot slot;

    public Long resolveSlotDoctorId() {
        Long slotDoctorId = slot.getDoctorId();
        if (slotDoctorId == null && slot.getDoctorAvailability() != null && slot.getDoctorAvailability().getDoctor() != null) {
            slotDoctorId = slot.getDoctorAvailability().getDoctor().getId();
        }
        return slotDoctorId;
    }

    public LocalDateTime resolveAppointmentDateTime() {
        return LocalDateTime.of(slot.getSlotDate(), slot.getSlotStartTime());
    }
}
