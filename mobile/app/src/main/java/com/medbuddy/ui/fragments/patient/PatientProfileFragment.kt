package com.medbuddy.ui.fragments.patient

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
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
        binding.progressBar.visibility = View.GONE
        binding.scrollContent.visibility = View.VISIBLE
        loadProfileInfo()

        binding.btnMyReviews.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(com.medbuddy.R.id.fragmentContainer, PatientRatingsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(com.medbuddy.R.id.fragmentContainer, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnLogout.setOnClickListener { logout() }
    }

    private fun loadProfileInfo() {
        val userJson = tokenManager.getUserJson().orEmpty()
        val user = runCatching { Gson().fromJson(userJson, UserDto::class.java) }.getOrNull()
        val firstName = user?.firstName.orEmpty()
        val lastName = user?.lastName.orEmpty()
        binding.tvName.text = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Patient" }
        binding.tvEmail.text = user?.email.orEmpty()

        val imageUrl = user?.profileImageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatar.visibility = View.GONE
            Glide.with(this).load(imageUrl).circleCrop().into(binding.ivAvatar)
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatar.visibility = View.VISIBLE
            binding.tvAvatar.text = patientInitials(firstName, lastName)
        }

        val phone = user?.phoneNumber?.takeIf { it.isNotBlank() }
        if (phone != null) {
            binding.tvPhone.text = phone
            binding.rowPhone.visibility = View.VISIBLE
        }
    }

    private fun logout() {
        tokenManager.clearSession()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        requireActivity().finish()
    }
}
