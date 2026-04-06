package com.medbuddy.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TemplateChangedEvent {

    private final Long doctorId;
}
