package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemMedicalRecordFileBinding
import com.medbuddy.dto.MedicalRecordFileResponse
import java.text.SimpleDateFormat
import java.util.Locale

class MedicalRecordFileAdapter(
    private val onOpenClick: (MedicalRecordFileResponse) -> Unit
) : ListAdapter<MedicalRecordFileResponse, MedicalRecordFileAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicalRecordFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onOpenClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemMedicalRecordFileBinding,
        private val onOpenClick: (MedicalRecordFileResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: MedicalRecordFileResponse) {
            binding.tvFileName.text = file.fileName
            binding.tvUploadedBy.text = "By: ${file.uploadedBy}"

            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val date = try {
                dateFormat.format(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(file.uploadedAt) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                file.uploadedAt
            }
            binding.tvUploadedAt.text = date

            binding.btnOpen.setOnClickListener { onOpenClick(file) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MedicalRecordFileResponse>() {
        override fun areItemsTheSame(
            oldItem: MedicalRecordFileResponse,
            newItem: MedicalRecordFileResponse
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: MedicalRecordFileResponse,
            newItem: MedicalRecordFileResponse
        ) = oldItem == newItem
    }
}
