package com.medbuddy.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.ActivityPatientDashboardBinding

class PatientDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardFindDoctor.setOnClickListener {
            startActivity(Intent(this, FindDoctorActivity::class.java))
        }

        binding.cardAppointments.setOnClickListener {
            startActivity(Intent(this, MyAppointmentsActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            TokenManager(applicationContext).clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        }
    }
}

