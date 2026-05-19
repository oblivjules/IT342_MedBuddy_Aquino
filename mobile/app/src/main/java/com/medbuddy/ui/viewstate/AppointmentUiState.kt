package com.medbuddy.ui.viewstate

import com.medbuddy.dto.AppointmentResponse

data class AppointmentUiState(
    val loading: Boolean = false,
    val items: List<AppointmentResponse> = emptyList(),
    val error: String? = null
)
