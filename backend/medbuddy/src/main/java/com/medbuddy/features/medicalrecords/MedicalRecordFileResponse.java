package com.medbuddy.features.medicalrecords;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecordFileResponse {

    private Long id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private Long fileSizeBytes;
    private String description;
    private LocalDateTime uploadedAt;
    private Long uploadedByUserId;
    private Long patientId;
    private Long appointmentId;
}

