package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.AppointmentResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for My Appointments screen.
 */
class MyAppointmentsViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _allAppointments = MutableLiveData<List<AppointmentResponse>>(emptyList())
    val allAppointments: LiveData<List<AppointmentResponse>> = _allAppointments

    private val _filteredAppointments = MutableLiveData<List<AppointmentResponse>>(emptyList())
    val filteredAppointments: LiveData<List<AppointmentResponse>> = _filteredAppointments

    private val _selectedFilter = MutableLiveData("ALL")
    val selectedFilter: LiveData<String> = _selectedFilter

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadAppointments()
    }

    fun loadAppointments() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val appointments = apiService.getMyAppointments().bodyOrThrow()
                _allAppointments.value = appointments
                applyFilter()
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load appointments"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val filter = _selectedFilter.value ?: "ALL"
        val filtered = if (filter == "ALL") {
            _allAppointments.value.orEmpty()
        } else {
            _allAppointments.value?.filter { 
                it.status.uppercase() == filter.uppercase() 
            }.orEmpty()
        }
        _filteredAppointments.value = filtered
    }

    fun cancelAppointment(appointmentId: Long) {
        viewModelScope.launch {
            try {
                apiService.updateAppointmentStatus(
                    appointmentId,
                    com.medbuddy.dto.AppointmentStatusRequest("CANCELLED")
                )
                loadAppointments()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to cancel appointment"
            }
        }
    }

    fun refresh() {
        loadAppointments()
    }
}

class MyAppointmentsViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return MyAppointmentsViewModel(application, apiService) as T
    }
}
