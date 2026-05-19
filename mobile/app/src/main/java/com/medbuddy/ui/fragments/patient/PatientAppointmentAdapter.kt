package com.medbuddy.ui.fragments.patient

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.R
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.ItemPatientAppointmentBinding
import com.medbuddy.dto.AppointmentResponse
import java.util.Locale

class PatientAppointmentAdapter(
    private val onCancelClick: (AppointmentResponse) -> Unit,
    private val onViewRecordClick: (AppointmentResponse) -> Unit,
    private val onRateClick: (AppointmentResponse) -> Unit,
) : ListAdapter<AppointmentResponse, PatientAppointmentAdapter.ViewHolder>(DiffCallback()) {

    private val feedbackProvidedIds = mutableSetOf<Long>()

    fun updateFeedbackProvidedIds(ids: Set<Long>) {
        feedbackProvidedIds.clear()
        feedbackProvidedIds.addAll(ids)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPatientAppointmentBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentResponse) {
            val fullName = listOf(item.doctor.firstName, item.doctor.lastName).filter { !it.isNullOrBlank() }.joinToString(" ")
                .ifBlank { item.doctor.email ?: "Doctor" }
            val specialization = item.doctor.specializations?.firstOrNull()
                ?: item.doctor.specialization
                ?: "General Practice"
            val initials = listOfNotNull(item.doctor.firstName, item.doctor.lastName)
                .mapNotNull { it.trim().firstOrNull()?.toString() }
                .joinToString("")
                .uppercase(Locale.getDefault())
                .ifBlank { "DR" }
            val status = AppointmentStatus.normalize(item.status)
            val statusLabel = formatStatusLabel(status)

            binding.tvAvatar.text = initials
            binding.tvDoctorName.text = fullName
            binding.tvSpecialization.text = specialization
            binding.tvDateTime.text = formatAppointmentDateTime(item.dateTime)
            binding.tvStatus.text = statusLabel
            styleStatusChip(status)

            binding.btnPrimary.visibility = View.GONE
            binding.btnSecondary.visibility = View.GONE

            when (status) {
                AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED -> {
                    binding.btnPrimary.visibility = View.VISIBLE
                    binding.btnPrimary.text = "Cancel"
                    binding.btnPrimary.setOnClickListener { onCancelClick(item) }
                    binding.btnSecondary.visibility = View.GONE
                }
                AppointmentStatus.COMPLETED -> {
                    binding.btnPrimary.visibility = View.VISIBLE
                    binding.btnPrimary.text = "View Record"
                    binding.btnPrimary.setOnClickListener { onViewRecordClick(item) }
                    if (!feedbackProvidedIds.contains(item.id)) {
                        binding.btnSecondary.visibility = View.VISIBLE
                        binding.btnSecondary.text = "Rate"
                        binding.btnSecondary.setOnClickListener { onRateClick(item) }
                    }
                }
                else -> Unit
            }
        }

        private fun styleStatusChip(status: String) {
            val context = itemView.context
            val (background, textColor) = when (status) {
                AppointmentStatus.CONFIRMED -> R.color.chip_confirmed_bg to R.color.chip_confirmed_text
                AppointmentStatus.COMPLETED -> R.color.chip_completed_bg to R.color.chip_completed_text
                AppointmentStatus.PENDING -> R.color.chip_pending_bg to R.color.chip_pending_text
                AppointmentStatus.CANCELLED -> R.color.chip_cancelled_bg to R.color.chip_cancelled_text
                else -> R.color.chip_pending_bg to R.color.chip_pending_text
            }
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, background))
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, textColor))
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<AppointmentResponse>() {
        override fun areItemsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppointmentResponse, newItem: AppointmentResponse): Boolean = oldItem == newItem
    }
}
