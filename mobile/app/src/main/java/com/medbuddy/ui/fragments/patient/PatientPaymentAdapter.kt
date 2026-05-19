package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.R
import com.medbuddy.databinding.ItemPatientPaymentBinding

data class PatientPaymentRow(
    val paymentId: Long?,
    val appointmentId: Long,
    val doctorName: String,
    val amount: Double,
    val status: String,
    val appointmentStatus: String,
    val dateLabel: String,
    val description: String,
)

class PatientPaymentAdapter(
    private val onPayNowClick: (PatientPaymentRow) -> Unit,
) : ListAdapter<PatientPaymentRow, PatientPaymentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPatientPaymentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PatientPaymentRow) {
            val normalized = item.status.uppercase()
            val appointmentStatus = item.appointmentStatus.uppercase()
            binding.tvReference.text = item.paymentId?.let { "Payment #$it" } ?: "Appointment #${item.appointmentId}"
            binding.tvDoctorName.text = "Dr. ${item.doctorName}"
            binding.tvAmount.text = "PHP ${String.format("%.2f", item.amount)}"
            binding.tvStatus.text = normalized.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            binding.tvDate.text = item.dateLabel
            binding.tvDescription.text = item.description
            styleStatusChip(normalized)

            val payableAppointment = appointmentStatus == "PENDING" || appointmentStatus == "CONFIRMED" || appointmentStatus == "COMPLETED"
            val payablePayment = normalized == "PENDING" || normalized == "PARTIAL" || normalized == "FAILED"
            binding.btnPayNow.visibility = if (payablePayment && payableAppointment) View.VISIBLE else View.GONE
            binding.btnPayNow.setOnClickListener { onPayNowClick(item) }
        }

        private fun styleStatusChip(status: String) {
            val context = itemView.context
            val (background, textColor) = when (status) {
                "PAID" -> R.color.chip_completed_bg to R.color.chip_completed_text
                "PARTIAL" -> R.color.chip_pending_bg to R.color.chip_pending_text
                "REFUNDED" -> R.color.status_refunded_bg to R.color.status_refunded_text
                "FAILED" -> R.color.chip_cancelled_bg to R.color.chip_cancelled_text
                else -> R.color.chip_pending_bg to R.color.chip_pending_text
            }
            binding.tvStatus.backgroundTintList = ContextCompat.getColorStateList(context, background)
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, textColor))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PatientPaymentRow>() {
        override fun areItemsTheSame(oldItem: PatientPaymentRow, newItem: PatientPaymentRow): Boolean =
            oldItem.paymentId == newItem.paymentId && oldItem.appointmentId == newItem.appointmentId

        override fun areContentsTheSame(oldItem: PatientPaymentRow, newItem: PatientPaymentRow): Boolean = oldItem == newItem
    }
}
