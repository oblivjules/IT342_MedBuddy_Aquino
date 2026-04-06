package com.medbuddy.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.medbuddy.model.AppointmentSlotStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AppointmentSlotResponse {

    private final Long id;
    private final Long doctorAvailabilityId;
    private final Long doctorId;
    private final LocalDate slotDate;
    private final LocalTime slotStartTime;
    private final LocalTime slotEndTime;
    private final AppointmentSlotStatus status;
}
