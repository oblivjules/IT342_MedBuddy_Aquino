package com.medbuddy.features.payment;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medbuddy.features.payment.PaymentRequest;
import com.medbuddy.features.payment.PaymentResponse;
import com.medbuddy.features.payment.PaymentTotalUpdateRequest;
import com.medbuddy.features.payment.PaymentService;
import com.medbuddy.shared.model.PaymentStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * PaymentController
 *
 * Endpoints:
 *   POST   /api/payments                          — create a payment for an appointment
 *   GET    /api/payments/{id}                     — get payment by ID
 *   GET    /api/payments/appointment/{id}          — get payment by appointment
 *   PATCH  /api/payments/{id}/status              — update payment status
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.create(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> initiate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody com.medbuddy.features.payment.PaymentInitiateRequest request) {
        try {
            com.medbuddy.features.payment.PaymentInitiateResponse resp = paymentService.initiatePayment(userDetails.getUsername(), request);
            if (resp == null || resp.getCheckoutUrl() == null) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Failed to create PayMongo checkout session. Please try again."));
            }
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                Map.of("error", ex.getMessage()));
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Validation failed: " + ex.getMessage()));
        }
    }

    @GetMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<?> getByAppointmentByPath(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        try {
            return ResponseEntity.ok(paymentService.getByAppointment(userDetails.getUsername(), appointmentId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/payment/{id}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<PaymentResponse> getById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getById(userDetails.getUsername(), id));
    }

    @GetMapping("/appointment/{appointmentId}")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    public ResponseEntity<?> getByAppointment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId) {
        try {
            PaymentResponse response = paymentService.getByAppointment(userDetails.getUsername(), appointmentId);
            // Return 404 when no payment record exists yet instead of 200 with null
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of("error", ex.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponse> updateStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam PaymentStatus status) {
        return ResponseEntity.ok(paymentService.updateStatus(userDetails.getUsername(), id, status));
    }

    @PatchMapping("/appointment/{appointmentId}/total")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PaymentResponse> updateTotalBill(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long appointmentId,
            @Valid @RequestBody PaymentTotalUpdateRequest request) {
        return ResponseEntity.ok(paymentService.updateTotalBillForAppointment(
                userDetails.getUsername(),
                appointmentId,
                request.getTotalBillAmount()));
    }

        @GetMapping("/webhook")
        public ResponseEntity<Void> webhookHealthCheck() {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/webhook")
        public ResponseEntity<Map<String, String>> webhook(
                @RequestHeader(value = "PayMongo-Signature", required = false) String signature,
                @RequestBody String rawPayload) {
            paymentService.handleWebhook(rawPayload, signature);
            return ResponseEntity.ok(Map.of("message", "Webhook received"));
        }
}
