package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.UserDto
import kotlinx.coroutines.launch

/**
 * ViewModel for Patient Dashboard.
 * Loads current user and next upcoming appointment.
 */
class PatientDashboardViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _currentUser = MutableLiveData<UserDto?>(null)
    val currentUser: LiveData<UserDto?> = _currentUser

    private val _upcomingAppointment = MutableLiveData<AppointmentResponse?>(null)
    val upcomingAppointment: LiveData<AppointmentResponse?> = _upcomingAppointment

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Load current user
                val user = apiService.getMe().bodyOrThrow()
                _currentUser.value = user
                
                // Load appointments and find next upcoming
                val appointments = apiService.getMyAppointments().bodyOrThrow()
                val upcoming = appointments
                    .filter { it.status in listOf("PENDING", "CONFIRMED") }
                    .minByOrNull { it.dateTime }
                
                _upcomingAppointment.value = upcoming
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load dashboard data"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadData()
    }
}

class PatientDashboardViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return PatientDashboardViewModel(application, apiService) as T
    }
}
