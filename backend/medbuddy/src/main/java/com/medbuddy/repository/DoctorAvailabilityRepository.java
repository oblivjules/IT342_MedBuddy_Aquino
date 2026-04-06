package com.medbuddy.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.DoctorAvailability;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    /** All availability slots for a given doctor. */
    List<DoctorAvailability> findByDoctor_IdOrderByAvailableDateAsc(Long doctorId);

    /** Availability slots for a doctor on a specific date. */
    Optional<DoctorAvailability> findByDoctor_IdAndAvailableDate(Long doctorId, LocalDate availableDate);

    List<DoctorAvailability> findByDoctor_IdAndAvailableDateBetweenOrderByAvailableDateAsc(
            Long doctorId,
            LocalDate startDate,
            LocalDate endDate);

    void deleteByDoctor_IdAndAvailableDate(Long doctorId, LocalDate availableDate);

}

