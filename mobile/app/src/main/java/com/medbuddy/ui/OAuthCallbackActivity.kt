package com.medbuddy.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.medbuddy.auth.TokenManager
import com.medbuddy.dto.UserDto

class OAuthCallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent?.data)
    }

    private fun handleDeepLink(data: Uri?) {
        val token = data?.getQueryParameter("token")
        val userJson = data?.getQueryParameter("user")

        if (!token.isNullOrBlank() && !userJson.isNullOrBlank()) {
            val tokenManager = TokenManager(applicationContext)
            tokenManager.saveToken(token)
            tokenManager.saveUserJson(userJson)
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        } else {
            Toast.makeText(this, "Google sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
        finish()
    }
}
