package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentPatientDoctorReviewsBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.FeedbackRepository
import com.medbuddy.repository.RatingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PatientDoctorReviewsFragment : Fragment() {

    private lateinit var binding: FragmentPatientDoctorReviewsBinding
    private lateinit var adapter: DoctorFeedbackAdapter
    private var doctor: DoctorDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPatientDoctorReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        doctor = arguments?.getSerializable(ARG_DOCTOR) as? DoctorDto

        adapter = DoctorFeedbackAdapter()
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        loadReviews()
    }

    private fun loadReviews() {
        val currentDoctor = doctor ?: return
        val apiService = RetrofitClient.getInstance(requireContext()).apiService
        val feedbackRepository = FeedbackRepository(apiService)
        val ratingRepository = RatingRepository(apiService)

        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (feedback, averageRating) = coroutineScope {
                    val reviews = async { runCatching { feedbackRepository.getDoctorFeedback(currentDoctor.id) }.getOrDefault(emptyList()) }
                    val average = async { runCatching { ratingRepository.getAverageRating(currentDoctor.id) }.getOrNull() }
                    Pair(reviews.await(), average.await())
                }

                adapter.submitList(feedback)
                binding.tvEmptyState.visibility = if (feedback.isEmpty()) View.VISIBLE else View.GONE
                binding.ratingBar.rating = (averageRating?.averageRating ?: 0.0).toFloat()
                binding.tvAverageRating.text = if ((averageRating?.averageRating ?: 0.0) > 0) {
                    String.format("%.1f average rating", averageRating?.averageRating ?: 0.0)
                } else {
                    "No ratings yet"
                }
            } catch (_: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.ratingBar.rating = 0f
                binding.tvAverageRating.text = "No ratings yet"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        private const val ARG_DOCTOR = "doctor"

        fun newInstance(doctor: DoctorDto): PatientDoctorReviewsFragment {
            return PatientDoctorReviewsFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_DOCTOR, doctor) }
            }
        }
    }
}
