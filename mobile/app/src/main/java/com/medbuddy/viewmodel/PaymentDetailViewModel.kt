package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.constants.AppConstants
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.PaymentResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for Payment Detail screen.
 */
class PaymentDetailViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _payment = MutableLiveData<PaymentResponse?>(null)
    val payment: LiveData<PaymentResponse?> = _payment

    private val _appointment = MutableLiveData<AppointmentResponse?>(null)
    val appointment: LiveData<AppointmentResponse?> = _appointment

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _initiatePaymentUrl = MutableLiveData<String?>(null)
    val initiatePaymentUrl: LiveData<String?> = _initiatePaymentUrl

    fun loadPaymentDetail(appointmentId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val payment = apiService.getPaymentByAppointment(appointmentId).bodyOrThrow()
                _payment.value = payment
                
                val appointment = apiService.getAppointmentById(appointmentId).bodyOrThrow()
                _appointment.value = appointment
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load payment details"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun initiatePayment(appointmentId: Long, amount: Double) {
        viewModelScope.launch {
            try {
                val response = apiService.initiatePayment(
                    com.medbuddy.dto.PaymentInitiateRequest(appointmentId, amount.toBigDecimal(), AppConstants.Payment.RETURN_URL)
                ).bodyOrThrow()
                _initiatePaymentUrl.value = response.checkoutUrl
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to initiate payment"
            }
        }
    }
}

class PaymentDetailViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return PaymentDetailViewModel(application, apiService) as T
    }
}
