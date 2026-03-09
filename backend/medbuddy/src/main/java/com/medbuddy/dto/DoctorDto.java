package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorDto {

    private Long id;            // Doctor profile ID
    private Long userId;        // Linked User ID
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String specialization;
    private String email;       // from User
}
