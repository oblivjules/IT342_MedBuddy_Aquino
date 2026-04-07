package com.medbuddy.service.appointment.booking;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

@Component
public class FutureSlotValidationHandler extends AbstractBookingValidationHandler {

    @Override
    protected void doValidate(BookingValidationContext context) {
        LocalDateTime appointmentDateTime = context.resolveAppointmentDateTime();
        if (!appointmentDateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment slot must be in the future.");
        }
    }
}
