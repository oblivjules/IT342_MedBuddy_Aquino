package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.constants.AppConstants
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.SlotUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppointmentUiState(
    val loading: Boolean = false,
    val items: List<AppointmentResponse> = emptyList(),
    val error: String? = null
)

data class SlotsUiState(
    val loading: Boolean = false,
    val items: List<SlotUiModel> = emptyList(),
    val error: String? = null
)

class AppointmentViewModel(
    private val repository: AppointmentRepository
) : ViewModel() {

    private val _appointmentsState = MutableStateFlow(AppointmentUiState(loading = true))
    val appointmentsState: StateFlow<AppointmentUiState> = _appointmentsState.asStateFlow()

    private val _slotsState = MutableStateFlow(SlotsUiState())
    val slotsState: StateFlow<SlotsUiState> = _slotsState.asStateFlow()

    fun loadAppointments(role: String) {
        viewModelScope.launch {
            _appointmentsState.value = _appointmentsState.value.copy(loading = true, error = null)
            try {
                val items = when (role) {
                    AppConstants.Role.DOCTOR -> repository.getDoctorAppointments()
                    else -> repository.getPatientAppointments()
                }
                _appointmentsState.value = AppointmentUiState(loading = false, items = items)
            } catch (e: Throwable) {
                _appointmentsState.value = AppointmentUiState(loading = false, error = e.message)
            }
        }
    }

    fun updateStatus(id: Long, targetStatus: String, role: String) {
        viewModelScope.launch {
            try {
                repository.updateAppointmentStatus(id, targetStatus)
                loadAppointments(role)
            } catch (e: Throwable) {
                _appointmentsState.value = _appointmentsState.value.copy(error = e.message)
            }
        }
    }

    fun updateStatus(id: Long, targetStatus: String) {
        viewModelScope.launch {
            try {
                repository.updateAppointmentStatus(id, targetStatus)
            } catch (e: Throwable) {
                _appointmentsState.value = _appointmentsState.value.copy(error = e.message)
            }
        }
    }

    fun loadSlots(doctorId: Long, date: String) {
        viewModelScope.launch {
            _slotsState.value = SlotsUiState(loading = true)
            try {
                val slots = repository.getSlotsByDoctorDate(doctorId, date)
                _slotsState.value = SlotsUiState(loading = false, items = slots)
            } catch (e: Throwable) {
                _slotsState.value = SlotsUiState(loading = false, error = e.message)
            }
        }
    }

    fun bookAppointment(doctorId: Long, slotId: Long, notes: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.bookAppointment(doctorId, slotId, notes)
                onSuccess()
            } catch (e: Throwable) {
                onError(e.message ?: "Booking failed")
            }
        }
    }

    companion object {
        fun factory(repository: AppointmentRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppointmentViewModel(repository) as T
                }
            }
        }
    }
}
