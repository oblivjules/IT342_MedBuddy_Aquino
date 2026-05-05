package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.medbuddy.R
import com.medbuddy.constants.AppConstants
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentAppointmentsBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.api.RetrofitClient
import com.medbuddy.ui.AppointmentAdapter
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch

class AppointmentsFragment : Fragment() {

    private lateinit var binding: FragmentAppointmentsBinding
    private lateinit var adapter: AppointmentAdapter
    private lateinit var viewModel: AppointmentViewModel
    private var allAppointments: List<AppointmentResponse> = emptyList()
    private var currentFilter: String = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAppointmentsBinding.inflate(inflater, container, false)
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
        adapter = AppointmentAdapter(AppConstants.Role.PATIENT) { appointment, targetStatus ->
            when (targetStatus) {
                AppointmentStatus.CANCELLED -> showCancelConfirmation(appointment.id)
                "RESCHEDULE" -> Toast.makeText(requireContext(), "Please choose a new slot from Find Doctor", Toast.LENGTH_SHORT).show()
                "VIEW_RECORD" -> Toast.makeText(requireContext(), "Medical record viewer will be connected next", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvAppointments.adapter = adapter
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener {
            currentFilter = "ALL"
            applyFilter()
        }
        binding.chipBooked.setOnClickListener {
            currentFilter = "ACTIVE"
            applyFilter()
        }
        binding.chipCompleted.setOnClickListener {
            currentFilter = AppointmentStatus.COMPLETED
            applyFilter()
        }
        binding.chipCanceled.setOnClickListener {
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
                viewModel.appointmentsState.collect { state ->
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
            "ACTIVE" -> allAppointments.filter {
                val status = AppointmentStatus.normalize(it.status)
                status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED
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
        viewModel.loadAppointments(AppConstants.Role.PATIENT)
    }

    private fun showCancelConfirmation(appointmentId: Long) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Cancel Appointment")
            .setMessage("Are you sure you want to cancel this appointment?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.updateStatus(appointmentId, AppointmentStatus.CANCELLED, AppConstants.Role.PATIENT)
            }
            .setNegativeButton("No", null)
            .show()
    }
}
