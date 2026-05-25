package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.constants.AppConstants
import com.medbuddy.dto.CreatePaymentRequest
import com.medbuddy.dto.PaymentConfirmRequest
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.dto.PaymentInitiateRequest
import com.medbuddy.dto.UpdatePaymentStatusRequest
import java.math.BigDecimal

class PaymentRepository(private val apiService: ApiService) {
    suspend fun getPaymentByAppointmentId(appointmentId: Long): PaymentResponse? {
        return try {
            apiService.getPaymentByAppointment(appointmentId).bodyOrThrow()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createPayment(appointmentId: Long, amount: Double): PaymentResponse {
        return apiService.createPayment(
            CreatePaymentRequest(
                appointmentId = appointmentId,
                feeAmount = amount,
                paymentStatus = "PENDING"
            )
        ).bodyOrThrow()
    }

    suspend fun initiatePayment(appointmentId: Long, amount: BigDecimal, returnUrl: String = AppConstants.Payment.RETURN_URL): com.medbuddy.dto.PaymentInitiateResponse {
        val request = PaymentInitiateRequest(appointmentId, amount, returnUrl)
        return apiService.initiatePayment(request).bodyOrThrow()
    }

    suspend fun confirmPayment(paymentIntentId: String, clientKey: String): PaymentResponse {
        return apiService.confirmPayment(PaymentConfirmRequest(paymentIntentId, clientKey)).bodyOrThrow()
    }

    suspend fun updatePaymentStatus(paymentId: Long, status: String): PaymentResponse {
        return apiService.updatePaymentStatus(paymentId, UpdatePaymentStatusRequest(status)).bodyOrThrow()
    }

    suspend fun updateTotalBill(appointmentId: Long, totalBillAmount: Double): PaymentResponse {
        return apiService.updateTotalBill(appointmentId, com.medbuddy.dto.PaymentTotalUpdateRequest(totalBillAmount)).bodyOrThrow()
    }
}
