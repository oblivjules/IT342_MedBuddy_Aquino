package com.medbuddy.constants

import com.medbuddy.BuildConfig

object AppConstants {
    object Auth {
        const val ACTION_SESSION_EXPIRED = "com.medbuddy.ACTION_SESSION_EXPIRED"
    }

    object Role {
        const val PATIENT = "PATIENT"
        const val DOCTOR = "DOCTOR"
    }

    fun apiBaseUrl(): String = BuildConfig.BASE_URL
}

