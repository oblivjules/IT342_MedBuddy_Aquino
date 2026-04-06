package com.medbuddy.service;

import java.time.LocalDateTime;
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
import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.AppointmentSlotStatus;
import com.medbuddy.model.AppointmentStatus;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Role;
import com.medbuddy.model.Specialization;
import com.medbuddy.model.User;
import com.medbuddy.repository.AppointmentRepository;
import com.medbuddy.repository.AppointmentSlotRepository;
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
    private final AppointmentSlotRepository appointmentSlotRepository;
    // private final EmailService emailService; 

    // ── Book ──────────────────────────────────────────────────────────────
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

        AppointmentSlot slot = appointmentSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment slot not found with id: " + request.getSlotId()));

        Long slotDoctorId = slot.getDoctorId();
        if (slotDoctorId == null && slot.getDoctorAvailability() != null && slot.getDoctorAvailability().getDoctor() != null) {
            slotDoctorId = slot.getDoctorAvailability().getDoctor().getId();
        }

        if (slotDoctorId == null) {
            throw new IllegalStateException("Selected slot has no associated doctor.");
        }

        if (!slotDoctorId.equals(doctor.getId())) {
            throw new IllegalArgumentException("Selected slot does not belong to the requested doctor.");
        }

        if (slot.getStatus() != AppointmentSlotStatus.AVAILABLE) {
            throw new IllegalStateException("Selected slot is not available.");
        }

        LocalDateTime appointmentDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getSlotStartTime());
        if (!appointmentDateTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Appointment slot must be in the future.");
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .slot(slot)
                .dateTime(appointmentDateTime)
                .notes(request.getNotes())
                .status(AppointmentStatus.PENDING)
                .build();

        slot.setStatus(AppointmentSlotStatus.BOOKED);

        AppointmentResponse saved = toResponse(appointmentRepository.save(appointment));

        // EMAIL DISABLED
        // String toEmail    = patient.getUser().getEmail();
        // String patientName = patient.getFirstName() + " " + patient.getLastName();
        // String doctorName  = doctor.getFirstName() + " " + doctor.getLastName();
        // emailService.sendAppointmentConfirmationEmail(
        //         toEmail, patientName, doctorName,
        //         appointmentDateTime);

        return saved;
    }

    // ── My appointments ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<AppointmentResponse> getMyAppointments(String userEmail) {
        User user = findUserByEmail(userEmail);

        List<Appointment> list;
        if (user.getRole() == Role.DOCTOR) {
            Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Doctor profile not found for user: " + userEmail));
            list = appointmentRepository.findByDoctor_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(doctor.getId());
        } else {
            Patient patient = patientRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Patient profile not found for user: " + userEmail));
            list = appointmentRepository.findByPatient_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(patient.getId());
        }

        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(String userEmail, Long appointmentId) {
        User user = findUserByEmail(userEmail);

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + appointmentId));

        boolean isOwnerPatient = appointment.getPatient().getUser().getId().equals(user.getId());
        boolean isOwnerDoctor = appointment.getDoctor().getUser().getId().equals(user.getId());

        if (!isOwnerPatient && !isOwnerDoctor) {
            throw new AccessDeniedException("You do not have permission to view this appointment.");
        }

        return toResponse(appointment);
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

        boolean isOwnerPatient = false;
        boolean isOwnerDoctor = false;

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

        appointment.setStatus(request.getStatus());

        if (request.getStatus() == AppointmentStatus.CANCELLED && appointment.getSlot() != null) {
            appointment.getSlot().setStatus(AppointmentSlotStatus.AVAILABLE);
        }

        AppointmentResponse updated = toResponse(appointmentRepository.save(appointment));

        // EMAIL DISABLED
        // if (request.getStatus() == AppointmentStatus.CONFIRMED) {
        //     String patientEmail = appointment.getPatient().getUser().getEmail();
        //     String patientName  = appointment.getPatient().getFirstName() + " "
        //                           + appointment.getPatient().getLastName();
        //     String doctorName   = appointment.getDoctor().getFirstName() + " "
        //                           + appointment.getDoctor().getLastName();
        //     emailService.sendAppointmentApprovedEmail(
        //             patientEmail, patientName, doctorName,
        //             resolveAppointmentDateTime(appointment));
        // }

        return updated;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return AppointmentResponse.builder()
                .id(a.getId())
                .patient(toPatientDto(a.getPatient()))
                .doctor(toDoctorDto(a.getDoctor()))
                .dateTime(resolveAppointmentDateTime(a))
                .status(a.getStatus())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private LocalDateTime resolveAppointmentDateTime(Appointment appointment) {
        if (appointment.getSlot() != null) {
            return LocalDateTime.of(
                    appointment.getSlot().getSlotDate(),
                    appointment.getSlot().getSlotStartTime());
        }

        if (appointment.getDateTime() != null) {
            return appointment.getDateTime();
        }

        if (appointment.getDate() != null && appointment.getTime() != null) {
            return LocalDateTime.of(appointment.getDate(), appointment.getTime());
        }

        return appointment.getCreatedAt();
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
        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phoneNumber(d.getPhoneNumber())
                .specializations(d.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList()))
                .profileImageUrl(d.getUser().getProfileImageUrl())
                .email(d.getUser().getEmail())
                .build();
    }
}