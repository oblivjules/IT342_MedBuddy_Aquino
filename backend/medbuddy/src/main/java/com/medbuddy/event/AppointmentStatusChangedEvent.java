package com.medbuddy.event;

import com.medbuddy.model.AppointmentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AppointmentStatusChangedEvent {

    private final Long appointmentId;
    private final AppointmentStatus previousStatus;
    private final AppointmentStatus newStatus;
    private final String actorEmail;
}
