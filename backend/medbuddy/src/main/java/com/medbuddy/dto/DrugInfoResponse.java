package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for drug information endpoint.
 * 
 * Contains a flag indicating whether drug information is available,
 * and the actual drug information data if available.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugInfoResponse {

    private Boolean available;
    private DrugInfoDto data;

    /**
     * Create a "not available" response.
     */
    public static DrugInfoResponse notAvailable() {
        return DrugInfoResponse.builder()
                .available(false)
                .data(null)
                .build();
    }

    /**
     * Create an "available" response with drug data.
     */
    public static DrugInfoResponse available(DrugInfoDto data) {
        return DrugInfoResponse.builder()
                .available(true)
                .data(data)
                .build();
    }
}
