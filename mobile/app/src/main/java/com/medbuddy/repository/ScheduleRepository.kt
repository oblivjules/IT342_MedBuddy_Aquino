package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.dto.DoctorAvailabilityRequest
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.TemplateRequestDto

class ScheduleRepository(
    private val apiService: ApiService
) {

    suspend fun getMyAvailability(): List<DoctorAvailabilityResponse> = apiService.getMyAvailability()

    suspend fun getDoctorAvailability(doctorId: Long): List<DoctorAvailabilityResponse> =
        apiService.getDoctorAvailability(doctorId)

    suspend fun addAvailability(date: String, startTime: String, endTime: String, status: String = "AVAILABLE") {
        apiService.addAvailability(
            DoctorAvailabilityRequest(
                availableDate = date,
                startTime = startTime,
                endTime = endTime,
                status = status
            )
        )
    }

    suspend fun deleteAvailabilityByDate(date: String) {
        apiService.deleteAvailabilityByDate(date)
    }

    suspend fun getTemplate(): List<TemplateRequestDto> = apiService.getMyScheduleTemplate()

    suspend fun saveTemplate(template: List<TemplateRequestDto>) {
        apiService.saveMyScheduleTemplate(template)
    }
}
