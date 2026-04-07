package com.medbuddy.service.appointment.booking;

import org.springframework.stereotype.Component;

@Component
public class SlotDoctorOwnershipValidationHandler extends AbstractBookingValidationHandler {

    @Override
    protected void doValidate(BookingValidationContext context) {
        Long slotDoctorId = context.resolveSlotDoctorId();
        if (slotDoctorId == null) {
            throw new IllegalStateException("Selected slot has no associated doctor.");
        }

        if (!slotDoctorId.equals(context.getDoctor().getId())) {
            throw new IllegalArgumentException("Selected slot does not belong to the requested doctor.");
        }
    }
}
