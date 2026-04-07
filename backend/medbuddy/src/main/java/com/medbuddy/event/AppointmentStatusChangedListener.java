package com.medbuddy.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AppointmentStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentStatusChangedListener.class);

    @EventListener
    public void onAppointmentStatusChanged(AppointmentStatusChangedEvent event) {
        log.info("Appointment {} status changed from {} to {} by {}",
                event.getAppointmentId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getActorEmail());
    }
}
