package com.medbuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.Specialization;

@Repository
public interface SpecializationRepository extends JpaRepository<Specialization, Long> {

	java.util.List<Specialization> findAllByOrderByNameAsc();

	boolean existsByNameIgnoreCase(String name);
}

