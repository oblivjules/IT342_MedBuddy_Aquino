package com.medbuddy.features.medicalrecords;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.features.medicalrecords.UnifiedRecordFileResponse;
import com.medbuddy.features.medicalrecords.UnifiedRecordFileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/record-files")
@RequiredArgsConstructor
public class UnifiedRecordFileController {

    // DEBUG: Logger for unified record file operations
    private static final Logger log = LoggerFactory.getLogger(UnifiedRecordFileController.class);

    private final UnifiedRecordFileService unifiedRecordFileService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<UnifiedRecordFileResponse>> getMyFiles(
            Authentication authentication) {
        // DEBUG: Log the raw authentication/principal details before any extraction happens.
        if (authentication == null) {
            log.warn("[DEBUG] GET /api/record-files/my - authentication=null");
            return ResponseEntity.badRequest().build();
        }

        Object principal = authentication.getPrincipal();
        log.info("[DEBUG] GET /api/record-files/my - authClass={} authName={} principalClass={} authorities={}",
                authentication.getClass().getName(),
                authentication.getName(),
                principal != null ? principal.getClass().getName() : "null",
                authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));

        String email = resolveRequesterEmail(authentication);
        log.info("[DEBUG] GET /api/record-files/my - resolvedRequesterEmail={}", email);
        if (email == null || email.isBlank()) {
            log.warn("[DEBUG] GET /api/record-files/my - unable to resolve requester email from authentication");
            return ResponseEntity.badRequest().build();
        }
        
        List<UnifiedRecordFileResponse> result = unifiedRecordFileService.getMyUnifiedFiles(email);
        
        // DEBUG: Log the result count and details
        log.info("[DEBUG] GET /api/record-files/my - responseSize={}", result.size());
        if (result.isEmpty()) {
            log.warn("[DEBUG] GET /api/record-files/my - EMPTY RESULT SET for email={}", email);
        } else {
            result.forEach(f -> log.info("[DEBUG] GET /api/record-files/my - fileId={} fileName={} uploadedBy={} medicalRecordId={}", 
                f.getFileId(), f.getFileName(), f.getUploadedBy(), f.getMedicalRecordId()));
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<UnifiedRecordFileResponse>> getPatientFiles(
            Authentication authentication,
            @PathVariable Long patientId) {
        String email = resolveRequesterEmail(authentication);
        log.info("[DEBUG] GET /api/record-files/patients/{} - resolvedRequesterEmail={}", patientId, email);
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(unifiedRecordFileService.getUnifiedFilesForPatient(email, patientId));
    }

    // DEBUG: Centralize principal/email extraction so patient and doctor flows share the same logic.
    private String resolveRequesterEmail(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    (org.springframework.security.core.userdetails.UserDetails) principal;
            return userDetails.getUsername();
        }

        if (principal instanceof String) {
            return (String) principal;
        }

        String authName = authentication.getName();
        return authName != null && !authName.isBlank() ? authName : null;
    }
}
