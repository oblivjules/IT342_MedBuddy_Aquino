package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentLeaveFeedbackBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.FeedbackRepository
import kotlinx.coroutines.launch

class LeaveFeedbackFragment : Fragment() {

    private lateinit var binding: FragmentLeaveFeedbackBinding
    private lateinit var feedbackRepository: FeedbackRepository

    private var appointmentId: Long = -1
    private var doctorId: Long = -1
    private var doctorName: String = ""
    private var appointmentDate: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentLeaveFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appointmentId = requireArguments().getLong(ARG_APPOINTMENT_ID, -1)
        doctorId = requireArguments().getLong(ARG_DOCTOR_ID, -1)
        doctorName = requireArguments().getString(ARG_DOCTOR_NAME, "Doctor")
        appointmentDate = requireArguments().getString(ARG_DATE, "")

        feedbackRepository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)

        binding.tvDoctorName.text = "Dr. $doctorName"
        binding.tvAppointmentDate.text = appointmentDate

        binding.ratingBar.setOnRatingBarChangeListener { _: RatingBar, rating: Float, _: Boolean ->
            binding.tvRatingLabel.text = when (rating.toInt()) {
                1 -> "Poor"
                2 -> "Fair"
                3 -> "Good"
                4 -> "Very Good"
                5 -> "Excellent!"
                else -> "Tap a star to rate"
            }
        }

        binding.btnSubmit.setOnClickListener { submitFeedback() }
        binding.btnCancel.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun submitFeedback() {
        val rating = binding.ratingBar.rating.toInt()
        if (rating <= 0) {
            showError("Please choose a rating.")
            return
        }

        val comment = binding.etComment.text?.toString()?.trim()?.ifBlank { null }

        binding.tvError.visibility = View.GONE
        binding.btnSubmit.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                feedbackRepository.createFeedback(appointmentId, doctorId, rating, comment)
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    Bundle().apply {
                        putBoolean(RESULT_SUCCESS, true)
                        putLong(RESULT_APPOINTMENT_ID, appointmentId)
                    }
                )
                parentFragmentManager.popBackStack()
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "Unable to submit review.")
                binding.btnSubmit.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    companion object {
        const val RESULT_KEY = "feedback_result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_APPOINTMENT_ID = "appointmentId"
        private const val ARG_APPOINTMENT_ID = "appointmentId"
        private const val ARG_DOCTOR_ID = "doctorId"
        private const val ARG_DOCTOR_NAME = "doctorName"
        private const val ARG_DATE = "date"

        fun newInstance(appointment: AppointmentResponse): LeaveFeedbackFragment {
            return LeaveFeedbackFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_APPOINTMENT_ID, appointment.id)
                    putLong(ARG_DOCTOR_ID, appointment.doctor.id)
                    putString(ARG_DOCTOR_NAME, doctorDisplayName(appointment.doctor))
                    putString(ARG_DATE, formatAppointmentDateTime(appointment.dateTime))
                }
            }
        }
    }
}
