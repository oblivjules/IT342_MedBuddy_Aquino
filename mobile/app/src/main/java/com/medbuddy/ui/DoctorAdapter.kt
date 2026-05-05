package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.medbuddy.databinding.ItemDoctorBinding
import com.medbuddy.dto.DoctorDto
import java.util.Locale

class DoctorAdapter(
    private val onBookClick: (DoctorDto) -> Unit
) : ListAdapter<DoctorDto, DoctorAdapter.DoctorViewHolder>(DiffCallback()) {

    fun submitList(items: List<DoctorDto>) {
        super.submitList(items)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val binding = ItemDoctorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DoctorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemCount(): Int = super.getItemCount()

    inner class DoctorViewHolder(
        private val binding: ItemDoctorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DoctorDto) {
            binding.tvDoctorName.text = "Dr. ${item.firstName} ${item.lastName}"
            val specializationText = when {
                !item.specializations.isNullOrEmpty() -> item.specializations.joinToString(", ")
                !item.specialization.isNullOrBlank() -> item.specialization
                else -> "General Practice"
            }
            binding.tvSpecialization.text = specializationText
            val initials = listOf(item.firstName, item.lastName)
                .mapNotNull { it.firstOrNull()?.toString() }
                .joinToString("")
                .uppercase(Locale.getDefault())
            binding.tvAvatar.text = initials.ifBlank { "DR" }
            binding.tvEmail.text = "★ 4.8 (127)"
            binding.btnBook.setOnClickListener { onBookClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DoctorDto>() {
        override fun areItemsTheSame(oldItem: DoctorDto, newItem: DoctorDto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DoctorDto, newItem: DoctorDto): Boolean {
            return oldItem == newItem
        }
    }
}

