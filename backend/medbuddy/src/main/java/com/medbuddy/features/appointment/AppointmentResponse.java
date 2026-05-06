package com.medbuddy.features.appointment;

import java.time.LocalDateTime;

import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.PatientDto;
import com.medbuddy.shared.model.AppointmentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response shape for every appointment endpoint.
 * Never exposes passwords or internal entity internals.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentResponse {

    private Long id;
    private PatientDto patient;
    private DoctorDto doctor;
    private LocalDateTime dateTime;
    private AppointmentStatus status;
    private String notes;
    private String rejectionReason;
    private LocalDateTime createdAt;
}
