package com.medbuddy.dto;

import com.medbuddy.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    @NotNull(message = "Appointment ID is required")
    private Long appointmentId;

    @NotNull(message = "Fee amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Fee amount must be greater than 0")
    private BigDecimal feeAmount;

    /** Optional — defaults to PENDING. */
    private PaymentStatus paymentStatus;
}
