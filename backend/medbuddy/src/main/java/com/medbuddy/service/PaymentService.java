package com.medbuddy.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medbuddy.dto.PaymentRequest;
import com.medbuddy.dto.PaymentResponse;
import com.medbuddy.features.appointment.AppointmentRepository;
import com.medbuddy.repository.PaymentRepository;
import com.medbuddy.repository.UserRepository;
import com.medbuddy.shared.model.Appointment;
import com.medbuddy.shared.model.Payment;
import com.medbuddy.shared.model.PaymentStatus;
import com.medbuddy.shared.model.Role;
import com.medbuddy.shared.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final PayMongoService payMongoService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PaymentResponse create(String userEmail, PaymentRequest request) {
        User user = findUserByEmail(userEmail);

        if (paymentRepository.existsByAppointment_Id(request.getAppointmentId())) {
            throw new IllegalStateException(
                    "A payment record already exists for appointment id: "
                            + request.getAppointmentId());
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + request.getAppointmentId()));

        validateAppointmentOwnership(user, appointment);

        PaymentStatus requestedStatus = request.getPaymentStatus() != null
                ? request.getPaymentStatus()
                : PaymentStatus.PENDING;

        if (user.getRole() == Role.PATIENT && requestedStatus != PaymentStatus.PAID) {
            throw new AccessDeniedException("Patients can only create payments with PAID status.");
        }

        Payment payment = Payment.builder()
                .feeAmount(request.getFeeAmount())
                .paidAmount(resolveInitialPaidAmount(request.getFeeAmount(), requestedStatus))
                .paymentStatus(requestedStatus)
                .appointment(appointment)
                .build();

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public com.medbuddy.dto.PaymentInitiateResponse initiatePayment(String userEmail, com.medbuddy.dto.PaymentInitiateRequest request) {
        User user = findUserByEmail(userEmail);
        if (user.getRole() != Role.PATIENT) {
            throw new org.springframework.security.access.AccessDeniedException("Only patients can initiate payments");
        }

        if (request.getAppointmentId() == null) {
            String error = "Validation error: appointmentId is required";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            String error = "Validation error: amount must be greater than 0";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        Appointment appointment = appointmentRepository.findById(request.getAppointmentId())
                .orElseThrow(() -> {
                    String error = "Appointment not found with id: " + request.getAppointmentId();
                    log.error(error);
                    return new IllegalArgumentException(error);
                });

        if (!appointment.getPatient().getUser().getId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("You do not own this appointment.");
        }

        // Payment can be initiated for PENDING, CONFIRMED, or COMPLETED appointments
        if (appointment.getStatus() != com.medbuddy.shared.model.AppointmentStatus.PENDING && 
            appointment.getStatus() != com.medbuddy.shared.model.AppointmentStatus.CONFIRMED &&
            appointment.getStatus() != com.medbuddy.shared.model.AppointmentStatus.COMPLETED) {
            String error = "Appointment must be PENDING, CONFIRMED, or COMPLETED to initiate payment. Current status: " + appointment.getStatus();
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        // Find or create payment record. For existing records, keep the server-side fee amount as source of truth.
        Payment payment = paymentRepository.findByAppointment_Id(appointment.getId())
                .orElseGet(() -> {
                    log.info("Creating payment record for appointment id: {}", appointment.getId());
                    Payment newPayment = Payment.builder()
                            .appointment(appointment)
                            .feeAmount(request.getAmount())
                            .paidAmount(java.math.BigDecimal.ZERO)
                            .paymentStatus(PaymentStatus.PENDING)
                            .build();
                    return paymentRepository.save(newPayment);
                });

        // Always charge only the unpaid balance in checkout.
        BigDecimal paidAmount = normalizePaidAmount(payment);
        BigDecimal remainingAmount = payment.getFeeAmount().subtract(paidAmount);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            String error = "No remaining balance for appointment id: " + appointment.getId();
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        Payment checkoutPayment = Payment.builder()
                .feeAmount(remainingAmount)
                .build();

        com.fasterxml.jackson.databind.JsonNode pmResp = payMongoService.createCheckoutSession(checkoutPayment, appointment);
        if (pmResp == null) {
            String error = "Failed to create PayMongo checkout session. PayMongo service returned null.";
            log.error(error);
            throw new IllegalArgumentException(error);
        }

        String sessionId = pmResp.at("/data/id").asText(null);
        String checkoutUrl = pmResp.at("/data/attributes/checkout_url").asText(null);

        if (sessionId == null || checkoutUrl == null) {
            log.error("PayMongo response missing session id or checkout_url: {}", pmResp.toString());
            throw new IllegalArgumentException("Invalid PayMongo response");
        }

        // Save session id and keep the payment pending until webhook confirmation
        payment.setPaymongoSessionId(sessionId);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        return new com.medbuddy.dto.PaymentInitiateResponse(checkoutUrl, payment.getId());
    }

    @Async
    @Transactional
    public void handleWebhook(String rawPayload, String signature) {
        log.info("Received PayMongo webhook payload");
        if (!payMongoService.verifyWebhookSignature(rawPayload, signature)) {
            logWebhookWarning("Invalid PayMongo webhook signature.");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode attributes = root.path("data").path("attributes");
            String eventType = extractEventType(root, attributes);

            if ("checkout_session.payment.paid".equals(eventType)) {
                String sessionId = root.path("data").path("attributes").path("data").path("id").asText(null);
                if (sessionId == null || sessionId.isBlank()) {
                    sessionId = root.path("data").path("id").asText(null);
                }
                if (sessionId == null || sessionId.isBlank()) {
                    logWebhookWarning("PayMongo webhook missing checkout session id.");
                    return;
                }

                java.util.Optional<Payment> paymentOptional = paymentRepository.findByPaymongoSessionId(sessionId);
                if (paymentOptional.isEmpty()) {
                    logWebhookWarning("PayMongo webhook payment not found for session id: " + sessionId);
                    return;
                }

                Payment payment = paymentOptional.get();
                JsonNode sessionAttributes = root.path("data").path("attributes").path("data").path("attributes");
                String transactionId = extractTransactionId(sessionAttributes);

                payment.setPaymentStatus(PaymentStatus.PAID);
                payment.setPaidAmount(payment.getFeeAmount());
                payment.setTransactionId(transactionId);
                payment.setPaidAt(LocalDateTime.now());
                paymentRepository.save(payment);

                log.info("Processed PayMongo paid webhook for sessionId={} transactionId={}", sessionId, transactionId);

                Appointment appt = payment.getAppointment();
                String patientEmail = appt.getPatient().getUser().getEmail();
                String patientName = appt.getPatient().getFirstName() + " " + appt.getPatient().getLastName();
                String doctorName = appt.getDoctor().getFirstName() + " " + appt.getDoctor().getLastName();
                emailService.sendPaymentReceipt(patientEmail, patientName, doctorName, appt.getDateTime(), payment.getFeeAmount(), transactionId, payment.getPaidAt());
            } else if ("checkout_session.payment.failed".equals(eventType)) {
                String sessionId = attributes.path("id").asText(null);
                if (sessionId == null || sessionId.isBlank()) {
                    sessionId = root.path("data").path("id").asText(null);
                }
                if (sessionId == null || sessionId.isBlank()) {
                    logWebhookWarning("PayMongo webhook failed event missing checkout session id.");
                    return;
                }

                java.util.Optional<Payment> paymentOptional = paymentRepository.findByPaymongoSessionId(sessionId);
                if (paymentOptional.isEmpty()) {
                    logWebhookWarning("PayMongo webhook failed event payment not found for session id: " + sessionId);
                    return;
                }

                Payment payment = paymentOptional.get();
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);

                log.info("Processed PayMongo failed webhook for sessionId={}", sessionId);
            }
        } catch (IOException | NumberFormatException e) {
            logWebhookWarning("Failed processing PayMongo webhook: " + e.getMessage());
        }
    }

    private String extractEventType(JsonNode root, JsonNode attributes) {
        if (root.path("type").isTextual()) {
            return root.path("type").asText();
        }
        if (attributes.path("type").isTextual()) {
            return attributes.path("type").asText();
        }
        if (attributes.path("event").isTextual()) {
            return attributes.path("event").asText();
        }
        return null;
    }

    private String extractTransactionId(JsonNode sessionAttributes) {
        // Extract from payments[0].id (PayMongo payment object ID)
        if (sessionAttributes.path("payments").isArray() && sessionAttributes.path("payments").size() > 0) {
            String paymentId = sessionAttributes.path("payments").get(0).path("id").asText(null);
            if (paymentId != null && !paymentId.isBlank()) {
                return paymentId;
            }
        } else if (sessionAttributes.path("payments").isArray() && sessionAttributes.path("payments").size() == 0) {
            log.warn("PayMongo webhook payments array is empty");
        }

        return null;
    }

    private void logWebhookWarning(String message) {
        log.warn(message);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByAppointment(String userEmail, Long appointmentId) {
        User user = findUserByEmail(userEmail);
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + appointmentId));

        validateAppointmentOwnership(user, appointment);

        return paymentRepository.findByAppointment_Id(appointmentId)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(String userEmail, Long id) {
        User user = findUserByEmail(userEmail);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found with id: " + id));

        validateAppointmentOwnership(user, payment.getAppointment());
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse updateStatus(String userEmail, Long id, PaymentStatus newStatus) {
        User user = findUserByEmail(userEmail);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Payment not found with id: " + id));

        validateAppointmentOwnership(user, payment.getAppointment());
        validateStatusTransition(user, newStatus);

        if (newStatus == PaymentStatus.PAID) {
            payment.setPaidAmount(payment.getFeeAmount());
        } else if (newStatus == PaymentStatus.PENDING) {
            payment.setPaidAmount(BigDecimal.ZERO);
        }

        payment.setPaymentStatus(newStatus);
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse updateTotalBillForAppointment(String userEmail, Long appointmentId, BigDecimal totalBillAmount) {
        User user = findUserByEmail(userEmail);
        if (user.getRole() != Role.DOCTOR) {
            throw new AccessDeniedException("Only doctors can set the total bill amount.");
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Appointment not found with id: " + appointmentId));

        validateAppointmentOwnership(user, appointment);

        Payment payment = paymentRepository.findByAppointment_Id(appointmentId)
                .orElseGet(() -> Payment.builder()
                        .appointment(appointment)
                        .feeAmount(BigDecimal.ZERO)
                        .paidAmount(BigDecimal.ZERO)
                        .paymentStatus(PaymentStatus.PENDING)
                        .build());

        // Add the new charge on top of what was already charged
        BigDecimal newTotalFee = payment.getFeeAmount().add(totalBillAmount);
        payment.setFeeAmount(newTotalFee);

        BigDecimal paidAmount = normalizePaidAmount(payment);

        if (paidAmount.compareTo(newTotalFee) >= 0) {
            payment.setPaidAmount(newTotalFee);
            payment.setPaymentStatus(PaymentStatus.PAID);
        } else {
            payment.setPaymentStatus(PaymentStatus.PENDING);
        }

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public void refundPaymentForAppointment(Long appointmentId) {
        Payment payment = paymentRepository.findByAppointment_Id(appointmentId).orElse(null);
        if (payment == null) {
            return;
        }

        BigDecimal paidAmount = normalizePaidAmount(payment);
        if (paidAmount.compareTo(BigDecimal.ZERO) <= 0
                && payment.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }

        payment.setPaidAmount(BigDecimal.ZERO);
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
    }

    private void validateStatusTransition(User user, PaymentStatus newStatus) {
        if (user.getRole() == Role.PATIENT && newStatus != PaymentStatus.PAID) {
            throw new AccessDeniedException("Patients can only mark their payment as PAID.");
        }
    }

    private BigDecimal resolveInitialPaidAmount(BigDecimal feeAmount, PaymentStatus status) {
        if (status == PaymentStatus.PAID) {
            return feeAmount;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal normalizePaidAmount(Payment payment) {
        if (payment.getPaidAmount() != null) {
            return payment.getPaidAmount();
        }
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            return payment.getFeeAmount();
        }
        return BigDecimal.ZERO;
    }

    private void validateAppointmentOwnership(User user, Appointment appointment) {
        Long userId = user.getId();
        boolean ownsPatientSide = appointment.getPatient().getUser().getId().equals(userId);
        boolean ownsDoctorSide = appointment.getDoctor().getUser().getId().equals(userId);

        if (!ownsPatientSide && !ownsDoctorSide) {
            throw new AccessDeniedException("You do not have permission to access this payment.");
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No account found with email: " + email));
    }

    private PaymentResponse toResponse(Payment p) {
        BigDecimal feeAmount = p.getFeeAmount() != null ? p.getFeeAmount() : BigDecimal.ZERO;
        BigDecimal paidAmount = normalizePaidAmount(p);
        BigDecimal remaining = feeAmount.subtract(paidAmount);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        return PaymentResponse.builder()
                .id(p.getId())
                .appointmentId(p.getAppointment().getId())
                .feeAmount(feeAmount)
                .paidAmount(paidAmount)
                .remainingAmount(remaining)
                .paymentStatus(p.getPaymentStatus())
                .build();
    }

}
