package com.medbuddy.features.prescription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugInfoDto {

    private String indicationsAndUsage;
    private String warnings;
    private String dosageAndAdministration;
    private String description;
}
