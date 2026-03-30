package com.medbuddy.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityBookAppointmentBinding
import com.medbuddy.dto.AppointmentRequest
import com.medbuddy.dto.DoctorAvailabilityRequest
import kotlinx.coroutines.launch

class BookAppointmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookAppointmentBinding
    private var doctorId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookAppointmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        doctorId = intent.getLongExtra("doctorId", -1L)
        val doctorName = intent.getStringExtra("doctorName") ?: "Doctor"
        binding.tvDoctorName.text = doctorName

        val role = sessionRole()
        val doctorMode = role == "DOCTOR" && doctorId == -1L

        if (doctorMode) {
            binding.btnBook.text = getString(R.string.btn_add_availability)
            binding.btnBook.setOnClickListener { addAvailability() }
        } else {
            binding.btnBook.setOnClickListener { bookAppointment() }
        }
        binding.btnLoadSlots.setOnClickListener { loadAvailabilityForDate() }
    }

    private fun bookAppointment() {
        if (doctorId <= 0) {
            showError(getString(R.string.error_generic))
            return
        }

        val date = binding.etDate.text.toString().trim()
        val time = binding.etTime.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim().ifBlank { null }

        if (date.isBlank() || time.isBlank()) {
            showError(getString(R.string.error_required))
            return
        }

        val dateTime = "${date}T${time}:00"
        setLoading(true)
        lifecycleScope.launch {
            try {
                RetrofitClient.getInstance(applicationContext).apiService.bookAppointment(
                    AppointmentRequest(doctorId, dateTime, notes)
                )
                AlertDialog.Builder(this@BookAppointmentActivity)
                    .setMessage(getString(R.string.success_booked))
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .show()
            } catch (e: Throwable) {
                showError(ApiErrorMapper.toUserMessage(this@BookAppointmentActivity, e, R.string.error_invalid_datetime))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun addAvailability() {
        val date = binding.etDate.text.toString().trim()
        val startTime = binding.etTime.text.toString().trim()
        if (date.isBlank() || startTime.isBlank()) {
            showError(getString(R.string.error_required))
            return
        }

        // Simple 1-hour slot step aligned with slot-based backend key.
        val hour = startTime.substringBefore(':').toIntOrNull() ?: run {
            showError(getString(R.string.error_invalid_datetime))
            return
        }
        val minute = startTime.substringAfter(':', "00").toIntOrNull() ?: 0
        val endHour = (hour + 1).coerceAtMost(23)
        val endTime = String.format("%02d:%02d", endHour, minute)

        setLoading(true)
        lifecycleScope.launch {
            try {
                RetrofitClient.getInstance(applicationContext).apiService.addAvailability(
                    DoctorAvailabilityRequest(
                        availableDate = date,
                        startTime = startTime,
                        endTime = endTime,
                        status = "AVAILABLE"
                    )
                )
                Toast.makeText(this@BookAppointmentActivity, getString(R.string.success_availability_added), Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                showError(ApiErrorMapper.toUserMessage(this@BookAppointmentActivity, e, R.string.error_invalid_datetime))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadAvailabilityForDate() {
        val date = binding.etDate.text.toString().trim()
        if (date.isBlank()) {
            showError(getString(R.string.error_required))
            return
        }

        val targetDoctorId = if (doctorId > 0) doctorId else TokenManager(applicationContext)
            .getUserJson()
            ?.let { json -> com.google.gson.Gson().fromJson(json, com.medbuddy.dto.UserDto::class.java).profileId }
            ?: -1L

        if (targetDoctorId <= 0) {
            showError(getString(R.string.error_generic))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val slots = RetrofitClient.getInstance(applicationContext)
                    .apiService
                    .getDoctorAvailabilityByDate(targetDoctorId, date)
                binding.tvSlots.text = if (slots.isEmpty()) {
                    getString(R.string.label_no_data)
                } else {
                    slots.joinToString("\n") { "${it.startTime} - ${it.endTime} (${it.status})" }
                }
            } catch (e: Throwable) {
                binding.tvSlots.text = ApiErrorMapper.toUserMessage(this@BookAppointmentActivity, e)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun sessionRole(): String? {
        val json = TokenManager(applicationContext).getUserJson() ?: return null
        return try {
            com.google.gson.Gson().fromJson(json, com.medbuddy.dto.UserDto::class.java).role
        } catch (_: Exception) {
            null
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvError.visibility = View.GONE
        binding.btnBook.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
    }
}

