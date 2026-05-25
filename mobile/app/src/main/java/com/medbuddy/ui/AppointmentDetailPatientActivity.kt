package com.medbuddy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.PaymentSessionManager
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.ActivityAppointmentDetailPatientBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.repository.RatingRepository
import com.medbuddy.repository.MedicalRecordRepository
import com.medbuddy.viewmodel.PaymentViewModel
import com.medbuddy.viewmodel.PaymentViewModelFactory
import com.medbuddy.viewmodel.RatingViewModel
import com.medbuddy.viewmodel.RatingViewModelFactory
import com.medbuddy.viewmodel.MedicalRecordViewModel
import com.medbuddy.viewmodel.MedicalRecordViewModelFactory
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

class AppointmentDetailPatientActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppointmentDetailPatientBinding
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var ratingViewModel: RatingViewModel
    private lateinit var medicalRecordViewModel: MedicalRecordViewModel
    private var appointment: AppointmentResponse? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppointmentDetailPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appointment = intent.getSerializableExtra("appointment") as? AppointmentResponse

        setupToolbar()
        setupViewModels()
        appointment?.let {
            displayAppointmentDetails(it)
            loadPaymentStatus(it.id)
            loadMedicalRecords(it.id)
        }

        setupPaymentButton()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupViewModels() {
        val paymentRepository = PaymentRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val paymentFactory = PaymentViewModelFactory(
            paymentRepository,
            PaymentSessionManager(applicationContext)
        )
        paymentViewModel = ViewModelProvider(this, paymentFactory).get(PaymentViewModel::class.java)

        val ratingRepository = RatingRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val ratingFactory = RatingViewModelFactory(ratingRepository)
        ratingViewModel = ViewModelProvider(this, ratingFactory).get(RatingViewModel::class.java)

        val medicalRecordRepository = MedicalRecordRepository(
            RetrofitClient.getInstance(applicationContext).apiService
        )
        val recordFactory = MedicalRecordViewModelFactory(medicalRecordRepository)
        medicalRecordViewModel = ViewModelProvider(this, recordFactory).get(MedicalRecordViewModel::class.java)
    }

    private fun displayAppointmentDetails(appointment: AppointmentResponse) {
        val fullName = "Dr. ${appointment.doctor.firstName} ${appointment.doctor.lastName}".trim()
        val initials = "${appointment.doctor.firstName} ${appointment.doctor.lastName}".trim()
            .split(" ")
            .filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
        binding.tvAvatar.text = initials.ifBlank { "DR" }

        binding.tvDoctorName.text = fullName
        binding.tvSpecialization.text = appointment.doctor.specialization
            ?: appointment.doctor.specializations?.firstOrNull()
            ?: "General Practice"
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
            binding.cardNotesContainer.visibility = View.VISIBLE
            binding.tvNotes.text = appointment.notes
        } else {
            binding.cardNotesContainer.visibility = View.GONE
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

    private fun loadPaymentStatus(appointmentId: Long) {
        paymentViewModel.loadPaymentStatus(appointmentId)
        lifecycleScope.launchWhenStarted {
            paymentViewModel.paymentState.collect { state ->
                binding.progressPayment.visibility = if (state.loading) View.VISIBLE else View.GONE

                state.payment?.let { payment ->
                    binding.cardPayment.visibility = View.VISIBLE
                    binding.tvPaymentStatus.text = payment.status ?: payment.paymentStatus
                    binding.tvPaymentAmount.text = "₱${payment.amount ?: payment.feeAmount}"

                    when (payment.status ?: payment.paymentStatus) {
                        "PENDING" -> {
                            binding.btnPayNow.visibility = View.VISIBLE
                            binding.tvPaymentComplete.visibility = View.GONE
                        }
                        "PAID" -> {
                            binding.btnPayNow.visibility = View.GONE
                            binding.tvPaymentComplete.visibility = View.VISIBLE
                            binding.tvPaymentComplete.text = "Payment complete${payment.paidAt?.let { " · $it" } ?: ""}"
                            showRatingSection()
                        }
                        else -> {
                            binding.btnPayNow.visibility = View.GONE
                            binding.tvPaymentComplete.text = "Payment ${payment.status ?: payment.paymentStatus}"
                            binding.tvPaymentComplete.visibility = View.VISIBLE
                        }
                    }
                }

                state.checkoutUrl?.let { url ->
                    openCheckoutUrl(url)
                    paymentViewModel.clearCheckoutUrl()
                }

                state.error?.let {
                    binding.tvPaymentError.visibility = View.VISIBLE
                    binding.tvPaymentError.text = it
                }
            }
        }
    }

    private fun loadMedicalRecords(appointmentId: Long) {
        medicalRecordViewModel.loadAppointmentRecord(appointmentId)
        lifecycleScope.launchWhenStarted {
            medicalRecordViewModel.detailState.collect { state ->
                val hasRecord = state.record != null
                val hasFiles = state.files.isNotEmpty()
                if (hasRecord || hasFiles) {
                    binding.cardMedicalRecords.visibility = View.VISIBLE
                    val fileCount = state.files.size
                    binding.tvRecordsCount.text = when {
                        hasRecord && fileCount > 0 -> "Diagnosis recorded · $fileCount file${if (fileCount > 1) "s" else ""} attached"
                        hasRecord -> "Diagnosis recorded"
                        fileCount > 0 -> "$fileCount file${if (fileCount > 1) "s" else ""} attached"
                        else -> ""
                    }
                    binding.btnViewRecords.setOnClickListener {
                        val intent = Intent(this@AppointmentDetailPatientActivity, MedicalRecordDetailActivity::class.java)
                        intent.putExtra("recordId", state.record?.id ?: 0L)
                        intent.putExtra("appointmentId", appointmentId)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun setupPaymentButton() {
        binding.btnPayNow.setOnClickListener {
            val currentAppointment = appointment ?: return@setOnClickListener

            if (currentAppointment.status == "CANCELLED") {
                binding.tvPaymentError.visibility = View.VISIBLE
                binding.tvPaymentError.text = "Payment cannot be initiated for cancelled appointments."
                return@setOnClickListener
            }

            val amount = paymentViewModel.paymentState.value.payment?.amount
                ?: paymentViewModel.paymentState.value.payment?.feeAmount

            if (amount == null || amount <= 0.0) {
                binding.tvPaymentError.visibility = View.VISIBLE
                binding.tvPaymentError.text = "Payment amount is unavailable. Please refresh and try again."
                return@setOnClickListener
            }

            paymentViewModel.initiatePayment(currentAppointment.id, BigDecimal.valueOf(amount)) { checkoutUrl ->
                if (checkoutUrl != null) {
                    openCheckoutUrl(checkoutUrl)
                }
            }
        }
    }

    private fun openCheckoutUrl(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(getColor(R.color.primary))
            .build()
        customTabsIntent.launchUrl(this, Uri.parse(url))
    }

    private fun showRatingSection() {
        binding.cardRating.visibility = View.VISIBLE
        appointment?.let {
            binding.ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
                if (fromUser) {
                    binding.btnSubmitRating.visibility = View.VISIBLE
                }
            }

            binding.btnSubmitRating.setOnClickListener {
                val feedback = binding.etFeedback.text.toString()
                val rating = binding.ratingBar.rating.toInt()
                if (rating > 0) {
                    ratingViewModel.submitRating(it.id, rating, feedback.ifEmpty { null })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appointment?.let {
            paymentViewModel.confirmStoredPayment { confirmedPayment ->
                if (confirmedPayment?.paymentStatus == "PAID") {
                    loadPaymentStatus(it.id)
                }
            }
            loadPaymentStatus(it.id)
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
