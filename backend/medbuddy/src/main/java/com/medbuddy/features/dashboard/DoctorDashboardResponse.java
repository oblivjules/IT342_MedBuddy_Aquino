package com.medbuddy.features.dashboard;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDashboardResponse {

    private String doctorName;
    private int todayAppointmentsCount;
    private int completedTodayCount;
    private NextPatientDto nextPatient;

    @Builder.Default
    private List<AppointmentSummaryDto> upcomingToday = List.of();
}