package com.medbuddy.features.medicalrecords;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MedicalRecordRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotBlank(message = "Diagnosis is required")
    @Size(max = 2000, message = "Diagnosis cannot exceed 2000 characters")
    private String diagnosis;

    @Size(max = 2000, message = "Prescription details cannot exceed 2000 characters")
    private String prescriptionDetails;

    @Size(max = 255, message = "Medicine name cannot exceed 255 characters")
    private String medicineName;

    @Size(max = 255, message = "Dosage cannot exceed 255 characters")
    private String dosage;

    @Size(max = 255, message = "Route cannot exceed 255 characters")
    private String route;

    @Size(max = 255, message = "Frequency cannot exceed 255 characters")
    private String frequency;

    @Size(max = 255, message = "Duration cannot exceed 255 characters")
    private String duration;

    @Size(max = 500, message = "Prescription notes cannot exceed 500 characters")
    private String prescriptionNotes;
}
