package com.medbuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.Appointment;
import com.medbuddy.model.AppointmentStatus;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** All appointments where the given patient profile is the booker. */
    List<Appointment> findByPatient_IdOrderByDateTimeAsc(Long patientId);

    /** All appointments where the given doctor profile is the provider. */
    List<Appointment> findByDoctor_IdOrderByDateTimeAsc(Long doctorId);

    /** Doctor's appointments filtered by status (e.g. fetch only PENDING ones). */
    List<Appointment> findByDoctor_IdAndStatusOrderByDateTimeAsc(Long doctorId, AppointmentStatus status);
}
