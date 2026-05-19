package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.medbuddy.BuildConfig
import com.medbuddy.R
import com.medbuddy.api.ApiErrorMapper
import com.medbuddy.api.bodyOrThrow
import com.medbuddy.api.RetrofitClient
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import com.medbuddy.databinding.ActivityLoginBinding
import com.medbuddy.dto.LoginRequest
import kotlinx.coroutines.launch
import java.net.URLEncoder

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
            showGoogleRoleDialog()
        }

        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateLoginEmail()
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) validateLoginPassword()
        }
    }

    private fun validateLoginEmail(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        return when {
            email.isBlank() -> {
                showFieldError(binding.tvErrorEmail, "Required")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                showFieldError(binding.tvErrorEmail, "Invalid email")
                false
            }
            else -> {
                clearFieldError(binding.tvErrorEmail)
                true
            }
        }
    }

    private fun validateLoginPassword(): Boolean {
        val password = binding.etPassword.text.toString()
        return if (password.isBlank()) {
            showFieldError(binding.tvErrorPassword, "Required")
            false
        } else {
            clearFieldError(binding.tvErrorPassword)
            true
        }
    }

    private fun showFieldError(errorView: android.widget.TextView, message: String) {
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun clearFieldError(errorView: android.widget.TextView) {
        errorView.visibility = View.GONE
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

    private fun showGoogleRoleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sign in as")
            .setItems(arrayOf("Patient", "Doctor")) { _, which ->
                val portal = if (which == 0) "patient" else "doctor"
                openGoogleSignIn(portal)
            }
            .show()
    }

    private fun openGoogleSignIn(portal: String) {
        val callbackUri = "medbuddy://oauth-callback"
        val encodedCallback = URLEncoder.encode(callbackUri, "UTF-8")
        val url = "${BuildConfig.BASE_URL}api/auth/oauth2/google?portal=$portal&redirect=$encodedCallback"
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, Uri.parse(url))
    }

    private fun attemptLogin() {
        if (!(validateLoginEmail() and validateLoginPassword())) return

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(applicationContext)
                    .apiService
                    .login(LoginRequest(email, password, null)).bodyOrThrow()

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
        role ?: run {
            val json = tokenManager.getUserJson() ?: return
            try {
                Gson().fromJson(json, com.medbuddy.dto.UserDto::class.java).role
            } catch (e: Exception) {
                tokenManager.clearSession()
                return
            }
        }

        // Navigate to MainActivity which handles fragment-based navigation
        startActivity(Intent(this, MainActivity::class.java))
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
