package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.UserDto
import kotlinx.coroutines.launch

/**
 * ViewModel for Patient Profile screen.
 */
class ProfileViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _currentUser = MutableLiveData<UserDto?>(null)
    val currentUser: LiveData<UserDto?> = _currentUser

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val user = apiService.getMe().bodyOrThrow()
                _currentUser.value = user
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load profile"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class ProfileViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(application, apiService) as T
    }
}
