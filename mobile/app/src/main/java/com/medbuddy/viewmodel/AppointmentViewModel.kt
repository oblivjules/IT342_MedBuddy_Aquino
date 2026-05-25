package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.constants.AppConstants
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.ui.viewstate.AppointmentUiState
import com.medbuddy.ui.viewstate.SlotsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class AppointmentViewModel(
    private val appointmentRepository: AppointmentRepository,
    private val paymentRepository: PaymentRepository? = null
) : ViewModel() {

    private val _appointments = MutableStateFlow(AppointmentUiState())
    val appointments: StateFlow<AppointmentUiState> = _appointments

    private val _slots = MutableStateFlow(SlotsUiState())
    val slots: StateFlow<SlotsUiState> = _slots

    fun getAppointments() {
        viewModelScope.launch {
            try {
                _appointments.value = _appointments.value.copy(loading = true)
                val appointments = appointmentRepository.getPatientAppointments()
                _appointments.value = _appointments.value.copy(loading = false, items = appointments)
            } catch (e: Exception) {
                _appointments.value = _appointments.value.copy(loading = false, error = e.message ?: "An error occurred")
            }
        }
    }

    fun loadAppointments(role: String) {
        viewModelScope.launch {
            try {
                _appointments.value = _appointments.value.copy(loading = true)
                val appointments = if (role == AppConstants.Role.PATIENT) {
                    appointmentRepository.getPatientAppointments()
                } else {
                    appointmentRepository.getDoctorAppointments()
                }
                _appointments.value = _appointments.value.copy(loading = false, items = appointments)
            } catch (e: Exception) {
                _appointments.value = _appointments.value.copy(loading = false, error = e.message ?: "An error occurred")
            }
        }
    }

    fun getDoctorAppointments() {
        viewModelScope.launch {
            try {
                _appointments.value = _appointments.value.copy(loading = true)
                val appointments = appointmentRepository.getDoctorAppointments()
                _appointments.value = _appointments.value.copy(loading = false, items = appointments)
            } catch (e: Exception) {
                _appointments.value = _appointments.value.copy(loading = false, error = e.message ?: "An error occurred")
            }
        }
    }

    fun getSlotsByDoctorDate(doctorId: Long, date: String) {
        viewModelScope.launch {
            try {
                _slots.value = _slots.value.copy(loading = true)
                val slots = appointmentRepository.getSlotsByDoctorDate(doctorId, date)
                _slots.value = _slots.value.copy(loading = false, items = slots)
            } catch (e: Exception) {
                _slots.value = _slots.value.copy(loading = false, error = e.message ?: "An error occurred")
            }
        }
    }

    fun bookAppointment(doctorId: Long, slotId: Long, reason: String?, onResult: (com.medbuddy.dto.AppointmentResponse?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = appointmentRepository.bookAppointment(doctorId, slotId, reason)
                onResult(response)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun updateAppointmentStatus(appointmentId: Long, status: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                appointmentRepository.updateAppointmentStatus(appointmentId, status)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun updateStatus(appointmentId: Long, status: String, role: String) {
        viewModelScope.launch {
            try {
                appointmentRepository.updateAppointmentStatus(appointmentId, status)
                // Reload appointments after update
                if (role == AppConstants.Role.PATIENT) {
                    val appointments = appointmentRepository.getPatientAppointments()
                    _appointments.value = _appointments.value.copy(items = appointments)
                } else {
                    val appointments = appointmentRepository.getDoctorAppointments()
                    _appointments.value = _appointments.value.copy(items = appointments)
                }
            } catch (e: Exception) {
                _appointments.value = _appointments.value.copy(error = e.message)
            }
        }
    }

    fun updateStatus(appointmentId: Long, status: String) {
        updateStatus(appointmentId, status, AppConstants.Role.DOCTOR)
    }

    fun initiatePayment(appointmentId: Long, amount: BigDecimal, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val repository = paymentRepository
            if (repository == null) {
                onResult(null)
                return@launch
            }
            val response = repository.initiatePayment(appointmentId, amount)
            onResult(response.checkoutUrl)
        }
    }

    companion object {
        fun factory(appointmentRepository: AppointmentRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(AppointmentViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return AppointmentViewModel(appointmentRepository, null) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }
    }
}
