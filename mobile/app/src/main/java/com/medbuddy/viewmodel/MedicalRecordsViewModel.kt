package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.MedicalRecordResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for Medical Records screen.
 */
class MedicalRecordsViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _medicalRecords = MutableLiveData<List<MedicalRecordResponse>>(emptyList())
    val medicalRecords: LiveData<List<MedicalRecordResponse>> = _medicalRecords

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadMedicalRecords()
    }

    fun loadMedicalRecords() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val appointments = apiService.getMyAppointments().bodyOrThrow()
                val completedAppointments = appointments.filter { 
                    it.status.uppercase() == "COMPLETED" 
                }
                
                val records = mutableListOf<MedicalRecordResponse>()
                for (appointment in completedAppointments) {
                    try {
                        val record = apiService.getMedicalRecordByAppointment(appointment.id).bodyOrThrow()
                        records.add(record)
                    } catch (e: Exception) {
                        // Ignore individual fetch errors
                    }
                }
                
                _medicalRecords.value = records
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load medical records"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        loadMedicalRecords()
    }
}

class MedicalRecordsViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return MedicalRecordsViewModel(application, apiService) as T
    }
}
