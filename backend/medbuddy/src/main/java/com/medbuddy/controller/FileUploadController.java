package com.medbuddy.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.medbuddy.dto.FileUploadResponse;
import com.medbuddy.service.FileUploadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final FileUploadService fileUploadService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<FileUploadResponse> upload(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("recordId") Long recordId,
            @RequestParam("file") MultipartFile file) {
        String email = Objects.requireNonNull(userDetails, "Authenticated user is required.").getUsername();
        log.info("[FILE_UPLOAD][CONTROLLER] POST /api/files/upload user={} recordId={} fileName={} size={} bytes",
            email,
                recordId,
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fileUploadService.upload(email, recordId, file));
    }

    @GetMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<FileUploadResponse>> getByRecord(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long recordId) {
        String email = Objects.requireNonNull(userDetails, "Authenticated user is required.").getUsername();
        return ResponseEntity.ok(fileUploadService.getByRecord(email, recordId));
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<FileUploadResponse>> getByAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        String email = Objects.requireNonNull(userDetails, "Authenticated user is required.").getUsername();
        return ResponseEntity.ok(fileUploadService.getByAppointment(email, appointmentId));
    }

    @GetMapping("/item/{fileId}/url")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<Map<String, String>> getSecureFileUrl(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long fileId) {
        String email = Objects.requireNonNull(userDetails, "Authenticated user is required.").getUsername();
        String url = fileUploadService.getAccessUrl(email, fileId);
        return ResponseEntity.ok(Map.of("url", url));
    }
}