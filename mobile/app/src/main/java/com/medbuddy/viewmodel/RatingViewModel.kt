package com.medbuddy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.medbuddy.dto.AverageRatingResponse
import com.medbuddy.dto.RatingResponse
import com.medbuddy.repository.RatingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoctorRatingsUiState(
    val loading: Boolean = false,
    val average: AverageRatingResponse? = null,
    val ratings: List<RatingResponse> = emptyList(),
    val error: String? = null
)

data class RatingUiState(
    val loading: Boolean = false,
    val rating: RatingResponse? = null,
    val error: String? = null
)

class RatingViewModel(
    private val repository: RatingRepository
) : ViewModel() {

    private val _doctorRatingsState = MutableStateFlow(DoctorRatingsUiState(loading = true))
    val doctorRatingsState: StateFlow<DoctorRatingsUiState> = _doctorRatingsState.asStateFlow()

    private val _ratingState = MutableStateFlow(RatingUiState())
    val ratingState: StateFlow<RatingUiState> = _ratingState.asStateFlow()

    fun loadDoctorRatings(doctorId: Long) {
        viewModelScope.launch {
            _doctorRatingsState.value = _doctorRatingsState.value.copy(loading = true, error = null)
            try {
                val average = repository.getAverageRating(doctorId)
                val ratings = repository.getDoctorRatings(doctorId)
                _doctorRatingsState.value = DoctorRatingsUiState(
                    loading = false,
                    average = average,
                    ratings = ratings
                )
            } catch (e: Throwable) {
                _doctorRatingsState.value = DoctorRatingsUiState(
                    loading = false,
                    error = e.message
                )
            }
        }
    }

    fun submitRating(appointmentId: Long, rating: Int, feedback: String?) {
        viewModelScope.launch {
            _ratingState.value = _ratingState.value.copy(loading = true, error = null)
            try {
                val result = repository.createRating(appointmentId, rating, feedback)
                _ratingState.value = RatingUiState(loading = false, rating = result)
            } catch (e: Throwable) {
                _ratingState.value = RatingUiState(loading = false, error = e.message)
            }
        }
    }

    fun submitRating(appointmentId: Int, rating: Int, feedback: String?) {
        submitRating(appointmentId.toLong(), rating, feedback)
    }
}

class RatingViewModelFactory(
    private val repository: RatingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RatingViewModel(repository) as T
    }
}
