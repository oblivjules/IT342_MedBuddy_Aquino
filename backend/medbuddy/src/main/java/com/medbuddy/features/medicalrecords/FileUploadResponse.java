package com.medbuddy.features.medicalrecords;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {

    private final Long id;
    private final Long medicalRecordId;
    private final Long appointmentId;
    private final Long uploadedByUserId;
    private final String fileName;
    private final String storagePath;
    private final String fileUrl;
    private final String fileType;
    private final String fileExtension;
    private final Long fileSizeBytes;
    private final LocalDateTime uploadedAt;
}