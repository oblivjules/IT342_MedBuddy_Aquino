package com.medbuddy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.constants.AppConstants
import com.medbuddy.databinding.ActivityRegisterBinding
import com.medbuddy.dto.RegisterRequest
import com.medbuddy.api.bodyOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val selectedSpecializationIds = mutableSetOf<Long>()

    private val primaryColor: Int
        get() = ContextCompat.getColor(this, R.color.primary)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleLoginFooterLink()
        setupRoleToggle()
        setupListeners()
    }

    private fun styleLoginFooterLink() {
        val prefix = getString(R.string.register_footer_prefix)
        val action = getString(R.string.log_in_action)
        val ss = SpannableString(prefix + action)
        ss.setSpan(
            ForegroundColorSpan(primaryColor),
            prefix.length,
            prefix.length + action.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvGoLogin.text = ss
    }

    // =========================
    // ROLE TOGGLE
    // =========================
    private fun setupRoleToggle() {
        val btnPatient = binding.btnRolePatient
        val btnDoctor = binding.btnRoleDoctor

        binding.toggleRole.check(btnPatient.id)

        binding.toggleRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val isDoctor = checkedId == btnDoctor.id

            binding.layoutSpecializations.visibility =
                if (isDoctor) View.VISIBLE else View.GONE

            styleSelected(btnPatient, checkedId == btnPatient.id)
            styleSelected(btnDoctor, checkedId == btnDoctor.id)

            if (isDoctor && binding.containerSpecializations.childCount == 0) {
                loadSpecializations()
            }
        }

        styleSelected(btnPatient, true)
        styleSelected(btnDoctor, false)
    }

    private fun styleSelected(button: MaterialButton, selected: Boolean) {
        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(primaryColor)
            button.setTextColor(ContextCompat.getColor(this, R.color.surface))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.surface)
            )
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    // =========================
    // LOAD SPECIALIZATIONS (IDs from GET /api/specializations)
    // =========================
    private fun loadSpecializations() {
        binding.tvSpecError.visibility = View.GONE
        binding.btnRetrySpecs.visibility = View.GONE
        binding.progressSpecializations.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    RetrofitClient.getInstance(applicationContext).apiService.getSpecializations().bodyOrThrow()
                }

                binding.containerSpecializations.removeAllViews()
                selectedSpecializationIds.clear()

                if (list.isEmpty()) {
                    binding.tvSpecError.text = getString(R.string.specializations_empty)
                    binding.tvSpecError.visibility = View.VISIBLE
                    binding.btnRetrySpecs.visibility = View.VISIBLE
                    return@launch
                }

                list.forEach { spec ->
                    val checkBox = CheckBox(this@RegisterActivity)
                    checkBox.text = spec.name
                    checkBox.tag = spec.id
                    checkBox.setTextColor(ContextCompat.getColor(this@RegisterActivity, R.color.text_primary))
                    checkBox.buttonTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        ),
                        intArrayOf(
                            ContextCompat.getColor(this@RegisterActivity, R.color.primary),
                            ContextCompat.getColor(this@RegisterActivity, R.color.text_primary)
                        )
                    )

                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        val id = spec.id
                        if (isChecked) {
                            selectedSpecializationIds.add(id)
                        } else {
                            selectedSpecializationIds.remove(id)
                        }
                    }

                    binding.containerSpecializations.addView(checkBox)
                }
            } catch (e: Throwable) {
                binding.tvSpecError.text = ApiErrorMapper.toUserMessage(
                    this@RegisterActivity,
                    e,
                    R.string.specializations_load_failed_local_hint
                )
                binding.tvSpecError.visibility = View.VISIBLE
                binding.btnRetrySpecs.visibility = View.VISIBLE
            } finally {
                binding.progressSpecializations.visibility = View.GONE
            }
        }
    }

    // =========================
    // LISTENERS + LIVE VALIDATION
    // =========================
    private fun setupListeners() {
        binding.btnCreateAccount.setOnClickListener { register() }
        binding.tvGoLogin.setOnClickListener { finish() }
        binding.btnRetrySpecs.setOnClickListener { loadSpecializations() }

        binding.etFirstName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateFirstName()
        }

        binding.etLastName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateLastName()
        }

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateEmail()
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePassword()
        }

        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateConfirmPassword()
        }

        binding.etPhoneNumber.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePhoneNumber()
        }
    }

    private fun setFieldError(view: View, hasError: Boolean) {
        view.isActivated = hasError
        view.setBackgroundResource(R.drawable.bg_input)
    }

    private fun showFieldError(errorView: android.widget.TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun clearFieldError(errorView: android.widget.TextView) {
        errorView.visibility = View.GONE
    }

    private fun validateFirstName(): Boolean {
        val name = binding.etFirstName.text.toString().trim()
        return when {
            name.isBlank() -> {
                setFieldError(binding.etFirstName, true)
                showFieldError(binding.tvErrorFirstName, "Required")
                false
            }
            name.length < 2 -> {
                setFieldError(binding.etFirstName, true)
                showFieldError(binding.tvErrorFirstName, "Too short")
                false
            }
            else -> {
                setFieldError(binding.etFirstName, false)
                clearFieldError(binding.tvErrorFirstName)
                true
            }
        }
    }

    private fun validateLastName(): Boolean {
        val name = binding.etLastName.text.toString().trim()
        return when {
            name.isBlank() -> {
                setFieldError(binding.etLastName, true)
                showFieldError(binding.tvErrorLastName, "Required")
                false
            }
            name.length < 2 -> {
                setFieldError(binding.etLastName, true)
                showFieldError(binding.tvErrorLastName, "Too short")
                false
            }
            else -> {
                setFieldError(binding.etLastName, false)
                clearFieldError(binding.tvErrorLastName)
                true
            }
        }
    }

    private fun validateEmail(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        return when {
            email.isBlank() -> {
                setFieldError(binding.etEmail, true)
                showFieldError(binding.tvErrorEmail, "Required")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                setFieldError(binding.etEmail, true)
                showFieldError(binding.tvErrorEmail, "Invalid email")
                false
            }
            else -> {
                setFieldError(binding.etEmail, false)
                clearFieldError(binding.tvErrorEmail)
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        val password = binding.etPassword.text.toString()
        return when {
            password.isBlank() -> {
                setFieldError(binding.etPassword, true)
                showFieldError(binding.tvErrorPassword, "Required")
                false
            }
            password.length < 8 -> {
                setFieldError(binding.etPassword, true)
                showFieldError(binding.tvErrorPassword, "Min. 8 characters")
                false
            }
            else -> {
                setFieldError(binding.etPassword, false)
                clearFieldError(binding.tvErrorPassword)
                true
            }
        }
    }

    private fun validateConfirmPassword(): Boolean {
        val confirm = binding.etConfirmPassword.text.toString()
        val password = binding.etPassword.text.toString()
        return when {
            confirm.isBlank() -> {
                setFieldError(binding.etConfirmPassword, true)
                showFieldError(binding.tvErrorConfirmPassword, "Required")
                false
            }
            confirm != password -> {
                setFieldError(binding.etConfirmPassword, true)
                showFieldError(binding.tvErrorConfirmPassword, "Doesn't match")
                false
            }
            else -> {
                setFieldError(binding.etConfirmPassword, false)
                clearFieldError(binding.tvErrorConfirmPassword)
                true
            }
        }
    }

    private fun validatePhoneNumber(): Boolean {
        val phoneDigits = binding.etPhoneNumber.text.toString().filter { it.isDigit() }
        val valid = phoneDigits.length == 10
        setFieldError(binding.layoutPhoneContainer, !valid)
        if (valid) clearFieldError(binding.tvErrorPhone) else showFieldError(binding.tvErrorPhone, "Must be 10 digits")
        return valid
    }

    // =========================
    // REGISTER
    // =========================
    private fun register() {
        val isDoctor = binding.toggleRole.checkedButtonId == binding.btnRoleDoctor.id
        val role = if (isDoctor) AppConstants.Role.DOCTOR else AppConstants.Role.PATIENT

        val allValid = validateFirstName() and validateLastName() and
                       validateEmail() and validatePassword() and
                       validateConfirmPassword() and validatePhoneNumber()
        if (!allValid) return

        if (isDoctor && selectedSpecializationIds.isEmpty()) {
            showError(getString(R.string.error_doctor_specialization))
            return
        }

        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val phoneDigits = binding.etPhoneNumber.text.toString().filter { it.isDigit() }

        setLoading(true)

        lifecycleScope.launch {
            try {
                RetrofitClient.getInstance(applicationContext).apiService.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        role = role,
                        firstName = firstName,
                        lastName = lastName,
                        phoneNumber = "+63$phoneDigits",
                        specializationIds = if (isDoctor) selectedSpecializationIds.toList() else null
                    )
                ).bodyOrThrow()

                Toast.makeText(
                    this@RegisterActivity,
                    getString(R.string.success_registered),
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                finish()

            } catch (e: Throwable) {
                showError(resolveRegisterErrorMessage(e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resolveRegisterErrorMessage(throwable: Throwable): String {
        if (throwable is HttpException) {
            val errorBody = runCatching {
                throwable.response()?.errorBody()?.string()
            }.getOrNull().orEmpty()

            val serverMessage = runCatching {
                val json = JSONObject(errorBody)
                json.optString("detail").takeIf { it.isNotBlank() }
                    ?: json.optString("message").takeIf { it.isNotBlank() }
            }.getOrNull()

            if (!serverMessage.isNullOrBlank()) {
                return serverMessage
            }
        }

        return getString(R.string.error_registration_failed)
    }

    private fun setLoading(loading: Boolean) {
        binding.btnCreateAccount.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
