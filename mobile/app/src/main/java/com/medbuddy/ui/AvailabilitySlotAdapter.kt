package com.medbuddy.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.medbuddy.R
import com.medbuddy.databinding.ItemAvailabilitySlotBinding
import com.medbuddy.dto.DoctorAvailabilityResponse
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AvailabilitySlotAdapter(
    private val onDelete: (DoctorAvailabilityResponse) -> Unit
) : ListAdapter<DoctorAvailabilityResponse, AvailabilitySlotAdapter.AvailabilitySlotViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvailabilitySlotViewHolder {
        val binding = ItemAvailabilitySlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AvailabilitySlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvailabilitySlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AvailabilitySlotViewHolder(
        private val binding: ItemAvailabilitySlotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DoctorAvailabilityResponse) {
            val start = formatTime(item.startTime)
            val end = formatTime(item.endTime)
            binding.tvTimeRange.text = "$start - $end"

            val normalized = item.status.uppercase(Locale.getDefault())
            val (chipBg, chipText, label) = when (normalized) {
                "BOOKED" -> Triple(R.color.chip_booked_bg, R.color.chip_booked_text, "Booked")
                "PENDING" -> Triple(R.color.chip_pending_bg, R.color.chip_pending_text, "Pending")
                else -> Triple(R.color.chip_confirmed_bg, R.color.chip_confirmed_text, "Available")
            }

            binding.tvStatus.text = label
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(itemView.context, chipBg)
            )
            binding.tvStatus.setTextColor(ContextCompat.getColor(itemView.context, chipText))
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }

        private fun formatTime(raw: String): String {
            val parseCandidates = listOf("HH:mm:ss", "HH:mm")
            parseCandidates.forEach { pattern ->
                val parsed = runCatching { LocalTime.parse(raw, DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
                if (parsed != null) {
                    return parsed.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
                }
            }
            return raw
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DoctorAvailabilityResponse>() {
        override fun areItemsTheSame(oldItem: DoctorAvailabilityResponse, newItem: DoctorAvailabilityResponse): Boolean {
            return oldItem.doctorId == newItem.doctorId && oldItem.availableDate == newItem.availableDate
        }

        override fun areContentsTheSame(oldItem: DoctorAvailabilityResponse, newItem: DoctorAvailabilityResponse): Boolean {
            return oldItem == newItem
        }
    }
}
