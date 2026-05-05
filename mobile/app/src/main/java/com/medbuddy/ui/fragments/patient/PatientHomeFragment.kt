package com.medbuddy.ui.fragments.patient

import android.content.Intent
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
import com.medbuddy.databinding.FragmentPatientHomeBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.ui.LoginActivity
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PatientHomeFragment : Fragment() {

    private lateinit var binding: FragmentPatientHomeBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPatientHomeBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateWelcomeText()
        loadUpcomingAppointmentCard()

        binding.btnFindDoctor.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FindDoctorFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnBookAppointment.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, FindDoctorFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnViewDetails.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AppointmentsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnMyAppointments.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AppointmentsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun updateWelcomeText() {
        val userJson = tokenManager.getUserJson()
        if (!userJson.isNullOrBlank()) {
            try {
                val user = Gson().fromJson(userJson, UserDto::class.java)
                binding.tvWelcome.text = "Welcome, ${user.firstName ?: "Patient"}! \uD83D\uDC4B"
            } catch (e: Exception) {
                // Keep default text
            }
        }
    }

    private fun loadUpcomingAppointmentCard() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appointments = AppointmentRepository(
                    RetrofitClient.getInstance(context ?: return@launch).apiService
                ).getPatientAppointments()

                val upcoming = appointments
                    .filter { it.status.equals("PENDING", true) || it.status.equals("CONFIRMED", true) }
                    .minByOrNull { it.dateTime }

                if (upcoming != null) {
                    val formatted = runCatching {
                        LocalDateTime.parse(upcoming.dateTime, DateTimeFormatter.ISO_DATE_TIME)
                            .format(DateTimeFormatter.ofPattern("MMMM d, yyyy • hh:mm a", Locale.getDefault()))
                    }.getOrDefault(upcoming.dateTime.replace("T", " "))
                    binding.tvDoctorName.text = "Dr. ${upcoming.doctor.firstName} ${upcoming.doctor.lastName}"
                    binding.tvDoctorSpec.text = upcoming.doctor.specialization
                        ?: upcoming.doctor.specializations?.firstOrNull()
                        ?: "General Practice"
                    binding.tvUpcomingCount.text = formatted
                } else {
                    binding.tvDoctorName.text = "No upcoming appointment"
                    binding.tvDoctorSpec.text = ""
                    binding.tvUpcomingCount.text = "Book a doctor to get started"
                }
            } catch (e: Throwable) {
                if (context == null) return@launch
                binding.tvDoctorName.text = "No upcoming appointment"
                binding.tvDoctorSpec.text = ""
                binding.tvUpcomingCount.text = "Book a doctor to get started"
            }
        }
    }

    private fun logout() {
        tokenManager.clearSession()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }
}
