package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemDoctorFeedbackBinding
import com.medbuddy.dto.FeedbackResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorFeedbackAdapter : ListAdapter<FeedbackResponse, DoctorFeedbackAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDoctorFeedbackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemDoctorFeedbackBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FeedbackResponse) {
            val patientName = listOfNotNull(item.patient?.firstName, item.patient?.lastName).joinToString(" ").ifBlank {
                item.patient?.email ?: "Patient"
            }
            binding.tvPatientName.text = patientName
            binding.tvRating.text = item.rating.toString()
            binding.ratingBar.rating = item.rating.toFloat()
            val feedbackText = item.comment?.takeIf { it.isNotBlank() }
                ?: item.feedback?.takeIf { it.isNotBlank() }
            if (feedbackText != null) {
                binding.tvFeedback.text = feedbackText
                binding.tvFeedback.visibility = android.view.View.VISIBLE
            } else {
                binding.tvFeedback.visibility = android.view.View.GONE
            }
            binding.tvDate.text = item.createdAt?.let { formatDate(it) } ?: ""
        }
    }

    private fun formatDate(value: String): String {
        return runCatching {
            val parsed = LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            parsed.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
        }.getOrDefault(value)
    }

    private class DiffCallback : DiffUtil.ItemCallback<FeedbackResponse>() {
        override fun areItemsTheSame(oldItem: FeedbackResponse, newItem: FeedbackResponse): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: FeedbackResponse, newItem: FeedbackResponse): Boolean = oldItem == newItem
    }
}
