package com.medbuddy.ui.fragments.doctor

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.constants.AppConstants
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentDoctorAppointmentsBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.ui.AppointmentAdapter
import com.medbuddy.ui.AppointmentDetailDoctorActivity
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorAppointmentsFragment : Fragment() {

    private lateinit var binding: FragmentDoctorAppointmentsBinding
    private lateinit var adapter: AppointmentAdapter
    private lateinit var viewModel: AppointmentViewModel
    private var allAppointments: List<AppointmentResponse> = emptyList()
    private var currentFilter: String = "ALL"
    private var searchQuery: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDoctorAppointmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(this, AppointmentViewModel.factory(repository))[AppointmentViewModel::class.java]

        setupRecyclerView()
        setupFilters()
        setupSearch()
        setupSwipeRefresh()
        binding.btnRetry.setOnClickListener { loadAppointments() }
        observeState()
        loadAppointments()
    }

    private fun setupRecyclerView() {
        adapter = AppointmentAdapter(
            AppConstants.Role.DOCTOR,
            onStatusUpdate = { appointment, status -> onStatusActionClicked(appointment, status) }
        )
        adapter.setOnDetailsClickListener { appointment -> openDetails(appointment) }
        binding.rvAppointments.adapter = adapter
        binding.rvAppointments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppointments.isNestedScrollingEnabled = false
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty().trim()
                applyFilter()
            }
        })
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilter()
                true
            } else false
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { currentFilter = "ALL"; applyFilter() }
        binding.chipPending.setOnClickListener { currentFilter = AppointmentStatus.PENDING; applyFilter() }
        binding.chipConfirmed.setOnClickListener { currentFilter = AppointmentStatus.CONFIRMED; applyFilter() }
        binding.chipCompleted.setOnClickListener { currentFilter = AppointmentStatus.COMPLETED; applyFilter() }
        binding.chipRejected.setOnClickListener { currentFilter = AppointmentStatus.CANCELLED; applyFilter() }
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
                    updateStatCards()
                    applyFilter()
                }
            }
        }
    }

    private fun updateStatCards() {
        binding.tvStatPending.text = allAppointments.count { AppointmentStatus.normalize(it.status) == AppointmentStatus.PENDING }.toString()
        binding.tvStatConfirmed.text = allAppointments.count { AppointmentStatus.normalize(it.status) == AppointmentStatus.CONFIRMED }.toString()
        binding.tvStatCompleted.text = allAppointments.count { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }.toString()
        binding.tvStatCancelled.text = allAppointments.count { AppointmentStatus.normalize(it.status) == AppointmentStatus.CANCELLED }.toString()
    }

    private fun filteredAppointments(): List<AppointmentResponse> {
        var list = when (currentFilter) {
            AppointmentStatus.PENDING -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.PENDING }
            AppointmentStatus.CONFIRMED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.CONFIRMED }
            AppointmentStatus.COMPLETED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }
            AppointmentStatus.CANCELLED -> allAppointments.filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.CANCELLED }
            else -> allAppointments
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase(Locale.getDefault())
            list = list.filter { apt ->
                "${apt.patient.firstName} ${apt.patient.lastName}".lowercase().contains(q) ||
                    apt.patient.email.lowercase().contains(q)
            }
        }
        return list.sortedByDescending { it.dateTime }
    }

    private fun applyFilter() {
        val items = filteredAppointments()
        binding.tvEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (items.isEmpty()) binding.tvEmptyState.text = getString(R.string.appointments_empty_state)
        adapter.submitList(items)
    }

    private fun loadAppointments() {
        binding.tvEmptyState.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        viewModel.getDoctorAppointments()
    }

    // Called by the adapter for primary/secondary status action buttons
    private fun onStatusActionClicked(appointment: AppointmentResponse, status: String) {
        when (AppointmentStatus.normalize(status)) {
            AppointmentStatus.CONFIRMED -> showApproveConfirmation(appointment)
            AppointmentStatus.CANCELLED -> showRejectDialog(appointment)
            AppointmentStatus.COMPLETED -> showCompletionSheet(appointment)
        }
    }

    private fun openDetails(appointment: AppointmentResponse) {
        val intent = Intent(requireContext(), AppointmentDetailDoctorActivity::class.java).apply {
            putExtra("appointment", appointment)
        }
        startActivity(intent)
    }

    // ── Approve ──────────────────────────────────────────────────────────────

    private fun showApproveConfirmation(appointment: AppointmentResponse) {
        val patientName = "${appointment.patient.firstName} ${appointment.patient.lastName}".trim()
        AlertDialog.Builder(requireContext())
            .setTitle("Approve Appointment")
            .setMessage("Approve the appointment with $patientName? This will notify the patient.")
            .setPositiveButton("Approve") { _, _ ->
                viewModel.updateStatus(appointment.id, AppointmentStatus.CONFIRMED, AppConstants.Role.DOCTOR)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Reject / Cancel ───────────────────────────────────────────────────────

    private fun showRejectDialog(appointment: AppointmentResponse) {
        val patientName = "${appointment.patient.firstName} ${appointment.patient.lastName}".trim()
        val input = EditText(requireContext()).apply {
            hint = "Reason for rejection (required)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 16, 64, 0)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Reject Appointment")
            .setMessage("Reject appointment with $patientName? This action is final.")
            .setView(container)
            .setPositiveButton("Reject") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isBlank()) {
                    android.widget.Toast.makeText(requireContext(), "Please provide a rejection reason.", android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateStatus(appointment.id, AppointmentStatus.CANCELLED, AppConstants.Role.DOCTOR)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    private fun showCompletionSheet(appointment: AppointmentResponse) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_complete_appointment, null)
        dialog.setContentView(sheetView)
        dialog.behavior.peekHeight = resources.displayMetrics.heightPixels / 2

        val etDiagnosis = sheetView.findViewById<EditText>(R.id.etDiagnosis)
        val etMedicine = sheetView.findViewById<EditText>(R.id.etMedicine)
        val etDosage = sheetView.findViewById<EditText>(R.id.etDosage)
        val etRoute = sheetView.findViewById<EditText>(R.id.etRoute)
        val etFrequency = sheetView.findViewById<EditText>(R.id.etFrequency)
        val etDuration = sheetView.findViewById<EditText>(R.id.etDuration)
        val etPrescriptionNotes = sheetView.findViewById<EditText>(R.id.etPrescriptionNotes)
        val etBillAmount = sheetView.findViewById<EditText>(R.id.etBillAmount)
        val tvError = sheetView.findViewById<TextView>(R.id.tvCompletionError)
        val tvPatientNotes = sheetView.findViewById<TextView>(R.id.tvPatientNotes)
        val tvPatientName = sheetView.findViewById<TextView>(R.id.tvCompletionPatientName)

        tvPatientName.text = "${appointment.patient.firstName} ${appointment.patient.lastName}"
        if (!appointment.notes.isNullOrBlank()) {
            tvPatientNotes.visibility = View.VISIBLE
            tvPatientNotes.text = "Patient notes: ${appointment.notes}"
        }

        // Pre-fill existing record if any
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val record: MedicalRecordResponse = MedicalRecordRepository(
                    RetrofitClient.getInstance(requireContext()).apiService
                ).getMedicalRecordByAppointment(appointment.id)
                if (isAdded) {
                    etDiagnosis.setText(record.diagnosis.orEmpty())
                    etMedicine.setText(record.medicineName.orEmpty())
                    etDosage.setText(record.dosage.orEmpty())
                    etRoute.setText(record.route.orEmpty())
                    etFrequency.setText(record.frequency.orEmpty())
                    etDuration.setText(record.duration.orEmpty())
                    etPrescriptionNotes.setText(record.prescriptionNotes.orEmpty())
                }
            } catch (_: Throwable) { /* no existing record – start fresh */ }
        }

        sheetView.findViewById<View>(R.id.btnCompleteAppointment).setOnClickListener {
            tvError.visibility = View.GONE
            val diagnosis = etDiagnosis.text?.toString()?.trim().orEmpty()
            val billText = etBillAmount.text?.toString()?.trim().orEmpty()
            val bill = billText.toBigDecimalOrNull()

            if (diagnosis.isBlank()) {
                tvError.text = "Diagnosis is required."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (bill == null || bill <= BigDecimal.ZERO) {
                tvError.text = "Total bill must be greater than 0."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            dialog.dismiss()
            submitCompletion(
                appointment = appointment,
                diagnosis = diagnosis,
                medicine = etMedicine.text?.toString()?.trim().orEmpty(),
                dosage = etDosage.text?.toString()?.trim().orEmpty(),
                route = etRoute.text?.toString()?.trim().orEmpty(),
                frequency = etFrequency.text?.toString()?.trim().orEmpty(),
                duration = etDuration.text?.toString()?.trim().orEmpty(),
                prescriptionNotes = etPrescriptionNotes.text?.toString()?.trim().orEmpty(),
                billAmount = bill
            )
        }

        sheetView.findViewById<View>(R.id.btnCancelCompletion).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun submitCompletion(
        appointment: AppointmentResponse,
        diagnosis: String,
        medicine: String,
        dosage: String,
        route: String,
        frequency: String,
        duration: String,
        prescriptionNotes: String,
        billAmount: BigDecimal
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).apiService
                val medRepo = MedicalRecordRepository(api)

                // Create or update medical record
                val prescriptionSummary = buildList<String> {
                    if (medicine.isNotBlank()) add("Medicine: $medicine")
                    if (dosage.isNotBlank()) add("Dosage: $dosage")
                    if (route.isNotBlank()) add("Route: $route")
                    if (frequency.isNotBlank()) add("Frequency: $frequency")
                    if (duration.isNotBlank()) add("Duration: $duration")
                    if (prescriptionNotes.isNotBlank()) add("Notes: $prescriptionNotes")
                }.joinToString(" | ")

                val existingRecord = runCatching {
                    medRepo.getMedicalRecordByAppointment(appointment.id)
                }.getOrNull()

                if (existingRecord?.id != null) {
                    api.updateMedicalRecord(
                        existingRecord.id,
                        com.medbuddy.dto.CreateMedicalRecordRequest(
                            appointmentId = appointment.id,
                            diagnosis = diagnosis,
                            prescriptionDetails = prescriptionSummary.ifBlank { null },
                            medicineName = medicine.ifBlank { null },
                            dosage = dosage.ifBlank { null },
                            route = route.ifBlank { null },
                            frequency = frequency.ifBlank { null },
                            duration = duration.ifBlank { null },
                            prescriptionNotes = prescriptionNotes.ifBlank { null }
                        )
                    ).bodyOrThrow()
                } else {
                    medRepo.createMedicalRecord(
                        appointmentId = appointment.id,
                        diagnosis = diagnosis,
                        prescriptionDetails = prescriptionSummary.ifBlank { null },
                        medicineName = medicine.ifBlank { null },
                        dosage = dosage.ifBlank { null },
                        route = route.ifBlank { null },
                        frequency = frequency.ifBlank { null },
                        duration = duration.ifBlank { null },
                        prescriptionNotes = prescriptionNotes.ifBlank { null }
                    )
                }

                // Update bill total
                val paymentRepo = PaymentRepository(api)
                runCatching { paymentRepo.updateTotalBill(appointment.id, billAmount.toDouble()) }

                // Mark completed
                viewModel.updateStatus(appointment.id, AppointmentStatus.COMPLETED, AppConstants.Role.DOCTOR)

                if (isAdded) {
                    android.widget.Toast.makeText(requireContext(), "Appointment completed.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                if (isAdded) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        e.message ?: "Failed to complete appointment.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
