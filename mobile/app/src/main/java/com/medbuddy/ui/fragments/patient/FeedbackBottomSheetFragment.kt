package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.lifecycle.lifecycleScope
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentFeedbackBottomSheetBinding
import com.medbuddy.repository.FeedbackRepository
import kotlinx.coroutines.launch

class FeedbackBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentFeedbackBottomSheetBinding
    private lateinit var feedbackRepository: FeedbackRepository
    private var appointmentId: Long = -1
    private var doctorId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentFeedbackBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appointmentId = requireArguments().getLong(ARG_APPOINTMENT_ID, -1)
        doctorId = requireArguments().getLong(ARG_DOCTOR_ID, -1)
        feedbackRepository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)

        binding.btnSubmit.setOnClickListener { submitFeedback() }
    }

    private fun submitFeedback() {
        val rating = binding.ratingBar.rating.toInt()
        val comment = binding.etComment.text?.toString()?.trim().orEmpty().ifBlank { null }
        if (rating <= 0) {
            binding.tvError.visibility = View.VISIBLE
            binding.tvError.text = "Please choose a rating."
            return
        }

        binding.tvError.visibility = View.GONE
        binding.btnSubmit.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                feedbackRepository.createFeedback(appointmentId, doctorId, rating, comment)
                parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_SUCCESS to true, RESULT_APPOINTMENT_ID to appointmentId))
                dismiss()
            } catch (throwable: Throwable) {
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = throwable.message ?: "Unable to submit feedback."
                binding.btnSubmit.isEnabled = true
            }
        }
    }

    companion object {
        const val RESULT_KEY = "feedback_result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_APPOINTMENT_ID = "appointmentId"
        private const val ARG_APPOINTMENT_ID = "appointmentId"
        private const val ARG_DOCTOR_ID = "doctorId"

        fun newInstance(appointmentId: Long, doctorId: Long): FeedbackBottomSheetFragment {
            return FeedbackBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_APPOINTMENT_ID, appointmentId)
                    putLong(ARG_DOCTOR_ID, doctorId)
                }
            }
        }
    }
}
