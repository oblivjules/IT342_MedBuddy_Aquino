package com.medbuddy.ui.fragments.doctor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentDoctorHomeBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.repository.AppointmentRepository
import com.medbuddy.ui.LoginActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DoctorHomeFragment : Fragment() {

    private lateinit var binding: FragmentDoctorHomeBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDoctorHomeBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindUserHeader()
        binding.tvDate.text = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        binding.btnViewSchedule.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.nav_doctor_schedule
        }
        binding.btnAddAvailability.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.nav_doctor_schedule
        }
        binding.btnStartConsultation.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.nav_doctor_appointments
        }
        loadTodayOverview()
    }

    private fun bindUserHeader() {
        val userJson = tokenManager.getUserJson().orEmpty()
        runCatching {
            val user = Gson().fromJson(userJson, UserDto::class.java)
            binding.tvWelcome.text = "Welcome, Dr. ${user.lastName ?: "Doctor"}! \uD83D\uDC4B"
        }
    }

    private fun loadTodayOverview() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appointments = AppointmentRepository(
                    RetrofitClient.getInstance(context ?: return@launch).apiService
                ).getDoctorAppointments()
                val total = appointments.size
                val completed = appointments.count { it.status.equals("COMPLETED", true) }
                binding.tvOverview.text = "Appointments: $total | Completed: $completed"

                val next = appointments
                    .filter { it.status.equals("PENDING", true) || it.status.equals("CONFIRMED", true) }
                    .minByOrNull { it.dateTime }
                if (next != null) {
                    binding.tvNextPatient.text = "Next Patient: ${next.patient.firstName} ${next.patient.lastName} • ${next.dateTime.replace("T", " ")}"
                } else {
                    binding.tvNextPatient.text = "Next Patient: -"
                }

                val listText = appointments
                    .take(3)
                    .joinToString("\n") {
                        "${it.patient.firstName} ${it.patient.lastName} - ${it.notes ?: "Consultation"} - ${it.dateTime.replace("T", " ")}"
                    }
                    .ifBlank { "No upcoming appointments" }
                binding.tvUpcomingList.text = listText
            } catch (e: Throwable) {
                val safeContext = context ?: return@launch
                binding.tvOverview.text = "Appointments: 0 | Completed: 0"
                binding.tvNextPatient.text = "Next Patient: -"
                binding.tvUpcomingList.text = "No upcoming appointments"
                Toast.makeText(safeContext, "Unable to load today's overview", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        tokenManager.clearSession()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }
}
