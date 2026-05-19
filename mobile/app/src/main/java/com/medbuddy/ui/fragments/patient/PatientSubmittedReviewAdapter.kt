package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemSubmittedReviewBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SubmittedReviewUiModel(
    val id: Long,
    val doctorName: String,
    val dateLabel: String,
    val rating: Int,
    val comment: String?
)

class PatientSubmittedReviewAdapter :
    ListAdapter<SubmittedReviewUiModel, PatientSubmittedReviewAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubmittedReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemSubmittedReviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubmittedReviewUiModel) {
            binding.tvDoctorName.text = item.doctorName
            binding.tvDate.text = item.dateLabel
            binding.ratingBar.rating = item.rating.toFloat()
            binding.tvRating.text = item.rating.toString()
            val comment = item.comment?.takeIf { it.isNotBlank() }
            if (comment != null) {
                binding.tvFeedback.text = comment
                binding.tvFeedback.visibility = View.VISIBLE
            } else {
                binding.tvFeedback.visibility = View.GONE
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SubmittedReviewUiModel>() {
        override fun areItemsTheSame(oldItem: SubmittedReviewUiModel, newItem: SubmittedReviewUiModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SubmittedReviewUiModel, newItem: SubmittedReviewUiModel) = oldItem == newItem
    }
}
