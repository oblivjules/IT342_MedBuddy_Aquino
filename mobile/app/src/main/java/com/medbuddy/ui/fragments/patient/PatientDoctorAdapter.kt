package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemPatientDoctorBinding
import com.medbuddy.dto.DoctorDto
import kotlin.math.roundToInt

class PatientDoctorAdapter(
    private val onCardClick: (DoctorDto) -> Unit,
    private val onBookClick: (DoctorDto) -> Unit,
) : ListAdapter<DoctorDto, PatientDoctorAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientDoctorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPatientDoctorBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DoctorDto) {
            val name = listOf(item.firstName, item.lastName).filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { item.email ?: "Doctor" }
            val specializations = item.specializations?.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: item.specialization
                ?: "General Practice"
            val rating = item.averageRating ?: 0.0
            val filledStars = rating.roundToInt().coerceIn(0, 5)

            binding.tvAvatar.text = listOfNotNull(item.firstName, item.lastName)
                .mapNotNull { it.trim().firstOrNull()?.toString() }
                .joinToString("")
                .uppercase()
                .ifBlank { "DR" }
            binding.tvDoctorName.text = name
            binding.tvSpecialization.text = specializations
            binding.tvRating.text = if (rating > 0) String.format("%.1f", rating) else "No ratings yet"
            binding.ratingBar.rating = filledStars.toFloat()

            binding.root.setOnClickListener { onCardClick(item) }
            binding.btnBook.setOnClickListener { onBookClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DoctorDto>() {
        override fun areItemsTheSame(oldItem: DoctorDto, newItem: DoctorDto): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DoctorDto, newItem: DoctorDto): Boolean = oldItem == newItem
    }
}
