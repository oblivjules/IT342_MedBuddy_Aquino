package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.dto.CreatePaymentRequest
import com.medbuddy.dto.InitiatePaymentRequest
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.dto.UpdatePaymentStatusRequest

class PaymentRepository(
    private val apiService: ApiService
) {

    suspend fun getPaymentStatus(appointmentId: Long): PaymentResponse {
        return apiService.getPaymentStatus(appointmentId)
    }

    suspend fun initiatePayment(appointmentId: Long): String {
        val response = apiService.initiatePayment(InitiatePaymentRequest(appointmentId))
        return response.checkoutUrl
    }

    suspend fun createPayment(appointmentId: Long, amount: Double): PaymentResponse {
        return apiService.createPayment(CreatePaymentRequest(appointmentId, amount))
    }

    suspend fun updatePaymentStatus(paymentId: Long, status: String): PaymentResponse {
        return apiService.updatePaymentStatus(paymentId, UpdatePaymentStatusRequest(status))
    }
}
