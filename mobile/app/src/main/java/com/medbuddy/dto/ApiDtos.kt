package com.medbuddy.dto

import com.google.gson.annotations.SerializedName

data class AuthResponseDto(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: Long,
    val email: String,
    val role: String,
    val profileImageUrl: String?,
    val profileId: Long?,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val specializations: List<String>?,
    val specialization: String?
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

data class DoctorDto(
    val id: Long,
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val specializations: List<String>?,
    val profileImageUrl: String?,
    val specialization: String?,
    val email: String
)

data class PatientDto(
    val id: Long,
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String?,
    val email: String
)

data class AppointmentRequest(
    val doctorId: Long,
    val dateTime: String,
    val notes: String?
)

data class AppointmentStatusRequest(
    val status: String
)

data class AppointmentResponse(
    val id: Long,
    val patient: PatientDto,
    val doctor: DoctorDto,
    val dateTime: String,
    val status: String,
    val notes: String?,
    val createdAt: String
)

data class DoctorAvailabilityRequest(
    val availableDate: String,
    val startTime: String,
    val endTime: String,
    val status: String? = null
)

data class DoctorAvailabilityResponse(
    val doctorId: Long,
    val availableDate: String,
    val startTime: String,
    val endTime: String,
    val status: String
)

