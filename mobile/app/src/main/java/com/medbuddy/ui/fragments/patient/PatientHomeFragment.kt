package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentPatientHomeRefinedBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.AppointmentRepository
import kotlinx.coroutines.launch

class PatientHomeFragment : Fragment() {

    private lateinit var binding: FragmentPatientHomeRefinedBinding
    private lateinit var tokenManager: TokenManager
    private lateinit var appointmentRepository: AppointmentRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPatientHomeRefinedBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        appointmentRepository = AppointmentRepository(RetrofitClient.getInstance(requireContext()).apiService)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnViewDetails.setOnClickListener { openAppointments() }
        binding.cardBookAppointment.setOnClickListener { openFindDoctor() }
        binding.cardMyRecords.setOnClickListener { openMedicalRecords() }

        loadDashboard()
    }

    private fun loadDashboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.scrollContent.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                updateWelcomeText()
                val appointments = appointmentRepository.getPatientAppointments()
                val upcoming = appointments
                    .filter {
                        val status = it.status.uppercase()
                        status == "PENDING" || status == "CONFIRMED"
                    }
                    .minByOrNull { it.dateTime }

                if (upcoming != null) {
                    bindUpcomingAppointment(upcoming)
                    binding.cardUpcoming.visibility = View.VISIBLE
                } else {
                    binding.cardUpcoming.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            } catch (_: Throwable) {
                binding.cardUpcoming.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE
            }
        }
    }

    private fun updateWelcomeText() {
        val userJson = tokenManager.getUserJson().orEmpty()
        val firstName = runCatching {
            Gson().fromJson(userJson, UserDto::class.java)?.firstName
        }.getOrNull().orEmpty()
        binding.tvWelcome.text = "Welcome, ${firstName.ifBlank { "Patient" }}! 👋"
        binding.tvSubtitle.text = "How are you feeling today?"
    }

    private fun bindUpcomingAppointment(appointment: com.medbuddy.dto.AppointmentResponse) {
        val doctorName = appointmentDoctorName(appointment)
        val specialization = appointmentDoctorSpecialization(appointment)
        val initials = doctorInitials(appointment.doctor.firstName, appointment.doctor.lastName)

        binding.tvAvatar.text = initials
        binding.tvDoctorName.text = doctorName
        binding.tvSpecialization.text = specialization
        binding.tvDateTime.text = formatAppointmentDateTime(appointment.dateTime)
        binding.tvStatusChip.text = formatStatusLabel(appointment.status)
    }

    private fun openAppointments() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AppointmentsFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openFindDoctor() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, FindDoctorFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun openMedicalRecords() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MedicalRecordsFragment())
            .addToBackStack(null)
            .commit()
    }
}
