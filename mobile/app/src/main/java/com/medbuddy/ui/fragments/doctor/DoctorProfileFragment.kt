package com.medbuddy.ui.fragments.doctor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentDoctorProfileBinding
import com.medbuddy.dto.UserDto
import com.medbuddy.ui.LoginActivity

class DoctorProfileFragment : Fragment() {

    private lateinit var binding: FragmentDoctorProfileBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDoctorProfileBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfileInfo()

        binding.btnPatientFeedback.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(com.medbuddy.R.id.fragmentContainer, DoctorRatingsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadProfileInfo() {
        val userJson = tokenManager.getUserJson()
        val user = runCatching {
            Gson().fromJson(userJson, UserDto::class.java)
        }.getOrNull()

        val first = user?.firstName.orEmpty()
        val last = user?.lastName.orEmpty()
        binding.tvName.text = "Dr. $first $last".trim()
        binding.tvEmail.text = user?.email.orEmpty()

        val imageUrl = user?.profileImageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatar.visibility = View.GONE
            Glide.with(this).load(imageUrl).circleCrop().into(binding.ivAvatar)
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatar.visibility = View.VISIBLE
            val initials = "${first.firstOrNull() ?: 'D'}${last.firstOrNull() ?: 'R'}".uppercase()
            binding.tvAvatar.text = initials
        }

        val phone = user?.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null) {
            binding.tvPhone.text = phone
            binding.rowPhone.visibility = View.VISIBLE
        }

        val specialization = user?.specializations?.joinToString(", ")
            ?: user?.specialization ?: "General Practice"
        binding.tvSpecialization.text = "Specialization: $specialization"

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
