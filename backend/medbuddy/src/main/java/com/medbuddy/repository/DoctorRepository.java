package com.medbuddy.repository;

import com.medbuddy.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    @EntityGraph(attributePaths = { "user", "specializations" })
    Optional<Doctor> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    @Query("SELECT DISTINCT d FROM Doctor d JOIN d.specializations s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :specialization, '%'))")
    List<Doctor> findBySpecializationContainingIgnoreCase(@Param("specialization") String specialization);

    @Query(value = "SELECT specializations FROM doctors WHERE id = :doctorId", nativeQuery = true)
    String findSpecializationsSummaryRawByDoctorId(@Param("doctorId") Long doctorId);

    @Override
    @EntityGraph(attributePaths = { "user", "specializations" })
    List<Doctor> findAll();

    @Query("SELECT d.id FROM Doctor d")
    List<Long> findAllIds();
}
