package com.medbuddy.service.appointment.booking;

import org.springframework.stereotype.Component;

import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.Doctor;

@Component
public class BookingValidationChain {

    private final BookingValidationHandler chain;

    public BookingValidationChain(
            SlotDoctorOwnershipValidationHandler slotDoctorOwnershipValidationHandler,
            SlotAvailabilityValidationHandler slotAvailabilityValidationHandler,
            FutureSlotValidationHandler futureSlotValidationHandler) {
        slotDoctorOwnershipValidationHandler
                .setNext(slotAvailabilityValidationHandler)
                .setNext(futureSlotValidationHandler);

        this.chain = slotDoctorOwnershipValidationHandler;
    }

    public void validate(Doctor doctor, AppointmentSlot slot) {
        chain.validate(new BookingValidationContext(doctor, slot));
    }
}
