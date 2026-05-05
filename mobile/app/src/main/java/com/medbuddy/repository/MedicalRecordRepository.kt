package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.dto.CreateMedicalRecordRequest
import com.medbuddy.dto.DrugInfoResponse
import com.medbuddy.dto.MedicalRecordFileResponse
import com.medbuddy.dto.MedicalRecordResponse

class MedicalRecordRepository(
    private val apiService: ApiService
) {

    suspend fun getMedicalRecords(): List<MedicalRecordResponse> {
        return apiService.getMedicalRecords()
    }

    suspend fun getMedicalRecord(id: Long): MedicalRecordResponse {
        return apiService.getMedicalRecord(id)
    }

    suspend fun getDrugInfo(recordId: Long): DrugInfoResponse {
        return apiService.getDrugInfo(recordId)
    }

    suspend fun getAppointmentFiles(appointmentId: Long): List<MedicalRecordFileResponse> {
        return apiService.getAppointmentFiles(appointmentId)
    }

    suspend fun getMedicalRecordFiles(recordId: Long): List<MedicalRecordFileResponse> {
        return apiService.getMedicalRecordFiles(recordId)
    }

    suspend fun createMedicalRecord(
        appointmentId: Long,
        diagnosis: String,
        prescriptionDetails: String? = null,
        medicineName: String? = null,
        dosage: String? = null,
        route: String? = null,
        frequency: String? = null,
        duration: String? = null,
        prescriptionNotes: String? = null
    ): MedicalRecordResponse {
        return apiService.createMedicalRecord(
            CreateMedicalRecordRequest(
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
        )
    }
}
