package com.medbuddy.dto;

import com.medbuddy.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the authenticated user returned inside every AuthResponse and /me.
 * The password hash is NEVER included here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String email;
    private Role role;
    private String profileImageUrl;

    // ── Profile fields (populated from Patient or Doctor entity) ──────
    /** Database ID of the Patient or Doctor profile entity. */
    private Long profileId;
    private String firstName;
    private String lastName;
    private String phoneNumber;

    /** Only populated when role == DOCTOR. */
    private java.util.List<String> specializations;

    /** Backward-compatible field for older clients. */
    private String specialization;
}
