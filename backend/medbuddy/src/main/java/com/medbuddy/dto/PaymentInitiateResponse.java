package com.medbuddy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateResponse {
    private String checkoutUrl;
    private Long paymentId;
}
