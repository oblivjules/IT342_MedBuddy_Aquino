package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.databinding.FragmentBookAppointmentRefinedBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

class BookAppointmentFragment : Fragment() {

    private lateinit var binding: FragmentBookAppointmentRefinedBinding
    private lateinit var viewModel: AppointmentViewModel
    private lateinit var dayAdapter: CalendarDayAdapter
    private var doctor: DoctorDto? = null
    private var selectedDate: LocalDate = LocalDate.now().plusDays(1)
    private var selectedSlotId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentBookAppointmentRefinedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        doctor = arguments?.getSerializable(ARG_DOCTOR) as? DoctorDto
        val repository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        viewModel = ViewModelProvider(this, AppointmentViewModel.factory(repository))[AppointmentViewModel::class.java]

        setupDoctorHeader()
        setupDates()
        observeSlots()

        binding.btnBook.setOnClickListener { submitBooking() }
        loadSlotsForSelectedDate()
    }

    private fun setupDoctorHeader() {
        val currentDoctor = doctor ?: return
        val doctorName = doctorDisplayName(currentDoctor)
        val specialization = currentDoctor.specializations?.firstOrNull()
            ?: currentDoctor.specialization
            ?: "General Practice"

        binding.tvAvatar.text = doctorInitials(currentDoctor.firstName, currentDoctor.lastName)
        binding.tvDoctorName.text = doctorName
        binding.tvDoctorSpec.text = specialization
        binding.tvReservationFee.text = "₱100 reservation fee"
    }

    private fun setupDates() {
        val dates = (0 until 14).map { LocalDate.now().plusDays((it + 1).toLong()) }
        dayAdapter = CalendarDayAdapter { date ->
            selectedDate = date
            selectedSlotId = null
            dayAdapter.selectedDate = date
            binding.btnBook.isEnabled = false
            loadSlotsForSelectedDate()
        }
        binding.rvDates.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvDates.adapter = dayAdapter
        dayAdapter.submitList(dates)
        dayAdapter.selectedDate = selectedDate
    }

    private fun observeSlots() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.slots.collect { state ->
                    binding.progressBar.visibility = if (state.loading) View.VISIBLE else View.GONE
                    if (state.loading) {
                        binding.scrollContent.visibility = View.GONE
                        return@collect
                    }

                    binding.scrollContent.visibility = View.VISIBLE
                    binding.chipGroupSlots.removeAllViews()

                    if (state.error != null) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = state.error
                        return@collect
                    }

                    if (state.items.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        return@collect
                    }

                    binding.tvEmptyState.visibility = View.GONE

                    state.items.forEach { slot ->
                        val isBooked = slot.status == "BOOKED" || slot.status == "UNAVAILABLE" || slot.status == "RESERVED"
                        val chip = Chip(requireContext()).apply {
                            id = View.generateViewId()
                            text = slot.label
                            isCheckable = !isBooked
                            isClickable = !isBooked
                            isEnabled = !isBooked
                            chipMinHeight = 48f
                            chipCornerRadius = 24f
                            if (isBooked) {
                                chipBackgroundColor = requireContext().getColorStateList(R.color.chip_completed_bg)
                                setTextColor(requireContext().getColorStateList(R.color.chip_completed_text))
                            } else {
                                chipBackgroundColor = requireContext().getColorStateList(R.color.chip_pending_bg)
                                setTextColor(requireContext().getColorStateList(R.color.chip_pending_text))
                                isChecked = selectedSlotId == slot.id
                                setOnClickListener {
                                    selectedSlotId = slot.id
                                    binding.btnBook.isEnabled = true
                                    refreshSlotStyles()
                                }
                            }
                        }
                        binding.chipGroupSlots.addView(chip)
                    }

                    refreshSlotStyles()
                }
            }
        }
    }

    private fun refreshSlotStyles() {
        for (index in 0 until binding.chipGroupSlots.childCount) {
            val chip = binding.chipGroupSlots.getChildAt(index) as? Chip ?: continue
            if (!chip.isEnabled) continue
            val selected = chip.isChecked
            chip.chipBackgroundColor = requireContext().getColorStateList(
                if (selected) R.color.primary else R.color.chip_pending_bg,
            )
            chip.setTextColor(
                requireContext().getColorStateList(
                    if (selected) R.color.white else R.color.chip_pending_text,
                ),
            )
        }
    }

    private fun loadSlotsForSelectedDate() {
        val currentDoctor = doctor ?: return
        viewModel.getSlotsByDoctorDate(currentDoctor.id, selectedDate.toString())
        binding.btnBook.isEnabled = false
    }

    private fun submitBooking() {
        val currentDoctor = doctor ?: return
        val chosenSlotId = selectedSlotId
        if (chosenSlotId == null) {
            Toast.makeText(requireContext(), "Please select an available slot", Toast.LENGTH_SHORT).show()
            return
        }
        val notes = binding.etNotes.text?.toString()?.trim().orEmpty()
        if (notes.isBlank()) {
            Toast.makeText(requireContext(), "Please enter a reason for your visit", Toast.LENGTH_SHORT).show()
            return
        }
        if (notes.length > 500) {
            Toast.makeText(requireContext(), "Notes cannot exceed 500 characters", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnBook.isEnabled = false
        viewModel.bookAppointment(
            doctorId = currentDoctor.id,
            slotId = chosenSlotId,
            reason = notes
        ) { success ->
            if (!isAdded) return@bookAppointment
            if (success) {
                Toast.makeText(requireContext(), getString(R.string.success_booked), Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AppointmentsFragment())
                    .commit()
            } else {
                binding.btnBook.isEnabled = true
                Toast.makeText(requireContext(), "Failed to book appointment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ARG_DOCTOR = "doctor"

        fun newInstance(doctor: DoctorDto): BookAppointmentFragment {
            return BookAppointmentFragment().apply {
                arguments = Bundle().apply { putSerializable(ARG_DOCTOR, doctor) }
            }
        }
    }
}
