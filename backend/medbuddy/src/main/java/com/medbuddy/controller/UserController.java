package com.medbuddy.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.UserDto;
import com.medbuddy.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * UserController
 *
 * All endpoints require authentication (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Returns the full profile of the currently authenticated user.
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserDto dto = userService.getMe(userDetails.getUsername());
        return ResponseEntity.ok(dto);
    }

    /**
     * List all registered doctors with their profile details.
     * Used by the patient-side "Find a Doctor" feature.
     * GET /api/users/doctors
     */
    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorDto>> getDoctors() {
        return ResponseEntity.ok(userService.getDoctors());
    }
}
