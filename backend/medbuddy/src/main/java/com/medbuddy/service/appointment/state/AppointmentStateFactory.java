package com.medbuddy.service.appointment.state;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medbuddy.model.AppointmentStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentStateFactory {

    private final List<AppointmentState> states;

    public AppointmentState resolve(AppointmentStatus status) {
        return states.stream()
                .filter(candidate -> candidate.status() == status)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No state handler for status: " + status));
    }
}
