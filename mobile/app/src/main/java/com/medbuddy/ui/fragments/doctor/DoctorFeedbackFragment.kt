package com.medbuddy.ui.fragments.doctor

import android.app.DatePickerDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentDoctorFeedbackBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.RatingResponse
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.RatingRepository
import com.medbuddy.ui.fragments.patient.formatAppointmentDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DoctorFeedbackFragment : Fragment() {

    private lateinit var binding: FragmentDoctorFeedbackBinding
    private lateinit var adapter: DoctorFeedbackAdapter

    private var allRatings: List<RatingResponse> = emptyList()
    private var allAppointments: List<AppointmentResponse> = emptyList()

    private var starFilter: Int = 0
    private var dateFromMillis: Long? = null
    private var dateToMillis: Long? = null

    private val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentDoctorFeedbackBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DoctorFeedbackAdapter()
        binding.rvFeedback.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFeedback.adapter = adapter

        setupStarFilterSpinner()
        setupDatePickers()
        binding.tvClearFilters.setOnClickListener { clearFilters() }

        loadFeedback()
    }

    private fun setupStarFilterSpinner() {
        val options = listOf("All Ratings", "5 Stars", "4 Stars", "3 Stars", "2 Stars", "1 Star")
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner_dark, options)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dark)
        binding.spinnerStarFilter.adapter = spinnerAdapter
        binding.spinnerStarFilter.setPopupBackgroundDrawable(ColorDrawable(0xFFFFFFFF.toInt()))
        binding.spinnerStarFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                starFilter = if (position == 0) 0 else (6 - position)
                applyFilters()
                updateClearFiltersVisibility()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupDatePickers() {
        binding.etDateFrom.setOnClickListener {
            showDatePicker { millis, label ->
                dateFromMillis = millis
                binding.etDateFrom.setText(label)
                applyFilters()
                updateClearFiltersVisibility()
            }
        }
        binding.etDateTo.setOnClickListener {
            showDatePicker { millis, label ->
                dateToMillis = millis
                binding.etDateTo.setText(label)
                applyFilters()
                updateClearFiltersVisibility()
            }
        }
    }

    private fun showDatePicker(onPicked: (Long, String) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            R.style.MedBuddyDatePickerDialog,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                onPicked(picked.timeInMillis, displayFormat.format(picked.time))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun clearFilters() {
        starFilter = 0
        dateFromMillis = null
        dateToMillis = null
        binding.spinnerStarFilter.setSelection(0)
        binding.etDateFrom.setText("")
        binding.etDateTo.setText("")
        applyFilters()
        updateClearFiltersVisibility()
    }

    private fun updateClearFiltersVisibility() {
        val active = starFilter != 0 || dateFromMillis != null || dateToMillis != null
        binding.tvClearFilters.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun loadFeedback() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch

            try {
                val currentUser = resolveCurrentUser()
                val doctorId = currentUser?.profileId
                if (doctorId == null) {
                    updateSummary(0.0, 0)
                    adapter.submitList(emptyList())
                    binding.tvEmptyState.visibility = View.VISIBLE
                    return@launch
                }

                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val ratingRepository = RatingRepository(apiService)
                val appointmentRepository = AppointmentRepository(apiService)

                val (ratings, appointments) = coroutineScope {
                    val ratingsDeferred = async { runCatching { ratingRepository.getDoctorRatings(doctorId) }.getOrDefault(emptyList()) }
                    val appointmentsDeferred = async { runCatching { appointmentRepository.getDoctorAppointments() }.getOrDefault(emptyList()) }
                    ratingsDeferred.await() to appointmentsDeferred.await()
                }

                if (!isAdded) return@launch

                allRatings = ratings.sortedByDescending { it.createdAt }
                allAppointments = appointments

                val average = if (ratings.isEmpty()) 0.0 else ratings.map { it.rating.toDouble() }.average()
                updateSummary(average, ratings.size)
                applyFilters()
            } catch (_: Throwable) {
                if (isAdded) {
                    updateSummary(0.0, 0)
                    adapter.submitList(emptyList())
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            } finally {
                if (isAdded) {
                    binding.progressBar.visibility = View.GONE
                    binding.scrollContent.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun applyFilters() {
        val appointmentById = allAppointments.associateBy { it.id }

        val filtered = allRatings.filter { rating ->
            if (starFilter != 0 && rating.rating != starFilter) return@filter false

            val ratingTimeMillis = runCatching {
                rating.createdAt?.let { isoFormat.parse(it.substring(0, 10))?.time }
                    ?: appointmentById[rating.appointmentId]?.dateTime?.let { isoFormat.parse(it.substring(0, 10))?.time }
            }.getOrNull()

            if (ratingTimeMillis != null) {
                dateFromMillis?.let { from -> if (ratingTimeMillis < from) return@filter false }
                dateToMillis?.let { to ->
                    val endOfDay = to + 86_399_999L
                    if (ratingTimeMillis > endOfDay) return@filter false
                }
            }
            true
        }

        val reviews = filtered.map { rating -> rating.toUiModel(appointmentById[rating.appointmentId]) }
        adapter.submitList(reviews)
        binding.tvEmptyState.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSummary(average: Double, totalReviews: Int) {
        binding.ratingBarAverage.rating = average.toFloat()
        binding.tvAverageRating.text = String.format(Locale.getDefault(), "%.1f", average)
        binding.tvTotalReviews.text = "Based on $totalReviews review${if (totalReviews == 1) "" else "s"}"
    }

    private suspend fun resolveCurrentUser(): UserDto? {
        val tokenManager = TokenManager(requireContext())
        val cachedUser = runCatching {
            Gson().fromJson(tokenManager.getUserJson(), UserDto::class.java)
        }.getOrNull()
        if (cachedUser?.profileId != null) return cachedUser

        return runCatching {
            RetrofitClient.getInstance(requireContext()).apiService.getMe().bodyOrThrow()
        }.getOrNull()
    }

    private fun RatingResponse.toUiModel(appointment: AppointmentResponse?): DoctorFeedbackUiModel {
        val patientName = patient?.let { p ->
            listOfNotNull(p.firstName, p.lastName).joinToString(" ").ifBlank { p.email ?: "Patient" }
        } ?: appointment?.let { apt ->
            listOfNotNull(apt.patient.firstName, apt.patient.lastName).joinToString(" ").ifBlank { "Patient" }
        } ?: "Patient"

        val appointmentDate = appointment?.dateTime?.let { formatAppointmentDateTime(it) }
            ?: createdAt?.let { formatAppointmentDateTime(it) }
            ?: ""

        val commentText = (comment ?: feedback ?: "No comment provided.").ifBlank { "No comment provided." }

        return DoctorFeedbackUiModel(
            id = id,
            patientName = patientName,
            appointmentDate = appointmentDate,
            rating = rating,
            comment = commentText,
        )
    }
}
