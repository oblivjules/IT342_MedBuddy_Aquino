package com.medbuddy.ui.fragments.patient

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentMedicalRecordsBinding
import com.medbuddy.dto.MedicalRecordFileResponse
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.MedicalRecordRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class MedicalRecordsFragment : Fragment() {

    private lateinit var binding: FragmentMedicalRecordsBinding
    private lateinit var recordAdapter: PatientMedicalRecordAdapter
    private lateinit var uploadAdapter: MedicalRecordFileAdapter
    private lateinit var recordRepository: MedicalRecordRepository
    private lateinit var appointmentRepository: AppointmentRepository
    private var records: List<MedicalRecordResponse> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentMedicalRecordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recordRepository = MedicalRecordRepository(RetrofitClient.getInstance(requireContext()).apiService)
        appointmentRepository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)

        recordAdapter = PatientMedicalRecordAdapter { record ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MedicalRecordDetailFragment.newInstance(record.id, record.appointmentId))
                .addToBackStack(null)
                .commit()
        }

        uploadAdapter = MedicalRecordFileAdapter { file -> openFile(file) }

        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = recordAdapter

        binding.rvUploads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUploads.adapter = uploadAdapter

        binding.swipeRefresh.setOnRefreshListener { loadAll() }

        loadAll()
    }

    private fun loadAll() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                coroutineScope {
                    val uploadsDeferred = async { runCatching { recordRepository.getMyRecordFiles() }.getOrDefault(emptyList()) }
                    val recordsDeferred = async { loadMedicalRecords() }

                    val allUploads = uploadsDeferred.await()
                    val patientUploads = allUploads.filter { file ->
                        file.uploadedBy?.trim()?.uppercase()?.let { it == "PATIENT" || it.contains("PATIENT") } ?: true
                    }

                    records = recordsDeferred.await()

                    uploadAdapter.submitList(patientUploads)
                    binding.tvUploadCount.text = "${patientUploads.size} file${if (patientUploads.size == 1) "" else "s"}"
                    binding.tvUploadsEmpty.visibility = if (patientUploads.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvUploads.visibility = if (patientUploads.isNotEmpty()) View.VISIBLE else View.GONE

                    recordAdapter.submitList(records)
                    binding.tvEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (throwable: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = throwable.message ?: "Unable to load records"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun openFile(file: MedicalRecordFileResponse) {
        viewLifecycleOwner.lifecycleScope.launch {
            val url = runCatching { recordRepository.getFileAccessUrl(file.id) }.getOrNull()
                ?: file.url
                ?: file.fileUrl

            if (!url.isNullOrBlank()) {
                CustomTabsIntent.Builder()
                    .setToolbarColor(requireContext().getColor(R.color.primary))
                    .build()
                    .launchUrl(requireContext(), Uri.parse(url))
            }
        }
    }

    private suspend fun loadMedicalRecords(): List<MedicalRecordResponse> {
        val appointments = appointmentRepository.getPatientAppointments()
            .filter { AppointmentStatus.normalize(it.status) == AppointmentStatus.COMPLETED }

        val combined = mutableListOf<MedicalRecordResponse>()
        appointments.forEach { appointment ->
            runCatching { recordRepository.getMedicalRecordByAppointment(appointment.id) }
                .onSuccess { record ->
                    combined.add(
                        record.copy(
                            doctorName = appointmentDoctorName(appointment),
                            formattedDate = formatAppointmentDateTime(appointment.dateTime),
                            type = if (!record.prescriptionDetails.isNullOrBlank() || !record.medicineName.isNullOrBlank()) "Prescription" else "Consultation",
                        )
                    )
                }
        }
        return combined.sortedByDescending { it.formattedDate.orEmpty() }
    }
}
