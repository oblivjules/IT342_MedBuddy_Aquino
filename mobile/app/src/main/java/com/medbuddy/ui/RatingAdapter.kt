package com.medbuddy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.databinding.ItemRatingBinding
import com.medbuddy.dto.RatingResponse
import java.text.SimpleDateFormat
import java.util.Locale

class RatingAdapter : ListAdapter<RatingResponse, RatingAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRatingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemRatingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rating: RatingResponse) {
            binding.tvPatientName.text = rating.patient?.let {
                "${it.firstName} ${it.lastName}"
            } ?: "Anonymous"

            binding.ratingBar.rating = rating.rating.toFloat()
            binding.tvRating.text = rating.rating.toString()

            binding.tvFeedback.text = rating.feedback ?: "No feedback provided"

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = try {
                dateFormat.format(
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(rating.createdAt) ?: System.currentTimeMillis()
                )
            } catch (e: Exception) {
                rating.createdAt
            }
            binding.tvDate.text = date
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<RatingResponse>() {
        override fun areItemsTheSame(
            oldItem: RatingResponse,
            newItem: RatingResponse
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: RatingResponse,
            newItem: RatingResponse
        ) = oldItem == newItem
    }
}
