package com.medbuddy.features.user;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medbuddy.features.user.DoctorDto;
import com.medbuddy.features.auth.AuthResponse;
import com.medbuddy.features.user.UpdateProfileRequest;
import com.medbuddy.features.user.UserDto;
import com.medbuddy.features.user.UserService;

import jakarta.validation.Valid;
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
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<UserDto> me(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserDto dto = userService.getMe(userDetails.getUsername());
        return ResponseEntity.ok(dto);
    }

    /**
     * Update the current authenticated user's basic profile.
     * PATCH /api/users/me
     */
    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<AuthResponse> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        AuthResponse response = userService.updateMe(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * List all registered doctors with their profile details.
     * Used by the patient-side "Find a Doctor" feature.
     * GET /api/users/doctors
     */
    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<DoctorDto>> getDoctors() {
        return ResponseEntity.ok(userService.getDoctors());
    }

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<UserDto> uploadDoctorProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateDoctorProfileImage(userDetails.getUsername(), file));
    }
}
