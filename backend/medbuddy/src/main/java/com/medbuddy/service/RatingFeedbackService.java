package com.medbuddy.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.PatientDto;
import com.medbuddy.dto.RatingFeedbackRequest;
import com.medbuddy.dto.RatingFeedbackResponse;
import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.RatingAndFeedbackRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.AppointmentStatus;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.RatingAndFeedback;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.Specialization;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RatingFeedbackService {

    private final RatingAndFeedbackRepository ratingRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    @Transactional
    public RatingFeedbackResponse submit(String patientEmail, RatingFeedbackRequest request) {
        User user = findUser(patientEmail);
        if (user.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can submit ratings.");
        }

        Patient patient = patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Patient profile not found for user: " + patientEmail));

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + request.getAppointmentId()));

        if (!appointment.getPatient().getId().equals(patient.getId())) {
            throw new AccessDeniedException("You can only rate appointments you booked.");
        }

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Only completed appointments can be rated.");
        }

        if (ratingRepository.existsByAppointment_Id(request.getAppointmentId())) {
            throw new IllegalStateException(
                    "Feedback already exists for appointment id: " + request.getAppointmentId());
        }

        Integer resolvedRating = request.resolvedRating();
        if (resolvedRating == null) {
            throw new IllegalArgumentException("Rating is required.");
        }

        RatingAndFeedback rating = RatingAndFeedback.builder()
                .ratingScore(resolvedRating)
                .feedbackComment(request.resolvedFeedback())
                .appointment(appointment)
                .doctor(appointment.getDoctor())
                .patient(patient)
                .build();

        return toResponse(ratingRepository.save(rating));
    }

    @Transactional(readOnly = true)
    public List<RatingFeedbackResponse> getByDoctor(Long doctorId) {
        return ratingRepository.findByDoctor_IdOrderByCreatedAtDesc(doctorId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RatingFeedbackResponse> getByPatient(Long patientId) {
        return ratingRepository.findByPatient_IdOrderByCreatedAtDesc(patientId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RatingFeedbackResponse getByAppointment(Long appointmentId) {
        return ratingRepository.findByAppointment_Id(appointmentId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Feedback not found for appointment id: " + appointmentId));
    }

    @Transactional(readOnly = true)
    public Double getDoctorAverage(Long doctorId) {
        Double average = ratingRepository.findAverageRatingByDoctorId(doctorId);
        return average == null ? 0.0d : average;
    }

    @Transactional
    public void delete(String patientEmail, Long id) {
        User user = findUser(patientEmail);
        RatingAndFeedback rating = ratingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Rating not found with id: " + id));
        if (!rating.getAppointment().getPatient().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only delete your own ratings.");
        }
        ratingRepository.deleteById(id);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));
    }

    private RatingFeedbackResponse toResponse(RatingAndFeedback r) {
        String patientName = r.getPatient().getFirstName() + " " + r.getPatient().getLastName();
        return RatingFeedbackResponse.builder()
                .id(r.getId())
                .appointmentId(r.getAppointment().getId())
                .patient(toPatientDto(r.getAppointment().getPatient()))
                .doctor(toDoctorDto(r.getAppointment().getDoctor()))
            .patientName(patientName.trim())
            .rating(r.getRatingScore())
            .feedback(r.getFeedbackComment())
                .ratingScore(r.getRatingScore())
                .feedbackComment(r.getFeedbackComment())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private PatientDto toPatientDto(Patient p) {
        return PatientDto.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phoneNumber(p.getPhoneNumber())
                .email(p.getUser().getEmail())
                .build();
    }

    private DoctorDto toDoctorDto(Doctor d) {
        return DoctorDto.builder()
                .id(d.getId())
                .userId(d.getUser().getId())
                .firstName(d.getFirstName())
                .lastName(d.getLastName())
                .phoneNumber(d.getPhoneNumber())
                .specializations(d.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList()))
                .email(d.getUser().getEmail())
                .build();
    }
}
