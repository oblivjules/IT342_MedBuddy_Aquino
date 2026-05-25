package com.medbuddy.dto

data class DoctorDashboardResponse(
    val doctorName: String,
    val todayAppointmentsCount: Int,
    val completedTodayCount: Int,
    val nextPatient: NextPatientDto? = null,
    val upcomingToday: List<AppointmentSummaryDto> = emptyList()
)

data class NextPatientDto(
    val appointmentId: Long? = null,
    val patientName: String,
    val appointmentTime: String,
    val reasonForVisit: String
)

data class AppointmentSummaryDto(
    val appointmentId: Long? = null,
    val patientName: String,
    val reasonForVisit: String,
    val appointmentTime: String
)