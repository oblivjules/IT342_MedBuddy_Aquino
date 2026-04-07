package com.medbuddy.adapter;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.medbuddy.dto.AppointmentResponse;
import com.medbuddy.dto.DoctorDto;
import com.medbuddy.dto.PatientDto;
import com.medbuddy.model.Appointment;
import com.medbuddy.model.Doctor;
import com.medbuddy.model.Patient;
import com.medbuddy.model.Specialization;

@Component
public class AppointmentResponseAdapter {

    public AppointmentResponse toResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patient(toPatientDto(appointment.getPatient()))
                .doctor(toDoctorDto(appointment.getDoctor()))
                .dateTime(resolveAppointmentDateTime(appointment))
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .createdAt(appointment.getCreatedAt())
                .build();
    }

    private LocalDateTime resolveAppointmentDateTime(Appointment appointment) {
        if (appointment.getSlot() != null) {
            return LocalDateTime.of(
                    appointment.getSlot().getSlotDate(),
                    appointment.getSlot().getSlotStartTime());
        }

        if (appointment.getDateTime() != null) {
            return appointment.getDateTime();
        }

        if (appointment.getDate() != null && appointment.getTime() != null) {
            return LocalDateTime.of(appointment.getDate(), appointment.getTime());
        }

        return appointment.getCreatedAt();
    }

    private PatientDto toPatientDto(Patient patient) {
        return PatientDto.builder()
                .id(patient.getId())
                .userId(patient.getUser().getId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .phoneNumber(patient.getPhoneNumber())
                .email(patient.getUser().getEmail())
                .build();
    }

    private DoctorDto toDoctorDto(Doctor doctor) {
        return DoctorDto.builder()
                .id(doctor.getId())
                .userId(doctor.getUser().getId())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .phoneNumber(doctor.getPhoneNumber())
                .specializations(doctor.getSpecializations().stream()
                        .map(Specialization::getName)
                        .collect(Collectors.toList()))
                .profileImageUrl(doctor.getUser().getProfileImageUrl())
                .email(doctor.getUser().getEmail())
                .build();
    }
}
