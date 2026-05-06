package com.medbuddy.features.appointment.state;

import com.medbuddy.shared.model.AppointmentStatus;

public abstract class TerminalAppointmentState implements AppointmentState {

    @Override
    public void validateTransition(AppointmentStatus targetStatus) {
        throw new IllegalStateException("Cannot modify an appointment that is already " + status() + ".");
    }
}
