package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medbuddy.constants.AppConstants
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.ui.viewstate.PaymentUiState
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PaymentViewModel(private val paymentRepository: PaymentRepository) : ViewModel() {

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
                val url = paymentRepository.initiatePayment(appointmentId, amount, returnUrl)
                _paymentState.value = _paymentState.value.copy(checkoutUrl = url)
                onResult(url)
            } catch (e: Exception) {
                _paymentState.value = _paymentState.value.copy(error = e.message)
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
