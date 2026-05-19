package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.constants.AppConstants
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentAppointmentsRefinedBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.FeedbackRepository
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch

class AppointmentsFragment : Fragment() {

    private lateinit var binding: FragmentAppointmentsRefinedBinding
    private lateinit var adapter: PatientAppointmentAdapter
    private lateinit var viewModel: AppointmentViewModel
    private lateinit var feedbackRepository: FeedbackRepository
    private var allAppointments: List<AppointmentResponse> = emptyList()
    private var currentFilter: String = "ALL"
    private var feedbackProvidedIds: Set<Long> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAppointmentsRefinedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appointmentRepository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(this, AppointmentViewModel.factory(appointmentRepository))[AppointmentViewModel::class.java]
        feedbackRepository = FeedbackRepository(RetrofitClient.getInstance(requireContext()).apiService)

        adapter = PatientAppointmentAdapter(
            onCancelClick = { appointment -> showCancelConfirmation(appointment) },
            onViewRecordClick = { appointment -> openMedicalRecordDetail(appointment) },
            onRateClick = { appointment -> openFeedbackSheet(appointment) },
        )

        binding.rvAppointments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppointments.adapter = adapter

        binding.chipAll.setOnClickListener { currentFilter = "ALL"; applyFilter() }
        binding.chipBooked.setOnClickListener { currentFilter = AppointmentStatus.PENDING; applyFilter() }
        binding.chipConfirmed.setOnClickListener { currentFilter = AppointmentStatus.CONFIRMED; applyFilter() }
        binding.chipCompleted.setOnClickListener { currentFilter = AppointmentStatus.COMPLETED; applyFilter() }
        binding.chipCancelled.setOnClickListener { currentFilter = AppointmentStatus.CANCELLED; applyFilter() }
        binding.btnBilling.setOnClickListener { openBilling() }

        parentFragmentManager.setFragmentResultListener(LeaveFeedbackFragment.RESULT_KEY, viewLifecycleOwner) { _, result ->
            if (result.getBoolean(LeaveFeedbackFragment.RESULT_SUCCESS)) {
                loadAppointments()
            }
        }

        observeState()
        loadAppointments()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appointments.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    if (state.loading) {
                        binding.scrollContent.visibility = View.GONE
                        return@collect
                    }

                    binding.scrollContent.visibility = View.VISIBLE

                    if (state.error != null) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = state.error
                        return@collect
                    }

                    allAppointments = state.items
                    feedbackProvidedIds = emptySet()
                    refreshFeedbackState()
                }
            }
        }
    }

    private fun refreshFeedbackState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val completedAppointments = allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }
            val existingFeedbackIds = mutableSetOf<Long>()

            completedAppointments.forEach { appointment ->
                runCatching { feedbackRepository.getFeedbackByAppointment(appointment.id) }
                    .onSuccess { existingFeedbackIds.add(appointment.id) }
            }

            feedbackProvidedIds = existingFeedbackIds
            adapter.updateFeedbackProvidedIds(existingFeedbackIds)
            applyFilter()
        }
    }

    private fun filteredAppointments(): List<AppointmentResponse> {
        return when (currentFilter) {
            AppointmentStatus.PENDING -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.PENDING }
            AppointmentStatus.CONFIRMED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.CONFIRMED }
            AppointmentStatus.COMPLETED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }
            AppointmentStatus.CANCELLED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.CANCELLED }
            else -> allAppointments
        }
    }

    private fun applyFilter() {
        val items = filteredAppointments()
        binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) {
            binding.tvEmptyState.text = getString(R.string.appointments_empty_state)
        }
        adapter.submitList(items)
    }

    private fun loadAppointments() {
        binding.tvEmptyState.visibility = View.GONE
        viewModel.getAppointments()
    }

    private fun showCancelConfirmation(appointment: AppointmentResponse) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Appointment")
            .setMessage("Are you sure you want to cancel this appointment?")
            .setPositiveButton("Cancel appointment") { _, _ ->
                viewModel.updateStatus(appointment.id, AppointmentStatus.CANCELLED, AppConstants.Role.PATIENT)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openBilling() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, BillingFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openMedicalRecordDetail(appointment: AppointmentResponse) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MedicalRecordDetailFragment.newInstance(-1, appointment.id))
            .addToBackStack(null)
            .commit()
    }

    private fun openFeedbackSheet(appointment: AppointmentResponse) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, LeaveFeedbackFragment.newInstance(appointment))
            .addToBackStack(null)
            .commit()
    }
}
