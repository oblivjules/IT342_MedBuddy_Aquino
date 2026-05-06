package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.constants.AppointmentStatus
import com.medbuddy.databinding.FragmentMedicalRecordsBinding
import com.medbuddy.dto.MedicalRecordResponse
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.repository.MedicalRecordRepository
import kotlinx.coroutines.launch

class MedicalRecordsFragment : Fragment() {

    private lateinit var binding: FragmentMedicalRecordsBinding
    private lateinit var adapter: PatientMedicalRecordAdapter
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
        adapter = PatientMedicalRecordAdapter { record ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MedicalRecordDetailFragment.newInstance(record.id, record.appointmentId))
                .addToBackStack(null)
                .commit()
        }

        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { loadRecords() }

        loadRecords()
    }

    private fun loadRecords() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
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

                records = combined.sortedByDescending { it.formattedDate.orEmpty() }
                adapter.submitList(records)
                binding.tvEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            } catch (throwable: Throwable) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = throwable.message ?: "No medical records found"
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }
}
