package com.medbuddy.features.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSummaryDto {

    private Long appointmentId;
    private String patientName;
    private String reasonForVisit;
    private String appointmentTime;
}