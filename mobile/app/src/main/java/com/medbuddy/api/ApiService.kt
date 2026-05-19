package com.medbuddy.api

import com.medbuddy.dto.AppointmentRequest
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.AppointmentSlotResponseDto
import com.medbuddy.dto.AppointmentStatusRequest
import com.medbuddy.dto.AuthResponseDto
import com.medbuddy.dto.CreateAppointmentRequest
import com.medbuddy.dto.CreateAvailabilityRequest
import com.medbuddy.dto.CreateFeedbackRequest
import com.medbuddy.dto.CreateMedicalRecordRequest
import com.medbuddy.dto.CreatePaymentRequest
import com.medbuddy.dto.CreateRatingRequest
import com.medbuddy.dto.DoctorAvailabilityRequest
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.DoctorDto
import com.medbuddy.dto.DrugInfoDto
import com.medbuddy.dto.DrugInfoResponse
import com.medbuddy.dto.FeedbackResponse
import com.medbuddy.dto.InitiatePaymentRequest
import com.medbuddy.dto.LoginRequest
import com.medbuddy.dto.MedicalRecordFileDto
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.dto.PaymentInitiateRequest
import com.medbuddy.dto.PaymentInitiateResponse
import com.medbuddy.dto.PaymentResponse
import com.medbuddy.dto.PaymentTotalUpdateRequest
import com.medbuddy.dto.RegisterRequest
import com.medbuddy.dto.RatingResponse
import com.medbuddy.dto.SpecializationDto
import com.medbuddy.dto.TemplateRequestDto
import com.medbuddy.dto.UpdateAppointmentStatusRequest
import com.medbuddy.dto.UpdatePaymentStatusRequest
import com.medbuddy.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT
import retrofit2.Response

interface AuthApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponseDto>

    @GET("api/auth/health")
    suspend fun health(): Response<Map<String, String>>
}

interface UserApiService {

    @GET("api/users/me")
    suspend fun getMe(): Response<UserDto>

    @PATCH("api/users/me")
    suspend fun updateMe(@Body request: com.medbuddy.dto.UpdateProfileRequest): Response<AuthResponseDto>

    @GET("api/users/doctors")
    suspend fun getDoctors(@Query("search") search: String? = null, @Query("specialization") specialization: String? = null): Response<List<DoctorDto>>

    @GET("api/specializations")
    suspend fun getSpecializations(): Response<List<SpecializationDto>>
}

interface AppointmentApiService {

    @POST("api/appointments")
    suspend fun bookAppointment(@Body request: AppointmentRequest): Response<AppointmentResponse>

    @GET("api/appointments")
    suspend fun getAppointments(): Response<List<AppointmentResponse>>

    @GET("api/appointments/my")
    suspend fun getMyAppointments(): Response<List<AppointmentResponse>>

    @GET("api/appointments/{id}")
    suspend fun getAppointmentById(@Path("id") id: Long): Response<AppointmentResponse>

    @PATCH("api/appointments/{id}/status")
    suspend fun updateAppointmentStatus(
        @Path("id") id: Long,
        @Body request: AppointmentStatusRequest
    ): Response<AppointmentResponse>
}

interface AvailabilityApiService {

    @GET("api/availability/doctor/{doctorId}")
    suspend fun getDoctorAvailability(@Path("doctorId") doctorId: Long): Response<List<DoctorAvailabilityResponse>>

    @GET("api/availability/doctor/{doctorId}/date/{date}")
    suspend fun getDoctorAvailabilityByDate(
        @Path("doctorId") doctorId: Long,
        @Path("date") date: String
    ): Response<List<DoctorAvailabilityResponse>>

    @POST("api/availability")
    suspend fun addAvailability(@Body request: DoctorAvailabilityRequest): Response<DoctorAvailabilityResponse>

    @DELETE("api/availability/{date}")
    suspend fun deleteAvailabilityByDate(@Path("date") date: String): Response<Unit>

    @GET("api/doctor/schedule/template")
    suspend fun getMyScheduleTemplate(): Response<List<TemplateRequestDto>>

    @POST("api/doctor/schedule/template")
    suspend fun saveMyScheduleTemplate(@Body request: List<TemplateRequestDto>): Response<Unit>

    @GET("api/doctor/schedule/exception")
    suspend fun getMyAvailability(): Response<List<DoctorAvailabilityResponse>>

    @POST("api/doctor/schedule/exception")
    suspend fun saveMyException(@Body request: DoctorAvailabilityRequest): Response<Unit>

    @GET("api/appointment-slots/by-doctor/{doctorId}")
    suspend fun getDoctorAppointmentSlots(
        @Path("doctorId") doctorId: Long,
        @Query("slotDate") slotDate: String
    ): Response<List<AppointmentSlotResponseDto>>
}

