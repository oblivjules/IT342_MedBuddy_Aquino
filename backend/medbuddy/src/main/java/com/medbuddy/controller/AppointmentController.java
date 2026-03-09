package com.medbuddy.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.AppointmentRequest;
import com.medbuddy.dto.AppointmentResponse;
import com.medbuddy.dto.AppointmentStatusRequest;
import com.medbuddy.service.AppointmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AppointmentController
 *
 * All endpoints are protected — a valid JWT is required.
 * The authenticated user's email is extracted from the JWT via @AuthenticationPrincipal.
 *
 * Endpoints:
 *   POST   /api/appointments                       — patient books an appointment
 *   GET    /api/appointments/my                    — returns all appointments for the caller
 *   PATCH  /api/appointments/{id}/status           — doctor confirms/cancels, patient cancels
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Book a new appointment.
     * Caller must have role PATIENT.
     * POST /api/appointments
     */
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AppointmentRequest request) {

        AppointmentResponse response =
                appointmentService.book(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieve all appointments for the authenticated user.
     * PATIENT → their bookings; DOCTOR → their schedule.
     * GET /api/appointments/my
     */
    @GetMapping("/my")
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<AppointmentResponse> list =
                appointmentService.getMyAppointments(userDetails.getUsername());
        return ResponseEntity.ok(list);
    }

    /**
     * Update the status of an appointment.
     * DOCTOR: CONFIRMED | CANCELLED | COMPLETED
     * PATIENT: CANCELLED only
     * PATCH /api/appointments/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AppointmentResponse> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AppointmentStatusRequest request) {

        AppointmentResponse response =
                appointmentService.updateStatus(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }
}
