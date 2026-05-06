package com.medbuddy.features.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^\\+63\\d{10}$",
            message = "Phone number must start with +63 and contain exactly 10 digits")
    private String phoneNumber;

    @Size(min = 8, max = 100, message = "Current password must be at least 8 characters")
    private String currentPassword;


    @Size(min = 8, max = 100, message = "New password must be at least 8 characters")
    private String newPassword;

    private List<@Positive(message = "Specialization ID must be greater than 0") Long> specializationIds;
}

