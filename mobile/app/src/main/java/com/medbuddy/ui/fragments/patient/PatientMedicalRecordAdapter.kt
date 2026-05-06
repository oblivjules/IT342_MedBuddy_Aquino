package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemPatientRecordBinding
import com.medbuddy.dto.MedicalRecordResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PatientMedicalRecordAdapter(
    private val onItemClick: (MedicalRecordResponse) -> Unit,
) : ListAdapter<MedicalRecordResponse, PatientMedicalRecordAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPatientRecordBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MedicalRecordResponse) {
            binding.tvTitle.text = item.diagnosis.take(48)
            binding.tvType.text = item.typeLabel()
            binding.tvDoctor.text = item.doctorName.orEmpty().ifBlank { "Doctor" }
            binding.tvDate.text = item.dateLabel()
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private fun MedicalRecordResponse.typeLabel(): String {
        return type?.takeIf { it.isNotBlank() } ?: if (!prescriptionDetails.isNullOrBlank()) "Prescription" else "Consultation"
    }

    private fun MedicalRecordResponse.dateLabel(): String {
        val value = formattedDate ?: uploadedAt ?: createdAt ?: ""
        return runCatching {
            val parsed = LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }.getOrDefault(value)
    }

    private class DiffCallback : DiffUtil.ItemCallback<MedicalRecordResponse>() {
        override fun areItemsTheSame(oldItem: MedicalRecordResponse, newItem: MedicalRecordResponse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MedicalRecordResponse, newItem: MedicalRecordResponse): Boolean = oldItem == newItem
    }
}
