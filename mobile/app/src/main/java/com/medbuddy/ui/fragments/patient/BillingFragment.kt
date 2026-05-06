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
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.databinding.FragmentBillingBinding
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.launch

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

                appointments.forEach { appointment ->
                    val response = runCatching { paymentRepository.getPaymentByAppointment(appointment.id) }.getOrNull()
                    if (response != null) {
                        rows.add(
                            PatientPaymentRow(
                                paymentId = response.id,
                                appointmentId = appointment.id,
                                doctorName = appointmentDoctorName(appointment),
                                amount = response.feeAmount,
                                status = response.paymentStatus,
                                dateLabel = formatAppointmentDateTime(appointment.dateTime),
                                description = response.status ?: "Consultation Bill",
                            )
                        )
                    }
                }

                adapter.submitList(rows)
                binding.tvTotalPending.text = "PHP ${String.format("%.2f", rows.filter { it.status.uppercase() != "PAID" }.sumOf { it.amount })}"
                binding.tvTotalPaid.text = "PHP ${String.format("%.2f", rows.filter { it.status.uppercase() == "PAID" }.sumOf { it.amount })}"
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

    private fun payNow(row: PatientPaymentRow) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { paymentRepository.initiatePayment(row.appointmentId) }
                .onSuccess { checkoutUrl -> openCheckout(checkoutUrl) }
                .onFailure { binding.tvEmptyState.visibility = View.VISIBLE }
        }
    }

    private fun openCheckout(url: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(requireContext().getColor(R.color.primary))
            .build()
        customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
    }
}
