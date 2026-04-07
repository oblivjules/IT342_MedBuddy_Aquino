package com.medbuddy.service.appointment.strategy;

import java.util.List;

import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;

public interface AppointmentAccessStrategy {

    boolean supports(Role role);

    List<Appointment> findAppointments(User user, String userEmail);

    boolean isOwner(User user, Appointment appointment);

    void validateRequestedStatus(AppointmentStatus requestedStatus);
}
