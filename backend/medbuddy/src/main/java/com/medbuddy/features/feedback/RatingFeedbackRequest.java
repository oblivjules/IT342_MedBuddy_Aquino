package com.medbuddy.features.feedback;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RatingFeedbackRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotNull(message = "Rating score is required")
    @Min(value = 1, message = "Rating score must be at least 1")
    @Max(value = 5, message = "Rating score cannot exceed 5")
    private Integer ratingScore;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;

    @Size(max = 1000, message = "Feedback comment cannot exceed 1000 characters")
    private String feedbackComment;

    @Size(max = 1000, message = "Feedback cannot exceed 1000 characters")
    private String feedback;

    public Integer resolvedRating() {
        return rating != null ? rating : ratingScore;
    }

    public String resolvedFeedback() {
        return feedback != null ? feedback : feedbackComment;
    }
}
