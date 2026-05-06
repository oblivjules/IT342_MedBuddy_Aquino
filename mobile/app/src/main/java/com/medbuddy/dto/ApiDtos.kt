package com.medbuddy.dto

import com.google.gson.annotations.SerializedName

data class AuthResponseDto(
    val token: String,
    val user: UserDto
)

typealias AuthResponse = AuthResponseDto

data class AuthRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: Long,
    val email: String,
    val role: String,
    val profileId: Long?,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val specialization: String? = null,
    val specializations: List<String>? = null,
    val profileImageUrl: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String,
    val role: String? = null
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val specialization: String? = null,
    val specializationIds: List<Long>? = null
)

data class SpecializationDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String
)

data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val specialization: String? = null
)

data class DoctorDto(
    val id: Long,
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null,
    val specializations: List<String>? = null,
    val profileImageUrl: String? = null,
    val specialization: String? = null,
    val email: String? = null,
    val yearsExperience: Int? = null,
    val averageRating: Double? = null
) : java.io.Serializable

data class PatientDto(
    val id: Long,
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val email: String
) : java.io.Serializable

data class CreateAppointmentRequest(
    val doctorId: Long,
    val dateTime: String,
    val notes: String? = null
)

data class AppointmentRequest(
    val doctorId: Long,
    val dateTime: String? = null,
    val notes: String? = null,
    val slotId: Long? = null
)

data class UpdateAppointmentStatusRequest(
    val status: String
)

typealias AppointmentStatusRequest = UpdateAppointmentStatusRequest

data class AppointmentResponse(
    val id: Long,
    val patient: PatientDto,
    val doctor: DoctorDto,
    val dateTime: String,
    val status: String,
    val notes: String?,
    val createdAt: String,
    val rejectionReason: String? = null,
    val patientId: Long? = null,
    val doctorId: Long? = null,
    val patientName: String? = null,
    val doctorName: String? = null,
    val doctorSpecialization: String? = null
) : java.io.Serializable

data class CreateAvailabilityRequest(
    val availableDate: String,
    val startTime: String,
    val endTime: String,
    val status: String? = null
)

typealias DoctorAvailabilityRequest = CreateAvailabilityRequest

data class DoctorAvailabilityResponse(
    val id: Long = 0,
    val doctorId: Long,
    val availableDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val slotStartTime: String? = null,
    val slotEndTime: String? = null
)

data class MedicalRecordFileDto(
    val id: Long,
    val fileName: String,
    val fileUrl: String,
    val fileType: String
)

data class MedicalRecordFileResponse(
    val id: Long,
    val fileName: String,
    val fileUrl: String,
    val fileType: String,
    val fileSizeBytes: Long? = null,
    val description: String? = null,
    val uploadedAt: String? = null,
    val uploadedByUserId: Long? = null,
    val patientId: Long? = null,
    val uploadedBy: String? = null,
    val url: String? = null
) : java.io.Serializable

typealias MedicalRecordFileDtoAlias = MedicalRecordFileResponse

data class MedicalRecordResponse(
    val id: Long,
    val appointmentId: Long,
    val patientId: Long? = null,
    val doctorId: Long? = null,
    val diagnosis: String,
    val prescriptionDetails: String? = null,
    val createdAt: String? = null,
    val uploadedAt: String? = null,
    val files: List<MedicalRecordFileDto>? = null,
    val medicineName: String? = null,
    val dosage: String? = null,
    val route: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val prescriptionNotes: String? = null,
    val doctorName: String? = null,
    val type: String? = null,
    val formattedDate: String? = null
) : java.io.Serializable

data class CreateMedicalRecordRequest(
    val appointmentId: Long,
    val diagnosis: String,
    val prescriptionDetails: String? = null,
    val medicineName: String? = null,
    val dosage: String? = null,
    val route: String? = null,
    val frequency: String? = null,
    val duration: String? = null,
    val prescriptionNotes: String? = null
)

data class DrugInfoDto(
    val indications: String? = null,
    val warnings: String? = null,
    val dosage: String? = null,
    val description: String? = null,
    val dosageAdministration: String? = null
)

data class DrugInfoResponse(
    val available: Boolean? = null,
    val data: DrugInfoDto? = null,
    val indications: String? = null,
    val warnings: String? = null,
    val dosage: String? = null,
    val description: String? = null,
    val dosageAdministration: String? = null
) {
    companion object {
        fun notAvailable(): DrugInfoResponse = DrugInfoResponse(available = false)

        fun available(data: DrugInfoDto): DrugInfoResponse = DrugInfoResponse(
            available = true,
            data = data,
            indications = data.indications,
            warnings = data.warnings,
            dosage = data.dosage,
            description = data.description,
            dosageAdministration = data.dosageAdministration ?: data.dosage
        )
    }
}

data class PaymentResponse(
    val id: Long,
    val appointmentId: Long,
    val feeAmount: Double,
    val paymentStatus: String,
    val paymentMethod: String? = null,
    val paidAt: String? = null,
    val status: String? = null,
    val amount: Double? = null
) : java.io.Serializable

data class CreatePaymentRequest(
    val appointmentId: Long,
    val feeAmount: Double,
    val paymentStatus: String? = null
)

data class PaymentInitiateRequest(
    val appointmentId: Long
)

data class PaymentInitiateResponse(
    val checkoutUrl: String
)

data class InitiatePaymentRequest(
    val appointmentId: Long
)

data class UpdatePaymentStatusRequest(
    val status: String
)

data class PaymentTotalUpdateRequest(
    val totalBillAmount: Double
)

data class FeedbackResponse(
    val id: Long,
    val appointmentId: Long,
    val doctorId: Long,
    val patientId: Long? = null,
    val rating: Int,
    val comment: String? = null,
    val createdAt: String? = null,
    val patient: PatientDto? = null,
    val feedback: String? = null
) : java.io.Serializable

data class CreateFeedbackRequest(
    val appointmentId: Long,
    val doctorId: Long,
    val rating: Int,
    val comment: String? = null
)

data class RatingResponse(
    val id: Long,
    val appointmentId: Long,
    val doctorId: Long,
    val patientId: Long? = null,
    val rating: Int,
    val comment: String? = null,
    val createdAt: String? = null,
    val patient: PatientDto? = null,
    val feedback: String? = null
) : java.io.Serializable

data class AverageRatingResponse(
    val averageRating: Double,
    val totalRatings: Int? = null
)

data class CreateRatingRequest(
    val appointmentId: Long,
    val rating: Int,
    val feedback: String? = null
)

data class TemplateRequestDto(
    val dayOfWeek: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val status: String? = null
)

data class AppointmentSlotResponseDto(
    val id: Long,
    val slotStartTime: String? = null,
    val slotEndTime: String? = null,
    val status: String? = null
)

