package com.medbuddy.ui.fragments.patient

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.databinding.FragmentBookAppointmentRefinedBinding
import com.medbuddy.dto.DoctorDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.viewmodel.AppointmentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

class BookAppointmentFragment : Fragment() {

    private lateinit var binding: FragmentBookAppointmentRefinedBinding
    private lateinit var viewModel: AppointmentViewModel
    private lateinit var dayAdapter: CalendarDayAdapter
    private var doctor: DoctorDto? = null
    private var selectedDate: LocalDate = LocalDate.now().plusDays(1)
    private var selectedSlotId: Long? = null
    private val selectedAttachmentUris = mutableListOf<Uri>()
    private val attachmentPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        selectedAttachmentUris.clear()
        selectedAttachmentUris.addAll(uris)
        renderSelectedAttachments()
    }

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
        setupAttachments()
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

    private fun setupAttachments() {
        binding.btnChooseFiles.setOnClickListener {
            attachmentPicker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
        }
        renderSelectedAttachments()
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
        ) { appointmentResponse ->
            if (!isAdded) return@bookAppointment
            if (appointmentResponse != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val failedUploads = uploadSelectedAttachments(notes, appointmentResponse.id)
                    val message = if (failedUploads.isEmpty()) {
                        getString(R.string.success_booked)
                    } else {
                        "Appointment booked, but some attachments failed to upload: ${failedUploads.joinToString(", ")}"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, AppointmentsFragment())
                        .commit()
                }
            } else {
                binding.btnBook.isEnabled = true
                Toast.makeText(requireContext(), "Failed to book appointment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderSelectedAttachments() {
        binding.containerSelectedFiles.removeAllViews()
        binding.tvSelectedFilesEmpty.visibility = if (selectedAttachmentUris.isEmpty()) View.VISIBLE else View.GONE

        selectedAttachmentUris.forEachIndexed { index, uri ->
            val row = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_selected_attachment,
                binding.containerSelectedFiles,
                false,
            )
            val nameView = row.findViewById<TextView>(R.id.tvAttachmentName)
            val removeButton = row.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRemoveAttachment)
            nameView.text = displayNameForUri(uri)
            removeButton.setOnClickListener {
                if (index in selectedAttachmentUris.indices) {
                    selectedAttachmentUris.removeAt(index)
                    renderSelectedAttachments()
                }
            }
            binding.containerSelectedFiles.addView(row)
        }
    }

    private suspend fun uploadSelectedAttachments(description: String, appointmentId: Long?): List<String> {
        if (selectedAttachmentUris.isEmpty()) return emptyList()

        val apiService = RetrofitClient.getInstance(requireContext()).apiService
        return withContext(Dispatchers.IO) {
            selectedAttachmentUris.map { uri ->
                async {
                    runCatching {
                        val requestBody = description.takeIf { it.isNotBlank() }
                            ?.toRequestBody("text/plain".toMediaTypeOrNull())
                        val appointmentBody = appointmentId?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
                        apiService.uploadMyMedicalRecordFile(uri.toMultipartPart(), requestBody, appointmentBody).bodyOrThrow()
                        null
                    }.getOrElse {
                        displayNameForUri(uri)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun Uri.toMultipartPart(): MultipartBody.Part {
        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(this)?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val fileName = displayNameForUri(this).ifBlank { "attachment" }
        val bytes = resolver.openInputStream(this)?.use { input -> input.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected file")
        val requestBody = bytes.toRequestBody(mimeType)
        return MultipartBody.Part.createFormData("file", fileName, requestBody)
    }

    private fun displayNameForUri(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
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
