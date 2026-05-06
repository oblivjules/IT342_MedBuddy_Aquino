package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.CreateFeedbackRequest
import com.medbuddy.dto.FeedbackResponse

class FeedbackRepository(
    private val apiService: ApiService
) {

    suspend fun createFeedback(
        appointmentId: Long,
        doctorId: Long,
        rating: Int,
        comment: String?
    ): FeedbackResponse {
        return apiService.createFeedback(
            CreateFeedbackRequest(
                appointmentId = appointmentId,
                doctorId = doctorId,
                rating = rating,
                comment = comment
            )
        ).bodyOrThrow()
    }

    suspend fun getDoctorFeedback(doctorId: Long): List<FeedbackResponse> {
        return apiService.getDoctorFeedback(doctorId).bodyOrThrow()
    }

    suspend fun getFeedbackByAppointment(appointmentId: Long): FeedbackResponse {
        return apiService.getFeedbackByAppointment(appointmentId).bodyOrThrow()
    }
}