package com.medbuddy.ui.fragments.doctor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentScheduleBinding
import com.medbuddy.dto.DoctorAvailabilityResponse
import com.medbuddy.dto.TemplateRequestDto
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.ScheduleRepository
import com.medbuddy.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale

private data class DayRow(
    val dayIndex: Int,
    val dayName: String,
    val switchView: SwitchCompat,
    val timePickersLayout: LinearLayout,
    val unavailableBadge: TextView,
    val startButton: MaterialButton,
    val endButton: MaterialButton,
    var startTime: String = "09:00",
    var endTime: String = "17:00"
)

private data class ExceptionEntry(
    val date: String,
    val status: String,
    val startTime: String,
    val endTime: String
)

class ScheduleFragment : Fragment() {

    private lateinit var binding: FragmentScheduleBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var viewModel: ScheduleViewModel
    private var doctorId: Long = -1

    private val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val dayRows = mutableListOf<DayRow>()
    private val exceptions = mutableListOf<ExceptionEntry>()
    private var savedExceptionDates = mutableSetOf<String>()

    // New exception form state
    private var newExceptionDate: String = ""
    private var newExceptionStatus: String = "UNAVAILABLE"
    private var newExceptionStart: String = "09:00"
    private var newExceptionEnd: String = "17:00"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentScheduleBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = ScheduleRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(this, ScheduleViewModel.factory(repository))[ScheduleViewModel::class.java]

