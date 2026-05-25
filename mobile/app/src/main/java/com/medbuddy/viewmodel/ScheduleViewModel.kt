package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.TemplateRequestDto
import com.medbuddy.repository.ScheduleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    fun loadTemplateAndExceptions(
        doctorId: Long,
        onResult: (template: List<TemplateRequestDto>, exceptions: List<DoctorAvailabilityResponse>) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val (template, exceptions) = coroutineScope {
                    val templateDeferred = async { repository.getTemplate() }
                    val exceptionsDeferred = async {
                        runCatching { repository.getMyAvailability() }
                            .getOrElse { repository.getDoctorAvailability(doctorId) }
                    }
                    templateDeferred.await() to exceptionsDeferred.await()
                }
                onResult(template, exceptions)
            } catch (e: Throwable) {
                onError(e.message ?: "Failed to load schedule")
            }
        }
    }

    fun saveTemplate(
        days: List<TemplateRequestDto>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.saveTemplate(days)
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Failed to save template")
            }
        }
    }

    fun saveExceptions(
        toSave: List<Triple<String, String, String>>,  // (date, startTime, endTime) for AVAILABLE
        toSaveUnavailable: List<String>,               // dates for UNAVAILABLE
        toDelete: List<String>,                        // dates to delete
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                for (date in toDelete) {
                    repository.deleteException(date)
                }
                for ((date, start, end) in toSave) {
                    repository.saveException(date, start, end, "AVAILABLE")
                }
                for (date in toSaveUnavailable) {
                    repository.saveException(date, "00:00:00", "00:30:00", "UNAVAILABLE")
                }
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Failed to save exceptions")
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
