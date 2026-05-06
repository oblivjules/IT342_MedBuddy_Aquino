package com.medbuddy.ui.fragments.patient

import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.DoctorDto
import com.medbuddy.dto.MedicalRecordResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun doctorDisplayName(doctor: DoctorDto?): String {
    val fullName = listOfNotNull(doctor?.firstName, doctor?.lastName).joinToString(" ").trim()
    return fullName.ifBlank { doctor?.email ?: "Doctor" }
}

fun doctorInitials(firstName: String?, lastName: String?, fallback: String = "DR"): String {
    val initials = listOfNotNull(firstName, lastName)
        .mapNotNull { it.trim().firstOrNull()?.toString() }
        .joinToString("")
        .uppercase(Locale.getDefault())
    return initials.ifBlank { fallback }
}

fun patientInitials(firstName: String?, lastName: String?, fallback: String = "PT"): String {
    return doctorInitials(firstName, lastName, fallback)
}

fun appointmentDoctorSpecialization(appointment: AppointmentResponse): String {
    return appointment.doctor.specializations?.firstOrNull()
        ?: appointment.doctor.specialization
        ?: appointment.doctorSpecialization
        ?: "General Practice"
}

fun appointmentDoctorName(appointment: AppointmentResponse): String {
    return doctorDisplayName(appointment.doctor)
}

fun formatAppointmentDateTime(dateTime: String): String {
    return runCatching {
        val parsed = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
        parsed.format(DateTimeFormatter.ofPattern("MMMM d, yyyy • hh:mm a", Locale.getDefault()))
    }.getOrDefault(dateTime.replace("T", " "))
}

fun formatAppointmentTime(dateTime: String): String {
    return runCatching {
        val parsed = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
        parsed.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
    }.getOrDefault(dateTime)
}

fun formatAppointmentDate(dateTime: String): String {
    return runCatching {
        val parsed = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
        parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }.getOrDefault(dateTime.replace("T", " "))
}

fun formatStatusLabel(status: String?): String {
    return when (AppointmentStatus.normalize(status)) {
        AppointmentStatus.PENDING -> "Booked"
        AppointmentStatus.CONFIRMED -> "Confirmed"
        AppointmentStatus.COMPLETED -> "Completed"
        AppointmentStatus.CANCELLED -> "Cancelled"
        else -> status.orEmpty().ifBlank { "Pending" }
    }
}

fun buildPrescriptionSummary(record: MedicalRecordResponse): String {
    val parts = buildList {
        record.medicineName?.takeIf { it.isNotBlank() }?.let { add("Medicine: $it") }
        record.dosage?.takeIf { it.isNotBlank() }?.let { add("Dosage: $it") }
        record.route?.takeIf { it.isNotBlank() }?.let { add("Route: $it") }
        record.frequency?.takeIf { it.isNotBlank() }?.let { add("Frequency: $it") }
        record.duration?.takeIf { it.isNotBlank() }?.let { add("Duration: $it") }
        record.prescriptionNotes?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.joinToString("\n")
}
