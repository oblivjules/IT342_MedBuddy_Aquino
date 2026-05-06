package com.medbuddy.features.user;

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
    private java.util.List<String> specializations;
    private String profileImageUrl;
    private String email;       // from User
}
