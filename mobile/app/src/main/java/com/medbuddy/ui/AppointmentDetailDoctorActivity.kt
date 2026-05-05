package com.medbuddy.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
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
import com.medbuddy.constants.AppointmentStatus
import java.text.SimpleDateFormat
import java.util.Locale

class AppointmentDetailDoctorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentDetailDoctorBinding
    private lateinit var medicalRecordViewModel: MedicalRecordViewModel
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var appointmentViewModel: AppointmentViewModel
    private lateinit var fileAdapter: MedicalRecordFileAdapter
    private var appointment: AppointmentResponse? = null

    companion object {
        private val VALID_TRANSITIONS = mapOf(
            "PENDING" to listOf("APPROVED", "REJECTED"),
            "APPROVED" to listOf("COMPLETED"),
            "COMPLETED" to emptyList()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentDetailDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appointment = intent.getSerializableExtra("appointment") as? AppointmentResponse

        setupToolbar()
        setupViewModels()
        setupAdapter()
        appointment?.let {
            displayAppointmentDetails(it)
            loadPatientRecords(it.id)
            loadPaymentStatus(it.id)
            setupStatusTransitions(it)
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
        val paymentFactory = PaymentViewModelFactory(paymentRepository)
        paymentViewModel = ViewModelProvider(this, paymentFactory).get(PaymentViewModel::class.java)

        val appointmentRepository = com.medbuddy.repository.AppointmentRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val appointmentFactory = AppointmentViewModelFactory(appointmentRepository)
        appointmentViewModel = ViewModelProvider(this, appointmentFactory).get(AppointmentViewModel::class.java)
    }

    private fun setupAdapter() {
        fileAdapter = MedicalRecordFileAdapter { file ->
            // Open or download file
        }
        binding.recyclerFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerFiles.adapter = fileAdapter
    }

    private fun displayAppointmentDetails(appointment: AppointmentResponse) {
        binding.tvPatientName.text = "${appointment.patient.firstName} ${appointment.patient.lastName}"
        binding.tvPatientEmail.text = appointment.patient.email
        binding.tvDateTime.text = formatDateTime(appointment.dateTime)
        binding.tvStatus.text = appointment.status
        binding.tvNotes.text = appointment.notes ?: "No notes"
    }

    private fun loadPatientRecords(appointmentId: Long) {
        medicalRecordViewModel.loadAppointmentFiles(appointmentId)
        lifecycleScope.launchWhenStarted {
            medicalRecordViewModel.detailState.collect { state ->
                fileAdapter.submitList(state.files)
                if (state.files.isEmpty()) {
                    binding.tvNoFiles.visibility = View.VISIBLE
                } else {
                    binding.tvNoFiles.visibility = View.GONE
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
                    binding.tvPaymentStatus.text = "Status: ${payment.status}"
                    binding.tvPaymentAmount.text = "Amount: ₱${payment.amount}"

                    if (payment.status == "PAID") {
                        binding.tvPaymentComplete.visibility = View.VISIBLE
                        binding.tvPaymentComplete.text = "Paid on ${payment.paidAt}"
                    } else {
                        binding.tvPaymentComplete.visibility = View.GONE
                    }

                    binding.btnCreatePayment.setOnClickListener {
                        showCreatePaymentDialog(appointmentId, payment.id)
                    }

                    binding.spinnerPaymentStatus.apply {
                        val statuses = listOf("PENDING", "PAID", "FAILED", "REFUNDED")
                        val adapter = android.widget.ArrayAdapter(
                            this@AppointmentDetailDoctorActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            statuses
                        )
                        this.adapter = adapter
                        setSelection(statuses.indexOf(payment.status))
                        setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: android.widget.AdapterView<*>?,
                                view: View?,
                                position: Int,
                                id: Long
                            ) {
                                val newStatus = statuses[position]
                                if (newStatus != payment.status) {
                                    paymentViewModel.updatePaymentStatus(payment.id, newStatus)
                                }
                            }

                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        })
                    }
                }

                state.error?.let {
                    binding.tvPaymentError.visibility = View.VISIBLE
                    binding.tvPaymentError.text = it
                }
            }
        }
    }

    private fun setupStatusTransitions(appointment: AppointmentResponse) {
        val availableTransitions = VALID_TRANSITIONS[appointment.status] ?: emptyList()

        if (availableTransitions.isEmpty()) {
            binding.cardStatusTransition.visibility = View.GONE
            return
        }

        binding.cardStatusTransition.visibility = View.VISIBLE
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            availableTransitions + appointment.status
        )
        binding.spinnerStatus.adapter = adapter
        binding.spinnerStatus.setSelection(availableTransitions.size)

        binding.spinnerStatus.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val newStatus = parent?.getItemAtPosition(position) as? String
                if (newStatus != null && newStatus != appointment.status) {
                    showStatusChangeConfirmation(newStatus, appointment.id)
                    binding.spinnerStatus.setSelection(availableTransitions.size)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun showStatusChangeConfirmation(newStatus: String, appointmentId: Long) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Confirm Status Change")

        if (newStatus == "REJECTED") {
            val input = android.widget.EditText(this)
            input.hint = "Enter rejection reason (required)"
            input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            dialogBuilder.setView(input)
            dialogBuilder.setMessage("Change status to $newStatus? This action is final.")
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
            dialogBuilder.setMessage("Change status to $newStatus? This action is final and cannot be undone.")
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
                binding.tvStatus.text = newStatus
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

    private fun showCreatePaymentDialog(appointmentId: Long, paymentId: Long) {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setTitle("Create Payment Record")
        val input = android.widget.EditText(this)
        input.hint = "Enter amount"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        dialogBuilder.setView(input)
        dialogBuilder.setNegativeButton(android.R.string.cancel, null)
        dialogBuilder.setPositiveButton("Create") { _, _ ->
            val amount = input.text.toString().toDoubleOrNull()
            if (amount != null && amount > 0) {
                paymentViewModel.createPayment(appointmentId, amount)
            } else {
                android.widget.Toast.makeText(this, "Invalid amount", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        dialogBuilder.show()
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
