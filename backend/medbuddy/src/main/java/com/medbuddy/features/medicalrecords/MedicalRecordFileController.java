package com.medbuddy.features.medicalrecords;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.medbuddy.features.medicalrecords.MedicalRecordFileResponse;
import com.medbuddy.features.medicalrecords.MedicalRecordFileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/medical-record-files")
@RequiredArgsConstructor
public class MedicalRecordFileController {

    private final MedicalRecordFileService medicalRecordFileService;

    @PostMapping(value = "/my", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<MedicalRecordFileResponse> uploadMyRecord(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "appointmentId", required = false) Long appointmentId) {
        MedicalRecordFileResponse response = medicalRecordFileService.uploadAsPatient(
                userDetails.getUsername(), file, description, appointmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<MedicalRecordFileResponse>> getByAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        return ResponseEntity.ok(medicalRecordFileService.getByAppointment(userDetails.getUsername(), appointmentId));
    }

    @PostMapping(value = "/patients/{patientId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalRecordFileResponse> uploadForPatient(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        MedicalRecordFileResponse response = medicalRecordFileService.uploadForPatient(
                userDetails.getUsername(), patientId, file, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/patients/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<MedicalRecordFileResponse>> getPatientFiles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long patientId) {
        return ResponseEntity.ok(medicalRecordFileService.getForPatient(userDetails.getUsername(), patientId));
    }

    @GetMapping("/{fileId}/url")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<Map<String, String>> getSecureFileUrl(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long fileId) {
        String url = medicalRecordFileService.getAccessUrl(userDetails.getUsername(), fileId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}

