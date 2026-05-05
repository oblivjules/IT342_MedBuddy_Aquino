package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.repository.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val loading: Boolean = false,
    val slots: List<DoctorAvailabilityResponse> = emptyList(),
    val error: String? = null
)

class ScheduleViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    fun loadAvailability(doctorId: Long?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val slots = if (doctorId == null || doctorId <= 0) {
                    repository.getMyAvailability()
                } else {
                    runCatching { repository.getMyAvailability() }
                        .getOrElse { repository.getDoctorAvailability(doctorId) }
                }
                _state.value = ScheduleUiState(loading = false, slots = slots)
            } catch (e: Throwable) {
                _state.value = ScheduleUiState(loading = false, error = e.message)
            }
        }
    }

    fun addSlot(date: String, start: String, end: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.addAvailability(date, start, end)
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Failed to add slot")
            }
        }
    }

    fun deleteDate(date: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteAvailabilityByDate(date)
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Failed to delete slot")
            }
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScheduleViewModel(repository) as T
                }
            }
        }
    }
}
