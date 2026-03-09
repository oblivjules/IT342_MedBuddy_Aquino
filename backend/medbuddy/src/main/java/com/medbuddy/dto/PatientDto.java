package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {

    private Long id;           // Patient profile ID
    private Long userId;       // Linked User ID
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String email;      // from User
}
