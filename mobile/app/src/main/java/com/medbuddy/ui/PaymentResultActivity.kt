package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.PaymentSessionManager
import com.medbuddy.databinding.ActivityPaymentResultBinding
import com.medbuddy.repository.PaymentRepository
import com.medbuddy.viewmodel.PaymentViewModel
import com.medbuddy.viewmodel.PaymentViewModelFactory
import kotlinx.coroutines.launch

class PaymentResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentResultBinding
    private lateinit var paymentViewModel: PaymentViewModel
    private lateinit var paymentSessionManager: PaymentSessionManager
    private val paymentResult by lazy { resolveResult(intent?.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewModel()
        observePaymentState()
        render(paymentResult)
        bindActions(paymentResult)
        confirmPendingPayment()
    }

    private fun resolveResult(data: Uri?): PaymentResult {
        val path = data?.path.orEmpty().lowercase()
        val appointmentId = data?.getQueryParameter("appointmentId")
        val message = data?.getQueryParameter("message")
            ?: data?.getQueryParameter("reason")

        return when {
            path.contains("success") -> PaymentResult.Success(appointmentId)
            path.contains("cancel") -> PaymentResult.Cancelled(appointmentId)
            else -> PaymentResult.Failed(appointmentId, message)
        }
    }

    private fun render(result: PaymentResult) {
        when (result) {
            is PaymentResult.Success -> {
                binding.tvBadge.text = getString(R.string.payment_status_success_badge)
                binding.tvTitle.text = getString(R.string.payment_confirmation_success_title)
                binding.tvMessage.text = getString(R.string.payment_confirmation_success_message)
                binding.tvDetails.text = result.appointmentId?.let {
                    getString(R.string.payment_confirmation_appointment_id, it)
                } ?: getString(R.string.payment_confirmation_success_details)
                binding.btnPrimary.text = getString(R.string.payment_result_view_appointments)
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = getString(R.string.payment_result_go_to_dashboard)
            }
            is PaymentResult.Cancelled -> {
                binding.tvBadge.text = getString(R.string.payment_status_cancelled_badge)
                binding.tvTitle.text = getString(R.string.payment_confirmation_cancelled_title)
                binding.tvMessage.text = getString(R.string.payment_confirmation_cancelled_message)
                binding.tvDetails.text = result.appointmentId?.let {
                    getString(R.string.payment_confirmation_appointment_id, it)
                } ?: getString(R.string.payment_confirmation_cancelled_details)
                binding.btnPrimary.text = getString(R.string.payment_result_view_appointments)
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = getString(R.string.payment_result_go_to_dashboard)
            }
            is PaymentResult.Failed -> {
                binding.tvBadge.text = getString(R.string.payment_status_failed_badge)
                binding.tvTitle.text = getString(R.string.payment_confirmation_failed_title)
                binding.tvMessage.text = getString(R.string.payment_confirmation_failed_message)
                binding.tvDetails.text = result.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.payment_confirmation_failed_details)
                binding.btnPrimary.text = getString(R.string.payment_result_view_appointments)
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = getString(R.string.payment_result_go_to_dashboard)
            }
        }

        bindActions(result)
    }

    private fun bindActions(result: PaymentResult) {
        binding.btnPrimary.setOnClickListener {
            when (result) {
                is PaymentResult.Success -> openAppointments()
                else -> openBilling()
            }
        }
        binding.btnSecondary.setOnClickListener {
            when (result) {
                is PaymentResult.Success, is PaymentResult.Cancelled, is PaymentResult.Failed -> openDashboard()
            }
        }
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun openAppointments() {
        startActivity(Intent(this, MyAppointmentsActivity::class.java))
        finish()
    }

    private fun openBilling() {
        startActivity(Intent(this, MyAppointmentsActivity::class.java))
        finish()
    }

    private fun openDashboard() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        })
        finish()
    }

    private sealed interface PaymentResult {
        data class Success(val appointmentId: String?) : PaymentResult
        data class Cancelled(val appointmentId: String?) : PaymentResult
        data class Failed(val appointmentId: String?, val message: String?) : PaymentResult
    }

    private fun setupViewModel() {
        paymentSessionManager = PaymentSessionManager(applicationContext)
        val paymentRepository = PaymentRepository(RetrofitClient.getInstance(applicationContext).apiService)
        val factory = PaymentViewModelFactory(paymentRepository, paymentSessionManager)
        paymentViewModel = ViewModelProvider(this, factory)[PaymentViewModel::class.java]
    }

    private fun observePaymentState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                paymentViewModel.paymentState.collect { state ->
                    if (state.loading) {
                        binding.tvBadge.text = getString(R.string.payment_confirmation_loading_badge)
                        binding.tvTitle.text = getString(R.string.payment_confirmation_loading_title)
                        binding.tvMessage.text = getString(R.string.payment_confirmation_loading_message)
                        binding.tvDetails.text = getString(R.string.payment_confirmation_loading_details)
                        binding.btnPrimary.isEnabled = false
                        binding.btnSecondary.isEnabled = false
                        return@collect
                    }

                    binding.btnPrimary.isEnabled = true
                    binding.btnSecondary.isEnabled = true

                    state.payment?.let { payment ->
                        when (payment.paymentStatus?.toString()?.uppercase()) {
                            "PAID" -> renderBackendResult(
                                result = PaymentResult.Success(null),
                                badge = getString(R.string.payment_status_success_badge),
                                title = getString(R.string.payment_confirmation_success_title),
                                message = getString(R.string.payment_confirmation_success_message),
                                details = getString(R.string.payment_confirmation_paid_details, payment.id)
                            )
                            "FAILED" -> renderBackendResult(
                                result = PaymentResult.Failed(null, null),
                                badge = getString(R.string.payment_status_failed_badge),
                                title = getString(R.string.payment_confirmation_failed_title),
                                message = getString(R.string.payment_confirmation_failed_message),
                                details = getString(R.string.payment_confirmation_failed_details)
                            )
                            else -> render(paymentResult)
                        }
                    }

                    state.error?.let { error ->
                        renderBackendResult(
                            result = PaymentResult.Failed(null, error),
                            badge = getString(R.string.payment_status_failed_badge),
                            title = getString(R.string.payment_confirmation_failed_title),
                            message = getString(R.string.payment_confirmation_failed_message),
                            details = error
                        )
                    }
                }
            }
        }
    }

    private fun confirmPendingPayment() {
        paymentViewModel.confirmStoredPayment()
    }

    private fun renderBackendResult(
        result: PaymentResult,
        badge: String,
        title: String,
        message: String,
        details: String
    ) {
        binding.tvBadge.text = badge
        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.tvDetails.text = details
        bindActions(result)
    }
}