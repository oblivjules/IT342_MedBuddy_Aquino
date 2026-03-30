package com.medbuddy.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.Specialization;

@Repository
public interface SpecializationRepository extends JpaRepository<Specialization, Long> {

    java.util.List<Specialization> findAllByOrderByNameAsc();

    Optional<Specialization> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}

