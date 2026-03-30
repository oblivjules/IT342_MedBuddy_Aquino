package com.medbuddy.constants

import com.medbuddy.BuildConfig

object AppConstants {
    object Role {
        const val PATIENT = "PATIENT"
        const val DOCTOR = "DOCTOR"
    }

    fun apiBaseUrl(): String = BuildConfig.API_BASE_URL
}

