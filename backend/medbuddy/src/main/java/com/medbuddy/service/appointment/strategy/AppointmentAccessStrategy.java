package com.medbuddy.service.appointment.strategy;

import java.util.List;

import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.AppointmentStatus;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.User;

public interface AppointmentAccessStrategy {

    boolean supports(Role role);

    List<Appointment> findAppointments(User user, String userEmail);

    boolean isOwner(User user, Appointment appointment);

    void validateRequestedStatus(AppointmentStatus requestedStatus);
}
