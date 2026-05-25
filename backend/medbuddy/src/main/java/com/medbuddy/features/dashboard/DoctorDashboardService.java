package com.medbuddy.features.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.features.user.DoctorRepository;
import com.medbuddy.features.user.UserRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.AppointmentStatus;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorDashboardService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public DoctorDashboardResponse getDashboard(String doctorEmail) {
        User user = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session."));

        Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found."));

        List<Appointment> allAppointments = appointmentRepository
                .findByDoctor_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(doctor.getId());

        List<Appointment> todayAppointments = allAppointments.stream()
                .filter(this::hasTodayDate)
                .sorted(Comparator.comparing(this::resolveDateTime))
                .toList();

        LocalDateTime now = LocalDateTime.now();
        List<Appointment> upcomingAppointments = todayAppointments.stream()
            .filter(appointment -> isUpcoming(appointment, now))
                .toList();

        return DoctorDashboardResponse.builder()
                .doctorName(formatDoctorName(doctor))
                .todayAppointmentsCount(todayAppointments.size())
                .completedTodayCount((int) todayAppointments.stream()
                        .filter(appointment -> appointment.getStatus() == AppointmentStatus.COMPLETED)
                        .count())
                .nextPatient(upcomingAppointments.isEmpty() ? null : toNextPatient(upcomingAppointments.get(0), 1))
                .upcomingToday(upcomingAppointments.stream()
                        .map(this::toAppointmentSummary)
                        .toList())
                .build();
    }

    private boolean hasTodayDate(Appointment appointment) {
        return resolveDateTime(appointment).toLocalDate().equals(LocalDate.now());
    }

    private boolean isUpcoming(Appointment appointment, LocalDateTime now) {
        AppointmentStatus status = appointment.getStatus();
        return status != AppointmentStatus.CANCELLED
                && status != AppointmentStatus.COMPLETED
                && !resolveDateTime(appointment).isBefore(now);
    }

    private NextPatientDto toNextPatient(Appointment appointment, int queuePosition) {
        return NextPatientDto.builder()
                .appointmentId(appointment.getId())
                .patientName(formatPatientName(appointment.getPatient()))
                .appointmentTime(formatDateTime(resolveDateTime(appointment)))
                .reasonForVisit(resolveReason(appointment))
                .queuePosition(queuePosition)
                .build();
    }

    private AppointmentSummaryDto toAppointmentSummary(Appointment appointment) {
        return AppointmentSummaryDto.builder()
                .appointmentId(appointment.getId())
                .patientName(formatPatientName(appointment.getPatient()))
                .reasonForVisit(resolveReason(appointment))
                .appointmentTime(formatDateTime(resolveDateTime(appointment)))
                .build();
    }

    private String formatDoctorName(Doctor doctor) {
        String fullName = (safeText(doctor.getFirstName()) + " " + safeText(doctor.getLastName())).trim();
        return fullName.isBlank() ? "Doctor" : fullName;
    }

    private String formatPatientName(Patient patient) {
        String fullName = (safeText(patient.getFirstName()) + " " + safeText(patient.getLastName())).trim();
        return fullName.isBlank() ? "Patient" : fullName;
    }

    private String resolveReason(Appointment appointment) {
        return StringUtils.hasText(appointment.getNotes()) ? appointment.getNotes() : "";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(TIME_FORMATTER);
    }

    private LocalDateTime resolveDateTime(Appointment appointment) {
        if (appointment.getSlot() != null) {
            return LocalDateTime.of(appointment.getSlot().getSlotDate(), appointment.getSlot().getSlotStartTime());
        }
        if (appointment.getDateTime() != null) {
            return appointment.getDateTime();
        }
        if (appointment.getDate() != null && appointment.getTime() != null) {
            return LocalDateTime.of(appointment.getDate(), appointment.getTime());
        }
        return appointment.getCreatedAt();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}