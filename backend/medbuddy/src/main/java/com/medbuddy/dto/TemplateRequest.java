package com.medbuddy.dto;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequest {

    private int dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
}
