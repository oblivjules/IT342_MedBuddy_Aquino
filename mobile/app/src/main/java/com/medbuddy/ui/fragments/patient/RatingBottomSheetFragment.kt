package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentRatingBottomSheetBinding
import com.medbuddy.repository.FeedbackRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

class RatingBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentRatingBottomSheetBinding
    private lateinit var feedbackRepository: FeedbackRepository

    private var appointmentId: Long = -1
    private var doctorId: Long = -1
    private var doctorName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentRatingBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appointmentId = requireArguments().getLong(ARG_APPOINTMENT_ID)
        doctorId = requireArguments().getLong(ARG_DOCTOR_ID)
        doctorName = requireArguments().getString(ARG_DOCTOR_NAME).orEmpty()

        binding.tvDoctorName.text = doctorName
        feedbackRepository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)

        // Show only if not already rated: 200 means rated, 404 means not rated yet.
        viewLifecycleOwner.lifecycleScope.launch {
            val alreadyRated = runCatching { feedbackRepository.getFeedbackByAppointment(appointmentId) }
                .isSuccess
            if (alreadyRated) {
                dismissAllowingStateLoss()
                return@launch
            }
        }

        binding.btnSubmit.setOnClickListener { submitReview() }
    }

    private fun submitReview() {
        val rating = binding.ratingBar.rating.toInt()
        if (rating <= 0) {
            Snackbar.make(binding.root, "Please select a rating.", Snackbar.LENGTH_SHORT).show()
            return
        }

        val comment = binding.etComment.text?.toString()?.trim().orEmpty().ifBlank { null }
        binding.btnSubmit.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                feedbackRepository.createFeedback(
                    appointmentId = appointmentId,
                    doctorId = doctorId,
                    rating = rating,
                    comment = comment
                )
            }.onSuccess {
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    bundleOf(RESULT_SUCCESS to true, RESULT_APPOINTMENT_ID to appointmentId)
                )
                Snackbar.make(binding.root, "Review submitted!", Snackbar.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            }.onFailure {
                binding.btnSubmit.isEnabled = true
                Snackbar.make(binding.root, it.message ?: "Failed to submit review.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val RESULT_KEY = "rating_result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_APPOINTMENT_ID = "appointmentId"

        private const val ARG_APPOINTMENT_ID = "appointmentId"
        private const val ARG_DOCTOR_ID = "doctorId"
        private const val ARG_DOCTOR_NAME = "doctorName"

        fun newInstance(appointmentId: Long, doctorId: Long, doctorName: String): RatingBottomSheetFragment {
            return RatingBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_APPOINTMENT_ID, appointmentId)
                    putLong(ARG_DOCTOR_ID, doctorId)
                    putString(ARG_DOCTOR_NAME, doctorName)
                }
            }
        }
    }
}
