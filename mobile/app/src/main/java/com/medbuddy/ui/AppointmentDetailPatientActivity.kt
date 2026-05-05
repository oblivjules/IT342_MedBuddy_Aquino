package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
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
        val paymentFactory = PaymentViewModelFactory(paymentRepository)
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
        binding.tvDoctorName.text = "${appointment.doctor.firstName} ${appointment.doctor.lastName}"
        binding.tvSpecialization.text = appointment.doctor.specialization ?: "General"
        binding.tvDateTime.text = formatDateTime(appointment.dateTime)
        binding.tvStatus.text = appointment.status
        binding.tvNotes.text = appointment.notes ?: "No notes"
    }

    private fun loadPaymentStatus(appointmentId: Long) {
        paymentViewModel.loadPaymentStatus(appointmentId)
        lifecycleScope.launchWhenStarted {
            paymentViewModel.paymentState.collect { state ->
                binding.progressPayment.visibility = if (state.loading) View.VISIBLE else View.GONE

                state.payment?.let { payment ->
                    binding.cardPayment.visibility = View.VISIBLE
                    binding.tvPaymentStatus.text = "Status: ${payment.status}"
                    binding.tvPaymentAmount.text = "Amount: ₱${payment.amount}"

                    when (payment.status) {
                        "PENDING" -> {
                            binding.btnPayNow.visibility = View.VISIBLE
                            binding.tvPaymentComplete.visibility = View.GONE
                        }
                        "PAID" -> {
                            binding.btnPayNow.visibility = View.GONE
                            binding.tvPaymentComplete.visibility = View.VISIBLE
                            binding.tvPaymentComplete.text = "Payment Complete (${payment.paidAt})"
                            showRatingSection()
                        }
                        else -> {
                            binding.btnPayNow.visibility = View.GONE
                            binding.tvPaymentComplete.text = "Payment ${payment.status}"
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
        medicalRecordViewModel.loadAppointmentFiles(appointmentId)
        lifecycleScope.launchWhenStarted {
            medicalRecordViewModel.detailState.collect { state ->
                if (state.files.isNotEmpty()) {
                    binding.cardMedicalRecords.visibility = View.VISIBLE
                    binding.tvRecordsCount.text = "Files: ${state.files.size}"
                    binding.btnViewRecords.setOnClickListener {
                        // Navigate to medical record detail
                        state.record?.let {
                            val intent = Intent(this@AppointmentDetailPatientActivity, MedicalRecordDetailActivity::class.java)
                            intent.putExtra("recordId", it.id)
                            intent.putExtra("appointmentId", appointmentId)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }

    private fun setupPaymentButton() {
        binding.btnPayNow.setOnClickListener {
            appointment?.let {
                paymentViewModel.initiatePayment(it.id)
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
