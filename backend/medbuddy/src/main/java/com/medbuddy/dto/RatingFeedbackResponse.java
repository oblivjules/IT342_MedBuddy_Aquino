package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingFeedbackResponse {

    private Long id;
    private Long appointmentId;
    private PatientDto patient;
    private DoctorDto doctor;
    private String patientName;
    private Integer rating;
    private String feedback;
    private Integer ratingScore;
    private String feedbackComment;
    private LocalDateTime createdAt;
}
