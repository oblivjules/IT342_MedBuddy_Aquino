package com.medbuddy.service.appointment.booking;

public interface BookingValidationHandler {

    BookingValidationHandler setNext(BookingValidationHandler next);

    void validate(BookingValidationContext context);
}
