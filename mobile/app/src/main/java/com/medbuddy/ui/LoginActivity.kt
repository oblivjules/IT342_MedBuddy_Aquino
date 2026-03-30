package com.medbuddy.ui

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import com.medbuddy.databinding.ActivityLoginBinding
import com.medbuddy.dto.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(applicationContext)

        if (tokenManager.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        styleRegisterFooterLink()

        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.btnGoogleSignIn.setOnClickListener {
            showError(getString(R.string.error_google_unavailable))
        }

        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun styleRegisterFooterLink() {
        val prefix = getString(R.string.login_footer_prefix)
        val action = getString(R.string.sign_up_action)
        val ss = SpannableString(prefix + action)
        val color = ContextCompat.getColor(this, R.color.primary)
        ss.setSpan(
            ForegroundColorSpan(color),
            prefix.length,
            prefix.length + action.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvGoRegister.text = ss
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showError(getString(R.string.error_required))
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(applicationContext)
                    .apiService
                    .login(LoginRequest(email, password, null))

                tokenManager.saveToken(response.token)
                tokenManager.saveUserJson(Gson().toJson(response.user))

                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.success_login),
                    Toast.LENGTH_SHORT
                ).show()
                navigateToDashboard(response.user.role)

            } catch (e: Throwable) {
                showError(ApiErrorMapper.toUserMessage(this@LoginActivity, e))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun navigateToDashboard(role: String? = null) {
        val resolvedRole = role ?: run {
            val json = tokenManager.getUserJson() ?: return
            try {
                Gson().fromJson(json, com.medbuddy.dto.UserDto::class.java).role
            } catch (e: Exception) {
                tokenManager.clearSession()
                return
            }
        }

        val destination = if (resolvedRole == AppConstants.Role.DOCTOR) {
            DoctorDashboardActivity::class.java
        } else {
            PatientDashboardActivity::class.java
        }

        startActivity(Intent(this, destination))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
