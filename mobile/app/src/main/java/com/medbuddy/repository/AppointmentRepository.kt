package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.dto.AppointmentRequest
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.AppointmentSlotResponseDto
import com.medbuddy.dto.AppointmentStatusRequest

data class SlotUiModel(
    val id: Long,
    val time24: String,
    val label: String
)

class AppointmentRepository(
    private val apiService: ApiService
) {

    suspend fun getAppointments(): List<AppointmentResponse> {
        val raw = apiService.getAppointments()
        return raw.map { it.copy(status = AppointmentStatus.normalize(it.status)) }
    }

    suspend fun getPatientAppointments(): List<AppointmentResponse> =
        getAppointments()

    suspend fun getDoctorAppointments(): List<AppointmentResponse> =
        getAppointments()

    suspend fun updateAppointmentStatus(id: Long, status: String): AppointmentResponse {
        return apiService.updateAppointmentStatus(id, AppointmentStatusRequest(AppointmentStatus.normalize(status)))
    }

    suspend fun getSlotsByDoctorDate(doctorId: Long, date: String): List<SlotUiModel> {
        val slots = apiService.getDoctorAppointmentSlotsByDate(doctorId, date)
        return slots
            .filter { AppointmentStatus.normalize(it.status ?: "") == AppointmentStatus.PENDING || (it.status ?: "").equals("AVAILABLE", true) }
            .mapNotNull { slot ->
                val time = normalizeTime(slot.slotStartTime)
                if (slot.id <= 0 || time == null) {
                    null
                } else {
                    SlotUiModel(
                        id = slot.id,
                        time24 = time,
                        label = formatTo12Hour(time)
                    )
                }
            }
            .sortedBy { it.time24 }
    }

    suspend fun bookAppointment(doctorId: Long, slotId: Long, notes: String?): AppointmentResponse {
        return apiService.bookAppointment(
            AppointmentRequest(
                doctorId = doctorId,
                slotId = slotId,
                notes = notes
            )
        )
    }

    private fun normalizeTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return when {
            text.length >= 5 -> text.substring(0, 5)
            else -> null
        }
    }

    private fun formatTo12Hour(time24: String): String {
        val parts = time24.split(":")
        if (parts.size < 2) return time24
        val hour = parts[0].toIntOrNull() ?: return time24
        val minute = parts[1].toIntOrNull() ?: return time24
        val period = if (hour >= 12) "PM" else "AM"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "$hour12:${minute.toString().padStart(2, '0')} $period"
    }
}
