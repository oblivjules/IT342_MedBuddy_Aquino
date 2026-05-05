package com.medbuddy.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentTotalUpdateRequest {

    @NotNull(message = "Total bill amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total bill amount must be greater than 0")
    private BigDecimal totalBillAmount;
}
