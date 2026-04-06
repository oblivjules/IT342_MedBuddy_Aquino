package com.medbuddy.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.DoctorAvailabilityRequest;
import com.medbuddy.dto.DoctorAvailabilityResponse;
import com.medbuddy.dto.TemplateRequest;
import com.medbuddy.service.DoctorAvailabilityService;
import com.medbuddy.service.DoctorScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * DoctorAvailabilityController
 *
 * Endpoints:
 *   GET    /api/availability/doctor/{doctorId}                       — public: list all slots for a doctor
 *   GET    /api/availability/doctor/{doctorId}/date/{date}           — public: slots on a specific date
 *   POST   /api/availability                                         — DOCTOR: create/update a slot
 *   DELETE /api/availability/{date}                                  — DOCTOR: remove their slot on a date
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService availabilityService;
    private final DoctorScheduleService doctorScheduleService;

    /**
     * List all availability slots for a given doctor.
     * GET /api/availability/doctor/{doctorId}
     */
    @GetMapping("/availability/doctor/{doctorId}")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getByDoctor(
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(availabilityService.getByDoctor(doctorId));
    }

    /**
     * List availability slots for a doctor on a specific date.
     * GET /api/availability/doctor/{doctorId}/date/{date}
     */
    @GetMapping("/availability/doctor/{doctorId}/date/{date}")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getByDoctorAndDate(
            @PathVariable Long doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(availabilityService.getByDoctorAndDate(doctorId, date));
    }

    /**
     * Create or update a doctor's availability slot (upsert on PK).
     * Only accessible by authenticated DOCTORs.
     * POST /api/availability
     */
    @PostMapping("/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> upsert(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        DoctorAvailabilityResponse response =
                availabilityService.upsert(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Delete the authenticated doctor's slot on the given date.
     * DELETE /api/availability/{date}
     */
    @DeleteMapping("/availability/{date}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        availabilityService.delete(userDetails.getUsername(), date);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/doctor/schedule/template")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> saveTemplate(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody List<TemplateRequest> request) {
        Long doctorId = availabilityService.getAuthenticatedDoctorId(userDetails.getUsername());
        doctorScheduleService.saveTemplate(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/doctor/schedule/template")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<TemplateRequest>> getTemplate(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long doctorId = availabilityService.getAuthenticatedDoctorId(userDetails.getUsername());
        return ResponseEntity.ok(doctorScheduleService.getTemplates(doctorId));
    }

    @PostMapping("/doctor/schedule/exception")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> saveException(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        Long doctorId = availabilityService.getAuthenticatedDoctorId(userDetails.getUsername());
        doctorScheduleService.saveException(doctorId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/doctor/schedule/exception/{date}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> deleteException(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long doctorId = availabilityService.getAuthenticatedDoctorId(userDetails.getUsername());
        doctorScheduleService.deleteException(doctorId, date);
        return ResponseEntity.noContent().build();
    }
}
