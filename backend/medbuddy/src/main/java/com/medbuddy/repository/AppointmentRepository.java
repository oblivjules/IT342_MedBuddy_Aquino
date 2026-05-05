package com.medbuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** All appointments where the given patient profile is the booker. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "slot"})
    List<Appointment> findByPatient_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(Long patientId);

    /** All appointments where the given doctor profile is the provider. */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "slot"})
    List<Appointment> findByDoctor_IdOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(Long doctorId);

    /** Doctor's appointments filtered by status (e.g. fetch only PENDING ones). */
    @EntityGraph(attributePaths = {"patient", "patient.user", "doctor", "doctor.user", "slot"})
    List<Appointment> findByDoctor_IdAndStatusOrderBySlot_SlotDateAscSlot_SlotStartTimeAsc(Long doctorId, AppointmentStatus status);

    boolean existsByDoctor_IdAndPatient_Id(Long doctorId, Long patientId);
}
