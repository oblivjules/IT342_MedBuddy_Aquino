package com.medbuddy.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.R
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import com.medbuddy.databinding.ActivityRegisterBinding
import com.medbuddy.dto.RegisterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        setupSpecializationScrollBehavior()

        setupRoleToggle()
        setupListeners()
    }

    private fun setupSpecializationScrollBehavior() {
        binding.scrollSpecializations.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
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
                    RetrofitClient.getInstance(applicationContext).apiService.getSpecializations()
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

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateEmail()
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validatePassword()
        }

        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateConfirmPassword()
        }
    }

    private fun setFieldError(view: View, hasError: Boolean) {
        view.isActivated = hasError
        view.setBackgroundResource(R.drawable.bg_input)
    }

    private fun validateEmail(): Boolean {
        val email = binding.etEmail.text.toString()
        val valid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
        setFieldError(binding.etEmail, !valid)
        return valid
    }

    private fun validatePassword(): Boolean {
        val password = binding.etPassword.text.toString()
        val valid = password.length >= 8
        setFieldError(binding.etPassword, !valid)
        return valid
    }

    private fun validateConfirmPassword(): Boolean {
        val confirm = binding.etConfirmPassword.text.toString()
        val valid = confirm == binding.etPassword.text.toString()
        setFieldError(binding.etConfirmPassword, !valid)
        return valid
    }

    // =========================
    // REGISTER
    // =========================
    private fun register() {
        val firstName = binding.etFirstName.text.toString().trim()
        val lastName = binding.etLastName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        val isDoctor = binding.toggleRole.checkedButtonId == binding.btnRoleDoctor.id
        val role = if (isDoctor) AppConstants.Role.DOCTOR else AppConstants.Role.PATIENT

        if (firstName.isBlank() || lastName.isBlank()) {
            showError(getString(R.string.error_required))
            return
        }

        if (!validateEmail() || !validatePassword() || !validateConfirmPassword()) {
            showError(getString(R.string.error_generic))
            return
        }

        if (isDoctor && selectedSpecializationIds.isEmpty()) {
            showError(getString(R.string.error_doctor_specialization))
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(applicationContext).apiService.register(
                    RegisterRequest(
                        email = email,
                        password = password,
                        role = role,
                        firstName = firstName,
                        lastName = lastName,
                        phoneNumber = null,
                        specializationIds = if (isDoctor) selectedSpecializationIds.toList() else null
                    )
                )

                val tokenManager = TokenManager(applicationContext)
                tokenManager.saveToken(response.token)
                tokenManager.saveUserJson(Gson().toJson(response.user))

                val destination = if (response.user.role == AppConstants.Role.DOCTOR) {
                    DoctorDashboardActivity::class.java
                } else {
                    PatientDashboardActivity::class.java
                }

                Toast.makeText(
                    this@RegisterActivity,
                    getString(R.string.success_registered),
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@RegisterActivity, destination))
                finishAffinity()

            } catch (e: Throwable) {
                showError(ApiErrorMapper.toUserMessage(this@RegisterActivity, e))
            } finally {
                setLoading(false)
            }
        }
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
