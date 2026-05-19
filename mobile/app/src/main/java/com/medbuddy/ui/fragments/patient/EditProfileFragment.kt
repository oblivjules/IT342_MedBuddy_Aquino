package com.medbuddy.ui.fragments.patient

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentEditProfileBinding
import com.medbuddy.dto.UpdateProfileRequest
import com.medbuddy.dto.UserDto
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {

    private lateinit var binding: FragmentEditProfileBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCurrentProfile()
        binding.etNewPassword.addTextChangedListener(passwordWatcher)
        binding.btnSave.setOnClickListener { saveChanges() }
        binding.btnDiscard.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun loadCurrentProfile() {
        val userJson = tokenManager.getUserJson().orEmpty()
        val user = runCatching { Gson().fromJson(userJson, UserDto::class.java) }.getOrNull() ?: return
        binding.etFirstName.setText(user.firstName.orEmpty())
        binding.etLastName.setText(user.lastName.orEmpty())
        binding.etEmail.setText(user.email.orEmpty())
        binding.etPhone.setText(user.phoneNumber.orEmpty())
    }

    private val passwordWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            val password = s?.toString().orEmpty()
            val hasContent = password.isNotEmpty()
            binding.layoutPasswordRules.visibility = if (hasContent) View.VISIBLE else View.GONE
            updatePasswordRule(binding.tvRuleLength, password.length >= 8)
            updatePasswordRule(binding.tvRuleUppercase, password.any { it.isUpperCase() })
            updatePasswordRule(binding.tvRuleNumber, password.any { it.isDigit() })
            updatePasswordRule(binding.tvRuleSpecial, password.any { !it.isLetterOrDigit() })
        }
    }

    private fun updatePasswordRule(view: android.widget.TextView, met: Boolean) {
        view.setTextColor(
            ContextCompat.getColor(requireContext(), if (met) R.color.primary else R.color.text_secondary)
        )
    }

    private fun saveChanges() {
        binding.tvError.visibility = View.GONE
        binding.tvSuccess.visibility = View.GONE

        val firstName = binding.etFirstName.text?.toString()?.trim()
        val lastName = binding.etLastName.text?.toString()?.trim()
        val email = binding.etEmail.text?.toString()?.trim()
        val phone = binding.etPhone.text?.toString()?.trim()
        val currentPassword = binding.etCurrentPassword.text?.toString()?.takeIf { it.isNotBlank() }
        val newPassword = binding.etNewPassword.text?.toString()?.takeIf { it.isNotBlank() }
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.takeIf { it.isNotBlank() }

        if (email != null && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.")
            return
        }

        if (newPassword != null) {
            if (currentPassword == null) { showError("Enter your current password to set a new one."); return }
            if (newPassword.length < 8) { showError("New password must be at least 8 characters."); return }
            if (!newPassword.any { it.isUpperCase() }) { showError("New password needs an uppercase letter."); return }
            if (!newPassword.any { it.isDigit() }) { showError("New password needs a number."); return }
            if (!newPassword.any { !it.isLetterOrDigit() }) { showError("New password needs a special character."); return }
            if (newPassword != confirmPassword) { showError("Passwords do not match."); return }
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = UpdateProfileRequest(
                    firstName = firstName?.ifBlank { null },
                    lastName = lastName?.ifBlank { null },
                    email = email?.ifBlank { null },
                    phoneNumber = phone?.ifBlank { null },
                    currentPassword = currentPassword,
                    newPassword = newPassword
                )
                val response = RetrofitClient.getInstance(requireContext()).apiService.updateMe(request).bodyOrThrow()
                tokenManager.saveToken(response.token)
                tokenManager.saveUserJson(Gson().toJson(response.user))
                binding.tvSuccess.text = "Profile updated successfully."
                binding.tvSuccess.visibility = View.VISIBLE
                binding.etCurrentPassword.setText("")
                binding.etNewPassword.setText("")
                binding.etConfirmPassword.setText("")
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "Failed to update profile.")
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
