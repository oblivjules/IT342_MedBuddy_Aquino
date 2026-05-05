package com.medbuddy.repository;

import com.medbuddy.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {

    Optional<MedicalRecord> findByAppointment_Id(Long appointmentId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.patient.user", "appointment.doctor", "appointment.doctor.user"})
    List<MedicalRecord> findByAppointment_Patient_User_IdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = {"appointment", "appointment.patient", "appointment.patient.user", "appointment.doctor", "appointment.doctor.user"})
    List<MedicalRecord> findByAppointment_Doctor_User_IdOrderByIdDesc(Long userId);

    boolean existsByAppointment_Id(Long appointmentId);
}
