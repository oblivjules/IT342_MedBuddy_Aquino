package com.medbuddy.ui.fragments.doctor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentDoctorPatientRecordsBinding
import com.medbuddy.dto.AppointmentResponse
import com.medbuddy.dto.MedicalRecordFileResponse
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.MedicalRecordRepository
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorPatientRecordsFragment : Fragment() {

    private lateinit var binding: FragmentDoctorPatientRecordsBinding
    private lateinit var adapter: PatientRecordAdapter
    private var allItems: List<PatientRecordItem> = emptyList()
    private var searchQuery: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDoctorPatientRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupSwipeRefresh()
        binding.btnRetry.setOnClickListener { loadRecords() }
        loadRecords()
    }

    private fun setupRecyclerView() {
        adapter = PatientRecordAdapter { item -> openEditSheet(item) }
        binding.rvPatientRecords.adapter = adapter
        binding.rvPatientRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPatientRecords.isNestedScrollingEnabled = false
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty().trim()
                applyFilter()
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadRecords() }
    }

    private fun loadRecords() {
        binding.tvEmptyState.visibility = View.GONE
        binding.btnRetry.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).apiService
                val appointments = AppointmentRepository(api).getDoctorAppointments()
                    .filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }
                    .sortedByDescending { it.dateTime }

                val medRepo = MedicalRecordRepository(api)
                val items = appointments.map { apt ->
                    val record = runCatching { medRepo.getMedicalRecordByAppointment(apt.id) }.getOrNull()
                    PatientRecordItem(apt, record)
                }

                if (!isAdded) return@launch
                allItems = items
                applyFilter()
            } catch (e: Throwable) {
                if (!isAdded) return@launch
                binding.tvEmptyState.text = e.message ?: "Failed to load patient records."
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.btnRetry.visibility = View.VISIBLE
            } finally {
                if (isAdded) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun applyFilter() {
        val filtered = if (searchQuery.isBlank()) {
            allItems
        } else {
            val q = searchQuery.lowercase(Locale.getDefault())
            allItems.filter { item ->
                val fullName = "${item.appointment.patient.firstName} ${item.appointment.patient.lastName}"
                fullName.lowercase().contains(q) ||
                    item.appointment.patient.email.lowercase().contains(q) ||
                    item.record?.diagnosis?.lowercase()?.contains(q) == true
            }
        }
        binding.tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty() && allItems.isNotEmpty()) {
            binding.tvEmptyState.text = "No records match your search."
        } else if (filtered.isEmpty()) {
            binding.tvEmptyState.text = "No completed appointments with records yet."
        }
        adapter.submitList(filtered)
    }

    private fun openEditSheet(item: PatientRecordItem) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.sheet_edit_medical_record, null)
        dialog.setContentView(sheetView)
        dialog.behavior.peekHeight = resources.displayMetrics.heightPixels / 2

        val tvPatientName = sheetView.findViewById<TextView>(R.id.tvPatientName)
        val tvDate = sheetView.findViewById<TextView>(R.id.tvAppointmentDate)
        val etDiagnosis = sheetView.findViewById<EditText>(R.id.etDiagnosis)
        val etMedicine = sheetView.findViewById<EditText>(R.id.etMedicine)
        val etDosage = sheetView.findViewById<EditText>(R.id.etDosage)
        val etRoute = sheetView.findViewById<EditText>(R.id.etRoute)
        val etFrequency = sheetView.findViewById<EditText>(R.id.etFrequency)
        val etDuration = sheetView.findViewById<EditText>(R.id.etDuration)
        val etPrescriptionNotes = sheetView.findViewById<EditText>(R.id.etPrescriptionNotes)
        val tvError = sheetView.findViewById<TextView>(R.id.tvError)
        val tvFilesStatus = sheetView.findViewById<TextView>(R.id.tvFilesStatus)
        val llFiles = sheetView.findViewById<LinearLayout>(R.id.llFiles)

        val apt = item.appointment
        tvPatientName.text = "Patient: ${apt.patient.firstName} ${apt.patient.lastName}".trim()
        tvDate.text = formatDate(apt.dateTime)

        // Fetch files like the web app — GET /api/files/appointment/{id}
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val files = RetrofitClient.getInstance(requireContext()).apiService
                    .getFilesByAppointment(apt.id).bodyOrThrow()
                if (files.isEmpty()) {
                    tvFilesStatus.text = "No files attached"
                } else {
                    tvFilesStatus.visibility = View.GONE
                    llFiles.visibility = View.VISIBLE
                    llFiles.removeAllViews()
                    files.forEach { file -> llFiles.addView(buildFileChip(file)) }
                }
            } catch (_: Exception) {
                tvFilesStatus.text = "Could not load files"
            }
        }

        val existing = item.record
        if (existing != null) {
            etDiagnosis.setText(existing.diagnosis.orEmpty())
            etMedicine.setText(existing.medicineName.orEmpty())
            etDosage.setText(existing.dosage.orEmpty())
            etRoute.setText(existing.route.orEmpty())
            etFrequency.setText(existing.frequency.orEmpty())
            etDuration.setText(existing.duration.orEmpty())
            etPrescriptionNotes.setText(existing.prescriptionNotes.orEmpty())
        }

        sheetView.findViewById<View>(R.id.btnSave).setOnClickListener {
            tvError.visibility = View.GONE
            val diagnosis = etDiagnosis.text?.toString()?.trim().orEmpty()
            if (diagnosis.isBlank()) {
                tvError.text = "Diagnosis is required."
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            dialog.dismiss()
            saveRecord(
                appointment = apt,
                existingRecord = existing,
                diagnosis = diagnosis,
                medicine = etMedicine.text?.toString()?.trim().orEmpty(),
                dosage = etDosage.text?.toString()?.trim().orEmpty(),
                route = etRoute.text?.toString()?.trim().orEmpty(),
                frequency = etFrequency.text?.toString()?.trim().orEmpty(),
                duration = etDuration.text?.toString()?.trim().orEmpty(),
                prescriptionNotes = etPrescriptionNotes.text?.toString()?.trim().orEmpty()
            )
        }

        sheetView.findViewById<View>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun buildFileChip(file: MedicalRecordFileResponse): View {
        val tv = TextView(requireContext())
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()
        tv.setPadding(dp12, dp8, dp12, dp8)
        tv.text = "📎 ${file.fileName}"
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        tv.textSize = 13f
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = dp8
        tv.layoutParams = params
        tv.setOnClickListener { openFile(file.id) }
        return tv
    }

    private fun openFile(fileId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = RetrofitClient.getInstance(requireContext()).apiService
                    .getFileAccessUrl(fileId).bodyOrThrow()
                val url = result["url"] ?: return@launch
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveRecord(
        appointment: AppointmentResponse,
        existingRecord: MedicalRecordResponse?,
        diagnosis: String,
        medicine: String,
        dosage: String,
        route: String,
        frequency: String,
        duration: String,
        prescriptionNotes: String
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).apiService
                val medRepo = MedicalRecordRepository(api)

                val prescriptionSummary = buildList<String> {
                    if (medicine.isNotBlank()) add("Medicine: $medicine")
                    if (dosage.isNotBlank()) add("Dosage: $dosage")
                    if (route.isNotBlank()) add("Route: $route")
                    if (frequency.isNotBlank()) add("Frequency: $frequency")
                    if (duration.isNotBlank()) add("Duration: $duration")
                    if (prescriptionNotes.isNotBlank()) add("Notes: $prescriptionNotes")
                }.joinToString(" | ")

                val savedRecord = if (existingRecord?.id != null) {
                    medRepo.updateMedicalRecord(
                        id = existingRecord.id,
                        appointmentId = appointment.id,
                        diagnosis = diagnosis,
                        prescriptionDetails = prescriptionSummary.ifBlank { null },
                        medicineName = medicine.ifBlank { null },
                        dosage = dosage.ifBlank { null },
                        route = route.ifBlank { null },
                        frequency = frequency.ifBlank { null },
                        duration = duration.ifBlank { null },
                        prescriptionNotes = prescriptionNotes.ifBlank { null }
                    )
                } else {
                    medRepo.createMedicalRecord(
                        appointmentId = appointment.id,
                        diagnosis = diagnosis,
                        prescriptionDetails = prescriptionSummary.ifBlank { null },
                        medicineName = medicine.ifBlank { null },
                        dosage = dosage.ifBlank { null },
                        route = route.ifBlank { null },
                        frequency = frequency.ifBlank { null },
                        duration = duration.ifBlank { null },
                        prescriptionNotes = prescriptionNotes.ifBlank { null }
                    )
                }

                if (!isAdded) return@launch

                // Update list in place
                allItems = allItems.map {
                    if (it.appointment.id == appointment.id) it.copy(record = savedRecord) else it
                }
                applyFilter()
                Toast.makeText(requireContext(), "Record saved.", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                if (isAdded) {
                    Toast.makeText(requireContext(), e.message ?: "Failed to save record.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun formatDate(dateTime: String): String {
        return runCatching {
            LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        }.getOrElse { dateTime }
    }
}

data class PatientRecordItem(
    val appointment: AppointmentResponse,
    val record: MedicalRecordResponse?
)

class PatientRecordAdapter(
    private val onClick: (PatientRecordItem) -> Unit
) : ListAdapter<PatientRecordItem, PatientRecordAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doctor_patient_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: PatientRecordItem) {
            val apt = item.appointment
            val fullName = "${apt.patient.firstName} ${apt.patient.lastName}".trim()
            val initials = fullName.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
                .ifBlank { "P" }

            val dateLabel = runCatching {
                LocalDateTime.parse(apt.dateTime, DateTimeFormatter.ISO_DATE_TIME)
                    .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
            }.getOrElse { apt.dateTime }

            itemView.findViewById<TextView>(R.id.tvAvatar).text = initials
            itemView.findViewById<TextView>(R.id.tvPatientName).text = fullName
            itemView.findViewById<TextView>(R.id.tvAppointmentDate).text = dateLabel
            itemView.findViewById<TextView>(R.id.tvDiagnosis).text =
                item.record?.diagnosis?.takeIf { it.isNotBlank() } ?: "No record yet — tap to add"

            itemView.setOnClickListener { onClick(item) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PatientRecordItem>() {
        override fun areItemsTheSame(old: PatientRecordItem, new: PatientRecordItem) =
            old.appointment.id == new.appointment.id
        override fun areContentsTheSame(old: PatientRecordItem, new: PatientRecordItem) =
            old == new
    }
}
