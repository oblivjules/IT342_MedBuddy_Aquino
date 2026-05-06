package com.medbuddy.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.medbuddy.shared.model.AvailabilityStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DoctorAvailabilityRequest {

    @NotNull(message = "Available date is required")
    private LocalDate availableDate;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    private LocalTime endTime;

    /** Optional — defaults to AVAILABLE if not supplied. */
    private AvailabilityStatus status;
}
