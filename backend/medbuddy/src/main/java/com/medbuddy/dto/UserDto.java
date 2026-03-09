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

    // ── Profile fields (populated from Patient or Doctor entity) ──────
    /** Database ID of the Patient or Doctor profile entity. */
    private Long profileId;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    /** Only populated when role == DOCTOR. */
    private String specialization;
}
