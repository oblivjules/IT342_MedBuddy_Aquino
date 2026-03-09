package com.medbuddy.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/appointments (patients booking an appointment).
 * {@code doctorId} is the Doctor profile ID (primary key of the doctors table).
 */
@Data
public class AppointmentRequest {

    @NotNull(message = "Doctor ID is required")
    private Long doctorId;

    @NotNull(message = "Date/time is required")
    @Future(message = "Appointment must be scheduled in the future")
    private LocalDateTime dateTime;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
}
