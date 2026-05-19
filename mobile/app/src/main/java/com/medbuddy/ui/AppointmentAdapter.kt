package com.medbuddy.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.medbuddy.R
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.ItemAppointmentBinding
import com.medbuddy.dto.AppointmentResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AppointmentAdapter(
    private val role: String,
    private val onStatusUpdate: (AppointmentResponse, String) -> Unit
) : ListAdapter<AppointmentResponse, AppointmentAdapter.AppointmentViewHolder>(DiffCallback()) {

    private var onItemClickListener: ((AppointmentResponse) -> Unit)? = null

    fun setOnItemClickListener(listener: (AppointmentResponse) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun getItemCount(): Int = super.getItemCount()

    inner class AppointmentViewHolder(
        private val binding: ItemAppointmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentResponse, position: Int) {
            val isDoctor = role == "DOCTOR"
            val fullName = if (isDoctor) {
                "${item.patient.firstName} ${item.patient.lastName}"
            } else {
                "Dr. ${item.doctor.firstName} ${item.doctor.lastName}"
            }.trim()
            val subtitle = if (isDoctor) {
                "Patient"
            } else {
                item.doctor.specialization
                    ?: item.doctor.specializations?.firstOrNull()
                    ?: "General Practice"
            }

            val initials = fullName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }

            val parsedDate = runCatching {
                LocalDateTime.parse(item.dateTime, DateTimeFormatter.ISO_DATE_TIME)
            }.getOrNull()

            val timeLabel = parsedDate?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
                ?: item.dateTime
            val dateLabel = parsedDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
                ?: item.dateTime.replace("T", " ")

            binding.tvAvatar.text = initials.ifBlank { "MB" }
            binding.tvTitle.text = fullName
            binding.tvSubtitle.text = subtitle
            binding.tvTime.text = timeLabel
            binding.tvDateTime.text = dateLabel
            binding.tvNotes.text = "Reason for visit: ${item.notes ?: "Not provided"}"

            if (isDoctor && position == 0 && item.status.uppercase(Locale.getDefault()) in setOf("PENDING", "CONFIRMED")) {
                binding.tvStatus.text = "Next"
                binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.primary_soft))
                binding.tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_dark))
            } else {
                binding.tvStatus.text = item.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                styleStatusChip(item.status)
            }

            // Add item click listener
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(item)
            }

            bindActions(item, isDoctor)
        }

        private fun bindActions(item: AppointmentResponse, isDoctor: Boolean) {
            val status = AppointmentStatus.normalize(item.status)
            binding.btnPrimaryAction.visibility = View.GONE
            binding.btnSecondaryAction.visibility = View.GONE
            binding.btnTertiaryAction.visibility = View.GONE

            if (isDoctor) {
                when (status) {
                    AppointmentStatus.PENDING -> {
                        binding.btnPrimaryAction.visibility = View.VISIBLE
                        binding.btnSecondaryAction.visibility = View.VISIBLE
                        binding.btnPrimaryAction.text = "Confirm"
                        binding.btnSecondaryAction.text = "Cancel"
                        binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CONFIRMED) }
                        binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CANCELLED) }
                    }
                    AppointmentStatus.CONFIRMED -> {
                        binding.btnPrimaryAction.visibility = View.VISIBLE
                        binding.btnSecondaryAction.visibility = View.VISIBLE
                        binding.btnPrimaryAction.text = "Complete"
                        binding.btnSecondaryAction.text = "Cancel"
                        binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.COMPLETED) }
                        binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CANCELLED) }
                    }
                }
            } else {
                when (status) {
                    AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED -> {
                        binding.btnPrimaryAction.visibility = View.VISIBLE
                        binding.btnSecondaryAction.visibility = View.GONE
                        binding.btnPrimaryAction.text = "Cancel"
                        binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CANCELLED) }
                    }
                    AppointmentStatus.COMPLETED -> {
                        binding.btnPrimaryAction.visibility = View.VISIBLE
                        binding.btnPrimaryAction.text = "View Record"
                        binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, "VIEW_RECORD") }
                    }
                }
            }
        }

        private fun styleStatusChip(status: String) {
            val context = itemView.context
            val (bg, text) = when (AppointmentStatus.normalize(status)) {
                AppointmentStatus.CONFIRMED -> R.color.chip_confirmed_bg to R.color.chip_confirmed_text
                AppointmentStatus.COMPLETED -> R.color.chip_completed_bg to R.color.chip_completed_text
                AppointmentStatus.PENDING -> R.color.chip_pending_bg to R.color.chip_pending_text
                AppointmentStatus.CANCELLED -> R.color.chip_cancelled_bg to R.color.chip_cancelled_text
                else -> R.color.chip_booked_bg to R.color.chip_booked_text
            }
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, text))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AppointmentResponse>() {
        override fun areItemsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse): Boolean {
            return oldItem == newItem
        }
    }
}

