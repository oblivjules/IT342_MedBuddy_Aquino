package com.medbuddy.service.appointment.state;

import com.medbuddy.model.AppointmentStatus;

public interface AppointmentState {

    AppointmentStatus status();

    void validateTransition(AppointmentStatus targetStatus);
}
