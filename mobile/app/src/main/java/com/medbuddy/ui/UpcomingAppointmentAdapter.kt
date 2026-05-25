package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.R
import com.medbuddy.databinding.ItemUpcomingAppointmentBinding
import com.medbuddy.dto.AppointmentSummaryDto
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class UpcomingAppointmentAdapter :
    ListAdapter<AppointmentSummaryDto, UpcomingAppointmentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUpcomingAppointmentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemUpcomingAppointmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentSummaryDto) {
            val initials = item.patientName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }

            binding.tvAvatar.text = initials.ifBlank { "MB" }
            binding.tvAvatar.backgroundTintList = ContextCompat.getColorStateList(
                binding.root.context,
                R.color.text_muted
            )
            binding.tvPatientName.text = item.patientName
            binding.tvReason.text = item.reasonForVisit
            binding.tvTime.text = formatAppointmentTime(item.appointmentTime)
            binding.tvDate.text = formatAppointmentDate(item.appointmentTime)
        }

        private fun formatAppointmentTime(value: String): String {
            val locale = Locale.getDefault()
            val isoDateTime = runCatching { LocalDateTime.parse(value) }.getOrNull()
            if (isoDateTime != null) {
                return isoDateTime.format(DateTimeFormatter.ofPattern("hh:mm a", locale))
            }
            val isoTime = runCatching { LocalTime.parse(value) }.getOrNull()
            if (isoTime != null) {
                return isoTime.format(DateTimeFormatter.ofPattern("hh:mm a", locale))
            }
            return value
        }

        private fun formatAppointmentDate(value: String): String {
            val locale = Locale.getDefault()
            val isoDateTime = runCatching { LocalDateTime.parse(value) }.getOrNull()
            if (isoDateTime != null) {
                return isoDateTime.format(DateTimeFormatter.ofPattern("MMM d", locale))
            }
            return ""
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AppointmentSummaryDto>() {
        override fun areItemsTheSame(oldItem: AppointmentSummaryDto, newItem: AppointmentSummaryDto): Boolean {
            return oldItem.appointmentId != null && oldItem.appointmentId == newItem.appointmentId
                    || oldItem.patientName == newItem.patientName && oldItem.appointmentTime == newItem.appointmentTime
        }

        override fun areContentsTheSame(oldItem: AppointmentSummaryDto, newItem: AppointmentSummaryDto): Boolean {
            return oldItem == newItem
        }
    }
}