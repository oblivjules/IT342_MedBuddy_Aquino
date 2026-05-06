package com.medbuddy.features.appointment;

import com.medbuddy.shared.model.AppointmentStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PATCH /api/appointments/{id}/status.
 * Doctors use this to confirm or cancel; patients can cancel.
 */
@Data
public class AppointmentStatusRequest {

    @NotNull(message = "Status is required")
    private AppointmentStatus status;

    @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
    private String rejectionReason;
}
