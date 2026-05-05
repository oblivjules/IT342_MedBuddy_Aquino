package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentUiState(
    val loading: Boolean = false,
    val payment: PaymentResponse? = null,
    val checkoutUrl: String? = null,
    val error: String? = null
)

class PaymentViewModel(
    private val repository: PaymentRepository
) : ViewModel() {

    private val _paymentState = MutableStateFlow(PaymentUiState())
    val paymentState: StateFlow<PaymentUiState> = _paymentState.asStateFlow()

    fun loadPaymentStatus(appointmentId: Long) {
        viewModelScope.launch {
            _paymentState.value = _paymentState.value.copy(loading = true, error = null)
            try {
                val payment = repository.getPaymentStatus(appointmentId)
                _paymentState.value = PaymentUiState(loading = false, payment = payment)
            } catch (e: Throwable) {
                _paymentState.value = PaymentUiState(loading = false, error = e.message)
            }
        }
    }

    fun initiatePayment(appointmentId: Long) {
        viewModelScope.launch {
            _paymentState.value = _paymentState.value.copy(loading = true, error = null)
            try {
                val checkoutUrl = repository.initiatePayment(appointmentId)
                _paymentState.value = PaymentUiState(
                    loading = false,
                    checkoutUrl = checkoutUrl,
                    payment = _paymentState.value.payment
                )
            } catch (e: Throwable) {
                _paymentState.value = _paymentState.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun createPayment(appointmentId: Long, amount: Double) {
        viewModelScope.launch {
            _paymentState.value = _paymentState.value.copy(loading = true, error = null)
            try {
                val payment = repository.createPayment(appointmentId, amount)
                _paymentState.value = PaymentUiState(loading = false, payment = payment)
            } catch (e: Throwable) {
                _paymentState.value = PaymentUiState(loading = false, error = e.message)
            }
        }
    }

    fun updatePaymentStatus(paymentId: Long, status: String) {
        viewModelScope.launch {
            _paymentState.value = _paymentState.value.copy(loading = true, error = null)
            try {
                val payment = repository.updatePaymentStatus(paymentId, status)
                _paymentState.value = PaymentUiState(loading = false, payment = payment)
            } catch (e: Throwable) {
                _paymentState.value = PaymentUiState(loading = false, error = e.message)
            }
        }
    }

    fun clearCheckoutUrl() {
        _paymentState.value = _paymentState.value.copy(checkoutUrl = null)
    }
}

class PaymentViewModelFactory(
    private val repository: PaymentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PaymentViewModel(repository) as T
    }
}
