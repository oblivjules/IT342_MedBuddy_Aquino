package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.CreatePaymentRequest
import com.medbuddy.dto.PaymentInitiateRequest
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.dto.UpdatePaymentStatusRequest

class PaymentRepository(
    private val apiService: ApiService
) {

    suspend fun getPaymentStatus(appointmentId: Long): PaymentResponse {
        return apiService.getPaymentStatus(appointmentId).bodyOrThrow()
    }

    suspend fun getPaymentByAppointment(appointmentId: Long): PaymentResponse {
        return apiService.getPaymentByAppointment(appointmentId).bodyOrThrow()
    }

    suspend fun initiatePayment(appointmentId: Long): String {
        val response = apiService.initiatePayment(PaymentInitiateRequest(appointmentId)).bodyOrThrow()
        return response.checkoutUrl
    }

    suspend fun createPayment(appointmentId: Long, amount: Double): PaymentResponse {
        return apiService.createPayment(CreatePaymentRequest(appointmentId, amount)).bodyOrThrow()
    }

    suspend fun updatePaymentStatus(paymentId: Long, status: String): PaymentResponse {
        return apiService.updatePaymentStatus(paymentId, UpdatePaymentStatusRequest(status)).bodyOrThrow()
    }
}
