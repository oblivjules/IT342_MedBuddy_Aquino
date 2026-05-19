package com.medbuddy.ui.viewstate

import com.medbuddy.repository.SlotUiModel

data class SlotsUiState(
    val loading: Boolean = false,
    val items: List<SlotUiModel> = emptyList(),
    val error: String? = null
)
