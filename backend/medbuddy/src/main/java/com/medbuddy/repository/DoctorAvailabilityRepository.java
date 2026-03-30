package com.medbuddy.repository;

import com.medbuddy.model.DoctorAvailability;
import com.medbuddy.model.DoctorAvailabilityId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, DoctorAvailabilityId> {

    List<DoctorAvailability> findByIdDoctorIdOrderByIdAvailableDateAscIdStartTimeAsc(Long doctorId);

    List<DoctorAvailability> findByIdDoctorIdAndIdAvailableDateOrderByIdStartTimeAsc(
            Long doctorId,
            LocalDate date);

    void deleteByIdDoctorIdAndIdAvailableDate(Long doctorId, LocalDate date);
}

