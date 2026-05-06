package com.medbuddy.features.medicalrecords;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.medbuddy.shared.model.FileUpload;

import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface FileUploadRepository extends JpaRepository<FileUpload, Long> {

	@EntityGraph(attributePaths = {"uploadedByUser", "medicalRecord", "medicalRecord.appointment", "appointment"})
	List<FileUpload> findByMedicalRecord_IdOrderByUploadedAtDesc(Long medicalRecordId);

	@EntityGraph(attributePaths = {"uploadedByUser", "medicalRecord", "medicalRecord.appointment", "appointment"})
	List<FileUpload> findByAppointment_IdOrderByUploadedAtDesc(Long appointmentId);

	@EntityGraph(attributePaths = {"uploadedByUser", "medicalRecord", "medicalRecord.appointment", "appointment"})
	List<FileUpload> findByAppointment_Patient_IdOrderByUploadedAtDesc(Long patientId);
}
