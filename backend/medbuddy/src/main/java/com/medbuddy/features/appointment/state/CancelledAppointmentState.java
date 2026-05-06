package com.medbuddy.features.appointment.state;

import org.springframework.stereotype.Component;

import com.medbuddy.shared.model.AppointmentStatus;

@Component
public class CancelledAppointmentState extends TerminalAppointmentState {

    @Override
    public AppointmentStatus status() {
        return AppointmentStatus.CANCELLED;
    }
}
