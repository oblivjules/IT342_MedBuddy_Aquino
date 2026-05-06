package com.medbuddy.features.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.PatientDto;
import com.medbuddy.features.appointment.AppointmentRequest;
import com.medbuddy.features.appointment.AppointmentResponse;
import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.features.appointment.AppointmentSlotRepository;
import com.medbuddy.features.appointment.AppointmentStatusRequest;
import com.medbuddy.service.EmailService;
import com.medbuddy.features.medicalrecords.FileStorageService;
import com.medbuddy.features.payment.PaymentService;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.AppointmentSlot;
import com.medbuddy.shared.model.AppointmentSlotStatus;
import com.medbuddy.shared.model.AppointmentStatus;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.Specialization;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final FileStorageService fileStorageService;

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
        if (!slot.getSlotDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Appointments must be booked at least one day in advance.");
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

        // System notification — send booking confirmation to the patient (async)
        String toEmail    = patient.getUser().getEmail();
        String patientName = patient.getFirstName() + " " + patient.getLastName();
        String doctorName  = doctor.getFirstName() + " " + doctor.getLastName();
        emailService.sendAppointmentConfirmationEmail(
                toEmail, patientName, doctorName,
            appointmentDateTime);

        return saved;
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

        if (isOwnerDoctor) {
            validateDoctorStatusTransition(appointment, request);
        }

        if (isOwnerPatient && request.getStatus() == AppointmentStatus.CANCELLED) {
            appointment.setRejectionReason(null);
        }

        appointment.setStatus(request.getStatus());

        if (request.getStatus() == AppointmentStatus.CANCELLED && isOwnerDoctor) {
            appointment.setRejectionReason(request.getRejectionReason().trim());
        }

        if (request.getStatus() == AppointmentStatus.CONFIRMED || request.getStatus() == AppointmentStatus.COMPLETED) {
            appointment.setRejectionReason(null);
        }

        if (request.getStatus() == AppointmentStatus.CANCELLED && appointment.getSlot() != null) {
            appointment.getSlot().setStatus(AppointmentSlotStatus.AVAILABLE);
        }

        AppointmentResponse updated = toResponse(appointmentRepository.save(appointment));

        if (request.getStatus() == AppointmentStatus.CANCELLED && isOwnerDoctor) {
            paymentService.refundPaymentForAppointment(appointmentId);
        }

        // System notification — email patient when doctor confirms their appointment
        if (request.getStatus() == AppointmentStatus.CONFIRMED) {
            String patientEmail = appointment.getPatient().getUser().getEmail();
            String patientName  = appointment.getPatient().getFirstName() + " "
                                  + appointment.getPatient().getLastName();
            String doctorName   = appointment.getDoctor().getFirstName() + " "
                                  + appointment.getDoctor().getLastName();
            emailService.sendAppointmentApprovedEmail(
                    patientEmail, patientName, doctorName,
                    resolveAppointmentDateTime(appointment));
        }

        if (request.getStatus() == AppointmentStatus.CANCELLED && isOwnerDoctor) {
            String patientEmail = appointment.getPatient().getUser().getEmail();
            String patientName = appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName();
            String doctorName = appointment.getDoctor().getFirstName() + " " + appointment.getDoctor().getLastName();

            emailService.sendAppointmentCancelledEmail(
                    patientEmail,
                    patientName,
                    doctorName,
                    resolveAppointmentDateTime(appointment),
                    appointment.getRejectionReason());
        }

        return updated;
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
                .dateTime(resolveAppointmentDateTime(a))
                .status(a.getStatus())
                .notes(a.getNotes())
            .rejectionReason(a.getRejectionReason())
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

    DoctorDto toDoctorDto(Doctor d) {
        String signedUrl = null;
        if (d.getUser() != null && StringUtils.hasText(d.getUser().getProfileImageUrl())) {
            try {
                String profileImageUrl = d.getUser().getProfileImageUrl();
                String objectPath = profileImageUrl;
                String publicBase = "https://gcvtswpohtmbjfqqvnns.supabase.co/storage/v1/object/public/medbuddy/";
                if (objectPath.startsWith(publicBase)) {
                    objectPath = objectPath.substring(publicBase.length());
                }
                log.debug("[DEBUG] toDoctorDto - normalized profileImage objectPath={}", objectPath);
                signedUrl = fileStorageService.createSignedUrl(objectPath, 3600);
            } catch (Exception e) {
                log.warn("[DEBUG] toDoctorDto - failed to sign profileImageUrl for doctorId={}, falling back to null. Error: {}", d.getId(), e.getMessage());
                signedUrl = null;
            }
        }

        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phoneNumber(d.getPhoneNumber())
                .specializations(d.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList()))
                .profileImageUrl(signedUrl)
                .email(d.getUser().getEmail())
                .build();
    }

    private void validateDoctorStatusTransition(Appointment appointment, AppointmentStatusRequest request) {
        AppointmentStatus currentStatus = appointment.getStatus();
        AppointmentStatus requestedStatus = request.getStatus();

        if (requestedStatus == null) {
            throw new IllegalArgumentException("Status is required.");
        }

        if (Objects.equals(currentStatus, requestedStatus)) {
            throw new IllegalStateException("Appointment is already in status " + requestedStatus + ".");
        }

        if (requestedStatus == AppointmentStatus.PENDING) {
            throw new IllegalStateException("Cannot move appointment back to PENDING.");
        }

        if (requestedStatus == AppointmentStatus.COMPLETED && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Appointment must be CONFIRMED before it can be COMPLETED.");
        }

        if (requestedStatus == AppointmentStatus.CONFIRMED && currentStatus != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only PENDING appointments can be CONFIRMED.");
        }

        if (requestedStatus == AppointmentStatus.CANCELLED) {
            if (currentStatus == AppointmentStatus.COMPLETED) {
                throw new IllegalStateException("Completed appointments cannot be cancelled.");
            }

            if (!StringUtils.hasText(request.getRejectionReason())) {
                throw new IllegalArgumentException("Rejection reason is required when rejecting an appointment.");
            }
        }
    }
}
