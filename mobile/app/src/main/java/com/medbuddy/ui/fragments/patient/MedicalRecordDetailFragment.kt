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
import com.medbuddy.repository.PaymentRepository
import kotlinx.coroutines.launch

class MedicalRecordDetailFragment : Fragment() {

    private lateinit var binding: FragmentMedicalRecordDetailBinding
    private lateinit var fileAdapter: MedicalRecordFileAdapter
    private lateinit var recordRepository: MedicalRecordRepository
    private lateinit var appointmentRepository: AppointmentRepository
    private lateinit var paymentRepository: PaymentRepository

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
        paymentRepository = PaymentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        fileAdapter = MedicalRecordFileAdapter { file ->
            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                try {
                    val accessUrl = runCatching { recordRepository.getFileAccessUrl(file.id) }.getOrNull() ?: file.fileUrl ?: file.url
                    accessUrl?.let { openFile(it) }
                } catch (_: Throwable) {
                    // silent fail - openFile handles nulls
                }
            }
        }

        binding.recyclerFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerFiles.adapter = fileAdapter

        loadDetail()
    }

    private fun loadDetail() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payment = loadPaymentStatus()
                if (!isPaymentUnlocked(payment?.paymentStatus)) {
                    showLockedState()
                    return@launch
                }

                val record = loadRecord()
                val appointment = appointmentRepository.getPatientAppointments().firstOrNull { it.id == record.appointmentId }
                val doctorName = appointment?.let { appointmentDoctorName(it) } ?: record.doctorName ?: "Doctor"
                val dateLabel = appointment?.let { formatAppointmentDateTime(it.dateTime) } ?: record.formattedDate ?: record.createdAt ?: record.uploadedAt.orEmpty()
                val files = record.id.let { runCatching { recordRepository.getMedicalRecordFiles(it) }.getOrDefault(emptyList()) }

                binding.cardLocked.visibility = View.GONE
                binding.cardContent.visibility = View.VISIBLE
                binding.cardContent.alpha = 1f

                binding.tvDiagnosis.text = record.diagnosis
                binding.tvDoctorName.text = doctorName
                binding.tvDate.text = dateLabel
                binding.tvMedicineName.text = record.medicineName?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvDosage.text = record.dosage?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvRoute.text = record.route?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvFrequency.text = record.frequency?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvDuration.text = record.duration?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvPrescriptionNotes.text = record.prescriptionNotes?.takeIf { it.isNotBlank() } ?: "N/A"
                binding.tvPrescriptionDetails.text = record.prescriptionDetails?.takeIf { it.isNotBlank() }
                    ?: buildPrescriptionSummary(record)
                fileAdapter.submitList(files)
                binding.tvNoFiles.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE

                if (!record.medicineName.isNullOrBlank()) {
                    loadDrugInfo(record.id)
                } else {
                    binding.progressDrugInfo.visibility = View.GONE
                    binding.cardDrugInfo.visibility = View.GONE
                    binding.tvDrugUnavailable.visibility = View.VISIBLE
                }
            } catch (throwable: Throwable) {
                binding.tvDiagnosis.text = throwable.message ?: "Unable to load medical record"
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = throwable.message ?: "Unable to load medical record"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    private suspend fun loadPaymentStatus() = when {
        appointmentId > 0 -> runCatching { paymentRepository.getPaymentByAppointmentId(appointmentId) }.getOrNull()
        recordId > 0 -> runCatching { loadRecord() }.getOrNull()?.let { record ->
            runCatching { paymentRepository.getPaymentByAppointmentId(record.appointmentId) }.getOrNull()
        }
        else -> null
    }

    private fun isPaymentUnlocked(status: String?): Boolean {
        return when (status?.uppercase()) {
            "PAID", "COMPLETED" -> true
            else -> false
        }
    }

    private fun showLockedState() {
        binding.cardLocked.visibility = View.VISIBLE
        binding.cardContent.visibility = View.GONE
        binding.cardDrugInfo.visibility = View.GONE
        binding.recyclerFiles.visibility = View.GONE
        binding.tvNoFiles.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvDiagnosis.text = ""
        binding.tvDoctorName.text = ""
        binding.tvDate.text = ""
        binding.tvPrescriptionDetails.text = ""
    }

    private suspend fun loadRecord(): MedicalRecordResponse {
        return when {
            recordId > 0 -> recordRepository.getMedicalRecord(recordId)
            appointmentId > 0 -> recordRepository.getMedicalRecordByAppointment(appointmentId)
            else -> throw IllegalStateException("Missing record reference")
        }
    }

    private fun loadDrugInfo(recordId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val drugInfo = runCatching { recordRepository.getDrugInfo(recordId) }.getOrNull()
                ?: return@launch
            if (drugInfo.available != true) return@launch

            binding.cardDrugInfo.visibility = View.VISIBLE
            drugInfo.description?.takeIf { it.isNotBlank() }?.let {
                binding.tvDrugDescription.text = it
                binding.tvDrugDescription.visibility = View.VISIBLE
            }
            drugInfo.indications?.takeIf { it.isNotBlank() }?.let {
                binding.tvDrugIndications.text = "Indications: $it"
                binding.tvDrugIndications.visibility = View.VISIBLE
            }
            val dosage = drugInfo.dosageAdministration?.takeIf { it.isNotBlank() } ?: drugInfo.dosage?.takeIf { it.isNotBlank() }
            dosage?.let {
                binding.tvDrugDosage.text = "Dosage: $it"
                binding.tvDrugDosage.visibility = View.VISIBLE
            }
            drugInfo.warnings?.takeIf { it.isNotBlank() }?.let {
                binding.tvDrugWarnings.text = "⚠ Warnings: $it"
                binding.tvDrugWarnings.visibility = View.VISIBLE
            }
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
