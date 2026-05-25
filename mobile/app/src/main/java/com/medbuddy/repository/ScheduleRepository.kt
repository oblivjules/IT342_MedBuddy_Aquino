package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.api.ensureSuccess
import com.medbuddy.dto.DoctorAvailabilityRequest
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.TemplateRequestDto

class ScheduleRepository(
    private val apiService: ApiService
) {

    suspend fun getMyAvailability(): List<DoctorAvailabilityResponse> = apiService.getMyAvailability().bodyOrThrow()

    suspend fun getDoctorAvailability(doctorId: Long): List<DoctorAvailabilityResponse> =
        apiService.getDoctorAvailability(doctorId).bodyOrThrow()

    suspend fun addAvailability(date: String, startTime: String, endTime: String, status: String = "AVAILABLE") {
        apiService.addAvailability(
            DoctorAvailabilityRequest(
                availableDate = date,
                startTime = startTime,
                endTime = endTime,
                status = status
            )
        ).bodyOrThrow()
    }

    suspend fun deleteAvailabilityByDate(date: String) {
        apiService.deleteAvailabilityByDate(date).ensureSuccess()
    }

    suspend fun getTemplate(): List<TemplateRequestDto> = apiService.getMyScheduleTemplate().bodyOrThrow()

    suspend fun saveTemplate(template: List<TemplateRequestDto>) {
        apiService.saveMyScheduleTemplate(template).ensureSuccess()
    }

    suspend fun saveException(date: String, startTime: String, endTime: String, status: String) {
        apiService.saveMyException(
            DoctorAvailabilityRequest(
                availableDate = date,
                startTime = startTime,
                endTime = endTime,
                status = status
            )
        ).ensureSuccess()
    }

    suspend fun deleteException(date: String) {
        apiService.deleteMyException(date).ensureSuccess()
    }
}
