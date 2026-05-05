package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.dto.DrugInfoResponse
import com.medbuddy.dto.MedicalRecordFileResponse
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.MedicalRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicalRecordsUiState(
    val loading: Boolean = false,
    val items: List<MedicalRecordResponse> = emptyList(),
    val error: String? = null
)

data class MedicalRecordDetailUiState(
    val loading: Boolean = false,
    val record: MedicalRecordResponse? = null,
    val files: List<MedicalRecordFileResponse> = emptyList(),
    val drugInfo: DrugInfoResponse? = null,
    val drugInfoLoading: Boolean = false,
    val error: String? = null
)

class MedicalRecordViewModel(
    private val repository: MedicalRecordRepository
) : ViewModel() {

    private val _recordsState = MutableStateFlow(MedicalRecordsUiState(loading = true))
    val recordsState: StateFlow<MedicalRecordsUiState> = _recordsState.asStateFlow()

    private val _detailState = MutableStateFlow(MedicalRecordDetailUiState())
    val detailState: StateFlow<MedicalRecordDetailUiState> = _detailState.asStateFlow()

    private val _createSuccess = MutableStateFlow<Boolean?>(null)
    val createSuccess: StateFlow<Boolean?> = _createSuccess.asStateFlow()

    fun loadRecords() {
        viewModelScope.launch {
            _recordsState.value = _recordsState.value.copy(loading = true, error = null)
            try {
                val items = repository.getMedicalRecords()
                _recordsState.value = MedicalRecordsUiState(loading = false, items = items)
            } catch (e: Throwable) {
                _recordsState.value = MedicalRecordsUiState(loading = false, error = e.message)
            }
        }
    }

    fun loadRecordDetail(recordId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(loading = true, error = null)
            try {
                val record = repository.getMedicalRecord(recordId)
                val files = repository.getMedicalRecordFiles(recordId)
                _detailState.value = MedicalRecordDetailUiState(
                    loading = false,
                    record = record,
                    files = files
                )
                if (!record.medicineName.isNullOrBlank()) {
                    loadDrugInfo(recordId)
                }
            } catch (e: Throwable) {
                _detailState.value = MedicalRecordDetailUiState(loading = false, error = e.message)
            }
        }
    }

    private fun loadDrugInfo(recordId: Long) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(drugInfoLoading = true)
            try {
                val drugInfo = repository.getDrugInfo(recordId)
                _detailState.value = _detailState.value.copy(
                    drugInfo = drugInfo,
                    drugInfoLoading = false
                )
            } catch (e: Throwable) {
                _detailState.value = _detailState.value.copy(drugInfoLoading = false)
            }
        }
    }

    fun loadAppointmentFiles(appointmentId: Long) {
        viewModelScope.launch {
            try {
                val files = repository.getAppointmentFiles(appointmentId)
                _detailState.value = _detailState.value.copy(files = files)
            } catch (e: Throwable) {
                // Silent fail - files are optional
            }
        }
    }

    fun createRecord(
        appointmentId: Long,
        diagnosis: String,
        prescriptionDetails: String? = null,
        medicineName: String? = null,
        dosage: String? = null,
        route: String? = null,
        frequency: String? = null,
        duration: String? = null,
        prescriptionNotes: String? = null
    ) {
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(loading = true, error = null)
            try {
                repository.createMedicalRecord(
                    appointmentId = appointmentId,
                    diagnosis = diagnosis,
                    prescriptionDetails = prescriptionDetails,
                    medicineName = medicineName,
                    dosage = dosage,
                    route = route,
                    frequency = frequency,
                    duration = duration,
                    prescriptionNotes = prescriptionNotes
                )
                _createSuccess.value = true
            } catch (e: Throwable) {
                _detailState.value = _detailState.value.copy(error = e.message, loading = false)
                _createSuccess.value = false
            }
        }
    }

    fun resetCreateSuccess() {
        _createSuccess.value = null
    }
}

class MedicalRecordViewModelFactory(
    private val repository: MedicalRecordRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MedicalRecordViewModel(repository) as T
    }
}
