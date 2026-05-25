package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.api.ensureSuccess
import com.medbuddy.dto.AverageRatingResponse
import com.medbuddy.dto.CreateRatingRequest
import com.medbuddy.dto.RatingResponse

class RatingRepository(
    private val apiService: ApiService
) {

    suspend fun getPatientRatings(patientId: Long): List<RatingResponse> {
        return apiService.getPatientRatings(patientId).bodyOrThrow()
    }

    suspend fun getDoctorRatings(doctorId: Long): List<RatingResponse> {
        return apiService.getDoctorRatings(doctorId).bodyOrThrow()
    }

    suspend fun getAverageRating(doctorId: Long): AverageRatingResponse {
        val response = apiService.getAverageRating(doctorId).bodyOrThrow()
        return AverageRatingResponse(
            averageRating = response["average"] ?: response["averageRating"] ?: 0.0,
            totalRatings = response["totalRatings"]?.toInt()
        )
    }

    suspend fun createRating(
        appointmentId: Long,
        rating: Int,
        feedback: String?
    ): RatingResponse {
        return apiService.createRating(
            CreateRatingRequest(
                appointmentId = appointmentId,
                ratingScore = rating,
                feedbackComment = feedback
            )
        ).bodyOrThrow()
    }

    suspend fun deleteRating(id: Long) {
        apiService.deleteRating(id).ensureSuccess()
    }
}
