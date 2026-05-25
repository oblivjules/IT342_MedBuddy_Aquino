package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.CreateRatingRequest
import com.medbuddy.dto.FeedbackResponse
import com.medbuddy.dto.RatingResponse

class FeedbackRepository(
    private val apiService: ApiService
) {

    suspend fun createFeedback(
        appointmentId: Long,
        doctorId: Long,
        rating: Int,
        comment: String?
    ): RatingResponse {
        return apiService.createRating(
            CreateRatingRequest(
                appointmentId = appointmentId,
                ratingScore = rating,
                feedbackComment = comment
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