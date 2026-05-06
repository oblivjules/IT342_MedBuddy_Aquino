package com.medbuddy.features.appointment;

import java.time.LocalDate;
import java.time.LocalTime;

import com.medbuddy.shared.model.AppointmentSlotStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppointmentSlotRequest {

    @NotNull
    private Long doctorAvailabilityId;

    @NotNull
    private LocalDate slotDate;

    @NotNull
    private LocalTime slotStartTime;

    @NotNull
    private LocalTime slotEndTime;

    private AppointmentSlotStatus status;
}
