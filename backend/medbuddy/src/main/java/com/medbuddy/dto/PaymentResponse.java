package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import com.medbuddy.shared.model.PaymentStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long appointmentId;
    private BigDecimal feeAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private PaymentStatus paymentStatus;
}
