package com.medbuddy.ui.fragments.patient

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentPatientProfileRefinedBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.ui.LoginActivity

class PatientProfileFragment : Fragment() {

    private lateinit var binding: FragmentPatientProfileRefinedBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPatientProfileRefinedBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProfileInfo()

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit profile is not connected yet.", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener { logout() }
    }

    private fun loadProfileInfo() {
        val userJson = tokenManager.getUserJson().orEmpty()
        val user = runCatching { Gson().fromJson(userJson, UserDto::class.java) }.getOrNull()
        val firstName = user?.firstName.orEmpty()
        val lastName = user?.lastName.orEmpty()
        binding.tvAvatar.text = patientInitials(firstName, lastName)
        binding.tvName.text = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Patient" }
        binding.tvEmail.text = user?.email.orEmpty()
        binding.tvPatientId.text = user?.profileId?.let { "#${it}" } ?: "#${user?.id ?: "-"}"

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        binding.switchNotifications.isChecked = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, checked).apply()
        }
    }

    private fun logout() {
        tokenManager.clearSession()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }

    companion object {
        private const val PREFS_NAME = "patient_profile_prefs"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
