package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.MedicalRecordResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for Medical Record Detail screen.
 */
class MedicalRecordDetailViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _medicalRecord = MutableLiveData<MedicalRecordResponse?>(null)
    val medicalRecord: LiveData<MedicalRecordResponse?> = _medicalRecord

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadMedicalRecord(recordId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val record = apiService.getMedicalRecord(recordId).bodyOrThrow()
                _medicalRecord.value = record
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load medical record"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class MedicalRecordDetailViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return MedicalRecordDetailViewModel(application, apiService) as T
    }
}
