package com.medbuddy.ui.fragments.patient

import android.app.DatePickerDialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentPatientMyReviewsBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.RatingResponse
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.RatingRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PatientMyReviewsFragment : Fragment() {

    private lateinit var binding: FragmentPatientMyReviewsBinding
    private lateinit var adapter: PatientMyReviewsAdapter

    private var allRatings: List<RatingResponse> = emptyList()
    private var allAppointments: List<AppointmentResponse> = emptyList()
    private var completedUnrated: List<AppointmentResponse> = emptyList()

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
        binding = FragmentPatientMyReviewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PatientMyReviewsAdapter(onDelete = ::handleDelete)
        binding.rvReviews.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReviews.adapter = adapter

        setupStarFilterSpinner()
        setupDatePickers()
        binding.tvClearFilters.setOnClickListener { clearFilters() }
        binding.btnSubmit.setOnClickListener { handleSubmit() }

        loadData()
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

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch

            try {
                val currentUser = resolveCurrentUser()
                val patientId = currentUser?.profileId

                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val appointmentRepository = AppointmentRepository(apiService)
                val ratingRepository = RatingRepository(apiService)

                val (appointments, ratings) = coroutineScope {
                    val apptDeferred = async { runCatching { appointmentRepository.getPatientAppointments() }.getOrDefault(emptyList()) }
                    val ratingsDeferred = async {
                        if (patientId != null) runCatching { ratingRepository.getPatientRatings(patientId) }.getOrDefault(emptyList())
                        else emptyList()
                    }
                    apptDeferred.await() to ratingsDeferred.await()
                }

                if (!isAdded) return@launch

                allAppointments = appointments
                allRatings = ratings

                val ratedIds = ratings.map { it.appointmentId }.toSet()
                completedUnrated = appointments.filter { it.status == "COMPLETED" && it.id !in ratedIds }

                updateStats()
                setupAppointmentSpinner()
                applyFilters()
            } catch (_: Throwable) {
                if (isAdded) {
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

    private fun updateStats() {
        val total = allRatings.size
        val avg = if (total == 0) 0.0 else allRatings.sumOf { it.rating.toDouble() } / total
        val fiveStar = allRatings.count { it.rating == 5 }

        binding.tvAvgRating.text = String.format(Locale.getDefault(), "%.1f", avg)
        binding.tvTotalReviews.text = total.toString()
        binding.tvFiveStarCount.text = fiveStar.toString()
    }

    private fun setupAppointmentSpinner() {
        val labels = mutableListOf("Choose a completed appointment")
        labels.addAll(completedUnrated.map { appt ->
            val doctorName = appt.doctor.let { d ->
                listOfNotNull(d.firstName, d.lastName).joinToString(" ").ifBlank { d.email ?: "Doctor" }
            }
            "Dr. $doctorName — ${formatAppointmentDate(appt.dateTime)}"
        })
        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.item_spinner_dark, labels)
        spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dark)
        binding.spinnerAppointment.adapter = spinnerAdapter
        binding.spinnerAppointment.setPopupBackgroundDrawable(ColorDrawable(0xFFFFFFFF.toInt()))
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

        val reviews = filtered.mapNotNull { rating ->
            appointmentById[rating.appointmentId]?.let { appt -> rating.toUiModel(appt) }
        }

        adapter.submitList(reviews)
        binding.tvEmptyState.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun handleSubmit() {
        val selectedPosition = binding.spinnerAppointment.selectedItemPosition
        if (selectedPosition == 0) {
            showFormError("Please select an appointment.")
            return
        }
        val selectedAppointment = completedUnrated.getOrNull(selectedPosition - 1)
        if (selectedAppointment == null) {
            showFormError("Please select an appointment.")
            return
        }
        val ratingScore = binding.ratingBarSubmit.rating.toInt()
        if (ratingScore == 0) {
            showFormError("Please select a rating.")
            return
        }
        val comment = binding.etComment.text?.toString()?.trim() ?: ""
        if (comment.isEmpty()) {
            showFormError("Please enter your review.")
            return
        }

        binding.tvFormError.visibility = View.GONE
        binding.btnSubmit.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val ratingRepository = RatingRepository(apiService)
                val created = ratingRepository.createRating(
                    appointmentId = selectedAppointment.id,
                    rating = ratingScore,
                    feedback = comment,
                )
                allRatings = listOf(created) + allRatings
                completedUnrated = completedUnrated.filter { it.id != selectedAppointment.id }

                binding.spinnerAppointment.setSelection(0)
                binding.ratingBarSubmit.rating = 0f
                binding.etComment.setText("")

                updateStats()
                setupAppointmentSpinner()
                applyFilters()

                Toast.makeText(requireContext(), "Feedback submitted. Thank you!", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                showFormError("Unable to submit feedback.")
            } finally {
                if (isAdded) binding.btnSubmit.isEnabled = true
            }
        }
    }

    private fun handleDelete(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val ratingRepository = RatingRepository(apiService)
                ratingRepository.deleteRating(id)

                val deleted = allRatings.find { it.id == id }
                allRatings = allRatings.filter { it.id != id }

                if (deleted != null) {
                    val restoredAppt = allAppointments.find { it.id == deleted.appointmentId }
                    if (restoredAppt != null && restoredAppt.status == "COMPLETED") {
                        completedUnrated = completedUnrated + restoredAppt
                    }
                }

                updateStats()
                setupAppointmentSpinner()
                applyFilters()
            } catch (_: Throwable) {
                if (isAdded) Toast.makeText(requireContext(), "Unable to delete review.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFormError(msg: String) {
        binding.tvFormError.text = msg
        binding.tvFormError.visibility = View.VISIBLE
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

    private fun RatingResponse.toUiModel(appointment: AppointmentResponse): PatientMyReviewUiModel {
        val doctorName = appointment.doctor.let { d ->
            listOfNotNull(d.firstName, d.lastName).joinToString(" ").ifBlank { d.email ?: "Doctor" }
        }
        val commentText = (comment ?: feedback ?: "No comment provided.").ifBlank { "No comment provided." }
        return PatientMyReviewUiModel(
            id = id,
            doctorName = doctorName,
            appointmentDate = formatAppointmentDateTime(appointment.dateTime),
            rating = rating,
            comment = commentText,
        )
    }

    private fun formatAppointmentDate(dateTime: String?): String {
        if (dateTime.isNullOrBlank()) return ""
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(dateTime) ?: return@runCatching dateTime
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }.getOrDefault(dateTime)
    }
}
