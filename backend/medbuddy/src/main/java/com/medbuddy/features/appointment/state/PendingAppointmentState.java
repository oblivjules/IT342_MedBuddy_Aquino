package com.medbuddy.features.appointment.state;

import org.springframework.stereotype.Component;

import com.medbuddy.shared.model.AppointmentStatus;

@Component
public class PendingAppointmentState extends MutableAppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.PENDING;
    }
}
