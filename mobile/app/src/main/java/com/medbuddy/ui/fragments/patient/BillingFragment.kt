package com.medbuddy.ui.fragments.patient

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentBillingBinding
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.launch

private const val RESERVATION_FEE = 100.0

class BillingFragment : Fragment() {

    private lateinit var binding: FragmentBillingBinding
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var appointmentRepository: AppointmentRepository
    private lateinit var adapter: PatientPaymentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentBillingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        paymentRepository = PaymentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        appointmentRepository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        adapter = PatientPaymentAdapter { row -> payNow(row) }

        binding.rvPayments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPayments.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadBilling() }

        loadBilling()
    }

    private fun loadBilling() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appointments = appointmentRepository.getPatientAppointments()
                val rows = mutableListOf<PatientPaymentRow>()

                for (appointment in appointments) {
                    val payment = runCatching { paymentRepository.getPaymentByAppointmentId(appointment.id) }.getOrNull()
                    val normalizedAppointmentStatus = AppointmentStatus.normalize(appointment.status)
                    rows.add(
                        PatientPaymentRow(
                            paymentId = payment?.id,
                            appointmentId = appointment.id,
                            doctorName = appointmentDoctorName(appointment),
                            amount = payment?.feeAmount ?: RESERVATION_FEE,
                            status = if (normalizedAppointmentStatus == AppointmentStatus.CANCELLED) {
                                AppointmentStatus.CANCELLED
                            } else {
                                normalizePaymentStatus(payment?.paymentStatus)
                            },
                            appointmentStatus = normalizedAppointmentStatus,
                            dateLabel = formatAppointmentDate(appointment.dateTime),
                            description = payment?.status ?: "Consultation Bill",
                        )
                    )
                }

                adapter.submitList(rows)

                val totalAmount = rows.sumOf { it.amount }
                val totalPaid = rows.filter { it.status.uppercase() == "PAID" }.sumOf { it.amount }
                val totalRefunded = rows.filter { it.status.uppercase() == "REFUNDED" }.sumOf { it.amount }
                val totalPending = rows.filter { it.status.uppercase() !in setOf("PAID", "REFUNDED") }.sumOf { it.amount }
                val paidPercent = if (totalAmount > 0) ((totalPaid / totalAmount) * 100).toInt() else 0

                binding.tvTotalPending.text = "PHP ${String.format("%.2f", totalPending)}"
                binding.tvTotalPaid.text = "PHP ${String.format("%.2f", totalPaid)}"
                binding.tvTotalRefunded.text = "PHP ${String.format("%.2f", totalRefunded)}"
                binding.tvProgressPercent.text = "$paidPercent%"
                binding.paymentProgressBar.progress = paidPercent

                binding.tvEmptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            } catch (throwable: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = throwable.message ?: "No payment records found"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun normalizePaymentStatus(status: String?): String {
        return when (status?.uppercase()) {
            "PAID", "COMPLETED" -> "PAID"
            "PARTIAL" -> "PARTIAL"
            "REFUNDED" -> "REFUNDED"
            "FAILED" -> "FAILED"
            else -> "PENDING"
        }
    }

    private fun payNow(row: PatientPaymentRow) {
        val payableAppointment = row.appointmentStatus.uppercase() in setOf("PENDING", "CONFIRMED", "COMPLETED")
        if (!payableAppointment) {
            binding.tvEmptyState.text = "Only pending, confirmed, or completed appointments can be paid."
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        val requestAmount = if (row.amount > 0.0) row.amount else RESERVATION_FEE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { paymentRepository.initiatePayment(row.appointmentId, requestAmount.toBigDecimal()) }
                .onSuccess { checkoutUrl -> openCheckout(checkoutUrl) }
                .onFailure {
                    binding.tvEmptyState.text = ApiErrorMapper.toUserMessage(requireContext(), it, R.string.error_bad_request)
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
        }
    }

    private fun openCheckout(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(requireContext().getColor(R.color.primary))
            .build()
        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
    }
}
