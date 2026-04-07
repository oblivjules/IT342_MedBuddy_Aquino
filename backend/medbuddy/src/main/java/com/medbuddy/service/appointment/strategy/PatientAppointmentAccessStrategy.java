package com.medbuddy.service.appointment.strategy;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;
import com.medbuddy.model.Role;
import com.medbuddy.model.User;
import com.medbuddy.repository.AppointmentRepository;
import com.medbuddy.repository.PatientRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PatientAppointmentAccessStrategy implements AppointmentAccessStrategy {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;

    @Override
    public boolean supports(Role role) {
        return role == Role.PATIENT;
    }

    @Override
    public List<Appointment> findAppointments(User user, String userEmail) {
        Long patientId = patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Patient profile not found for user: " + userEmail))
                .getId();

        return appointmentRepository.findByPatient_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(patientId);
    }

    @Override
    public boolean isOwner(User user, Appointment appointment) {
        return appointment.getPatient().getUser().getId().equals(user.getId());
    }

    @Override
    public void validateRequestedStatus(AppointmentStatus requestedStatus) {
        if (requestedStatus != AppointmentStatus.CANCELLED) {
            throw new AccessDeniedException("Patients can only cancel appointments.");
        }
    }
}
