package com.medbuddy.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityMyAppointmentsBinding
import com.medbuddy.dto.AppointmentResponse
import kotlinx.coroutines.launch

class MyAppointmentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyAppointmentsBinding
    private lateinit var adapter: AppointmentAdapter
    private var role: String = "PATIENT"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyAppointmentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        role = resolveRole()
        adapter = AppointmentAdapter(role) { appointment, targetStatus ->
            handleAppointmentAction(appointment, targetStatus)
        }

        binding.recyclerAppointments.layoutManager = LinearLayoutManager(this)
        binding.recyclerAppointments.adapter = adapter

        // Add item click listener to navigate to detail activity
        adapter.setOnDetailsClickListener { appointment ->
            navigateToAppointmentDetail(appointment)
        }

        loadAppointments()
    }

    private fun loadAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val items = RetrofitClient.getInstance(applicationContext).apiService.getMyAppointments().bodyOrThrow()
                adapter.submitList(items)
                if (items.isEmpty()) {
                    binding.tvEmpty.text = getString(R.string.label_no_data)
                    binding.tvEmpty.visibility = View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
            } catch (e: Throwable) {
                binding.tvEmpty.text = ApiErrorMapper.toUserMessage(this@MyAppointmentsActivity, e)
                binding.tvEmpty.visibility = View.VISIBLE
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun handleAppointmentAction(appointment: AppointmentResponse, targetStatus: String) {
        when (targetStatus) {
            "VIEW_RECORD" -> navigateToMedicalHistory()
            else -> confirmStatusChange(appointment.id, targetStatus)
        }
    }

    private fun navigateToAppointmentDetail(appointment: AppointmentResponse) {
        val intent = if (role == "DOCTOR") {
            Intent(this, AppointmentDetailDoctorActivity::class.java)
        } else {
            Intent(this, AppointmentDetailPatientActivity::class.java)
        }
        intent.putExtra("appointment", appointment)
        startActivity(intent)
    }

    private fun navigateToMedicalHistory() {
        val intent = Intent(this, MedicalHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun confirmStatusChange(appointmentId: Long, targetStatus: String) {
        AlertDialog.Builder(this)
            .setMessage("Set appointment status to $targetStatus?")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                updateStatus(appointmentId, targetStatus)
            }
            .show()
    }

    private fun updateStatus(appointmentId: Long, targetStatus: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                RetrofitClient.getInstance(applicationContext).apiService
                    .updateAppointmentStatus(
                        appointmentId,
                        com.medbuddy.dto.AppointmentStatusRequest(targetStatus)
                    ).bodyOrThrow()
                loadAppointments()
            } catch (_: Exception) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun resolveRole(): String {
        val json = TokenManager(applicationContext).getUserJson() ?: return "PATIENT"
        return try {
            Gson().fromJson(json, com.medbuddy.dto.UserDto::class.java).role
        } catch (_: Exception) {
            "PATIENT"
        }
    }
}

