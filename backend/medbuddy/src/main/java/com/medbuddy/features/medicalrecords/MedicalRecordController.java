package com.medbuddy.features.medicalrecords;

import com.medbuddy.features.prescription.DrugInfoResponse;
import com.medbuddy.features.prescription.DrugInfoService;
import com.medbuddy.features.medicalrecords.MedicalRecordRequest;
import com.medbuddy.features.medicalrecords.MedicalRecordResponse;
import com.medbuddy.features.medicalrecords.MedicalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MedicalRecordController
 *
 * Endpoints:
 *   POST   /api/medical-records               — DOCTOR: create a record for an appointment
 *   GET    /api/medical-records/{id}           — authenticated: get record by ID
 *   GET    /api/medical-records/{id}/drug-info — authenticated: get drug info for a record
 *   GET    /api/medical-records/appointment/{id} — authenticated: get record by appointment
 *   PUT    /api/medical-records/{id}           — DOCTOR: update a record
 *   DELETE /api/medical-records/{id}           — DOCTOR: delete a record
 */
@RestController
@RequestMapping("/api/medical-records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private final MedicalRecordService medicalRecordService;
    private final DrugInfoService drugInfoService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalRecordResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MedicalRecordRequest request) {
        MedicalRecordResponse response =
                medicalRecordService.create(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<List<MedicalRecordResponse>> getMyRecords(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(medicalRecordService.getMyRecords(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<MedicalRecordResponse> getById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(medicalRecordService.getById(userDetails.getUsername(), id));
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<?> getByAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        try {
            MedicalRecordResponse response = medicalRecordService.getByAppointment(userDetails.getUsername(), appointmentId);
            // Return 404 if no medical record exists yet (null), rather than throwing exception
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/drug-info")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<DrugInfoResponse> getDrugInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        MedicalRecordResponse record = medicalRecordService.getById(userDetails.getUsername(), id);

        var drugInfo = drugInfoService.getDrugInfo(record.getMedicineName());

        if (drugInfo == null) {
            return ResponseEntity.ok(DrugInfoResponse.notAvailable());
        }

        return ResponseEntity.ok(DrugInfoResponse.available(drugInfo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicalRecordResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody MedicalRecordRequest request) {
        MedicalRecordResponse response =
                medicalRecordService.update(userDetails.getUsername(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        medicalRecordService.delete(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
