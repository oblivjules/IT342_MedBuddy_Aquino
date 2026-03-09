package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level response returned by POST /api/auth/login and POST /api/auth/register.
 * Shape: { "token": "...", "user": { "id": 1, "email": "...", "role": "PATIENT" } }
 * Matches the shape expected by the web (AuthContext) and mobile (AuthDtos.kt) clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private UserDto user;
}
