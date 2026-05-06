package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.DoctorAvailabilityResponse
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel for Book Appointment screen.
 */
class BookAppointmentViewModel(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _selectedDate = MutableLiveData<LocalDate?>(null)
    val selectedDate: LiveData<LocalDate?> = _selectedDate

    private val _availableSlots = MutableLiveData<List<DoctorAvailabilityResponse>>(emptyList())
    val availableSlots: LiveData<List<DoctorAvailabilityResponse>> = _availableSlots

    private val _selectedSlot = MutableLiveData<DoctorAvailabilityResponse?>(null)
    val selectedSlot: LiveData<DoctorAvailabilityResponse?> = _selectedSlot

    private val _notes = MutableLiveData("")
    val notes: LiveData<String> = _notes

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isBooking = MutableLiveData(false)
    val isBooking: LiveData<Boolean> = _isBooking

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private val _bookingSuccess = MutableLiveData<AppointmentResponse?>(null)
    val bookingSuccess: LiveData<AppointmentResponse?> = _bookingSuccess

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
        loadAvailableSlots()
    }

    private fun loadAvailableSlots() {
        val date = _selectedDate.value ?: return
        val doctorId = _selectedDate.value  // This would come from args, adjust as needed
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                // This is a placeholder; in real implementation, doctorId comes from fragment arguments
                // val slots = apiService.getDoctorAvailabilityByDate(doctorId, date.toString()).bodyOrThrow()
                // _availableSlots.value = slots
                _availableSlots.value = emptyList()
                
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load available slots"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSelectedSlot(slot: DoctorAvailabilityResponse) {
        _selectedSlot.value = slot
    }

    fun setNotes(notes: String) {
        _notes.value = notes
    }

    fun canConfirmBooking(): Boolean {
        return _selectedDate.value != null && _selectedSlot.value != null
    }
}

class BookAppointmentViewModelFactory(
    private val application: Application,
    private val apiService: ApiService
) : androidx.lifecycle.ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return BookAppointmentViewModel(application, apiService) as T
    }
}
