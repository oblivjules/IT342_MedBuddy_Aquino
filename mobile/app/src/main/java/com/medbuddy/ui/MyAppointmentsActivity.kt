package com.medbuddy.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityMyAppointmentsBinding
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
            confirmStatusChange(appointment.id, targetStatus)
        }

        binding.recyclerAppointments.layoutManager = LinearLayoutManager(this)
        binding.recyclerAppointments.adapter = adapter

        loadAppointments()
    }

    private fun loadAppointments() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val items = RetrofitClient.getInstance(applicationContext).apiService.getMyAppointments()
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
                    )
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

