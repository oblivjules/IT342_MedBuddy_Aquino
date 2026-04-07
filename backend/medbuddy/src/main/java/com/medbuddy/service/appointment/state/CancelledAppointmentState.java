package com.medbuddy.service.appointment.state;

import org.springframework.stereotype.Component;

import com.medbuddy.model.AppointmentStatus;

@Component
public class CancelledAppointmentState extends TerminalAppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.CANCELLED;
    }
}
