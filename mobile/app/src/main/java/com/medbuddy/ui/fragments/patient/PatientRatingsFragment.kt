package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentPatientRatingsBinding
import com.medbuddy.repository.AppointmentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PatientRatingsFragment : Fragment() {

    private lateinit var binding: FragmentPatientRatingsBinding
    private lateinit var adapter: PatientSubmittedReviewAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPatientRatingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = PatientSubmittedReviewAdapter()
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        parentFragmentManager.setFragmentResultListener(LeaveFeedbackFragment.RESULT_KEY, viewLifecycleOwner) { _, result ->
            if (result.getBoolean(LeaveFeedbackFragment.RESULT_SUCCESS)) loadReviews()
        }

        loadReviews()
    }

    private fun loadReviews() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val appointmentRepository = AppointmentRepository(apiService)

                val completedAppointments = appointmentRepository.getPatientAppointments()
                    .filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }

                val reviews = coroutineScope {
                    completedAppointments.map { appointment ->
                        async {
                            val rating = runCatching { apiService.getRatingByAppointment(appointment.id).body() }.getOrNull()
                                ?: return@async null
                            val doctorName = appointmentDoctorName(appointment)
                            val dateLabel = formatAppointmentDateTime(appointment.dateTime)
                            val comment = rating.comment?.takeIf { it.isNotBlank() }
                                ?: rating.feedback?.takeIf { it.isNotBlank() }
                            SubmittedReviewUiModel(
                                id = rating.id,
                                doctorName = doctorName,
                                dateLabel = dateLabel,
                                rating = rating.rating,
                                comment = comment
                            )
                        }
                    }.awaitAll().filterNotNull()
                }

                adapter.submitList(reviews)
                binding.tvEmptyState.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }
}
