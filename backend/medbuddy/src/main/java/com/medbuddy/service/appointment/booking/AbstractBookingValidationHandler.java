package com.medbuddy.service.appointment.booking;

public abstract class AbstractBookingValidationHandler implements BookingValidationHandler {

    private BookingValidationHandler next;

    @Override
    public BookingValidationHandler setNext(BookingValidationHandler next) {
        this.next = next;
        return next;
    }

    @Override
    public void validate(BookingValidationContext context) {
        doValidate(context);
        if (next != null) {
            next.validate(context);
        }
    }

    protected abstract void doValidate(BookingValidationContext context);
}
