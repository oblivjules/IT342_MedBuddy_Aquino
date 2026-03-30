package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemDoctorBinding
import com.medbuddy.dto.DoctorDto

class DoctorAdapter(
    private val onBookClick: (DoctorDto) -> Unit
) : RecyclerView.Adapter<DoctorAdapter.DoctorViewHolder>() {

    private val doctors = mutableListOf<DoctorDto>()

    fun submitList(items: List<DoctorDto>) {
        doctors.clear()
        doctors.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DoctorViewHolder {
        val binding = ItemDoctorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DoctorViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DoctorViewHolder, position: Int) {
        holder.bind(doctors[position])
    }

    override fun getItemCount(): Int = doctors.size

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
            binding.tvEmail.text = item.email
            binding.btnBook.setOnClickListener { onBookClick(item) }
        }
    }
}

