package com.medbuddy.service.appointment.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.AppointmentRepository;
import com.medbuddy.repository.DoctorRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DoctorAppointmentAccessStrategy implements AppointmentAccessStrategy {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    @Override
    public boolean supports(Role role) {
        return role == Role.DOCTOR;
    }

    @Override
    public List<Appointment> findAppointments(User user, String userEmail) {
        Long doctorId = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Doctor profile not found for user: " + userEmail))
                .getId();

        return appointmentRepository.findByDoctor_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(doctorId);
    }

    @Override
    public boolean isOwner(User user, Appointment appointment) {
        return appointment.getDoctor().getUser().getId().equals(user.getId());
    }

    @Override
    public void validateRequestedStatus(AppointmentStatus requestedStatus) {
        // Doctors can set any non-terminal transition, validated by state machine.
    }
}
