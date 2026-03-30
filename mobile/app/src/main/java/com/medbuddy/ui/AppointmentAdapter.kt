package com.medbuddy.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.R
import com.medbuddy.databinding.ItemAppointmentBinding
import com.medbuddy.dto.AppointmentResponse

class AppointmentAdapter(
    private val role: String,
    private val onStatusUpdate: (AppointmentResponse, String) -> Unit
) : RecyclerView.Adapter<AppointmentAdapter.AppointmentViewHolder>() {

    private val appointments = mutableListOf<AppointmentResponse>()

    fun submitList(items: List<AppointmentResponse>) {
        appointments.clear()
        appointments.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(appointments[position])
    }

    override fun getItemCount(): Int = appointments.size

    inner class AppointmentViewHolder(
        private val binding: ItemAppointmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentResponse) {
            val isDoctor = role == "DOCTOR"
            val counterpart = if (isDoctor) {
                "${item.patient.firstName} ${item.patient.lastName}"
            } else {
                "Dr. ${item.doctor.firstName} ${item.doctor.lastName}"
            }

            binding.tvTitle.text = counterpart
            binding.tvDateTime.text = item.dateTime.replace("T", " ")
            binding.tvNotes.text = item.notes ?: "No notes"
            binding.tvStatus.text = item.status
            styleStatusChip(item.status)
            bindActions(item, isDoctor)
        }

        private fun bindActions(item: AppointmentResponse, isDoctor: Boolean) {
            val immutable = item.status == "CANCELLED" || item.status == "COMPLETED"
            if (immutable) {
                binding.btnPrimaryAction.visibility = View.GONE
                binding.btnSecondaryAction.visibility = View.GONE
                return
            }

            if (isDoctor) {
                binding.btnPrimaryAction.visibility = View.VISIBLE
                binding.btnSecondaryAction.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = itemView.context.getString(R.string.status_confirmed)
                binding.btnSecondaryAction.text = itemView.context.getString(R.string.status_completed)
                binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, "CONFIRMED") }
                binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, "COMPLETED") }
            } else {
                binding.btnPrimaryAction.visibility = View.GONE
                binding.btnSecondaryAction.visibility = View.VISIBLE
                binding.btnSecondaryAction.text = itemView.context.getString(R.string.status_cancelled)
                binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, "CANCELLED") }
            }
        }

        private fun styleStatusChip(status: String) {
            val context = itemView.context
            val (bg, text) = when (status) {
                "CONFIRMED", "PAID", "COMPLETED" -> R.color.chip_completed_bg to R.color.chip_completed_text
                "PENDING" -> R.color.chip_pending_bg to R.color.chip_pending_text
                "CANCELLED" -> R.color.chip_cancelled_bg to R.color.chip_cancelled_text
                else -> R.color.chip_booked_bg to R.color.chip_booked_text
            }
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, text))
        }
    }
}

