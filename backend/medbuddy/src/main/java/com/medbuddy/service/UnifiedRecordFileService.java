package com.medbuddy.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medbuddy.dto.UnifiedRecordFileResponse;
import com.medbuddy.repository.AppointmentRepository;
import com.medbuddy.repository.DoctorRepository;
import com.medbuddy.repository.FileUploadRepository;
import com.medbuddy.repository.MedicalRecordFileRepository;
import com.medbuddy.repository.PatientRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.MedicalRecordFile;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnifiedRecordFileService {

    // DEBUG: Logger for unified record file operations
    private static final Logger log = LoggerFactory.getLogger(UnifiedRecordFileService.class);

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileUploadRepository fileUploadRepository;
    private final MedicalRecordFileRepository medicalRecordFileRepository;
    private final FileUploadService fileUploadService;
    private final MedicalRecordFileService medicalRecordFileService;

    @Transactional(readOnly = true)
    public List<UnifiedRecordFileResponse> getMyUnifiedFiles(String requesterEmail) {
        User requester = findUser(requesterEmail);
        if (requester.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can access their unified records.");
        }

        Patient patient = patientRepository.findByUser_Id(requester.getId())
                .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + requesterEmail));
        
        // DEBUG: Log patient resolution
        log.info("[DEBUG] getMyUnifiedFiles - requesterEmail={} requesterUserId={} patientId={} patientUserId={}",
            requesterEmail, requester.getId(), patient.getId(), patient.getUser().getId());

        return buildUnifiedFiles(requesterEmail, patient.getId(), patient.getUser().getId());
    }

    @Transactional(readOnly = true)
    public List<UnifiedRecordFileResponse> getUnifiedFilesForPatient(String requesterEmail, Long patientId) {
        User requester = findUser(requesterEmail);
        if (requester.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can access patient unified records.");
        }

        Doctor doctor = doctorRepository.findByUser_Id(requester.getId())
                .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + requesterEmail));

        if (!appointmentRepository.existsByDoctor_IdAndPatient_Id(doctor.getId(), patientId)) {
            throw new AccessDeniedException("Doctor can only access records for assigned patients.");
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + patientId));

        return buildUnifiedFiles(requesterEmail, patientId, patient.getUser().getId());
    }

    private List<UnifiedRecordFileResponse> buildUnifiedFiles(String requesterEmail, Long patientId, Long patientUserId) {
        // DEBUG: Log query parameters
        log.info("[DEBUG] buildUnifiedFiles START - patientId={} patientUserId={}", patientId, patientUserId);
        
        // Query MedicalRecordFile records for patient uploads (Your Past Uploads)
        // Source: medical_record_files table, filtered to records where uploadedBy.role == PATIENT
        List<MedicalRecordFile> patientUploadRaw = medicalRecordFileRepository.findByPatient_IdOrderByUploadedAtDesc(patientId);
        log.info("[DEBUG] buildUnifiedFiles - medicalRecordFileRepository (patient uploads) returned {} records", patientUploadRaw.size());
        
        List<UnifiedRecordFileResponse> patientUploads = patientUploadRaw.stream()
            .filter(file -> patientUserId.equals(file.getUploadedByUserId()))
            .map(file -> {
                String fileUrl = resolveMedicalRecordFileUrl(requesterEmail, file.getId());
                return UnifiedRecordFileResponse.builder()
                    .fileId(file.getId())
                    .appointmentId(null)
                    .medicalRecordId(null)
                    .fileName(file.getFileName())
                    .fileUrl(fileUrl)
                    .uploadedBy(Role.PATIENT.name())
                    .uploadedAt(file.getUploadedAt())
                    .locked(fileUrl == null)
                    .build();
            })
            .toList();
        log.info("[DEBUG] buildUnifiedFiles - patientUploads after filtering: {} records", patientUploads.size());
        
        // Query MedicalRecordFile records for doctor uploads (Doctor Shared Records)
        // Source: medical_record_files table, filtered to records where uploaded_by_user_id != patientUserId
        List<UnifiedRecordFileResponse> doctorUploads = patientUploadRaw.stream()
            .filter(file -> !patientUserId.equals(file.getUploadedByUserId()))
            .map(file -> {
                String fileUrl = resolveMedicalRecordFileUrl(requesterEmail, file.getId());
                return UnifiedRecordFileResponse.builder()
                    .fileId(file.getId())
                    .appointmentId(null)
                    .medicalRecordId(null)
                    .fileName(file.getFileName())
                    .fileUrl(fileUrl)
                    .uploadedBy(Role.DOCTOR.name())
                    .uploadedAt(file.getUploadedAt())
                    .locked(fileUrl == null)
                    .build();
            })
            .toList();
        log.info("[DEBUG] buildUnifiedFiles - doctorUploads after filtering: {} records", doctorUploads.size());

        List<UnifiedRecordFileResponse> finalResult = Stream
            .concat(patientUploads.stream(), doctorUploads.stream())
            .sorted(Comparator.comparing(UnifiedRecordFileResponse::getUploadedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        log.info("[DEBUG] buildUnifiedFiles END - Final result count: {} records", finalResult.size());
        
        return finalResult;
    }

    private String resolveFileUrl(String requesterEmail, Long fileId) {
        try {
            return fileUploadService.getAccessUrl(requesterEmail, fileId);
        } catch (Exception ex) {
            log.warn("[DEBUG] resolveFileUrl failed for fileId={} requesterEmail={} reason={}", fileId, requesterEmail, ex.getMessage());
            return null;
        }
    }

    private String resolveMedicalRecordFileUrl(String requesterEmail, Long fileId) {
        try {
            return medicalRecordFileService.getAccessUrl(requesterEmail, fileId);
        } catch (Exception ex) {
            log.warn("[DEBUG] resolveMedicalRecordFileUrl failed for fileId={} requesterEmail={} reason={}", fileId, requesterEmail, ex.getMessage());
            return null;
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + email));
    }
}
