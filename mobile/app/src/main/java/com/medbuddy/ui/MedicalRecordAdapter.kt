package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemMedicalRecordBinding
import com.medbuddy.dto.MedicalRecordResponse
import java.text.SimpleDateFormat
import java.util.Locale

class MedicalRecordAdapter(
    private val onItemClick: (MedicalRecordResponse) -> Unit
) : ListAdapter<MedicalRecordResponse, MedicalRecordAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicalRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemMedicalRecordBinding,
        private val onItemClick: (MedicalRecordResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: MedicalRecordResponse) {
            binding.tvDiagnosis.text = "Diagnosis: ${record.diagnosis.take(50)}..."
            val prescriptionSummary = listOfNotNull(
                record.medicineName,
                record.prescriptionDetails
            ).firstOrNull { !it.isNullOrBlank() } ?: "N/A"
            binding.tvPrescription.text = if (prescriptionSummary == "N/A") {
                "Prescription: N/A"
            } else {
                "Prescription: ${prescriptionSummary.take(50)}..."
            }
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = try {
                dateFormat.format(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(record.uploadedAt.orEmpty()) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                record.uploadedAt.orEmpty()
            }
            binding.tvDate.text = date

            binding.root.setOnClickListener { onItemClick(record) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MedicalRecordResponse>() {
        override fun areItemsTheSame(
            oldItem: MedicalRecordResponse,
            newItem: MedicalRecordResponse
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: MedicalRecordResponse,
            newItem: MedicalRecordResponse
        ) = oldItem == newItem
    }
}
