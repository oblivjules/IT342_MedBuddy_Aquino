package com.medbuddy.ui.fragments.patient

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medbuddy.R
import com.medbuddy.databinding.ItemCalendarDayBinding
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class CalendarDayAdapter(
    private val onDayClick: (LocalDate) -> Unit,
) : ListAdapter<LocalDate, CalendarDayAdapter.ViewHolder>(DiffCallback()) {

    var selectedDate: LocalDate? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), getItem(position) == selectedDate)
    }

    inner class ViewHolder(
        private val binding: ItemCalendarDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(date: LocalDate, selected: Boolean) {
            binding.tvWeekday.text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2)
            binding.tvDay.text = date.dayOfMonth.toString()

            val context = itemView.context
            val background = if (selected) R.color.primary else R.color.surface
            val textColor = if (selected) R.color.white else R.color.text_primary
            binding.cardDay.setCardBackgroundColor(ContextCompat.getColor(context, background))
            binding.tvWeekday.setTextColor(ContextCompat.getColor(context, textColor))
            binding.tvDay.setTextColor(ContextCompat.getColor(context, textColor))
            binding.root.setOnClickListener { onDayClick(date) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<LocalDate>() {
        override fun areItemsTheSame(oldItem: LocalDate, newItem: LocalDate): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: LocalDate, newItem: LocalDate): Boolean = oldItem == newItem
    }
}
