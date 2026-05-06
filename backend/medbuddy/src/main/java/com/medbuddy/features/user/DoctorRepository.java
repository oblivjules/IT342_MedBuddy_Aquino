package com.medbuddy.features.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medbuddy.shared.model.Doctor;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUser_Id(Long userId);

    @Query("SELECT DISTINCT d FROM Doctor d LEFT JOIN FETCH d.specializations WHERE d.user.id = :userId")
    Optional<Doctor> findByUser_IdWithSpecializations(@Param("userId") Long userId);

    boolean existsByUser_Id(Long userId);

    @Query("SELECT DISTINCT d FROM Doctor d JOIN d.specializations s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :specialization, '%'))")
    List<Doctor> findBySpecializationContainingIgnoreCase(@Param("specialization") String specialization);

    @Query(value = "SELECT specializations FROM doctors WHERE id = :doctorId", nativeQuery = true)
    String findSpecializationsSummaryRawByDoctorId(@Param("doctorId") Long doctorId);

    @Query("SELECT d.id FROM Doctor d")
    List<Long> findAllIds();
}