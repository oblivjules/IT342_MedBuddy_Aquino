package com.medbuddy.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UnifiedRecordFileResponse {

    private final Long fileId;
    private final Long appointmentId;
    private final Long medicalRecordId;
    private final String fileName;
    private final String fileUrl;
    private final String uploadedBy;
    private final LocalDateTime uploadedAt;
    private final boolean locked;
}
