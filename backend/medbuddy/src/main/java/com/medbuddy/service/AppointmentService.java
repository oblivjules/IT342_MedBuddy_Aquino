package com.medbuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.AppointmentRequest;
import com.medbuddy.dto.AppointmentResponse;
import com.medbuddy.dto.AppointmentStatusRequest;
import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.PatientDto;
import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.AppointmentRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    // ── Book ──────────────────────────────────────────────────────────────
    /**
     * A PATIENT books an appointment with a DOCTOR.
     *
     * @param patientEmail authenticated patient's email (from JWT)
     * @param request      booking details (doctorId refers to Doctor profile ID)
     */
    @Transactional
    public AppointmentResponse book(String patientEmail, AppointmentRequest request) {
        User patientUser = findUserByEmail(patientEmail);

        if (patientUser.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can book appointments.");
        }

        Patient patient = patientRepository.findByUser_Id(patientUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Patient profile not found for user: " + patientEmail));

        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Doctor not found with id: " + request.getDoctorId()));

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .dateTime(request.getDateTime())
                .notes(request.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();

        return toResponse(appointmentRepository.save(appointment));
    }

    // ── My appointments (caller-role aware) ───────────────────────────────
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(String userEmail) {
        User user = findUserByEmail(userEmail);

        List<Appointment> list;
        if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Doctor profile not found for user: " + userEmail));
            list = appointmentRepository.findByDoctor_IdOrderByDateTimeAsc(doctor.getId());
        } else {
            Patient patient = patientRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Patient profile not found for user: " + userEmail));
            list = appointmentRepository.findByPatient_IdOrderByDateTimeAsc(patient.getId());
        }

        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Update status ─────────────────────────────────────────────────────
    @Transactional
    public AppointmentResponse updateStatus(String userEmail,
                                             Long appointmentId,
                                             AppointmentStatusRequest request) {
        User user = findUserByEmail(userEmail);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + appointmentId));

        // Resolve profile IDs for ownership check
        boolean isOwnerPatient = false;
        boolean isOwnerDoctor  = false;

        if (user.getRole() == Role.PATIENT) {
            isOwnerPatient = appointment.getPatient().getUser().getId().equals(user.getId());
        } else if (user.getRole() == Role.DOCTOR) {
            isOwnerDoctor = appointment.getDoctor().getUser().getId().equals(user.getId());
        }

        if (!isOwnerPatient && !isOwnerDoctor) {
            throw new AccessDeniedException(
                    "You do not have permission to modify this appointment.");
        }

        AppointmentStatus current = appointment.getStatus();
        if (current == AppointmentStatus.CANCELLED || current == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot modify an appointment that is already " + current + ".");
        }

        if (isOwnerPatient && request.getStatus() != AppointmentStatus.CANCELLED) {
            throw new AccessDeniedException("Patients can only cancel appointments.");
        }

        if (isOwnerDoctor && request.getStatus() == AppointmentStatus.PENDING) {
            throw new IllegalArgumentException("Doctors cannot set appointment status back to PENDING.");
        }

        appointment.setStatus(request.getStatus());
        return toResponse(appointmentRepository.save(appointment));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
    }

    /** Maps an Appointment entity → AppointmentResponse DTO. */
    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .patient(toPatientDto(a.getPatient()))
                .doctor(toDoctorDto(a.getDoctor()))
                .dateTime(a.getDateTime())
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }

    static PatientDto toPatientDto(Patient p) {
        return PatientDto.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phoneNumber(p.getPhoneNumber())
                .email(p.getUser().getEmail())
                .build();
    }

    static DoctorDto toDoctorDto(Doctor d) {
        List<String> specNames = d.getSpecializations().stream()
                .map(s -> s.getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phoneNumber(d.getPhoneNumber())
                .specializations(specNames)
                .specialization(d.getSpecializationsSummary())
                .profileImageUrl(d.getUser().getProfileImageUrl())
                .email(d.getUser().getEmail())
                .build();
    }
}
