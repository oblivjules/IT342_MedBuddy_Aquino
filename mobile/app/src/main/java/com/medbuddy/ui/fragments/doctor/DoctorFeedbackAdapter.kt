package com.medbuddy.ui.fragments.doctor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemDoctorFeedbackReceivedBinding

data class DoctorFeedbackUiModel(
    val id: Long,
    val patientName: String,
    val appointmentDate: String,
    val rating: Int,
    val comment: String,
)

class DoctorFeedbackAdapter : ListAdapter<DoctorFeedbackUiModel, DoctorFeedbackAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDoctorFeedbackReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemDoctorFeedbackReceivedBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DoctorFeedbackUiModel) {
            binding.tvPatientName.text = item.patientName
            binding.tvAppointmentDate.text = item.appointmentDate
            binding.ratingBar.rating = item.rating.toFloat()
            binding.tvRating.text = item.rating.toString()
            binding.tvComment.text = item.comment.ifBlank { "No comment provided." }
            binding.tvComment.visibility = View.VISIBLE
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DoctorFeedbackUiModel>() {
        override fun areItemsTheSame(oldItem: DoctorFeedbackUiModel, newItem: DoctorFeedbackUiModel): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DoctorFeedbackUiModel, newItem: DoctorFeedbackUiModel): Boolean = oldItem == newItem
    }
}
