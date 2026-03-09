package com.medbuddy.repository;

import com.medbuddy.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    List<Doctor> findBySpecializationIgnoreCase(String specialization);
}
