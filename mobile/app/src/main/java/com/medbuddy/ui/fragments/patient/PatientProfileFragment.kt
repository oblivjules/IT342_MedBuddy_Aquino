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
import com.medbuddy.databinding.FragmentPatientProfileBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.ui.LoginActivity

class PatientProfileFragment : Fragment() {

    private lateinit var binding: FragmentPatientProfileBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPatientProfileBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfileInfo()

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadProfileInfo() {
        val userJson = tokenManager.getUserJson()
        if (!userJson.isNullOrBlank()) {
            try {
                val user = Gson().fromJson(userJson, UserDto::class.java)
                val first = user.firstName.orEmpty()
                val last = user.lastName.orEmpty()
                val initials = "${first.firstOrNull() ?: 'P'}${last.firstOrNull() ?: 'T'}".uppercase()
                binding.tvAvatar.text = initials
                binding.tvName.text = "$first $last".trim()
                binding.tvEmail.text = "Email\n${user.email}"
                binding.tvPatientId.text = "Patient ID\n#${user.profileId ?: user.id}"
            } catch (e: Exception) {
                binding.tvName.text = "Patient"
                binding.tvEmail.text = "Email\n-"
                binding.tvPatientId.text = "Patient ID\n-"
            }
        }

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(requireContext(), "Edit profile flow coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        tokenManager.clearSession()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }
}
