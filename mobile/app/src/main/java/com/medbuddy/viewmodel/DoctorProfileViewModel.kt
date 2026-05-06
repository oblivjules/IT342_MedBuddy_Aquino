package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.DoctorDto
import com.medbuddy.dto.RatingResponse
import kotlinx.coroutines.launch

/**
 * ViewModel for Doctor Profile screen.
 */
class DoctorProfileViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _doctorProfile = MutableLiveData<DoctorDto?>(null)
    val doctorProfile: LiveData<DoctorDto?> = _doctorProfile

    private val _reviews = MutableLiveData<List<RatingResponse>>(emptyList())
    val reviews: LiveData<List<RatingResponse>> = _reviews

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadDoctorProfile(doctorId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // Get doctor from list
                val doctors = apiService.getDoctors().bodyOrThrow()
                val doctor = doctors.find { it.id == doctorId }
                _doctorProfile.value = doctor
                
                // Get reviews
                val ratings = apiService.getDoctorRatings(doctorId).bodyOrThrow()
                _reviews.value = ratings
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load doctor profile"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class DoctorProfileViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return DoctorProfileViewModel(application, apiService) as T
    }
}
