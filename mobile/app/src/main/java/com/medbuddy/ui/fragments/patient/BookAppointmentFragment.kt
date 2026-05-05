package com.medbuddy.ui.fragments.patient

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
import com.google.android.material.chip.Chip
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentBookAppointmentBinding
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BookAppointmentFragment : Fragment() {

    private lateinit var binding: FragmentBookAppointmentBinding
    private lateinit var viewModel: AppointmentViewModel
    private var doctorId: Long = -1
    private var doctorName: String = ""
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedSlotId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBookAppointmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        doctorId = arguments?.getLong("doctorId") ?: -1
        doctorName = arguments?.getString("doctorName") ?: ""

        val repository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(
            this,
            AppointmentViewModel.factory(repository)
        )[AppointmentViewModel::class.java]

        bindDoctorHeader()
        setupDateStrip()
        observeSlots()

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnBook.setOnClickListener { submitBooking() }

        loadSlotsForSelectedDate()
    }

    private fun bindDoctorHeader() {
        binding.tvDoctorName.text = "Dr. $doctorName"
        binding.tvDoctorAvatar.text = doctorName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
            .ifBlank { "DR" }
        binding.tvDoctorSpec.text = "Verified specialist"
    }

    private fun setupDateStrip() {
        binding.dateContainer.removeAllViews()
        val formatter = DateTimeFormatter.ofPattern("EEE\nMM/dd", Locale.getDefault())

        (0..20).forEach { index ->
            val date = LocalDate.now().plusDays(index.toLong())
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = date.format(formatter)
                isCheckable = true
                isClickable = true
                chipMinHeight = 52f
                chipStrokeWidth = 1f
                chipStrokeColor = requireContext().getColorStateList(R.color.primary)
                chipBackgroundColor = requireContext().getColorStateList(R.color.chip_filter_bg_color)
                setTextColor(requireContext().getColorStateList(R.color.chip_filter_text_color))
                setOnClickListener {
                    selectedDate = date
                    selectedSlotId = -1
                    binding.etDate.setText(date.toString())
                    binding.btnBook.isEnabled = false
                    loadSlotsForSelectedDate()
                }
            }

            if (index == 0) {
                chip.isChecked = true
                binding.etDate.setText(date.toString())
            }
            binding.dateContainer.addView(chip)
        }
    }

    private fun observeSlots() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.slotsState.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    binding.chipGroupTime.removeAllViews()

                    if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
                        return@collect
                    }

                    if (state.items.isEmpty()) {
                        Toast.makeText(
                            requireContext(),
                            "No available time slots for this date.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@collect
                    }

                    state.items.forEach { slot ->
                        val chip = Chip(requireContext()).apply {
                            id = View.generateViewId()
                            text = slot.label
                            isCheckable = true
                            isClickable = true
                            chipMinHeight = 48f
                            chipStrokeWidth = 1f
                            chipStrokeColor = requireContext().getColorStateList(R.color.primary)
                            chipBackgroundColor = requireContext().getColorStateList(R.color.chip_filter_bg_color)
                            setTextColor(requireContext().getColorStateList(R.color.chip_filter_text_color))
                            setOnClickListener {
                                selectedSlotId = slot.id
                                binding.btnBook.isEnabled = true
                            }
                        }
                        binding.chipGroupTime.addView(chip)
                    }
                }
            }
        }
    }

    private fun loadSlotsForSelectedDate() {
        viewModel.loadSlots(doctorId, selectedDate.toString())
    }

    private fun submitBooking() {
        if (selectedSlotId <= 0) {
            Toast.makeText(requireContext(), "Please select a valid time slot", Toast.LENGTH_SHORT).show()
            return
        }

        val notes = binding.etNotes.text?.toString()?.trim().orEmpty().ifBlank { null }
        binding.btnBook.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewModel.bookAppointment(
            doctorId = doctorId,
            slotId = selectedSlotId,
            notes = notes,
            onSuccess = {
                if (!isAdded) return@bookAppointment
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.success_booked), Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AppointmentsFragment())
                    .addToBackStack(null)
                    .commit()
            },
            onError = { message ->
                if (!isAdded) return@bookAppointment
                binding.progressBar.visibility = View.GONE
                binding.btnBook.isEnabled = true
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    companion object {
        fun newInstance(doctorId: Long, doctorName: String): BookAppointmentFragment {
            return BookAppointmentFragment().apply {
                arguments = Bundle().apply {
                    putLong("doctorId", doctorId)
                    putString("doctorName", doctorName)
                }
            }
        }
    }
}
