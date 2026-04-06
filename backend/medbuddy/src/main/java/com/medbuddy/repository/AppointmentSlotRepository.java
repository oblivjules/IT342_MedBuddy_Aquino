package com.medbuddy.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.model.AppointmentSlot;
import com.medbuddy.model.AppointmentSlotStatus;

@Repository
public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {

    List<AppointmentSlot> findByDoctorAvailability_IdOrderBySlotDateAscSlotStartTimeAsc(Long doctorAvailabilityId);

    List<AppointmentSlot> findByDoctorAvailability_Doctor_IdAndSlotDateOrderBySlotStartTimeAsc(Long doctorId, LocalDate slotDate);

    List<AppointmentSlot> findByDoctorIdAndSlotDateAndStatus(Long doctorId, LocalDate date, AppointmentSlotStatus status);

        @Modifying
        @Transactional
        @Query(value = """
                            INSERT INTO appointment_slots
                                (doctor_id, slot_date, slot_start_time, slot_end_time, status, doctor_availability_id)
                            VALUES
                                (:doctorId, :date, :start, :end, 'AVAILABLE', NULL)
                            ON CONFLICT (doctor_id, slot_date, slot_start_time) DO NOTHING
                        """, nativeQuery = true)
        void insertIfNotExists(@Param("doctorId") Long doctorId,
                                                     @Param("date") LocalDate date,
                                                     @Param("start") LocalTime start,
                                                     @Param("end") LocalTime end);

        @Modifying
        @Transactional
        @Query(value = """
                        DELETE FROM appointment_slots
                        WHERE doctor_id = :doctorId
                            AND slot_date = :date
                            AND status = 'AVAILABLE'
                        """, nativeQuery = true)
        void deleteUnbookedSlotsForDoctorAndDate(@Param("doctorId") Long doctorId,
                                                                                         @Param("date") LocalDate date);
}
