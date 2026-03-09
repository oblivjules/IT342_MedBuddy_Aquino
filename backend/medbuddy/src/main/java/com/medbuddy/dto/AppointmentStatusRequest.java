package com.medbuddy.dto;

import com.medbuddy.model.AppointmentStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for PATCH /api/appointments/{id}/status.
 * Doctors use this to confirm or cancel; patients can cancel.
 */
@Data
public class AppointmentStatusRequest {

    @NotNull(message = "Status is required")
    private AppointmentStatus status;
}
