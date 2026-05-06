package com.medbuddy.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.DoctorDto;
import com.medbuddy.features.schedule.DoctorAvailabilityRequest;
import com.medbuddy.features.schedule.DoctorAvailabilityResponse;
import com.medbuddy.features.schedule.DoctorAvailabilityService;
import com.medbuddy.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final UserService userService;
    private final DoctorAvailabilityService availabilityService;

    @GetMapping
    public ResponseEntity<List<DoctorDto>> getDoctors() {
        return ResponseEntity.ok(userService.getDoctors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorDto> getDoctorById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getDoctorById(id));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<DoctorAvailabilityResponse>> getDoctorAvailability(
            @PathVariable Long id) {
        return ResponseEntity.ok(availabilityService.getByDoctor(id));
    }

    @PostMapping("/{id}/availability")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> upsertDoctorAvailability(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        assertAuthenticatedDoctorOwnsPath(userDetails, id);
        return ResponseEntity.ok(availabilityService.upsert(userDetails.getUsername(), request));
    }

    // The existing availability key is date-based, so this id is interpreted as yyyy-MM-dd.
    @PutMapping("/availability/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorAvailabilityResponse> updateAvailabilityByDateId(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody DoctorAvailabilityRequest request) {
        LocalDate date = parseDateId(id);
        request.setAvailableDate(date);
        return ResponseEntity.ok(availabilityService.upsert(userDetails.getUsername(), request));
    }

    // The existing availability key is date-based, so this id is interpreted as yyyy-MM-dd.
    @DeleteMapping("/availability/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> deleteAvailabilityByDateId(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        availabilityService.delete(userDetails.getUsername(), parseDateId(id));
        return ResponseEntity.noContent().build();
    }

    private void assertAuthenticatedDoctorOwnsPath(UserDetails userDetails, Long doctorIdInPath) {
        Long authenticatedDoctorId = availabilityService.getAuthenticatedDoctorId(userDetails.getUsername());
        if (!authenticatedDoctorId.equals(doctorIdInPath)) {
            throw new AccessDeniedException("You can only manage your own availability.");
        }
    }

    private LocalDate parseDateId(String id) {
        try {
            return LocalDate.parse(id);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Availability id must be an ISO date (yyyy-MM-dd).", ex);
        }
    }
}

