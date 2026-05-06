package com.medbuddy.features.feedback;

import com.medbuddy.features.feedback.RatingFeedbackRequest;
import com.medbuddy.features.feedback.RatingFeedbackResponse;
import com.medbuddy.features.feedback.RatingFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * RatingFeedbackController
 *
 * Endpoints:
 *   POST   /api/ratings               — PATIENT: submit a rating for a doctor
 *   GET    /api/ratings/doctor/{id}   — public: get all ratings for a doctor
 *   GET    /api/ratings/patient/{id}  — authenticated: get ratings submitted by a patient
 *   DELETE /api/ratings/{id}          — PATIENT (owner): delete own rating
 */
@RestController
@RequestMapping({"/api/ratings", "/api/feedback"})
@RequiredArgsConstructor
public class RatingFeedbackController {

    private final RatingFeedbackService ratingFeedbackService;

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RatingFeedbackResponse> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RatingFeedbackRequest request) {
        RatingFeedbackResponse response =
                ratingFeedbackService.submit(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<List<RatingFeedbackResponse>> getByDoctor(
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(ratingFeedbackService.getByDoctor(doctorId));
    }

    @GetMapping("/doctor/{doctorId}/average")
    public ResponseEntity<Map<String, Double>> getDoctorAverage(
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(Map.of("average", ratingFeedbackService.getDoctorAverage(doctorId)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<RatingFeedbackResponse>> getByPatient(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(ratingFeedbackService.getByPatient(patientId));
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<RatingFeedbackResponse> getByAppointment(
            @PathVariable Long appointmentId) {
        return ResponseEntity.ok(ratingFeedbackService.getByAppointment(appointmentId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        ratingFeedbackService.delete(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
