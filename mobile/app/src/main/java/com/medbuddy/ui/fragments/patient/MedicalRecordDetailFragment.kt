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
import com.medbuddy.databinding.FragmentMedicalRecordDetailBinding
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.MedicalRecordRepository
import kotlinx.coroutines.launch

class MedicalRecordDetailFragment : Fragment() {

    private lateinit var binding: FragmentMedicalRecordDetailBinding
    private lateinit var fileAdapter: MedicalRecordFileAdapter
    private lateinit var recordRepository: MedicalRecordRepository
    private lateinit var appointmentRepository: AppointmentRepository

    private var recordId: Long = -1
    private var appointmentId: Long = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentMedicalRecordDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordId = requireArguments().getLong(ARG_RECORD_ID, -1)
        appointmentId = requireArguments().getLong(ARG_APPOINTMENT_ID, -1)

        recordRepository = MedicalRecordRepository(RetrofitClient.getInstance(requireContext()).apiService)
        appointmentRepository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        fileAdapter = MedicalRecordFileAdapter { file -> openFile(file.url ?: file.fileUrl) }

        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter

        loadDetail()
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val record = loadRecord()
                val appointment = appointmentRepository.getPatientAppointments().firstOrNull { it.id == record.appointmentId }
                val doctorName = appointment?.let { appointmentDoctorName(it) } ?: record.doctorName ?: "Doctor"
                val dateLabel = appointment?.let { formatAppointmentDateTime(it.dateTime) } ?: record.formattedDate ?: record.createdAt ?: record.uploadedAt.orEmpty()
                val files = record.id.let { runCatching { recordRepository.getMedicalRecordFiles(it) }.getOrDefault(emptyList()) }

                binding.tvDiagnosis.text = record.diagnosis
                binding.tvDoctorName.text = doctorName
                binding.tvDate.text = dateLabel
                binding.tvPrescriptionDetails.text = record.prescriptionDetails?.takeIf { it.isNotBlank() }
                    ?: buildPrescriptionSummary(record)
                fileAdapter.submitList(files)
                binding.tvFilesEmptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
            } catch (throwable: Throwable) {
                binding.tvDiagnosis.text = throwable.message ?: "Unable to load medical record"
                binding.tvFilesEmptyState.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun loadRecord(): MedicalRecordResponse {
        return when {
            recordId > 0 -> recordRepository.getMedicalRecord(recordId)
            appointmentId > 0 -> recordRepository.getMedicalRecordByAppointment(appointmentId)
            else -> throw IllegalStateException("Missing record reference")
        }
    }

    private fun openFile(url: String?) {
        if (url.isNullOrBlank()) return
        val intent = CustomTabsIntent.Builder().setToolbarColor(requireContext().getColor(R.color.primary)).build()
        intent.launchUrl(requireContext(), Uri.parse(url))
    }

    companion object {
        private const val ARG_RECORD_ID = "recordId"
        private const val ARG_APPOINTMENT_ID = "appointmentId"

        fun newInstance(recordId: Long, appointmentId: Long): MedicalRecordDetailFragment {
            return MedicalRecordDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_RECORD_ID, recordId)
                    putLong(ARG_APPOINTMENT_ID, appointmentId)
                }
            }
        }
    }
}
