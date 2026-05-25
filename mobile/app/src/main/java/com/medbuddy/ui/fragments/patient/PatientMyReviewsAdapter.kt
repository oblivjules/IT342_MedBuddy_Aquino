package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemPatientMyReviewBinding

data class PatientMyReviewUiModel(
    val id: Long,
    val doctorName: String,
    val appointmentDate: String,
    val rating: Int,
    val comment: String,
)

class PatientMyReviewsAdapter(
    private val onDelete: (Long) -> Unit,
) : ListAdapter<PatientMyReviewUiModel, PatientMyReviewsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPatientMyReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemPatientMyReviewBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PatientMyReviewUiModel) {
            binding.tvDoctorName.text = "Dr. ${item.doctorName}"
            binding.tvAppointmentDate.text = item.appointmentDate
            binding.ratingBar.rating = item.rating.toFloat()
            binding.tvRating.text = item.rating.toString()
            binding.tvComment.text = item.comment.ifBlank { "No comment provided." }
            binding.tvComment.visibility = View.VISIBLE
            binding.btnDelete.setOnClickListener { onDelete(item.id) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PatientMyReviewUiModel>() {
        override fun areItemsTheSame(oldItem: PatientMyReviewUiModel, newItem: PatientMyReviewUiModel): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PatientMyReviewUiModel, newItem: PatientMyReviewUiModel): Boolean = oldItem == newItem
    }
}
