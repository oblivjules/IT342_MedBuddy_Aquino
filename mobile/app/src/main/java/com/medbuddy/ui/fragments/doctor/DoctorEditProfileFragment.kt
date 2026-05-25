package com.medbuddy.ui.fragments.doctor

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.auth.TokenManager
import com.medbuddy.databinding.FragmentDoctorEditProfileBinding
import com.medbuddy.dto.SpecializationDto
import com.medbuddy.dto.UpdateProfileRequest
import com.medbuddy.dto.UserDto
import com.medbuddy.ui.fragments.patient.doctorInitials
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class DoctorEditProfileFragment : Fragment() {

    private lateinit var binding: FragmentDoctorEditProfileBinding
    private lateinit var tokenManager: TokenManager

    private var currentUser: UserDto? = null
    private var allSpecializations: List<SpecializationDto> = emptyList()
    private val selectedSpecializationIds = linkedSetOf<Long>()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadSelectedImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentDoctorEditProfileBinding.inflate(inflater, container, false)
        tokenManager = TokenManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadInitialData()

        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val filtered = s?.toString().orEmpty().filter { it.isDigit() }.take(10)
                if (filtered != s?.toString().orEmpty()) {
                    binding.etPhone.setText(filtered)
                    binding.etPhone.setSelection(filtered.length)
                }
            }
        })

        binding.etNewPassword.addTextChangedListener(passwordWatcher)

        binding.btnChoosePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            saveChanges()
        }

        binding.btnDiscard.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadInitialData() {
        val cachedUser = runCatching {
            val userJson = tokenManager.getUserJson().orEmpty()
            Gson().fromJson(userJson, UserDto::class.java)
        }.getOrNull()

        cachedUser?.let {
            currentUser = it
            populateUser(it)
        }

        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getInstance(requireContext()).apiService
                val userDeferred = async { apiService.getMe().bodyOrThrow() }
                val specsDeferred = async { apiService.getSpecializations().bodyOrThrow() }

                val liveUser = userDeferred.await()
                val specs = specsDeferred.await()

                currentUser = liveUser
                allSpecializations = specs
                populateUser(liveUser)
                renderSpecializationChips(liveUser)
            } catch (_: Throwable) {
                if (cachedUser == null) {
                    showError("Failed to load profile details.")
                }
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun populateUser(user: UserDto) {
        binding.etFirstName.setText(user.firstName.orEmpty())
        binding.etLastName.setText(user.lastName.orEmpty())
        binding.etEmail.setText(user.email.orEmpty())

        val digits = user.phoneNumber.orEmpty().removePrefix("+63")
        binding.etPhone.setText(digits)

        val firstName = user.firstName.orEmpty()
        val lastName = user.lastName.orEmpty()
        binding.tvAvatar.text = doctorInitials(firstName, lastName)

        val imageUrl = user.profileImageUrl
        if (!imageUrl.isNullOrBlank()) {
            binding.ivAvatar.visibility = View.VISIBLE
            binding.tvAvatar.visibility = View.GONE
            Glide.with(this).load(imageUrl).circleCrop().into(binding.ivAvatar)
        } else {
            binding.ivAvatar.visibility = View.GONE
            binding.tvAvatar.visibility = View.VISIBLE
        }
    }

    private fun renderSpecializationChips(user: UserDto) {
        selectedSpecializationIds.clear()
        val selectedNames = user.specializations.orEmpty().toSet()
        selectedSpecializationIds += allSpecializations
            .filter { selectedNames.contains(it.name) }
            .map { it.id }

        binding.chipGroupSpecializations.removeAllViews()

        allSpecializations.forEach { specialization ->
            val chip = Chip(requireContext()).apply {
                text = specialization.name
                isCheckable = true
                isClickable = true
                isChecked = selectedSpecializationIds.contains(specialization.id)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedSpecializationIds.add(specialization.id)
                    } else {
                        selectedSpecializationIds.remove(specialization.id)
                    }
                }
            }
            binding.chipGroupSpecializations.addView(chip)
        }
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

    private fun updatePasswordRule(view: TextView, met: Boolean) {
        view.setTextColor(
            ContextCompat.getColor(requireContext(), if (met) R.color.primary else R.color.text_secondary),
        )
    }

    private fun saveChanges() {
        binding.tvError.visibility = View.GONE
        binding.tvSuccess.visibility = View.GONE

        val firstName = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val lastName = binding.etLastName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val phoneDigits = binding.etPhone.text?.toString()?.trim().orEmpty()
        val currentPassword = binding.etCurrentPassword.text?.toString()?.takeIf { it.isNotBlank() }
        val newPassword = binding.etNewPassword.text?.toString()?.takeIf { it.isNotBlank() }
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.takeIf { it.isNotBlank() }

        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.")
            return
        }

        if (phoneDigits.isNotBlank() && phoneDigits.length != 10) {
            showError("Phone number must be 10 digits.")
            return
        }

        if (newPassword != null) {
            if (currentPassword == null) {
                showError("Enter your current password to set a new one.")
                return
            }
            if (newPassword.length < 8) {
                showError("New password must be at least 8 characters.")
                return
            }
            if (!newPassword.any { it.isUpperCase() }) {
                showError("New password needs an uppercase letter.")
                return
            }
            if (!newPassword.any { it.isDigit() }) {
                showError("New password needs a number.")
                return
            }
            if (!newPassword.any { !it.isLetterOrDigit() }) {
                showError("New password needs a special character.")
                return
            }
            if (newPassword != confirmPassword) {
                showError("Passwords do not match.")
                return
            }
        }

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = UpdateProfileRequest(
                    firstName = firstName.ifBlank { null },
                    lastName = lastName.ifBlank { null },
                    email = email.ifBlank { null },
                    phoneNumber = phoneDigits.ifBlank { null }?.let { "+63$it" },
                    specializationIds = selectedSpecializationIds.toList().ifEmpty { emptyList() },
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                )
                val response = RetrofitClient.getInstance(requireContext()).apiService.updateMe(request).bodyOrThrow()
                tokenManager.saveToken(response.token)
                tokenManager.saveUserJson(Gson().toJson(response.user))
                currentUser = response.user
                populateUser(response.user)
                binding.tvSuccess.text = "Profile updated successfully."
                binding.tvSuccess.visibility = View.VISIBLE
                binding.etCurrentPassword.setText("")
                binding.etNewPassword.setText("")
                binding.etConfirmPassword.setText("")
                binding.layoutPasswordRules.visibility = View.GONE
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "Failed to update profile.")
            } finally {
                binding.btnSave.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun uploadSelectedImage(uri: Uri) {
        binding.tvError.visibility = View.GONE
        binding.tvSuccess.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).apiService
                val updatedUser = api.uploadMyProfileImage(uri.toMultipartPart()).bodyOrThrow()
                tokenManager.saveUserJson(Gson().toJson(updatedUser))
                currentUser = updatedUser
                populateUser(updatedUser)
                binding.tvSuccess.text = "Profile picture updated successfully."
                binding.tvSuccess.visibility = View.VISIBLE
            } catch (throwable: Throwable) {
                showError(throwable.message ?: "Failed to upload profile image.")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun Uri.toMultipartPart(): MultipartBody.Part {
        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(this)?.toMediaTypeOrNull() ?: "image/*".toMediaTypeOrNull()
        val fileName = displayNameForUri(this).ifBlank { "doctor-profile" }
        val bytes = resolver.openInputStream(this)?.use { input -> input.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected image")
        val requestBody = bytes.toRequestBody(mimeType)
        return MultipartBody.Part.createFormData("file", fileName, requestBody)
    }

    private fun displayNameForUri(uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
