package com.medbuddy.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.DoctorScheduleTemplate;

@Repository
public interface DoctorScheduleTemplateRepository extends JpaRepository<DoctorScheduleTemplate, Long> {

    List<DoctorScheduleTemplate> findByDoctorIdAndIsActiveTrue(Long doctorId);

    List<DoctorScheduleTemplate> findByDoctorId(Long doctorId);

    Optional<DoctorScheduleTemplate> findByDoctorIdAndDayOfWeek(Long doctorId, int dayOfWeek);
}
