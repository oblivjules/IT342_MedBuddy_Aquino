package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.medbuddy.R
import com.medbuddy.databinding.ActivityPaymentResultBinding

class PaymentResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val result = resolveResult(intent?.data)
        render(result)
        bindActions(result)
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
                binding.tvBadge.text = "SUCCESS"
                binding.tvTitle.text = "Payment Successful"
                binding.tvMessage.text = "Your payment has been processed successfully."
                binding.tvDetails.text = result.appointmentId?.let { "Appointment ID: $it" } ?: "Your receipt will be available shortly."
                binding.btnPrimary.text = "View Appointments"
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = "Go to Dashboard"
            }
            is PaymentResult.Cancelled -> {
                binding.tvBadge.text = "CANCELLED"
                binding.tvTitle.text = "Payment Cancelled"
                binding.tvMessage.text = "You cancelled the payment. Your appointment remains unpaid."
                binding.tvDetails.text = result.appointmentId?.let { "Appointment ID: $it" } ?: "You can try again anytime from Billing."
                binding.btnPrimary.text = "View Appointments"
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = "Dashboard"
            }
            is PaymentResult.Failed -> {
                binding.tvBadge.text = "FAILED"
                binding.tvTitle.text = "Payment Failed"
                binding.tvMessage.text = "We couldn't complete the payment."
                binding.tvDetails.text = result.message?.takeIf { it.isNotBlank() } ?: "Please try again or contact support."
                binding.btnPrimary.text = "View Appointments"
                binding.btnSecondary.visibility = View.VISIBLE
                binding.btnSecondary.text = "Dashboard"
            }
        }
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
}