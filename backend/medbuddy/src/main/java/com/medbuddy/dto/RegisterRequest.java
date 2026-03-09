package com.medbuddy.dto;

import com.medbuddy.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull(message = "Role is required (PATIENT or DOCTOR)")
    private Role role;

    // ── Profile fields ────────────────────────────────────────────────
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    @Pattern(
            regexp = "^\\+63\\d{10}$",
            message = "Phone number must start with +63 and contain exactly 10 digits")
    private String phoneNumber;

    /** Required when role == DOCTOR. */
    @Size(max = 1000, message = "Specialization cannot exceed 1000 characters")
    private String specialization;
}
