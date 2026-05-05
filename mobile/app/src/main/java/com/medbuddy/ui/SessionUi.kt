package com.medbuddy.ui

import android.content.Intent
import androidx.fragment.app.Fragment
import com.medbuddy.auth.TokenManager
import retrofit2.HttpException

object SessionUi {

    fun redirectToLogin(fragment: Fragment) {
        TokenManager(fragment.requireContext()).clearSession()
        fragment.startActivity(Intent(fragment.requireContext(), LoginActivity::class.java))
        fragment.requireActivity().finish()
    }

    fun handleAuthError(fragment: Fragment, throwable: Throwable): Boolean {
        val isUnauthorized = throwable is HttpException && throwable.code() == 401
        if (isUnauthorized || !TokenManager(fragment.requireContext()).isLoggedIn()) {
            redirectToLogin(fragment)
            return true
        }
        return false
    }
}
