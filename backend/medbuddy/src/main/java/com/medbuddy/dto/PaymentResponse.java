package com.medbuddy.dto;

import com.medbuddy.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
