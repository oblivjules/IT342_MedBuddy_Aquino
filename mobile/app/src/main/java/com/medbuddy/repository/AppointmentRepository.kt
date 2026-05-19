package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.dto.AppointmentRequest
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.AppointmentSlotResponseDto
import com.medbuddy.dto.AppointmentStatusRequest

data class SlotUiModel(
    val id: Long,
    val time24: String,
    val label: String,
    val status: String
)

class AppointmentRepository(
    private val apiService: ApiService
) {

    suspend fun getAppointments(): List<AppointmentResponse> {
        val raw = apiService.getAppointments().bodyOrThrow()
        return raw.map { it.copy(status = AppointmentStatus.normalize(it.status)) }
    }

    suspend fun getPatientAppointments(): List<AppointmentResponse> {
        val raw = apiService.getMyAppointments().bodyOrThrow()
        return raw.map { it.copy(status = AppointmentStatus.normalize(it.status)) }
    }

    suspend fun getDoctorAppointments(): List<AppointmentResponse> =
        getAppointments()

    suspend fun updateAppointmentStatus(id: Long, status: String): AppointmentResponse {
        return apiService.updateAppointmentStatus(id, AppointmentStatusRequest(AppointmentStatus.normalize(status))).bodyOrThrow()
    }

    suspend fun getSlotsByDoctorDate(doctorId: Long, date: String): List<SlotUiModel> {
        val slots = apiService.getDoctorAppointmentSlots(doctorId, date).bodyOrThrow()
        return slots
            .mapNotNull { slot ->
                if (slot.id <= 0) return@mapNotNull null
                val time = normalizeTime(slot.slotStartTime) ?: return@mapNotNull null
                SlotUiModel(
                    id = slot.id,
                    time24 = time,
                    label = formatTo12Hour(time),
                    status = (slot.status ?: "AVAILABLE").trim().uppercase()
                )
            }
            .sortedBy { it.time24 }
    }

    suspend fun bookAppointment(doctorId: Long, dateTime: String, notes: String?): AppointmentResponse {
        return apiService.bookAppointment(
            AppointmentRequest(
                doctorId = doctorId,
                dateTime = dateTime,
                notes = notes
            )
        ).bodyOrThrow()
    }

    suspend fun bookAppointment(doctorId: Long, slotId: Long, notes: String?): AppointmentResponse {
        return apiService.bookAppointment(
            AppointmentRequest(
                doctorId = doctorId,
                slotId = slotId,
                notes = notes
            )
        ).bodyOrThrow()
    }

    private fun normalizeTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        // Pad single-digit hours like "9:30:00" → "09:30:00"
        val padded = if (text.length > 1 && text[1] == ':') "0$text" else text
        return if (padded.length >= 5) padded.substring(0, 5) else null
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
