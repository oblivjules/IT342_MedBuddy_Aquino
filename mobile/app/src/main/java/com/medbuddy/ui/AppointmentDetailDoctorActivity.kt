package com.medbuddy.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.PaymentSessionManager
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.ActivityAppointmentDetailDoctorBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.viewmodel.MedicalRecordViewModel
import com.medbuddy.viewmodel.MedicalRecordViewModelFactory
import com.medbuddy.viewmodel.PaymentViewModel
import com.medbuddy.viewmodel.PaymentViewModelFactory
import com.medbuddy.viewmodel.AppointmentViewModel
import com.medbuddy.viewmodel.AppointmentViewModelFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AppointmentDetailDoctorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentDetailDoctorBinding
    private lateinit var medicalRecordViewModel: MedicalRecordViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var appointmentViewModel: AppointmentViewModel
    private lateinit var fileAdapter: MedicalRecordFileAdapter
    private var appointment: AppointmentResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentDetailDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appointment = intent.getSerializableExtra("appointment") as? AppointmentResponse

        setupToolbar()
        setupViewModels()
        setupAdapter()
        if (appointment != null) {
            appointment?.let {
                displayAppointmentDetails(it)
                loadPatientRecords(it.id)
                loadPaymentStatus(it.id)
                setupStatusTransitions(it)
            }
        } else {
            loadAppointmentFromId()
        }
    }

    private fun loadAppointmentFromId() {
        val appointmentId = intent.getLongExtra("appointmentId", 0L)
        if (appointmentId <= 0L) {
            binding.progressBar.visibility = View.GONE
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launchWhenStarted {
            try {
                val fetched = RetrofitClient.getInstance(applicationContext).apiService
                    .getAppointmentById(appointmentId)
                    .bodyOrThrow()
                appointment = fetched
                displayAppointmentDetails(fetched)
                loadPatientRecords(fetched.id)
                loadPaymentStatus(fetched.id)
                setupStatusTransitions(fetched)
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    this@AppointmentDetailDoctorActivity,
                    "Unable to load appointment details",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupViewModels() {
        val medicalRecordRepository = MedicalRecordRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val recordFactory = MedicalRecordViewModelFactory(medicalRecordRepository)
        medicalRecordViewModel = ViewModelProvider(this, recordFactory).get(MedicalRecordViewModel::class.java)

        val paymentRepository = PaymentRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val paymentFactory = PaymentViewModelFactory(
            paymentRepository,
            PaymentSessionManager(applicationContext)
        )
        paymentViewModel = ViewModelProvider(this, paymentFactory).get(PaymentViewModel::class.java)

        val appointmentRepository = com.medbuddy.repository.AppointmentRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val appointmentFactory = AppointmentViewModelFactory(appointmentRepository)
        appointmentViewModel = ViewModelProvider(this, appointmentFactory).get(AppointmentViewModel::class.java)
    }

    private fun setupAdapter() {
        fileAdapter = MedicalRecordFileAdapter { file ->
            openFileUrl(file.id)
        }
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = fileAdapter
    }

    private fun openFileUrl(fileId: Long) {
        lifecycleScope.launchWhenStarted {
            try {
                val result = RetrofitClient.getInstance(applicationContext).apiService
                    .getMedicalRecordFileAccessUrl(fileId).bodyOrThrow()
                val url = result["url"] ?: return@launchWhenStarted
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            } catch (_: Exception) {
                android.widget.Toast.makeText(
                    this@AppointmentDetailDoctorActivity, "Unable to open file", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun displayAppointmentDetails(appointment: AppointmentResponse) {
        val fullName = "${appointment.patient.firstName} ${appointment.patient.lastName}".trim()
        val initials = fullName.split(" ")
            .filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
        binding.tvAvatar.text = initials.ifBlank { "PT" }

        binding.tvPatientName.text = fullName
        binding.tvPatientEmail.text = appointment.patient.email
        binding.tvDateTime.text = formatDateTime(appointment.dateTime)

        val statusText = when (AppointmentStatus.normalize(appointment.status)) {
            AppointmentStatus.CONFIRMED -> "Approved"
            AppointmentStatus.CANCELLED -> "Rejected"
            else -> appointment.status.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
        binding.tvStatus.text = statusText
        styleStatusBadge(appointment.status)

        if (!appointment.notes.isNullOrBlank()) {
            binding.cardNotes.visibility = View.VISIBLE
            binding.tvNotes.text = appointment.notes
        } else {
            binding.cardNotes.visibility = View.GONE
        }
    }

    private fun styleStatusBadge(status: String) {
        val (bg, text) = when (AppointmentStatus.normalize(status)) {
            AppointmentStatus.CONFIRMED -> R.color.chip_confirmed_bg to R.color.chip_confirmed_text
            AppointmentStatus.COMPLETED -> R.color.chip_completed_bg to R.color.chip_completed_text
            AppointmentStatus.PENDING -> R.color.chip_pending_bg to R.color.chip_pending_text
            AppointmentStatus.CANCELLED -> R.color.chip_cancelled_bg to R.color.chip_cancelled_text
            else -> R.color.chip_booked_bg to R.color.chip_booked_text
        }
        binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, text))
    }

    private fun loadPatientRecords(appointmentId: Long) {
        lifecycleScope.launchWhenStarted {
            try {
                val files = RetrofitClient.getInstance(applicationContext).apiService
                    .getAppointmentFiles(appointmentId)
                    .bodyOrThrow()
                binding.cardFiles.visibility = View.VISIBLE
                if (files.isNotEmpty()) {
                    binding.tvNoFiles.visibility = View.GONE
                    fileAdapter.submitList(files)
                } else {
                    binding.tvNoFiles.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                binding.cardFiles.visibility = View.VISIBLE
                binding.tvNoFiles.visibility = View.VISIBLE
            }
        }

        // Medical record + drug info via ViewModel
        medicalRecordViewModel.loadAppointmentRecord(appointmentId)
        lifecycleScope.launchWhenStarted {
            medicalRecordViewModel.detailState.collect { state ->
                state.record?.let { record ->
                    binding.cardMedicalRecord.visibility = View.VISIBLE
                    binding.tvDiagnosis.text = record.diagnosis.ifBlank { "No diagnosis recorded." }
                    binding.tvMedicineName.text = record.medicineName?.ifBlank { "—" } ?: "—"
                    binding.tvDosage.text = record.dosage?.ifBlank { "—" } ?: "—"
                    binding.tvRoute.text = record.route?.ifBlank { "—" } ?: "—"
                    binding.tvFrequency.text = record.frequency?.ifBlank { "—" } ?: "—"
                    binding.tvDuration.text = record.duration?.ifBlank { "—" } ?: "—"
                    binding.tvPrescriptionNotes.text = record.prescriptionNotes?.ifBlank { "—" } ?: "—"
                }
                state.drugInfo?.let { drug ->
                    if (drug.available == true && drug.data != null) {
                        binding.cardDrugInfo.visibility = View.VISIBLE
                        drug.indications?.takeIf { it.isNotBlank() }?.let {
                            binding.layoutDrugIndications.visibility = View.VISIBLE
                            binding.tvDrugIndications.text = it
                        }
                        drug.warnings?.takeIf { it.isNotBlank() }?.let {
                            binding.layoutDrugWarnings.visibility = View.VISIBLE
                            binding.tvDrugWarnings.text = it
                        }
                        (drug.dosageAdministration ?: drug.dosage)?.takeIf { it.isNotBlank() }?.let {
                            binding.layoutDrugDosage.visibility = View.VISIBLE
                            binding.tvDrugDosage.text = it
                        }
                        drug.description?.takeIf { it.isNotBlank() }?.let {
                            binding.layoutDrugDescription.visibility = View.VISIBLE
                            binding.tvDrugDescription.text = it
                        }
                    }
                }
            }
        }
    }

    private fun loadPaymentStatus(appointmentId: Long) {
        paymentViewModel.loadPaymentStatus(appointmentId)
        lifecycleScope.launchWhenStarted {
            paymentViewModel.paymentState.collect { state ->
                state.payment?.let { payment ->
                    binding.cardPayment.visibility = View.VISIBLE
                    binding.tvPaymentStatus.text = payment.status ?: payment.paymentStatus
                    binding.tvPaymentAmount.text = "₱${payment.amount ?: payment.feeAmount}"
                }
            }
        }
    }

    private fun setupStatusTransitions(appointment: AppointmentResponse) {
        val status = AppointmentStatus.normalize(appointment.status)

        binding.btnApprove.visibility = View.GONE
        binding.btnComplete.visibility = View.GONE
        binding.btnReject.visibility = View.GONE

        when (status) {
            AppointmentStatus.PENDING -> {
                binding.cardStatusTransition.visibility = View.VISIBLE
                binding.btnApprove.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
                binding.btnApprove.setOnClickListener {
                    showStatusChangeConfirmation(AppointmentStatus.CONFIRMED, appointment.id)
                }
                binding.btnReject.setOnClickListener {
                    showStatusChangeConfirmation(AppointmentStatus.CANCELLED, appointment.id)
                }
            }
            AppointmentStatus.CONFIRMED -> {
                binding.cardStatusTransition.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
                if (isAppointmentDayReached(appointment.dateTime)) {
                    binding.btnComplete.visibility = View.VISIBLE
                    binding.btnComplete.setOnClickListener {
                        showStatusChangeConfirmation(AppointmentStatus.COMPLETED, appointment.id)
                    }
                }
                binding.btnReject.setOnClickListener {
                    showStatusChangeConfirmation(AppointmentStatus.CANCELLED, appointment.id)
                }
            }
            else -> binding.cardStatusTransition.visibility = View.GONE
        }
    }

    private fun isAppointmentDayReached(dateTime: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val appointmentDate = format.parse(dateTime) ?: return false
            val todayMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            !appointmentDate.after(todayMidnight)
        } catch (e: Exception) {
            false
        }
    }

    private fun showStatusChangeConfirmation(newStatus: String, appointmentId: Long) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Confirm Status Change")

        if (newStatus == AppointmentStatus.CANCELLED) {
            val input = android.widget.EditText(this)
            input.hint = "Enter rejection reason (required)"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            dialogBuilder.setView(input)
            dialogBuilder.setMessage("Reject this appointment? This action is final.")
            dialogBuilder.setNegativeButton(android.R.string.cancel, null)
            dialogBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
                val reason = input.text.toString()
                if (reason.isNotEmpty()) {
                    updateAppointmentStatus(appointmentId, newStatus, reason)
                } else {
                    android.widget.Toast.makeText(this, "Rejection reason is required", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val label = when (newStatus) {
                AppointmentStatus.CONFIRMED -> "approve"
                AppointmentStatus.COMPLETED -> "complete"
                else -> "update"
            }
            dialogBuilder.setMessage("Are you sure you want to $label this appointment? This action is final and cannot be undone.")
            dialogBuilder.setNegativeButton(android.R.string.cancel, null)
            dialogBuilder.setPositiveButton(android.R.string.ok) { _, _ ->
                updateAppointmentStatus(appointmentId, newStatus, null)
            }
        }

        dialogBuilder.show()
    }

    private fun updateAppointmentStatus(appointmentId: Long, newStatus: String, rejectionReason: String?) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launchWhenStarted {
            try {
                appointmentViewModel.updateStatus(appointmentId, newStatus)
                val updated = appointment?.copy(status = newStatus)
                appointment = updated
                styleStatusBadge(newStatus)
                val statusText = when (AppointmentStatus.normalize(newStatus)) {
                    AppointmentStatus.CONFIRMED -> "Approved"
                    AppointmentStatus.CANCELLED -> "Rejected"
                    else -> newStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
                binding.tvStatus.text = statusText
                if (updated != null) setupStatusTransitions(updated)
                binding.progressBar.visibility = View.GONE
                android.widget.Toast.makeText(
                    this@AppointmentDetailDoctorActivity,
                    "Status updated successfully",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                android.widget.Toast.makeText(
                    this@AppointmentDetailDoctorActivity,
                    "Failed to update status: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun formatDateTime(dateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date ?: System.currentTimeMillis())
        } catch (e: Exception) {
            dateTime
        }
    }
}
