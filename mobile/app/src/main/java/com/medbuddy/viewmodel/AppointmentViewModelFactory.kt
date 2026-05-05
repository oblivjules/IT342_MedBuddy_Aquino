package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.medbuddy.repository.AppointmentRepository

class AppointmentViewModelFactory(
    private val repository: AppointmentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppointmentViewModel(repository) as T
    }
}
