package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemMedicalRecordFileBinding
import com.medbuddy.dto.MedicalRecordFileResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MedicalRecordFileAdapter(
    private val onOpenClick: (MedicalRecordFileResponse) -> Unit,
) : ListAdapter<MedicalRecordFileResponse, MedicalRecordFileAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicalRecordFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMedicalRecordFileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MedicalRecordFileResponse) {
            binding.tvFileName.text = item.fileName
            binding.btnOpen.setOnClickListener { onOpenClick(item) }
            binding.root.setOnClickListener { onOpenClick(item) }
        }
    }

    private fun formatDate(value: String): String {
        return runCatching {
            val parsed = LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }.getOrDefault(value)
    }

    private class DiffCallback : DiffUtil.ItemCallback<MedicalRecordFileResponse>() {
        override fun areItemsTheSame(oldItem: MedicalRecordFileResponse, newItem: MedicalRecordFileResponse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MedicalRecordFileResponse, newItem: MedicalRecordFileResponse): Boolean = oldItem == newItem
    }
}
