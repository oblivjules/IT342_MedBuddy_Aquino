package com.medbuddy.features.appointment.state;

import com.medbuddy.shared.model.AppointmentStatus;

public interface AppointmentState {

    AppointmentStatus status();

    void validateTransition(AppointmentStatus targetStatus);
}
