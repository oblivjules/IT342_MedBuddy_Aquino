package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.PaymentResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for Billing / Payments screen.
 */
class BillingViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _payments = MutableLiveData<List<PaymentResponse>>(emptyList())
    val payments: LiveData<List<PaymentResponse>> = _payments

    private val _totalPending = MutableLiveData(0.0)
    val totalPending: LiveData<Double> = _totalPending

    private val _totalPaid = MutableLiveData(0.0)
    val totalPaid: LiveData<Double> = _totalPaid

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadPayments()
    }

    fun loadPayments() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val appointments = apiService.getMyAppointments().bodyOrThrow()
                val paymentsList = mutableListOf<PaymentResponse>()
                
                for (appointment in appointments) {
                    try {
                        val payment = apiService.getPaymentByAppointment(appointment.id).bodyOrThrow()
                        paymentsList.add(payment)
                    } catch (e: Exception) {
                        // Ignore individual payment fetch errors
                    }
                }
                
                _payments.value = paymentsList
                
                // Calculate totals
                var pending = 0.0
                var paid = 0.0
                for (payment in paymentsList) {
                    val amount = payment.feeAmount ?: payment.amount ?: 0.0
                    when (payment.paymentStatus?.uppercase() ?: payment.status?.uppercase()) {
                        "PENDING" -> pending += amount
                        "PAID" -> paid += amount
                    }
                }
                
                _totalPending.value = pending
                _totalPaid.value = paid
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load payments"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadPayments()
    }
}

class BillingViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return BillingViewModel(application, apiService) as T
    }
}
