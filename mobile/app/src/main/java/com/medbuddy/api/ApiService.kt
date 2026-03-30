package com.medbuddy.api

import com.medbuddy.dto.AppointmentRequest
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.AppointmentStatusRequest
import com.medbuddy.dto.AuthResponseDto
import com.medbuddy.dto.DoctorAvailabilityRequest
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.DoctorDto
import com.medbuddy.dto.LoginRequest
import com.medbuddy.dto.RegisterRequest
import com.medbuddy.dto.SpecializationDto
import com.medbuddy.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponseDto

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponseDto

    @GET("api/auth/health")
    suspend fun health(): Map<String, String>

    @GET("api/users/me")
    suspend fun getMe(): UserDto

    @GET("api/users/doctors")
    suspend fun getDoctors(): List<DoctorDto>

    @GET("api/specializations")
    suspend fun getSpecializations(): List<SpecializationDto>

    @POST("api/appointments")
    suspend fun bookAppointment(@Body request: AppointmentRequest): AppointmentResponse

    @GET("api/appointments/my")
    suspend fun getMyAppointments(): List<AppointmentResponse>

    @PATCH("api/appointments/{id}/status")
    suspend fun updateAppointmentStatus(
        @Path("id") id: Long,
        @Body request: AppointmentStatusRequest
    ): AppointmentResponse

    @GET("api/availability/doctor/{doctorId}")
    suspend fun getDoctorAvailability(@Path("doctorId") doctorId: Long): List<DoctorAvailabilityResponse>

    @GET("api/availability/doctor/{doctorId}/date/{date}")
    suspend fun getDoctorAvailabilityByDate(
        @Path("doctorId") doctorId: Long,
        @Path("date") date: String
    ): List<DoctorAvailabilityResponse>

    @POST("api/availability")
    suspend fun addAvailability(@Body request: DoctorAvailabilityRequest): DoctorAvailabilityResponse

    @DELETE("api/availability/{date}")
    suspend fun deleteAvailabilityByDate(@Path("date") date: String)
}

