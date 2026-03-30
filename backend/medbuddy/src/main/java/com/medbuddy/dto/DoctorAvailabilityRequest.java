package com.medbuddy.dto;

import com.medbuddy.model.AvailabilityStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Data;

@Data
public class DoctorAvailabilityRequest {

    @NotNull(message = "availableDate is required")
    @FutureOrPresent(message = "availableDate must be today or in the future")
    private LocalDate availableDate;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;

    private AvailabilityStatus status;
}

