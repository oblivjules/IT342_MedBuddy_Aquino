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
import com.medbuddy.constants.AppConstants
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

    private var onDetailsClick: ((AppointmentResponse) -> Unit)? = null

    fun setOnDetailsClickListener(listener: (AppointmentResponse) -> Unit) {
        onDetailsClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppointmentViewHolder(
        private val binding: ItemAppointmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentResponse) {
            val isDoctor = role == AppConstants.Role.DOCTOR
            val fullName = if (isDoctor) {
                "${item.patient.firstName} ${item.patient.lastName}"
            } else {
                "Dr. ${item.doctor.firstName} ${item.doctor.lastName}"
            }.trim()
            val subtitle = if (isDoctor) "Patient" else {
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

            val timeLabel = parsedDate?.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())) ?: item.dateTime
            val dateLabel = parsedDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())) ?: item.dateTime.replace("T", " ")

            binding.tvAvatar.text = initials.ifBlank { "MB" }
            binding.tvTitle.text = fullName
            binding.tvSubtitle.text = subtitle
            binding.tvTime.text = timeLabel
            binding.tvDateTime.text = dateLabel
            binding.tvNotes.text = "Reason: ${item.notes?.takeIf { it.isNotBlank() } ?: "Not provided"}"

            val displayStatus = when (AppointmentStatus.normalize(item.status)) {
                AppointmentStatus.CONFIRMED -> "Approved"
                AppointmentStatus.CANCELLED -> "Rejected"
                else -> item.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            binding.tvStatus.text = displayStatus
            styleStatusChip(item.status)

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
                        binding.btnTertiaryAction.visibility = View.VISIBLE
                        binding.btnPrimaryAction.text = "Approve"
                        binding.btnSecondaryAction.text = "Reject"
                        binding.btnTertiaryAction.text = "Details"
                        binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CONFIRMED) }
                        binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CANCELLED) }
                        binding.btnTertiaryAction.setOnClickListener { onDetailsClick?.invoke(item) }
                    }
                    AppointmentStatus.CONFIRMED -> {
                        val today = java.time.LocalDate.now()
                        val aptDate = runCatching {
                            LocalDateTime.parse(item.dateTime, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
                        }.getOrNull()
                        val dayReached = aptDate != null && !aptDate.isAfter(today)

                        if (dayReached) {
                            binding.btnPrimaryAction.visibility = View.VISIBLE
                            binding.btnPrimaryAction.text = "Complete"
                            binding.btnPrimaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.COMPLETED) }
                        }
                        binding.btnSecondaryAction.visibility = View.VISIBLE
                        binding.btnTertiaryAction.visibility = View.VISIBLE
                        binding.btnSecondaryAction.text = "Reject"
                        binding.btnTertiaryAction.text = "Details"
                        binding.btnSecondaryAction.setOnClickListener { onStatusUpdate(item, AppointmentStatus.CANCELLED) }
                        binding.btnTertiaryAction.setOnClickListener { onDetailsClick?.invoke(item) }
                    }
                    AppointmentStatus.COMPLETED -> {
                        binding.btnTertiaryAction.visibility = View.VISIBLE
                        binding.btnTertiaryAction.text = "Details"
                        binding.btnTertiaryAction.setOnClickListener { onDetailsClick?.invoke(item) }
                    }
                }
            } else {
                when (status) {
                    AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED -> {
                        binding.btnPrimaryAction.visibility = View.VISIBLE
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
        override fun areItemsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse) = oldItem == newItem
    }
}
