package com.medbuddy.repository

import com.medbuddy.api.ApiService
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.dto.CreateMedicalRecordRequest
import com.medbuddy.dto.DrugInfoResponse
import com.medbuddy.dto.MedicalRecordFileResponse
import com.medbuddy.dto.MedicalRecordResponse

class MedicalRecordRepository(
    private val apiService: ApiService
) {

    suspend fun getMedicalRecords(): List<MedicalRecordResponse> {
        return apiService.getMedicalRecords().bodyOrThrow()
    }

    suspend fun getMyRecordFiles(): List<MedicalRecordFileResponse> {
        return apiService.getMyRecordFiles().bodyOrThrow()
    }

    suspend fun getMedicalRecord(id: Long): MedicalRecordResponse {
        return apiService.getMedicalRecord(id).bodyOrThrow()
    }

    suspend fun getMedicalRecordByAppointment(appointmentId: Long): MedicalRecordResponse {
        return apiService.getMedicalRecordByAppointment(appointmentId).bodyOrThrow()
    }

    suspend fun getDrugInfo(recordId: Long): DrugInfoResponse {
        return apiService.getDrugInfo(recordId).bodyOrThrow()
    }

    suspend fun getAppointmentFiles(appointmentId: Long): List<MedicalRecordFileResponse> {
        return apiService.getAppointmentFiles(appointmentId).bodyOrThrow()
    }

    suspend fun getFilesByAppointment(appointmentId: Long): List<MedicalRecordFileResponse> {
        return apiService.getFilesByAppointment(appointmentId).bodyOrThrow()
    }

    suspend fun getFileAccessUrl(fileId: Long): String {
        val map = apiService.getFileAccessUrl(fileId).bodyOrThrow()
        return map["url"] ?: throw IllegalStateException("Missing url from file access response")
    }

    suspend fun getMedicalRecordFileAccessUrl(fileId: Long): String {
        val map = apiService.getMedicalRecordFileAccessUrl(fileId).bodyOrThrow()
        return map["url"] ?: throw IllegalStateException("Missing url from file access response")
    }

    suspend fun getMedicalRecordFiles(recordId: Long): List<MedicalRecordFileResponse> {
        // Prefer the backend-record-files endpoint which returns doctor-attached uploads
        return runCatching { apiService.getFilesByRecord(recordId).bodyOrThrow() }
            .getOrElse { apiService.getMedicalRecordFiles(recordId).bodyOrThrow() }
    }

    suspend fun updateMedicalRecord(
        id: Long,
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
        return apiService.updateMedicalRecord(
            id,
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
        ).bodyOrThrow()
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
        ).bodyOrThrow()
    }
}