interface MedicalRecordApiService {

    @POST("api/medical-records")
    suspend fun createMedicalRecord(@Body request: CreateMedicalRecordRequest): Response<MedicalRecordResponse>

    @GET("api/medical-records")
    suspend fun getMedicalRecords(): Response<List<MedicalRecordResponse>>

    @GET("api/medical-records/{id}")
    suspend fun getMedicalRecord(@Path("id") id: Long): Response<MedicalRecordResponse>

    @GET("api/medical-records/appointment/{appointmentId}")
    suspend fun getMedicalRecordByAppointment(@Path("appointmentId") appointmentId: Long): Response<MedicalRecordResponse>

    @PUT("api/medical-records/{id}")
    suspend fun updateMedicalRecord(
        @Path("id") id: Long,
        @Body request: CreateMedicalRecordRequest
    ): Response<MedicalRecordResponse>

    @DELETE("api/medical-records/{id}")
    suspend fun deleteMedicalRecord(@Path("id") id: Long): Response<Unit>

    @GET("api/medical-records/{id}/drug-info")
    suspend fun getDrugInfo(@Path("id") id: Long): Response<DrugInfoResponse>

    @GET("api/medical-record-files/record/{recordId}")
    suspend fun getMedicalRecordFiles(@Path("recordId") recordId: Long): Response<List<com.medbuddy.dto.MedicalRecordFileResponse>>

    @GET("api/medical-record-files/appointment/{appointmentId}")
    suspend fun getAppointmentFiles(@Path("appointmentId") appointmentId: Long): Response<List<com.medbuddy.dto.MedicalRecordFileResponse>>

    @GET("api/record-files/my")
    suspend fun getMyRecordFiles(): Response<List<com.medbuddy.dto.MedicalRecordFileResponse>>
}

interface PaymentApiService {

    @POST("api/payments")
    suspend fun createPayment(@Body request: CreatePaymentRequest): Response<PaymentResponse>

    @GET("api/payments/{id}")
    suspend fun getPaymentById(@Path("id") id: Long): Response<PaymentResponse>

    @GET("api/payments/appointment/{appointmentId}")
    suspend fun getPaymentByAppointment(@Path("appointmentId") appointmentId: Long): Response<PaymentResponse>

    @GET("api/payments/{appointmentId}")
    suspend fun getPaymentStatus(@Path("appointmentId") appointmentId: Long): Response<PaymentResponse>

    @POST("api/payments/initiate")
    suspend fun initiatePayment(@Body request: PaymentInitiateRequest): Response<PaymentInitiateResponse>

    @PATCH("api/payments/{id}/status")
    suspend fun updatePaymentStatus(
        @Path("id") id: Long,
        @Body request: UpdatePaymentStatusRequest
    ): Response<PaymentResponse>

    @PATCH("api/payments/appointment/{appointmentId}/total")
    suspend fun updateTotalBill(
        @Path("appointmentId") appointmentId: Long,
        @Body request: PaymentTotalUpdateRequest
    ): Response<PaymentResponse>
}

interface FeedbackApiService {

    @POST("api/feedback")
    suspend fun createFeedback(@Body request: CreateFeedbackRequest): Response<FeedbackResponse>

    @GET("api/feedback/doctor/{id}")
    suspend fun getDoctorFeedback(@Path("id") id: Long): Response<List<FeedbackResponse>>

    @GET("api/feedback/appointment/{id}")
    suspend fun getFeedbackByAppointment(@Path("id") id: Long): Response<FeedbackResponse>

    @GET("api/ratings/doctor/{doctorId}")
    suspend fun getDoctorRatings(@Path("doctorId") doctorId: Long): Response<List<RatingResponse>>

    @GET("api/ratings/doctor/{doctorId}/average")
    suspend fun getAverageRating(@Path("doctorId") doctorId: Long): Response<Map<String, Double>>

    @POST("api/ratings")
    suspend fun createRating(@Body request: CreateRatingRequest): Response<RatingResponse>

    @GET("api/ratings/patient/{patientId}")
    suspend fun getPatientRatings(@Path("patientId") patientId: Long): Response<List<RatingResponse>>

    @GET("api/ratings/appointment/{appointmentId}")
    suspend fun getRatingByAppointment(@Path("appointmentId") appointmentId: Long): Response<RatingResponse>
}

interface ApiService :
    AuthApiService,
    UserApiService,
    AppointmentApiService,
    AvailabilityApiService,
    MedicalRecordApiService,
    PaymentApiService,
    FeedbackApiService

