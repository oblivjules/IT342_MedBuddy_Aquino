package com.medbuddy.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import com.medbuddy.databinding.ActivityMainBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.ui.fragments.patient.PatientHomeFragment
import com.medbuddy.ui.fragments.patient.AppointmentsFragment
import com.medbuddy.ui.fragments.patient.FindDoctorFragment
import com.medbuddy.ui.fragments.patient.PatientProfileFragment
import com.medbuddy.ui.fragments.doctor.DoctorHomeFragment
import com.medbuddy.ui.fragments.doctor.ScheduleFragment
import com.medbuddy.ui.fragments.doctor.DoctorAppointmentsFragment
import com.medbuddy.ui.fragments.doctor.DoctorProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tokenManager: TokenManager
    private var userRole: String? = null
    private val sessionExpiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AppConstants.Auth.ACTION_SESSION_EXPIRED) {
                tokenManager.clearSession()
                navigateToLogin()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(applicationContext)

        // Check if user is logged in
        if (!tokenManager.isLoggedIn()) {
            navigateToLogin()
            return
        }

        // Load user role
        val userJson = tokenManager.getUserJson()
        if (userJson == null) {
            tokenManager.clearSession()
            navigateToLogin()
            return
        }

        try {
            val user = Gson().fromJson(userJson, UserDto::class.java)
            userRole = user.role
        } catch (e: Exception) {
            tokenManager.clearSession()
            navigateToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation(savedInstanceState == null)
    }

    private fun setupBottomNavigation(isFirstCreation: Boolean) {
        val menu = if (userRole == AppConstants.Role.DOCTOR) {
            R.menu.menu_bottom_nav_doctor
        } else {
            R.menu.menu_bottom_nav_patient
        }

        binding.bottomNavigation.inflateMenu(menu)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // Patient navigation
                R.id.nav_patient_home -> {
                    loadFragment(PatientHomeFragment())
                    true
                }
                R.id.nav_patient_appointments -> {
                    loadFragment(AppointmentsFragment())
                    true
                }
                R.id.nav_find_doctor -> {
                    loadFragment(FindDoctorFragment())
                    true
                }
                R.id.nav_patient_profile -> {
                    loadFragment(PatientProfileFragment())
                    true
                }

                // Doctor navigation
                R.id.nav_doctor_home -> {
                    loadFragment(DoctorHomeFragment())
                    true
                }
                R.id.nav_doctor_schedule -> {
                    loadFragment(ScheduleFragment())
                    true
                }
                R.id.nav_doctor_appointments -> {
                    loadFragment(DoctorAppointmentsFragment())
                    true
                }
                R.id.nav_doctor_profile -> {
                    loadFragment(DoctorProfileFragment())
                    true
                }

                else -> false
            }
        }

        // Load the first fragment by default
        if (isFirstCreation) {
            val defaultFragment = if (userRole == AppConstants.Role.DOCTOR) {
                DoctorHomeFragment()
            } else {
                PatientHomeFragment()
            }
            loadFragment(defaultFragment)
            binding.bottomNavigation.selectedItemId = if (userRole == AppConstants.Role.DOCTOR) {
                R.id.nav_doctor_home
            } else {
                R.id.nav_patient_home
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onBackPressed() {
        val count = supportFragmentManager.backStackEntryCount
        if (count <= 1) {
            // Don't pop the last fragment, just super call (which will exit)
            super.onBackPressed()
        } else {
            supportFragmentManager.popBackStack()
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            sessionExpiredReceiver,
            IntentFilter(AppConstants.Auth.ACTION_SESSION_EXPIRED),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(sessionExpiredReceiver) }
        super.onStop()
    }
}
