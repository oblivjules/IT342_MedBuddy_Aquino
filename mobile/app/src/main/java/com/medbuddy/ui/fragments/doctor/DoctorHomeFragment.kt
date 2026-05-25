package com.medbuddy.ui.fragments.doctor

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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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
            binding.tvWelcome.text = "Welcome, Dr. ${user.lastName ?: "Doctor"}!"
        }
    }

    private fun loadTodayOverview() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appointments = AppointmentRepository(
                    RetrofitClient.getInstance(context ?: return@launch).apiService
                ).getDoctorAppointments()

                val today = LocalDate.now()
                val todayAppointments = appointments.filter { apt ->
                    parseDateTime(apt.dateTime)?.toLocalDate() == today
                }

                val total = todayAppointments.size
                val completed = todayAppointments.count { it.status.equals("COMPLETED", true) }
                binding.tvOverview.text = "Today: $total appointment${if (total != 1) "s" else ""} | $completed completed"

                val now = LocalDateTime.now()
                val next = todayAppointments
                    .filter { it.status.equals("PENDING", true) || it.status.equals("CONFIRMED", true) }
                    .mapNotNull { apt -> parseDateTime(apt.dateTime)?.let { dt -> apt to dt } }
                    .filter { (_, dt) -> dt.isAfter(now) }
                    .minByOrNull { (_, dt) -> dt }
                    ?.first

                if (next != null) {
                    val patientName = "${next.patient.firstName} ${next.patient.lastName}".trim()
                    val time = parseDateTime(next.dateTime)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())) ?: next.dateTime
                    binding.tvNextPatient.text = "Next: $patientName at $time"
                } else {
                    binding.tvNextPatient.text = "No upcoming patients today"
                }

                val listText = todayAppointments
                    .take(3)
                    .joinToString("\n") { apt ->
                        val patientName = "${apt.patient.firstName} ${apt.patient.lastName}".trim()
                        val time = parseDateTime(apt.dateTime)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())) ?: apt.dateTime
                        "$patientName - ${apt.notes?.takeIf { it.isNotBlank() } ?: "Consultation"} - $time"
                    }
                    .ifBlank { "No appointments today" }
                binding.tvUpcomingList.text = listText
            } catch (e: Throwable) {
                val safeContext = context ?: return@launch
                binding.tvOverview.text = "Today: 0 appointments"
                binding.tvNextPatient.text = "No upcoming patients today"
                binding.tvUpcomingList.text = "No appointments today"
                Toast.makeText(safeContext, "Unable to load today's overview", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        runCatching { LocalDateTime.parse(value) }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull()?.let { return it }
        runCatching {
            val normalized = value.replace(Regex("\\.\\d+$"), "")
            LocalDateTime.parse(normalized)
        }.getOrNull()?.let { return it }
        return null
    }
}
