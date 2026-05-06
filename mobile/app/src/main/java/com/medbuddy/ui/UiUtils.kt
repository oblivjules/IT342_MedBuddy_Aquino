package com.medbuddy.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.medbuddy.R

/**
 * UI Helper utilities for design system consistency.
 */
object UiUtils {

    /**
     * Set status chip appearance based on status string.
     * Maps statuses to their respective background and text colors.
     */
    fun statusChipAppearance(status: String, chip: Chip) {
        val normalizedStatus = status.trim().uppercase()

        val (bgColorRes, textColorRes) = when (normalizedStatus) {
            "BOOKED", "CONFIRMED" -> R.color.status_booked_bg to R.color.status_booked_text
            "COMPLETED" -> R.color.status_completed_bg to R.color.status_completed_text
            "CANCELLED", "CANCELED" -> R.color.status_cancelled_bg to R.color.status_cancelled_text
            "PENDING" -> R.color.status_pending_bg to R.color.status_pending_text
            "PAID" -> R.color.status_paid_bg to R.color.status_paid_text
            "REFUNDED" -> R.color.status_refunded_bg to R.color.status_refunded_text
            else -> R.color.status_cancelled_bg to R.color.status_cancelled_text
        }

        chip.setChipBackgroundColorResource(bgColorRes)
        chip.setTextColor(ContextCompat.getColor(chip.context, textColorRes))
    }

    /**
     * Set initials avatar on a TextView with circular background.
     * Extracts first letter of each word (max 2 letters).
     * Applies circular drawable background with primary color.
     */
    fun setInitialsAvatar(view: TextView, name: String) {
        val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val initials = parts.take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "?" }

        view.text = initials
        view.setTextColor(Color.WHITE)

        val primaryColor = Color.parseColor("#E91E8C")
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primaryColor)
        }

        view.background = drawable
    }

    /**
     * Set gradient initials avatar (for payment detail screen).
     * Gradient from primary to primary_dark.
     */
    fun setGradientInitialsAvatar(view: TextView, name: String?) {
        if (name.isNullOrBlank()) {
            view.text = "?"
            return
        }
        
        val initials = name.trim()
            .split("\\s+".toRegex())
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
        
        view.text = initials
        view.setTextColor(Color.WHITE)
        
        val context = view.context
        val primaryColor = ContextCompat.getColor(context, R.color.primary)
        val primaryDarkColor = ContextCompat.getColor(context, R.color.primary_dark)
        
        // Create gradient drawable (top to bottom gradient)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(primaryColor, primaryDarkColor)
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        
        view.background = drawable
    }

    /**
     * Format time slot for display (e.g., "9:00 AM - 10:00 AM").
     */
    fun formatTimeSlot(startTime: String, endTime: String): String {
        return try {
            val start = formatTime12Hour(startTime)
            val end = formatTime12Hour(endTime)
            "$start - $end"
        } catch (e: Exception) {
            "$startTime - $endTime"
        }
    }

    /**
     * Convert 24-hour time to 12-hour format with AM/PM.
     */
    private fun formatTime12Hour(time24: String): String {
        return try {
            val parts = time24.split(":")
            var hour = parts[0].toInt()
            val minute = parts[1]
            val ampm = if (hour >= 12) "PM" else "AM"
            if (hour > 12) hour -= 12
            if (hour == 0) hour = 12
            String.format("%02d:%s %s", hour, minute, ampm)
        } catch (e: Exception) {
            time24
        }
    }

    /**
     * Format date string from API to readable format.
     * Input: "2026-05-15" or "2026-05-15T10:30:00"
     * Output: "May 15, 2026"
     */
    fun formatDateReadable(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
        return try {
            val datePart = dateString.substringBefore("T")
            val (year, month, day) = datePart.split("-").map { it.toInt() }
            val monthName = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[month - 1]
            "$monthName $day, $year"
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * Format date and time together.
     * Input: "2026-05-15T10:30:00"
     * Output: "May 15, 2026 at 10:30 AM"
     */
    fun formatDateTimeReadable(dateTimeString: String?): String {
        if (dateTimeString.isNullOrBlank()) return ""
        return try {
            val datePart = dateTimeString.substringBefore("T")
            val timePart = dateTimeString.substringAfter("T").substringBefore(".").take(5)
            val date = formatDateReadable(datePart)
            val time = formatTime12Hour(timePart)
            "$date at $time"
        } catch (e: Exception) {
            dateTimeString
        }
    }

    /**
     * Get day abbreviation from date string.
     * Input: "2026-05-15"
     * Output: "Fri"
     */
    fun getDayAbbreviation(dateString: String?): String {
        if (dateString.isNullOrBlank()) return ""
        return try {
            val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val (year, month, day) = dateString.split("-").map { it.toInt() }
            
            // Zeller's congruence to find day of week
            val adjustedMonth = if (month < 3) month + 12 else month
            val adjustedYear = if (month < 3) year - 1 else year
            val q = day
            val m = adjustedMonth
            val k = adjustedYear % 100
            val j = adjustedYear / 100
            
            val h = (q + ((13 * (m + 1)) / 5) + k + (k / 4) + (j / 4) - (2 * j)) % 7
            val dayIndex = (h + 6) % 7  // Convert to 0=Sunday
            
            dayNames[dayIndex]
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Format currency for payment display.
     */
    fun formatCurrency(amount: Double?): String {
        if (amount == null) return "₱0.00"
        return String.format("₱%.2f", amount)
    }
}
