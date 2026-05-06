package com.medbuddy.features.schedule;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TemplateChangedEvent {

    private final Long doctorId;
}
