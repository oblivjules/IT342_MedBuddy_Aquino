package com.medbuddy.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.medbuddy.model.AvailabilityStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DoctorAvailabilityRequest {

    @NotNull(message = "Available date is required")
    private LocalDate availableDate;

    /**
     * Required for AVAILABLE entries.
     * Optional for UNAVAILABLE entries (service applies defaults).
     */
    private LocalTime startTime;

    /**
     * Required for AVAILABLE entries.
     * Optional for UNAVAILABLE entries (service applies defaults).
     */
    private LocalTime endTime;

    /** Optional — defaults to AVAILABLE if not supplied. */
    private AvailabilityStatus status;
}