        doctorId = getDoctorIdFromSession()
        buildTemplateDayRows()
        setupExceptionForm()
        setupButtons()
        loadData()
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun buildTemplateDayRows() {
        dayRows.clear()
        binding.llTemplateDays.removeAllViews()

        dayNames.forEachIndexed { index, name ->
            val rowView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_day_schedule_row, binding.llTemplateDays, false)

            val switchView = rowView.findViewById<SwitchCompat>(R.id.switchEnabled)
            val tvDayName = rowView.findViewById<TextView>(R.id.tvDayName)
            val llTimePickers = rowView.findViewById<LinearLayout>(R.id.llTimePickers)
            val tvUnavailable = rowView.findViewById<TextView>(R.id.tvUnavailable)
            val btnStart = rowView.findViewById<MaterialButton>(R.id.btnStartTime)
            val btnEnd = rowView.findViewById<MaterialButton>(R.id.btnEndTime)

            // Weekends off by default
            val defaultEnabled = index < 5
            switchView.isChecked = defaultEnabled
            tvDayName.text = name
            llTimePickers.visibility = if (defaultEnabled) View.VISIBLE else View.GONE
            tvUnavailable.visibility = if (defaultEnabled) View.GONE else View.VISIBLE

            val row = DayRow(
                dayIndex = index,
                dayName = name,
                switchView = switchView,
                timePickersLayout = llTimePickers,
                unavailableBadge = tvUnavailable,
                startButton = btnStart,
                endButton = btnEnd
            )
            btnStart.text = toDisplay12h(row.startTime)
            btnEnd.text = toDisplay12h(row.endTime)
            dayRows.add(row)

            switchView.setOnCheckedChangeListener { _, isChecked ->
                llTimePickers.visibility = if (isChecked) View.VISIBLE else View.GONE
                tvUnavailable.visibility = if (isChecked) View.GONE else View.VISIBLE
                updateStatCards()
            }

            btnStart.setOnClickListener {
                showTimePicker(row.startTime) { time ->
                    row.startTime = time
                    btnStart.text = toDisplay12h(time)
                    updateStatCards()
                }
            }

            btnEnd.setOnClickListener {
                showTimePicker(row.endTime) { time ->
                    row.endTime = time
                    btnEnd.text = toDisplay12h(time)
                    updateStatCards()
                }
            }

            // Add divider between rows
            if (index < dayNames.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(requireContext().getColor(R.color.border))
                }
                binding.llTemplateDays.addView(rowView)
                binding.llTemplateDays.addView(divider)
            } else {
                binding.llTemplateDays.addView(rowView)
            }
        }
    }

    private fun setupExceptionForm() {
        binding.chipDayOff.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                newExceptionStatus = "UNAVAILABLE"
                binding.chipCustomHours.isChecked = false
                binding.llCustomHours.visibility = View.GONE
            }
        }
        binding.chipCustomHours.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                newExceptionStatus = "AVAILABLE"
                binding.chipDayOff.isChecked = false
                binding.llCustomHours.visibility = View.VISIBLE
            }
        }

        binding.btnPickDate.setOnClickListener { showDatePicker() }

        binding.btnExceptionStart.setOnClickListener {
            showTimePicker(newExceptionStart) { time ->
                newExceptionStart = time
                binding.btnExceptionStart.text = toDisplay12h(time)
            }
        }

        binding.btnExceptionEnd.setOnClickListener {
            showTimePicker(newExceptionEnd) { time ->
                newExceptionEnd = time
                binding.btnExceptionEnd.text = toDisplay12h(time)
            }
        }

        binding.btnAddException.setOnClickListener { addException() }
    }

    private fun setupButtons() {
        binding.btnSaveTemplate.setOnClickListener { saveTemplate() }
        binding.btnSaveExceptions.setOnClickListener { saveExceptions() }
    }

    // ── Data loading ──────────────────────────────────────────────────────

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvLoadError.visibility = View.GONE

        viewModel.loadTemplateAndExceptions(
            doctorId = doctorId,
            onResult = { template, availabilityData ->
                if (!isAdded) return@loadTemplateAndExceptions
                binding.progressBar.visibility = View.GONE
                applyTemplate(template)
                applyExceptions(availabilityData)
                updateStatCards()
            },
            onError = { message ->
                if (!isAdded) return@loadTemplateAndExceptions
                binding.progressBar.visibility = View.GONE
                binding.tvLoadError.text = message
                binding.tvLoadError.visibility = View.VISIBLE
                updateStatCards()
            }
        )
    }

    private fun applyTemplate(template: List<TemplateRequestDto>) {
        val byDay = template.associateBy { it.dayOfWeek }
        dayRows.forEach { row ->
            val saved = byDay[row.dayIndex]
            if (saved != null) {
                row.switchView.isChecked = true
                row.timePickersLayout.visibility = View.VISIBLE
                row.unavailableBadge.visibility = View.GONE

                val start = saved.startTime?.take(5) ?: "09:00"
                val end = saved.endTime?.take(5) ?: "17:00"
                row.startTime = start
                row.endTime = end
                row.startButton.text = toDisplay12h(start)
                row.endButton.text = toDisplay12h(end)
            } else {
                row.switchView.isChecked = false
                row.timePickersLayout.visibility = View.GONE
                row.unavailableBadge.visibility = View.VISIBLE
            }
        }
    }

    private fun applyExceptions(data: List<DoctorAvailabilityResponse>) {
        exceptions.clear()
        savedExceptionDates.clear()

        data.filter { it.status == "AVAILABLE" || it.status == "UNAVAILABLE" }
            .sortedBy { it.availableDate }
            .forEach { item ->
                exceptions.add(
                    ExceptionEntry(
                        date = item.availableDate,
                        status = item.status,
                        startTime = item.startTime.take(5),
                        endTime = item.endTime.take(5)
                    )
                )
                savedExceptionDates.add(item.availableDate)
            }

        rebuildExceptionList()
    }

    // ── Template save ─────────────────────────────────────────────────────

    private fun saveTemplate() {
        binding.tvTemplateError.visibility = View.GONE
        binding.tvTemplateSuccess.visibility = View.GONE

        // Validate end > start for enabled days
        for (row in dayRows) {
            if (!row.switchView.isChecked) continue
            if (!isEndAfterStart(row.startTime, row.endTime)) {
                showTemplateError("${row.dayName}: end time must be at least 30 minutes after start (last slot is 30 min before closing).")
                return
            }
        }

        binding.btnSaveTemplate.isEnabled = false
        binding.btnSaveTemplate.text = "Saving..."

        val activeDays = dayRows
            .filter { it.switchView.isChecked }
            .map { row ->
                TemplateRequestDto(
                    dayOfWeek = row.dayIndex,
                    startTime = toApiTime(row.startTime),
                    endTime = toApiTime(row.endTime)
                )
            }

        viewModel.saveTemplate(
            days = activeDays,
            onSuccess = {
                if (!isAdded) return@saveTemplate
                binding.btnSaveTemplate.isEnabled = true
                binding.btnSaveTemplate.text = "Save Template"
                binding.tvTemplateSuccess.text = "Weekly template saved. Slot regeneration running in background."
                binding.tvTemplateSuccess.visibility = View.VISIBLE
                binding.root.postDelayed({ binding.tvTemplateSuccess.visibility = View.GONE }, 5000)
                updateStatCards()
            },
            onError = { message ->
                if (!isAdded) return@saveTemplate
                binding.btnSaveTemplate.isEnabled = true
                binding.btnSaveTemplate.text = "Save Template"
                showTemplateError(message)
            }
        )
    }

    // ── Exception management ──────────────────────────────────────────────

    private fun addException() {
        binding.tvExceptionError.visibility = View.GONE

        if (newExceptionDate.isBlank()) {
            showExceptionError("Please pick a date for the exception.")
            return
        }

        val today = LocalDate.now().toString()
        if (newExceptionDate < today) {
            showExceptionError("You cannot add an exception in the past.")
            return
        }

        if (newExceptionStatus == "AVAILABLE" && !isEndAfterStart(newExceptionStart, newExceptionEnd)) {
            showExceptionError("End time must be at least 30 minutes after start (last slot is 30 min before closing).")
            return
        }

        // Remove existing for same date
        exceptions.removeAll { it.date == newExceptionDate }

        exceptions.add(
            ExceptionEntry(
                date = newExceptionDate,
                status = newExceptionStatus,
                startTime = newExceptionStart,
                endTime = newExceptionEnd
            )
        )
        exceptions.sortBy { it.date }

        // Reset form
        newExceptionDate = ""
        newExceptionStatus = "UNAVAILABLE"
        newExceptionStart = "09:00"
        newExceptionEnd = "17:00"
        binding.btnPickDate.text = "Pick date"
        binding.chipDayOff.isChecked = true
        binding.chipCustomHours.isChecked = false
        binding.llCustomHours.visibility = View.GONE
        binding.btnExceptionStart.text = toDisplay12h(newExceptionStart)
        binding.btnExceptionEnd.text = toDisplay12h(newExceptionEnd)

        rebuildExceptionList()
        updateStatCards()
    }

    private fun removeException(date: String) {
        exceptions.removeAll { it.date == date }
        rebuildExceptionList()
        updateStatCards()
    }

    private fun rebuildExceptionList() {
        binding.llExceptions.removeAllViews()

        if (exceptions.isEmpty()) {
            binding.tvNoExceptions.visibility = View.VISIBLE
            return
        }
        binding.tvNoExceptions.visibility = View.GONE

        exceptions.forEach { entry ->
            val rowView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_exception_row, binding.llExceptions, false)

            rowView.findViewById<TextView>(R.id.tvExceptionDate).text = entry.date
            rowView.findViewById<TextView>(R.id.tvExceptionDetail).text = when (entry.status) {
                "UNAVAILABLE" -> "Day Off"
                else -> "${toDisplay12h(entry.startTime)} – ${toDisplay12h(entry.endTime)}"
            }
            val statusChip = rowView.findViewById<TextView>(R.id.tvExceptionStatus)
            statusChip.text = if (entry.status == "UNAVAILABLE") "Day Off" else "Custom"
            statusChip.setTextColor(
                requireContext().getColor(
                    if (entry.status == "UNAVAILABLE") R.color.chip_cancelled_text
                    else R.color.chip_confirmed_text
                )
            )

            rowView.findViewById<MaterialButton>(R.id.btnDeleteException).setOnClickListener {
                removeException(entry.date)
            }

            // Divider
            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(requireContext().getColor(R.color.border))
            }
            binding.llExceptions.addView(rowView)
            binding.llExceptions.addView(divider)
        }
    }

    private fun saveExceptions() {
        binding.tvExceptionError.visibility = View.GONE
        binding.tvExceptionSuccess.visibility = View.GONE

        if (exceptions.isEmpty()) {
            showExceptionError("No exceptions to save.")
            return
        }

        binding.btnSaveExceptions.isEnabled = false
        binding.btnSaveExceptions.text = "Saving..."

        val currentDates = exceptions.map { it.date }.toSet()
        val toDelete = savedExceptionDates.filter { !currentDates.contains(it) }
        val toSaveAvailable = exceptions
            .filter { it.status == "AVAILABLE" }
            .map { Triple(it.date, toApiTime(it.startTime), toApiTime(it.endTime)) }
        val toSaveUnavailable = exceptions
            .filter { it.status == "UNAVAILABLE" }
            .map { it.date }

        viewModel.saveExceptions(
            toSave = toSaveAvailable,
            toSaveUnavailable = toSaveUnavailable,
            toDelete = toDelete,
            onSuccess = {
                if (!isAdded) return@saveExceptions
                binding.btnSaveExceptions.isEnabled = true
                binding.btnSaveExceptions.text = "Save Exceptions"
                // Promote current local list to "saved" without wiping it
                savedExceptionDates = exceptions.map { it.date }.toMutableSet()
                binding.tvExceptionSuccess.text = "Exceptions saved successfully."
                binding.tvExceptionSuccess.visibility = View.VISIBLE
                binding.root.postDelayed({ binding.tvExceptionSuccess.visibility = View.GONE }, 5000)
                updateStatCards()
            },
            onError = { message ->
                if (!isAdded) return@saveExceptions
                binding.btnSaveExceptions.isEnabled = true
                binding.btnSaveExceptions.text = "Save Exceptions"
                showExceptionError(message)
            }
        )
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    private fun updateStatCards() {
        val enabledRows = dayRows.filter { it.switchView.isChecked }
        binding.tvStatActiveDays.text = "${enabledRows.size} / 7"
        binding.tvStatExceptions.text = exceptions.size.toString()

        val avgHours = if (enabledRows.isEmpty()) 0
        else {
            val totalMinutes = enabledRows.sumOf { row ->
                minutesBetween(row.startTime, row.endTime).coerceAtLeast(0)
            }
            (totalMinutes / enabledRows.size) / 60
        }
        binding.tvStatAvgHours.text = "${avgHours}h"
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            R.style.MedBuddyDatePickerDialog,
            { _, year, month, day ->
                newExceptionDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                binding.btnPickDate.text = newExceptionDate
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).also { it.datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    private fun showTimePicker(current: String, onSelected: (String) -> Unit) {
        val parts = current.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            requireContext(),
            { _, h, m -> onSelected(String.format(Locale.getDefault(), "%02d:%02d", h, m)) },
            hour,
            minute,
            false
        ).show()
    }

    private fun getDoctorIdFromSession(): Long {
        val userJson = tokenManager.getUserJson().orEmpty()
        return runCatching {
            Gson().fromJson(userJson, UserDto::class.java).let { it.profileId ?: it.id }
        }.getOrDefault(-1)
    }

    private fun toDisplay12h(time: String): String {
        return runCatching {
            val parts = time.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val amPm = if (h < 12) "AM" else "PM"
            val hour12 = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            "%d:%02d %s".format(hour12, m, amPm)
        }.getOrDefault(time)
    }

    private fun toApiTime(display: String): String {
        return if (display.length == 5) "$display:00" else display
    }

    private fun isEndAfterStart(start: String, end: String): Boolean {
        return runCatching {
            LocalTime.parse(end) >= LocalTime.parse(start).plusMinutes(30)
        }.getOrDefault(false)
    }

    private fun minutesBetween(start: String, end: String): Int {
        return runCatching {
            val s = LocalTime.parse(start)
            val e = LocalTime.parse(end)
            e.hour * 60 + e.minute - (s.hour * 60 + s.minute)
        }.getOrDefault(0)
    }

    private fun showTemplateError(message: String) {
        binding.tvTemplateError.text = message
        binding.tvTemplateError.visibility = View.VISIBLE
    }

    private fun showExceptionError(message: String) {
        binding.tvExceptionError.text = message
        binding.tvExceptionError.visibility = View.VISIBLE
    }
}
