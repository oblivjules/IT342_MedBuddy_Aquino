package com.medbuddy.ui.fragments.doctor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.databinding.FragmentDoctorDashboardBinding
import com.medbuddy.dto.DoctorDashboardResponse
import com.medbuddy.dto.AppointmentSummaryDto
import com.medbuddy.ui.AppointmentDetailDoctorActivity
import com.medbuddy.ui.MainActivity
import com.medbuddy.ui.SessionUi
import com.medbuddy.ui.UpcomingAppointmentAdapter
import com.medbuddy.viewmodel.DoctorDashboardViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorDashboardFragment : Fragment() {

    private lateinit var binding: FragmentDoctorDashboardBinding
    private lateinit var viewModel: DoctorDashboardViewModel
    private lateinit var adapter: UpcomingAppointmentAdapter
    private var allUpcomingAppointments: List<AppointmentSummaryDto> = emptyList()
    private var currentUpcomingFilter: UpcomingFilter = UpcomingFilter.ALL_UPCOMING

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDoctorDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[DoctorDashboardViewModel::class.java]

        setupRecyclerView()
        setupActions()
        setupFilters()
        observeState()
        binding.tvDate.text = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))

        // Dashboard data is loaded automatically by ViewModel's init {} block
        // No need to call viewModel.loadDashboard() here
    }

    private fun setupRecyclerView() {
        adapter = UpcomingAppointmentAdapter()
        binding.recyclerUpcomingToday.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerUpcomingToday.adapter = adapter
        binding.recyclerUpcomingToday.isNestedScrollingEnabled = false
    }

    private fun setupActions() {
        binding.cardViewSchedule.setOnClickListener {
            selectDoctorTab(R.id.nav_doctor_appointments)
        }

        binding.cardAddAvailability.setOnClickListener {
            selectDoctorTab(R.id.nav_doctor_schedule)
        }

        binding.btnStartConsultation.setOnClickListener {
            val appointmentId = binding.btnStartConsultation.tag as? Long ?: return@setOnClickListener
            val intent = Intent(requireContext(), AppointmentDetailDoctorActivity::class.java).apply {
                putExtra("appointmentId", appointmentId)
            }
            startActivity(intent)
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadDashboard()
        }
    }

    private fun setupFilters() {
        binding.chipGroupUpcomingFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            currentUpcomingFilter = when (checkedId) {
                R.id.chipAllUpcoming -> UpcomingFilter.ALL_UPCOMING
                R.id.chipToday -> UpcomingFilter.TODAY
                R.id.chipThisWeek -> UpcomingFilter.THIS_WEEK
                else -> UpcomingFilter.ALL_UPCOMING
            }
            applyUpcomingFilter()
        }
    }

    private fun observeState() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (!isAdded) return@observe
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.contentContainer.visibility = if (loading) View.GONE else View.VISIBLE
        }

        viewModel.error.observe(viewLifecycleOwner) { message ->
            if (!isAdded) return@observe
            val hasError = !message.isNullOrBlank()
            binding.tvError.visibility = if (hasError) View.VISIBLE else View.GONE
            binding.btnRetry.visibility = if (hasError) View.VISIBLE else View.GONE
            if (hasError) {
                if (SessionUi.handleAuthError(this, IllegalStateException(message))) {
                    return@observe
                }
                binding.tvError.text = message
            }
        }

        viewModel.dashboardData.observe(viewLifecycleOwner) { dashboard ->
            if (!isAdded) return@observe
            bindDashboard(dashboard)
        }
    }

    private fun bindDashboard(dashboard: DoctorDashboardResponse) {
        // Guard against fragment being detached before any UI updates
        if (!isAdded) return

        binding.tvWelcome.text = getString(R.string.dashboard_welcome_format, dashboard.doctorName)
        binding.tvAppointmentsCount.text = dashboard.todayAppointmentsCount.toString()
        binding.tvCompletedCount.text = dashboard.completedTodayCount.toString()

        val nextPatient = dashboard.nextPatient
        if (nextPatient == null) {
            binding.nextPatientEmpty.visibility = View.VISIBLE
            binding.nextPatientContent.visibility = View.GONE
            binding.btnStartConsultation.visibility = View.GONE
        } else {
            binding.nextPatientEmpty.visibility = View.GONE
            binding.nextPatientContent.visibility = View.VISIBLE
            binding.btnStartConsultation.visibility = View.VISIBLE
            binding.btnStartConsultation.isEnabled = nextPatient.appointmentId != null
            binding.btnStartConsultation.alpha = if (nextPatient.appointmentId != null) 1f else 0.5f

            binding.tvNextPatientInitials.text = nextPatient.patientName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "MB" }
            binding.tvNextPatientName.text = nextPatient.patientName
            binding.tvNextPatientTime.text = nextPatient.appointmentTime
            binding.tvNextPatientReason.text = nextPatient.reasonForVisit
            binding.btnStartConsultation.tag = nextPatient.appointmentId
        }

        allUpcomingAppointments = dashboard.upcomingToday
        applyUpcomingFilter()
    }

    private fun applyUpcomingFilter() {
        val filtered = when (currentUpcomingFilter) {
            UpcomingFilter.ALL_UPCOMING -> allUpcomingAppointments
            UpcomingFilter.TODAY -> allUpcomingAppointments.filter { isToday(it.appointmentTime) }
            UpcomingFilter.THIS_WEEK -> allUpcomingAppointments.filter { isThisWeek(it.appointmentTime) }
        }

        adapter.submitList(filtered)
        val hasUpcoming = filtered.isNotEmpty()
        binding.recyclerUpcomingToday.visibility = if (hasUpcoming) View.VISIBLE else View.GONE
        binding.tvUpcomingEmpty.visibility = if (hasUpcoming) View.GONE else View.VISIBLE
        if (!hasUpcoming) {
            binding.tvUpcomingEmpty.text = when (currentUpcomingFilter) {
                UpcomingFilter.ALL_UPCOMING -> "No upcoming appointments."
                UpcomingFilter.TODAY -> "No appointments today."
                UpcomingFilter.THIS_WEEK -> "No appointments this week."
            }
        }
    }

    private fun isToday(dateTimeValue: String): Boolean {
        return parseDateTime(dateTimeValue)?.toLocalDate() == LocalDate.now()
    }

    private fun isThisWeek(dateTimeValue: String): Boolean {
        val dateTime = parseDateTime(dateTimeValue) ?: return false
        val today = LocalDate.now()
        val endOfWeek = today.plusDays(6)
        val date = dateTime.toLocalDate()
        return !date.isBefore(today) && !date.isAfter(endOfWeek)
    }

    private fun parseDateTime(value: String): java.time.LocalDateTime? {
        return runCatching { java.time.LocalDateTime.parse(value) }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()
            ?: runCatching {
                val normalized = value.replace(Regex("\\.\\d+$"), "")
                java.time.LocalDateTime.parse(normalized)
            }.getOrNull()
    }

    private fun selectDoctorTab(menuItemId: Int) {
        val activity = activity as? MainActivity ?: return
        activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            ?.selectedItemId = menuItemId
    }

    private enum class UpcomingFilter {
        ALL_UPCOMING,
        TODAY,
        THIS_WEEK
    }
}