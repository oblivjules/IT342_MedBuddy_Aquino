package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medbuddy.constants.AppConstants
import com.medbuddy.auth.PaymentSessionManager
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.ui.viewstate.PaymentUiState
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PaymentViewModel(
    private val paymentRepository: PaymentRepository,
    private val paymentSessionManager: PaymentSessionManager
) : ViewModel() {

    private val _paymentState = MutableStateFlow(PaymentUiState())
    val paymentState: StateFlow<PaymentUiState> = _paymentState

    fun loadPaymentStatus(appointmentId: Long) {
        viewModelScope.launch {
            try {
                _paymentState.value = _paymentState.value.copy(loading = true)
                val payment = paymentRepository.getPaymentByAppointmentId(appointmentId)
                _paymentState.value = _paymentState.value.copy(loading = false, payment = payment)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun clearCheckoutUrl() {
        _paymentState.value = _paymentState.value.copy(checkoutUrl = null)
    }

    fun initiatePayment(
        appointmentId: Long,
        amount: BigDecimal,
        returnUrl: String = AppConstants.Payment.RETURN_URL,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val response = paymentRepository.initiatePayment(appointmentId, amount, returnUrl)
                paymentSessionManager.savePendingPayment(appointmentId, response.paymentIntentId, response.clientKey)
                _paymentState.value = _paymentState.value.copy(
                    checkoutUrl = response.checkoutUrl,
                    paymentIntentId = response.paymentIntentId,
                    clientKey = response.clientKey,
                    error = null
                )
                onResult(response.checkoutUrl)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(error = e.message)
                onResult(null)
            }
        }
    }

    fun confirmStoredPayment(onResult: (PaymentResponse?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val session = paymentSessionManager.getPendingPayment()
                if (session == null) {
                    onResult(null)
                    return@launch
                }

                _paymentState.value = _paymentState.value.copy(loading = true, error = null)
                val payment = paymentRepository.confirmPayment(session.paymentIntentId, session.clientKey)
                paymentSessionManager.clearPendingPayment()
                _paymentState.value = _paymentState.value.copy(
                    loading = false,
                    payment = payment,
                    paymentIntentId = null,
                    clientKey = null,
                    checkoutUrl = null,
                    error = null
                )
                onResult(payment)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(loading = false, error = e.message)
                onResult(null)
            }
        }
    }

    fun createPayment(appointmentId: Long, amount: Double, onResult: (PaymentResponse?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val payment = paymentRepository.createPayment(appointmentId, amount)
                _paymentState.value = _paymentState.value.copy(payment = payment)
                onResult(payment)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(error = e.message)
                onResult(null)
            }
        }
    }

    fun updatePaymentStatus(paymentId: Long, status: String, onResult: (PaymentResponse?) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val payment = paymentRepository.updatePaymentStatus(paymentId, status)
                _paymentState.value = _paymentState.value.copy(payment = payment)
                onResult(payment)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(error = e.message)
                onResult(null)
            }
        }
    }
}
