package com.medbuddy.features.medicalrecords;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.medbuddy.features.medicalrecords.MedicalRecordFileResponse;
import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.features.user.DoctorRepository;
import com.medbuddy.features.user.PatientRepository;
import com.medbuddy.features.user.UserRepository;
import com.medbuddy.features.medicalrecords.MedicalRecordFileRepository;
import com.medbuddy.shared.model.Doctor;
import com.medbuddy.shared.model.MedicalRecordFile;
import com.medbuddy.shared.model.Patient;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicalRecordFileService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordFileService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "pdf");

    @Value("${app.storage.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes;

    private final MedicalRecordFileRepository medicalRecordFileRepository;
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final FileStorageService fileStorageService;
    private final FileUploadValidationService fileUploadValidationService;

    @Transactional
    public MedicalRecordFileResponse uploadAsPatient(String email, MultipartFile file, String description, Long appointmentId) {
        log.info("[MEDICAL_RECORD_FILE][PATIENT] request email={} fileName={} size={} bytes appointmentId={}",
                email,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null,
                appointmentId);

        User user = findUser(email);
        if (user.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("Only patients can upload personal records.");
        }

        Patient patient = patientRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + email));

        com.medbuddy.shared.model.Appointment appointment = null;
        if (appointmentId != null) {
            appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));
            if (!appointment.getPatient().getId().equals(patient.getId())) {
                throw new AccessDeniedException("Cannot attach file to an appointment that does not belong to you.");
            }
        }

        return saveFile(file, description, user, patient, appointment, "medical-records/patient-" + patient.getId());
    }

    @Transactional
    public MedicalRecordFileResponse uploadForPatient(String doctorEmail, Long patientId, MultipartFile file, String description) {
        log.info("[MEDICAL_RECORD_FILE][DOCTOR] request email={} patientId={} fileName={} size={} bytes",
                doctorEmail,
                patientId,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);

        User user = findUser(doctorEmail);
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can upload records for patients.");
        }

        Doctor doctor = doctorRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + doctorEmail));

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + patientId));

        if (!appointmentRepository.existsByDoctor_IdAndPatient_Id(doctor.getId(), patient.getId())) {
            throw new AccessDeniedException("Doctor can only upload records for assigned patients.");
        }

        return saveFile(file, description, user, patient, null, "medical-records/patient-" + patient.getId());
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordFileResponse> getByAppointment(String requesterEmail, Long appointmentId) {
        User requester = findUser(requesterEmail);
        com.medbuddy.shared.model.Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

        switch (requester.getRole()) {
            case PATIENT -> {
                Patient patient = patientRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + requesterEmail));
                if (!patient.getId().equals(appointment.getPatient().getId())) {
                    throw new AccessDeniedException("You can only view files for your own appointments.");
                }
            }
            case DOCTOR -> {
                Doctor doctor = doctorRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + requesterEmail));
                if (!doctor.getId().equals(appointment.getDoctor().getId())) {
                    throw new AccessDeniedException("You can only view files for appointments assigned to you.");
                }
            }
            default -> throw new AccessDeniedException("Not allowed to access appointment files.");
        }

        return medicalRecordFileRepository.findByAppointment_IdOrderByUploadedAtDesc(appointmentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalRecordFileResponse> getForPatient(String requesterEmail, Long patientId) {
        User requester = findUser(requesterEmail);
        switch (requester.getRole()) {
            case PATIENT -> {
                Patient patient = patientRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + requesterEmail));
                if (!patient.getId().equals(patientId)) {
                    throw new AccessDeniedException("Patients can only access their own records.");
                }
            }
            case DOCTOR -> {
                Doctor doctor = doctorRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + requesterEmail));
                if (!appointmentRepository.existsByDoctor_IdAndPatient_Id(doctor.getId(), patientId)) {
                    throw new AccessDeniedException("Doctor can only access records for assigned patients.");
                }
            }
            default -> throw new AccessDeniedException("Not allowed to access medical records.");
        }

        return medicalRecordFileRepository.findByPatient_IdOrderByUploadedAtDesc(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String getAccessUrl(String requesterEmail, Long fileId) {
        User requester = findUser(requesterEmail);
        MedicalRecordFile file = medicalRecordFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Medical record file not found with id: " + fileId));

        enforceReadAccess(requester, file.getPatient().getId(), requesterEmail);

        log.debug("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} storagePath={} fileUrl={}",
                file.getId(), file.getStoragePath(), file.getFileUrl());

        String pathForSignedUrl;
        String chosenField;
        if (StringUtils.hasText(file.getStoragePath())) {
            pathForSignedUrl = file.getStoragePath();
            chosenField = "storagePath";
        } else if (StringUtils.hasText(file.getFileUrl())) {
            pathForSignedUrl = file.getFileUrl();
            chosenField = "fileUrl";
        } else {
            log.warn("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} has no storagePath or fileUrl; cannot resolve access URL",
                    file.getId());
            throw new IllegalArgumentException("Unable to resolve file access URL for this record.");
        }

        // Explicit debug: which DB field we chose for signing and its value (truncated for readability)
        String displayValue = pathForSignedUrl.length() > 200 ? pathForSignedUrl.substring(0, 200) + "..." : pathForSignedUrl;
        log.debug("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} chosenField={} chosenValue={}",
                file.getId(), chosenField, displayValue);

        try {
            String accessUrl = fileStorageService.createSignedUrl(pathForSignedUrl);
            if (StringUtils.hasText(accessUrl)) {
                log.debug("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} resolvedSignedUrl={}", file.getId(), accessUrl);
                return accessUrl;
            }
        } catch (Exception ex) {
            log.warn("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} failed to sign path='{}' (field={}) : {}",
                    file.getId(), displayValue, chosenField, ex.getMessage());
        }

        if (StringUtils.hasText(file.getFileUrl())) {
            log.debug("[MEDICAL_RECORD_FILE][ACCESS_URL] fileId={} falling back to database fileUrl={}",
                    file.getId(), file.getFileUrl());
            return file.getFileUrl();
        }

        throw new IllegalArgumentException("Unable to resolve file access URL for this record.");
    }

    private void enforceReadAccess(User requester, Long patientId, String requesterEmail) {
        switch (requester.getRole()) {
            case PATIENT -> {
                Patient patient = patientRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Patient profile not found for user: " + requesterEmail));
                if (!patient.getId().equals(patientId)) {
                    throw new AccessDeniedException("Patients can only access their own records.");
                }
            }
            case DOCTOR -> {
                Doctor doctor = doctorRepository.findByUser_Id(requester.getId())
                        .orElseThrow(() -> new IllegalStateException("Doctor profile not found for user: " + requesterEmail));
                if (!appointmentRepository.existsByDoctor_IdAndPatient_Id(doctor.getId(), patientId)) {
                    throw new AccessDeniedException("Doctor can only access records for assigned patients.");
                }
            }
            default -> throw new AccessDeniedException("Not allowed to access medical records.");
        }
    }

    private MedicalRecordFileResponse saveFile(MultipartFile file, String description, User uploadedBy, Patient patient, com.medbuddy.shared.model.Appointment appointment, String folder) {
        validate(file);

        FileUploadValidationService.ValidatedFileMetadata metadata =
                fileUploadValidationService.validate(file, ALLOWED_TYPES, ALLOWED_EXTENSIONS);

        StorageUploadResult storageResult = fileStorageService.store(file, folder);

        log.info("[MEDICAL_RECORD_FILE][STORE] uploadedByUserId={} patientId={} appointmentId={} storagePath={}",
            uploadedBy.getId(),
            patient.getId(),
            appointment != null ? appointment.getId() : null,
            storageResult.storagePath());

        MedicalRecordFile recordFile = MedicalRecordFile.builder()
                .fileName(file.getOriginalFilename())
            .fileUrl(storageResult.fileUrl())
            .storagePath(storageResult.storagePath())
            .fileType(metadata.mimeType())
                .fileSizeBytes(file.getSize())
                .description(description)
                .uploadedBy(uploadedBy)
                .patient(patient)
                .appointment(appointment)
                .build();

        log.info("[MEDICAL_RECORD_FILE][DB] About to persist entity: uploadedByUserId={} patientId={} fileName={}",
            uploadedBy.getId(),
            patient.getId(),
            recordFile.getFileName());

        try {
            MedicalRecordFile saved = medicalRecordFileRepository.save(recordFile);
            log.info("[MEDICAL_RECORD_FILE][DB] Persisted successfully: fileId={} uploadedByUserId={} patientId={}",
                saved.getId(),
                uploadedBy.getId(),
                patient.getId());
            return toResponse(saved);
        } catch (Exception ex) {
            log.error("[MEDICAL_RECORD_FILE][DB] FAILED to persist entity: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new IllegalArgumentException("File exceeds maximum allowed size.");
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found with email: " + email));
    }

    private MedicalRecordFileResponse toResponse(MedicalRecordFile file) {
        log.debug("[MEDICAL_RECORD_FILE][RESPONSE] fileId={} storagePath={} fileUrl={}",
            file.getId(), file.getStoragePath(), file.getFileUrl());

        return MedicalRecordFileResponse.builder()
                .id(file.getId())
                .fileName(file.getFileName())
            .fileUrl(file.getFileUrl())
                .fileType(file.getFileType())
                .fileSizeBytes(file.getFileSizeBytes())
                .description(file.getDescription())
                .uploadedAt(file.getUploadedAt())
                .uploadedByUserId(file.getUploadedBy().getId())
                .patientId(file.getPatient().getId())
                .appointmentId(file.getAppointment() != null ? file.getAppointment().getId() : null)
                .build();
    }
}

