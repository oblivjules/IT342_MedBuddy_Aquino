package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.databinding.FragmentPatientDoctorProfileBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.RatingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorProfileFragment : Fragment() {

    private lateinit var binding: FragmentPatientDoctorProfileBinding
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

        binding.btnBookAppointment.setOnClickListener {
            doctor?.let { selectedDoctor ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, BookAppointmentFragment.newInstance(selectedDoctor))
                    .addToBackStack(null)
                    .commit()
            }
        }

        binding.btnViewReviews.setOnClickListener {
            doctor?.let { selectedDoctor ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, PatientDoctorReviewsFragment.newInstance(selectedDoctor))
                    .addToBackStack(null)
                    .commit()
            }
        }

        renderDoctorHeader()
        loadRatings()
    }

    private fun renderDoctorHeader() {
        val currentDoctor = doctor ?: return
        binding.tvDoctorName.text = doctorDisplayName(currentDoctor)
        binding.tvSpecialization.text = currentDoctor.specializations?.joinToString(", ")
            ?: currentDoctor.specialization
            ?: "General Practice"

        val imageUrl = currentDoctor.profileImageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivDoctorAvatar.visibility = View.VISIBLE
            binding.tvAvatar.visibility = View.GONE
            Glide.with(this).load(imageUrl).circleCrop().into(binding.ivDoctorAvatar)
        } else {
            binding.ivDoctorAvatar.visibility = View.GONE
            binding.tvAvatar.visibility = View.VISIBLE
            binding.tvAvatar.text = doctorInitials(currentDoctor.firstName, currentDoctor.lastName)
        }

        val email = currentDoctor.email?.takeIf { it.isNotBlank() }
        if (email != null) {
            binding.tvDoctorEmail.text = email
            binding.rowEmail.visibility = View.VISIBLE
        }

        val phone = currentDoctor.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null) {
            binding.tvDoctorPhone.text = phone
            binding.rowPhone.visibility = View.VISIBLE
        }
    }

    private fun loadRatings() {
        val currentDoctor = doctor ?: return
        val apiService = RetrofitClient.getInstance(requireContext()).apiService
        val ratingRepository = RatingRepository(apiService)
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (avgResponse, availabilityDates) = coroutineScope {
                    val r = async { runCatching { ratingRepository.getAverageRating(currentDoctor.id) }.getOrNull() }
                    val today = LocalDate.now()
                    val a = async {
                        (1..14).mapNotNull { offset ->
                            val date = today.plusDays(offset.toLong())
                            val slots = runCatching {
                                apiService.getDoctorAppointmentSlots(currentDoctor.id, date.toString()).bodyOrThrow()
                            }.getOrDefault(emptyList())
                            val available = slots.count { s ->
                                val st = (s.status ?: "AVAILABLE").trim().uppercase()
                                st != "BOOKED" && st != "UNAVAILABLE" && st != "RESERVED"
                            }
                            if (available > 0) date to available else null
                        }
                    }
                    Pair(r.await(), a.await())
                }

                val rating = avgResponse?.averageRating ?: 0.0
                binding.ratingBar.rating = rating.toFloat()
                binding.tvAverageRating.text = if (rating > 0) String.format("%.1f average rating", rating) else "No ratings yet"

                renderAvailability(availabilityDates)
            } catch (_: Throwable) {
                binding.ratingBar.rating = 0f
                binding.tvAverageRating.text = "No ratings yet"
                renderAvailability(emptyList())
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    private fun renderAvailability(dates: List<Pair<LocalDate, Int>>) {
        binding.containerAvailability.removeAllViews()
        if (dates.isEmpty()) {
            binding.tvAvailabilityEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvAvailabilityEmpty.visibility = View.GONE

        val displayFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        dates.forEach { (date, count) ->
            val dateLabel = date.format(displayFmt)
            val row = TextView(requireContext()).apply {
                text = "$dateLabel  •  $count slot${if (count != 1) "s" else ""} available"
                textSize = 14f
                setTextColor(requireContext().getColor(com.medbuddy.R.color.text_primary))
                setPadding(0, 8, 0, 8)
            }
            binding.containerAvailability.addView(row)
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