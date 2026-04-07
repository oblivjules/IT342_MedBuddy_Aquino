package com.medbuddy.service.appointment.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medbuddy.model.Role;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppointmentAccessStrategyFactory {

    private final List<AppointmentAccessStrategy> strategies;

    public AppointmentAccessStrategy resolve(Role role) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(role))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported role: " + role));
    }
}
