package com.medbuddy.constants

object AppointmentStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val CANCELLED = "CANCELLED"
    const val COMPLETED = "COMPLETED"

    val ACTIVE_STATUSES = setOf(PENDING, CONFIRMED)
    val TERMINAL_STATUSES = setOf(CANCELLED, COMPLETED)

    fun normalize(raw: String?): String {
        return when (raw.orEmpty().trim().uppercase()) {
            "BOOKED" -> CONFIRMED
            "REJECTED" -> CANCELLED
            PENDING, CONFIRMED, CANCELLED, COMPLETED -> raw.orEmpty().trim().uppercase()
            else -> PENDING
        }
    }
}
