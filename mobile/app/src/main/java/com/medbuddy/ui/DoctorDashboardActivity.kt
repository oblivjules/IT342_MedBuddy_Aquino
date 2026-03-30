package com.medbuddy.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityDoctorDashboardBinding

class DoctorDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDoctorDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoctorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardAppointments.setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }

        binding.cardAvailability.setOnClickListener {
            startActivity(Intent(this, BookAppointmentActivity::class.java).apply {
                putExtra("doctorId", -1L)
                putExtra("doctorName", "My Availability")
            })
        }

        binding.btnLogout.setOnClickListener {
            TokenManager(applicationContext).clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }
}

