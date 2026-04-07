package com.medbuddy.service.appointment.booking;

import org.springframework.stereotype.Component;

import com.medbuddy.model.AppointmentSlotStatus;

@Component
public class SlotAvailabilityValidationHandler extends AbstractBookingValidationHandler {

    @Override
    protected void doValidate(BookingValidationContext context) {
        if (context.getSlot().getStatus() != AppointmentSlotStatus.AVAILABLE) {
            throw new IllegalStateException("Selected slot is not available.");
        }
    }
}
