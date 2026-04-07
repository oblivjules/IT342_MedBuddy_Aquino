package com.medbuddy.service.appointment.state;

import org.springframework.stereotype.Component;

import com.medbuddy.model.AppointmentStatus;

@Component
public class ConfirmedAppointmentState extends MutableAppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.CONFIRMED;
    }
}
