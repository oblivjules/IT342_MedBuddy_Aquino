package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.medbuddy.auth.PaymentSessionManager
import com.medbuddy.repository.PaymentRepository

class PaymentViewModelFactory(
    private val paymentRepository: PaymentRepository,
    private val paymentSessionManager: PaymentSessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PaymentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PaymentViewModel(paymentRepository, paymentSessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
