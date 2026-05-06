package com.medbuddy.features.medicalrecords;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordResponse {

    private Long id;
    private Long appointmentId;
    private String diagnosis;
    private String prescriptionDetails;
    private String medicineName;
    private String dosage;
    private String route;
    private String frequency;
    private String duration;
    private String prescriptionNotes;
}
