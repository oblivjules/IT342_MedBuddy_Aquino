package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.databinding.FragmentPatientDoctorProfileBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.FeedbackRepository
import kotlinx.coroutines.launch

class DoctorProfileFragment : Fragment() {

    private lateinit var binding: FragmentPatientDoctorProfileBinding
    private lateinit var feedbackAdapter: DoctorFeedbackAdapter
    private lateinit var feedbackRepository: FeedbackRepository
    private var doctor: DoctorDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPatientDoctorProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        doctor = arguments?.getSerializable(ARG_DOCTOR) as? DoctorDto
        feedbackRepository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)

        feedbackAdapter = DoctorFeedbackAdapter()
        binding.rvFeedback.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFeedback.adapter = feedbackAdapter

        binding.btnBookAppointment.setOnClickListener {
            doctor?.let { selectedDoctor ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, BookAppointmentFragment.newInstance(selectedDoctor))
                    .addToBackStack(null)
                    .commit()
            }
        }

        renderDoctorHeader()
        loadFeedback()
    }

    private fun renderDoctorHeader() {
        val currentDoctor = doctor ?: return
        binding.tvAvatar.text = doctorInitials(currentDoctor.firstName, currentDoctor.lastName)
        binding.tvDoctorName.text = doctorDisplayName(currentDoctor)
        binding.tvSpecialization.text = currentDoctor.specializations?.joinToString(", ")
            ?: currentDoctor.specialization
            ?: "General Practice"

        val rating = currentDoctor.averageRating ?: 0.0
        binding.ratingBar.rating = rating.toFloat()
        binding.tvAverageRating.text = if (rating > 0) String.format("%.1f average rating", rating) else "No ratings yet"
    }

    private fun loadFeedback() {
        val currentDoctor = doctor ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val feedback = feedbackRepository.getDoctorFeedback(currentDoctor.id)
                feedbackAdapter.submitList(feedback)
                binding.tvEmptyState.visibility = if (feedback.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyState.text = "No feedback yet"
            } catch (_: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "No feedback yet"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val ARG_DOCTOR = "doctor"

        fun newInstance(doctor: DoctorDto): DoctorProfileFragment {
            return DoctorProfileFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_DOCTOR, doctor) }
            }
        }
    }
}