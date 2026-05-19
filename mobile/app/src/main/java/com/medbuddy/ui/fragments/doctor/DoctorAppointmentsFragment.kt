package com.medbuddy.ui.fragments.doctor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.constants.AppConstants
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentDoctorAppointmentsBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.api.RetrofitClient
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.ui.AppointmentAdapter
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch

class DoctorAppointmentsFragment : Fragment() {

    private lateinit var binding: FragmentDoctorAppointmentsBinding
    private lateinit var adapter: AppointmentAdapter
    private lateinit var viewModel: AppointmentViewModel
    private var allAppointments: List<AppointmentResponse> = emptyList()
    private var currentFilter: String = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDoctorAppointmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(this, AppointmentViewModel.factory(repository))[AppointmentViewModel::class.java]

        setupRecyclerView()
        setupFilters()
        setupSwipeRefresh()
        binding.btnRetry.setOnClickListener { loadAppointments() }
        observeState()
        loadAppointments()
    }

    private fun setupRecyclerView() {
        adapter = AppointmentAdapter(
            AppConstants.Role.DOCTOR,
            onStatusUpdate = { appointment, status -> onStatusUpdateClicked(appointment, status) }
        )
        binding.rvAppointments.adapter = adapter
        binding.rvAppointments.layoutManager = LinearLayoutManager(requireContext())

        adapter.setOnItemClickListener { appointment ->
            when (AppointmentStatus.normalize(appointment.status)) {
                AppointmentStatus.PENDING -> showCancelConfirmation(appointment.id)
                AppointmentStatus.CONFIRMED -> showCompleteConfirmation(appointment.id)
            }
        }
    }

    private fun onStatusUpdateClicked(appointment: AppointmentResponse, status: String) {
        viewModel.updateStatus(appointment.id, status, AppConstants.Role.DOCTOR)
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            currentFilter = "ALL"
            applyFilter()
        }
        binding.chipPending.setOnClickListener {
            currentFilter = AppointmentStatus.PENDING
            applyFilter()
        }
        binding.chipCompleted.setOnClickListener {
            currentFilter = AppointmentStatus.COMPLETED
            applyFilter()
        }
        binding.chipRejected.setOnClickListener {
            currentFilter = AppointmentStatus.CANCELLED
            applyFilter()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadAppointments() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.appointments.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    if (state.error != null) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = state.error
                        binding.btnRetry.visibility = View.VISIBLE
                        return@collect
                    }

                    binding.btnRetry.visibility = View.GONE
                    allAppointments = state.items
                    applyFilter()
                }
            }
        }
    }

    private fun filteredAppointments(): List<AppointmentResponse> {
        return when (currentFilter) {
            AppointmentStatus.PENDING -> allAppointments.filter {
                AppointmentStatus.normalize(it.status) == AppointmentStatus.PENDING
            }
            AppointmentStatus.COMPLETED -> allAppointments.filter {
                AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED
            }
            AppointmentStatus.CANCELLED -> allAppointments.filter {
                AppointmentStatus.normalize(it.status) == AppointmentStatus.CANCELLED
            }
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
        binding.btnRetry.visibility = View.GONE
        viewModel.getDoctorAppointments()
    }

    private fun showCancelConfirmation(appointmentId: Long) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancel Appointment")
            .setMessage("Are you sure you want to cancel this appointment?")
            .setPositiveButton("Yes") { _, _ -> viewModel.updateStatus(appointmentId, AppointmentStatus.CANCELLED, AppConstants.Role.DOCTOR) }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showCompleteConfirmation(appointmentId: Long) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Complete Appointment")
            .setMessage("Mark this appointment as completed?")
            .setPositiveButton("Complete") { _, _ -> viewModel.updateStatus(appointmentId, AppointmentStatus.COMPLETED, AppConstants.Role.DOCTOR) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
