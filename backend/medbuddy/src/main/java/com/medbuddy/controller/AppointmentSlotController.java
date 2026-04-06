package com.medbuddy.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.AppointmentSlotRequest;
import com.medbuddy.dto.AppointmentSlotResponse;
import com.medbuddy.model.AppointmentSlotStatus;
import com.medbuddy.service.AppointmentSlotService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/appointment-slots")
@RequiredArgsConstructor
@Validated
public class AppointmentSlotController {

    private final AppointmentSlotService appointmentSlotService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentSlotResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AppointmentSlotRequest request) {
        AppointmentSlotResponse response = appointmentSlotService.create(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-availability/{doctorAvailabilityId}")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
    public ResponseEntity<List<AppointmentSlotResponse>> getByAvailability(@PathVariable Long doctorAvailabilityId) {
        return ResponseEntity.ok(appointmentSlotService.getByAvailability(doctorAvailabilityId));
    }

    @GetMapping("/by-doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR','PATIENT')")
    public ResponseEntity<List<AppointmentSlotResponse>> getByDoctorAndDate(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate slotDate) {
        return ResponseEntity.ok(appointmentSlotService.getByDoctorAndDate(doctorId, slotDate));
    }

    @PatchMapping("/{slotId}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentSlotResponse> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long slotId,
            @RequestParam AppointmentSlotStatus status) {
        return ResponseEntity.ok(appointmentSlotService.updateStatus(userDetails.getUsername(), slotId, status));
    }
}
