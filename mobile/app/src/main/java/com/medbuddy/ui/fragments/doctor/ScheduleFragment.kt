package com.medbuddy.ui.fragments.doctor

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentScheduleBinding
import com.medbuddy.databinding.SheetAddTimeSlotBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.ScheduleRepository
import com.medbuddy.ui.AvailabilitySlotAdapter
import com.medbuddy.ui.SessionUi
import com.medbuddy.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleFragment : Fragment() {

    private lateinit var binding: FragmentScheduleBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var adapter: AvailabilitySlotAdapter
    private lateinit var viewModel: ScheduleViewModel
    private var doctorId: Long = -1
    private var selectedDate: LocalDate = LocalDate.now()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentScheduleBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!tokenManager.isLoggedIn()) {
            SessionUi.redirectToLogin(this)
            return
        }

        val repository = ScheduleRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(
            this,
            ScheduleViewModel.factory(repository)
        )[ScheduleViewModel::class.java]

        doctorId = getDoctorIdFromSession()
        setupRecyclerView()
        setupWeekStrip()
        setupSwipeRefresh()
        binding.btnRetry.setOnClickListener { loadAvailability() }
        binding.fabAddSlot.setOnClickListener { showAddSlotBottomSheet() }
        binding.tvMonthLabel.text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

        observeState()
        loadAvailability()
    }

    private fun setupRecyclerView() {
        adapter = AvailabilitySlotAdapter { slot ->
            deleteDateSlots(slot.availableDate)
        }
        binding.rvSchedule.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSchedule.adapter = adapter
    }

    private fun setupWeekStrip() {
        binding.chipGroupDays.removeAllViews()
        val start = LocalDate.now().minusDays(1)
        (0..6).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            val chip = Chip(requireContext()).apply {
                text = date.format(DateTimeFormatter.ofPattern("EEE\n d", Locale.getDefault()))
                isCheckable = true
                isClickable = true
                chipMinHeight = 52f
                id = View.generateViewId()
                chipStrokeWidth = 1f
                chipStrokeColor = requireContext().getColorStateList(R.color.primary)
                chipBackgroundColor = requireContext().getColorStateList(R.color.chip_filter_bg_color)
                setTextColor(requireContext().getColorStateList(R.color.chip_filter_text_color))
                setOnClickListener {
                    selectedDate = date
                    binding.tvMonthLabel.text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    applyDateFilter()
                }
            }
            binding.chipGroupDays.addView(chip)
        }
        val todayIndex = binding.chipGroupDays.childCount - 2
        if (todayIndex >= 0) {
            (binding.chipGroupDays.getChildAt(todayIndex) as? Chip)?.isChecked = true
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadAvailability() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    if (state.error != null) {
                        if (SessionUi.handleAuthError(this@ScheduleFragment, Throwable(state.error))) {
                            return@collect
                        }
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = state.error
                        binding.btnRetry.visibility = View.VISIBLE
                        return@collect
                    }

                    binding.btnRetry.visibility = View.GONE
                    val daySlots = state.slots.filter { it.availableDate == selectedDate.toString() }
                    binding.tvSlotCount.text = "${daySlots.size} slots"
                    binding.tvEmptyState.visibility = if (daySlots.isEmpty()) View.VISIBLE else View.GONE
                    if (daySlots.isEmpty()) {
                        binding.tvEmptyState.text = "No slots available for this day"
                    }
                    adapter.submitList(daySlots)
                }
            }
        }
    }

    private fun applyDateFilter() {
        val state = viewModel.state.value
        val daySlots = state.slots.filter { it.availableDate == selectedDate.toString() }
        binding.tvSlotCount.text = "${daySlots.size} slots"
        binding.tvEmptyState.visibility = if (daySlots.isEmpty()) View.VISIBLE else View.GONE
        if (daySlots.isEmpty()) {
            binding.tvEmptyState.text = "No slots available for this day"
        }
        adapter.submitList(daySlots)
    }

    private fun loadAvailability() {
        viewModel.loadAvailability(doctorId)
    }

    private fun deleteDateSlots(date: String) {
        viewModel.deleteDate(
            date = date,
            onSuccess = {
                if (!isAdded) return@deleteDate
                Toast.makeText(requireContext(), "Availability deleted", Toast.LENGTH_SHORT).show()
                loadAvailability()
            },
            onError = { message ->
                if (!isAdded) return@deleteDate
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showAddSlotBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetBinding = SheetAddTimeSlotBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.etStartTime.setOnClickListener {
            showTimePicker { value -> sheetBinding.etStartTime.setText(value) }
        }
        sheetBinding.etEndTime.setOnClickListener {
            showTimePicker { value -> sheetBinding.etEndTime.setText(value) }
        }

        sheetBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        sheetBinding.btnAddSlot.setOnClickListener {
            val start = sheetBinding.etStartTime.text?.toString()?.trim().orEmpty()
            val end = sheetBinding.etEndTime.text?.toString()?.trim().orEmpty()
            if (start.isBlank() || end.isBlank()) {
                Toast.makeText(requireContext(), "Please select start and end time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addSlot(
                date = selectedDate.toString(),
                start = toApiTime(start),
                end = toApiTime(end),
                onSuccess = {
                    if (!isAdded) return@addSlot
                    Toast.makeText(requireContext(), "Time slot added", Toast.LENGTH_SHORT).show()
                    loadAvailability()
                },
                onError = { message ->
                    if (!isAdded) return@addSlot
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getDoctorIdFromSession(): Long {
        val userJson = tokenManager.getUserJson().orEmpty()
        return runCatching {
            val user = Gson().fromJson(userJson, UserDto::class.java)
            user.profileId ?: user.id
        }.getOrDefault(-1)
    }

    private fun showTimePicker(onSelected: (String) -> Unit) {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val value = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                onSelected(value)
            },
            9,
            0,
            false
        ).show()
    }

    private fun toApiTime(display: String): String {
        val parsed = runCatching {
            java.time.LocalTime.parse(display, DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrNull() ?: return display
        return parsed.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }
}
