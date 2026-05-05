package com.medbuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.model.MedicalRecordFile;

@Repository
public interface MedicalRecordFileRepository extends JpaRepository<MedicalRecordFile, Long> {

    List<MedicalRecordFile> findByPatient_IdOrderByUploadedAtDesc(Long patientId);
}

