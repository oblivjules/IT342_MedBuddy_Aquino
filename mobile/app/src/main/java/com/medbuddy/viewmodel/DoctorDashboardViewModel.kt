package com.medbuddy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.AppointmentSummaryDto
import com.medbuddy.dto.DoctorDashboardResponse
import com.medbuddy.dto.NextPatientDto
import com.medbuddy.dto.UserDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = RetrofitClient.getInstance(application.applicationContext).apiService
    private val tokenManager = TokenManager(application.applicationContext)

    private val _dashboardData = MutableLiveData<DoctorDashboardResponse>()
    val dashboardData: LiveData<DoctorDashboardResponse> = _dashboardData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private var fetchJob: Job? = null

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        if (fetchJob?.isActive == true) return
        if (_isLoading.value == true) return

        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                if (tokenManager.getToken().isNullOrBlank()) {
                    _error.value = "Session expired. Please sign in again."
                    return@launch
                }

                val dashboard = loadDashboardData()
                _dashboardData.value = dashboard
            } catch (throwable: Throwable) {
                val isTimeout = isTimeoutException(throwable)
                if (isTimeout) {
                    // Retry once for Render cold-start
                    try {
                        delay(RETRY_DELAY_MS)
                        val dashboard = loadDashboardData()
                        _dashboardData.value = dashboard
                        return@launch
                    } catch (_: Throwable) {}
                    _error.value = "Connection timed out. The server may be waking up - please try again."
                } else {
                    _error.value = throwable.message ?: "Unable to load dashboard."
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadDashboardData(): DoctorDashboardResponse {
        val user = apiService.getMe().bodyOrThrow()
        val appointments = apiService.getMyAppointments().bodyOrThrow()

        return buildDashboard(
            user = user,
            appointments = appointments
        )
    }

    private fun buildDashboard(
        user: UserDto,
        appointments: List<AppointmentResponse>
    ): DoctorDashboardResponse {
        val normalizedAppointments = appointments.map {
            it.copy(status = AppointmentStatus.normalize(it.status))
        }

        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val todaysAppointments = normalizedAppointments.filter { appointment ->
            parseDateTime(appointment.dateTime)?.toLocalDate() == today
        }

        val upcomingTodayAppointments = todaysAppointments
            .mapNotNull { appointment ->
                parseDateTime(appointment.dateTime)?.let { dateTime -> appointment to dateTime }
            }
            .filter { (appointment, dateTime) ->
                dateTime.isAfter(now) && isActiveStatus(appointment.status)
            }
            .sortedBy { (_, dateTime) -> dateTime }

        val upcomingAppointments = normalizedAppointments
            .mapNotNull { appointment ->
                parseDateTime(appointment.dateTime)?.let { dateTime -> appointment to dateTime }
            }
            .filter { (appointment, dateTime) ->
                dateTime.isAfter(now) && isActiveStatus(appointment.status)
            }
            .sortedBy { (_, dateTime) -> dateTime }

        val upcomingToday = upcomingAppointments.map { (appointment, dateTime) ->
            AppointmentSummaryDto(
                appointmentId = appointment.id,
                patientName = buildPatientName(appointment),
                reasonForVisit = appointment.notes?.takeIf { it.isNotBlank() } ?: "Consultation",
                appointmentTime = dateTime.toString()
            )
        }

        val nextPatient = upcomingTodayAppointments.firstOrNull()?.let { (appointment, dateTime) ->
            NextPatientDto(
                appointmentId = appointment.id,
                patientName = buildPatientName(appointment),
                appointmentTime = dateTime.toString(),
                reasonForVisit = appointment.notes?.takeIf { it.isNotBlank() } ?: "Consultation"
            )
        }

        return DoctorDashboardResponse(
            doctorName = buildDoctorName(user),
            todayAppointmentsCount = todaysAppointments.size,
            completedTodayCount = todaysAppointments.count { it.status == AppointmentStatus.COMPLETED },
            nextPatient = nextPatient,
            upcomingToday = upcomingToday
        )
    }

    private fun buildDoctorName(user: UserDto): String {
        val name = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .trim()

        return when {
            name.isNotBlank() -> "Dr. $name"
            !user.email.isBlank() -> "Dr. ${user.email.substringBefore('@')}"
            else -> "Dr. Doctor"
        }
    }

    private fun buildPatientName(appointment: AppointmentResponse): String {
        return listOfNotNull(appointment.patient.firstName, appointment.patient.lastName)
            .joinToString(" ")
            .trim()
            .ifBlank {
                appointment.patientName.orEmpty().ifBlank { "Patient" }
            }
    }

    private fun isActiveStatus(status: String): Boolean {
        return status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        runCatching { LocalDateTime.parse(value) }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()?.let { return it }
        runCatching {
            val normalized = value.replace(Regex("\\.\\d+$"), "")
            LocalDateTime.parse(normalized)
        }.getOrNull()?.let { return it }
        return null
    }

    private fun formatTime(dateTime: LocalDateTime): String {
        return dateTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    private fun isTimeoutException(throwable: Throwable): Boolean {
        return throwable is SocketTimeoutException ||
               throwable is InterruptedIOException ||
               throwable.cause is SocketTimeoutException ||
               throwable.cause is InterruptedIOException ||
               throwable.message?.contains("timeout", ignoreCase = true) == true
    }

    companion object {
        private const val RETRY_DELAY_MS = 3000L
    }
}
