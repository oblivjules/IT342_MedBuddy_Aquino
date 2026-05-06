package com.medbuddy.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.medbuddy.dto.FileUploadResponse;
import com.medbuddy.repository.FileUploadRepository;
import com.medbuddy.repository.MedicalRecordRepository;
import com.medbuddy.repository.PaymentRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.FileUpload;
import com.medbuddy.shared.model.MedicalRecord;
import com.medbuddy.shared.model.PaymentStatus;
import com.medbuddy.shared.model.User;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileUploadService {

	private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private static final java.util.Set<String> ALLOWED_MIME_TYPES = java.util.Set.of("application/pdf", "image/jpeg", "image/png");
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of("jpg", "jpeg", "png", "pdf");

    private final FileUploadRepository fileUploadRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final FileStorageService fileStorageService;
    private final FileUploadValidationService fileUploadValidationService;
    private final EntityManager entityManager;

    @Transactional
    public FileUploadResponse upload(String email, Long medicalRecordId, MultipartFile file) {
        log.info("[FILE_UPLOAD][RECORD] request email={} medicalRecordId={} fileName={} size={} bytes",
            email,
            medicalRecordId,
            file != null ? file.getOriginalFilename() : null,
            file != null ? file.getSize() : null);

        User user = requireUser(email);
        log.debug("[FILE_UPLOAD][RECORD] User found: id={} email={}", user.getId(), user.getEmail());
        
        MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Medical record not found with id: " + medicalRecordId));
        log.debug("[FILE_UPLOAD][RECORD] MedicalRecord found: id={} appointmentId={}", medicalRecord.getId(), medicalRecord.getAppointment().getId());

        Appointment appointment = medicalRecord.getAppointment();
        
        authorize(user, appointment);
        log.debug("[FILE_UPLOAD][RECORD] Authorization passed for user {} on appointment {}", user.getId(), appointment.getId());
        
        // Prevent file uploads to completed appointments
        if (appointment.getStatus() == com.medbuddy.shared.model.AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot attach files to a completed appointment.");
        }
        log.debug("[FILE_UPLOAD][RECORD] Appointment status validated: status={}", appointment.getStatus());

        FileUploadValidationService.ValidatedFileMetadata metadata =
            fileUploadValidationService.validate(file, ALLOWED_MIME_TYPES, ALLOWED_EXTENSIONS);
        log.debug("[FILE_UPLOAD][RECORD] File validation passed: mimeType={} extension={}", metadata.mimeType(), metadata.extension());
        
        long fileSizeBytes = file != null ? file.getSize() : 0L;

        StorageUploadResult storageResult = fileStorageService.store(file, buildFolder(medicalRecord));
        log.info("[FILE_UPLOAD][RECORD] Supabase upload successful: storagePath={} url={}", storageResult.storagePath(), storageResult.fileUrl());
        
        if (storageResult.storagePath() == null || storageResult.storagePath().isBlank()) {
            throw new IllegalStateException("Storage path is required for file_uploads persistence.");
        }

        log.info("[FILE_UPLOAD][RECORD] stored appointmentId={} userId={} storagePath={} url={}",
            medicalRecord.getAppointment().getId(),
            user.getId(),
            storageResult.storagePath(),
            storageResult.fileUrl());

        FileUpload upload = FileUpload.builder()
                .medicalRecord(medicalRecord)
                .appointment(medicalRecord.getAppointment())
                .uploadedByUser(user)
            .fileName(resolveFileName(file))
                .storagePath(storageResult.storagePath())
                .fileUrl(storageResult.fileUrl())
                .fileType(metadata.mimeType())
                .fileExtension(metadata.extension())
                .fileSizeBytes(fileSizeBytes)
                .build();

        log.info("[FILE_UPLOAD][DB] About to persist FileUpload entity: userId={} recordId={} fileName={} storagePath={}", 
            user.getId(), 
            medicalRecord.getId(), 
            upload.getFileName(),
            upload.getStoragePath());

        try {
            FileUpload savedUpload = fileUploadRepository.save(upload);
            
            log.info("[FILE_UPLOAD][DB] FileUpload successfully persisted to database: fileId={} userId={} recordId={} storagePath={}", 
                savedUpload.getId(),
                user.getId(), 
                medicalRecord.getId(), 
                savedUpload.getStoragePath());

            // Forces Hibernate to flush the persistence context to the database
            // to ensure the INSERT is executed and committed before returning
            entityManager.flush();
            log.debug("[FILE_UPLOAD][DB] Transaction flushed and changes committed to database for fileId={}", savedUpload.getId());

            return toResponse(savedUpload);
        } catch (Exception ex) {
            log.error("[FILE_UPLOAD][DB] FAILED to persist FileUpload entity to database: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<FileUploadResponse> getByAppointment(String email, Long appointmentId) {
        log.info("[FILE_UPLOAD][APPOINTMENT] request email={} appointmentId={}", email, appointmentId);

        User user = requireUser(email);
        MedicalRecord medicalRecord = medicalRecordRepository.findByAppointment_Id(appointmentId)
                .orElse(null);

        if (medicalRecord == null) {
            return List.of();
        }

        authorize(user, medicalRecord.getAppointment());

        return fileUploadRepository.findByAppointment_IdOrderByUploadedAtDesc(appointmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FileUploadResponse> getByRecord(String email, Long medicalRecordId) {
        log.info("[FILE_UPLOAD][RECORD] list request email={} medicalRecordId={}", email, medicalRecordId);

        User user = requireUser(email);
        MedicalRecord medicalRecord = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Medical record not found with id: " + medicalRecordId));

        authorize(user, medicalRecord.getAppointment());

        return fileUploadRepository.findByMedicalRecord_IdOrderByUploadedAtDesc(medicalRecordId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getAccessUrl(String email, Long fileId) {
        User user = requireUser(email);
        FileUpload upload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + fileId));

        Appointment appointment = upload.getAppointment();
        if (appointment == null && upload.getMedicalRecord() != null) {
            appointment = upload.getMedicalRecord().getAppointment();
        }

        if (appointment == null) {
            throw new IllegalStateException("File is missing appointment linkage.");
        }

        authorize(user, appointment);
        assertAppointmentIsPaid(appointment);

        String accessUrl = fileStorageService.createSignedUrl(upload.getStoragePath());

        if (!StringUtils.hasText(accessUrl)) {
            throw new IllegalArgumentException("Unable to resolve file access URL for this upload.");
        }

        return accessUrl;
    }

    private void authorize(User user, Appointment appointment) {
        boolean isPatientOwner = appointment.getPatient().getUser().getId().equals(user.getId());
        boolean isDoctorOwner = appointment.getDoctor().getUser().getId().equals(user.getId());

        if (!isPatientOwner && !isDoctorOwner) {
            throw new AccessDeniedException("You are not allowed to access these files.");
        }
    }

    private void assertAppointmentIsPaid(Appointment appointment) {
        if (!isAppointmentPaid(appointment)) {
            throw new AccessDeniedException("File access is locked until appointment payment is PAID.");
        }
    }

    private boolean isAppointmentPaid(Appointment appointment) {
        var payment = paymentRepository.findByAppointment_Id(appointment.getId())
                .orElse(null);
        return payment != null && payment.getPaymentStatus() == PaymentStatus.PAID;
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + email));
    }

    private String buildFolder(MedicalRecord medicalRecord) {
        return "medical-records/record-" + medicalRecord.getId();
    }

    private String resolveFileName(MultipartFile file) {
        String original = file != null ? file.getOriginalFilename() : null;
        return StringUtils.hasText(original) ? original : "file";
    }

    private FileUploadResponse toResponse(FileUpload upload) {
        return FileUploadResponse.builder()
                .id(upload.getId())
                .medicalRecordId(upload.getMedicalRecord() != null ? upload.getMedicalRecord().getId() : null)
                .appointmentId(upload.getAppointment() != null ? upload.getAppointment().getId() : null)
                .uploadedByUserId(upload.getUploadedByUser().getId())
                .fileName(upload.getFileName())
                .storagePath(upload.getStoragePath())
                .fileUrl(null)
                .fileType(upload.getFileType())
                .fileExtension(upload.getFileExtension())
                .fileSizeBytes(upload.getFileSizeBytes())
                .uploadedAt(upload.getUploadedAt())
                .build();
    }
}