package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.dto.AverageRatingResponse
import com.medbuddy.dto.CreateRatingRequest
import com.medbuddy.dto.RatingResponse

class RatingRepository(
    private val apiService: ApiService
) {

    suspend fun getDoctorRatings(doctorId: Long): List<RatingResponse> {
        return apiService.getDoctorRatings(doctorId)
    }

    suspend fun getAverageRating(doctorId: Long): AverageRatingResponse {
        return apiService.getAverageRating(doctorId)
    }

    suspend fun createRating(
        appointmentId: Long,
        rating: Int,
        feedback: String?
    ): RatingResponse {
        return apiService.createRating(
            CreateRatingRequest(
                appointmentId = appointmentId,
                rating = rating,
                feedback = feedback
            )
        )
    }
}
