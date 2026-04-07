package com.medbuddy.service.appointment.state;

import com.medbuddy.model.AppointmentStatus;

public abstract class MutableAppointmentState implements AppointmentState {

    @Override
    public void validateTransition(AppointmentStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("Status is required.");
        }
    }
}
