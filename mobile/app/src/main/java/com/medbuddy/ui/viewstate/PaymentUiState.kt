package com.medbuddy.ui.viewstate

import com.medbuddy.dto.PaymentResponse

data class PaymentUiState(
    val loading: Boolean = false,
    val payment: PaymentResponse? = null,
    val checkoutUrl: String? = null,
    val error: String? = null
)
