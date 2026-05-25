package com.medbuddy.api

import android.content.Context
import android.content.Intent
import android.util.Base64
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject

class AuthInterceptor(
    private val tokenManager: TokenManager,
    private val context: Context
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenManager.getToken()

        // Check if token is expired before proceeding
        if (!token.isNullOrBlank() && isTokenExpired(token)) {
            // Clear stored credentials
            tokenManager.clearSession()
            
            // Broadcast session expired
            context.sendBroadcast(
                Intent(AppConstants.Auth.ACTION_SESSION_EXPIRED).apply {
                    setPackage(context.packageName)
                }
            )
            
            // Return a cancelled response without proceeding
            throw java.io.IOException("Session expired. Please log in again.")
        }

        val authenticatedRequest = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(authenticatedRequest)

        // Handle 401 responses (server-side token invalidation)
        if (response.code == 401) {
            response.close()
            tokenManager.clearSession()
            context.sendBroadcast(
                Intent(AppConstants.Auth.ACTION_SESSION_EXPIRED).apply {
                    setPackage(context.packageName)
                }
            )
            throw java.io.IOException("Unauthorized. Please log in again.")
        }

        return response
    }

    /**
     * Check if the JWT token is expired by decoding the payload and checking the 'exp' claim.
     * Returns true if the token is expired or if parsing fails (treat invalid tokens as expired).
     */
    private fun isTokenExpired(token: String): Boolean {
        return try {
            val expiryTime = getTokenExpiryTime(token)
            if (expiryTime == null) {
                // Cannot parse expiry - treat as expired
                true
            } else {
                // Compare expiry (in seconds) with current time
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                // Add a 30-second buffer to avoid edge cases
                expiryTime <= currentTimeSeconds + TOKEN_EXPIRY_BUFFER_SECONDS
            }
        } catch (e: Exception) {
            // If parsing fails, treat token as expired
            true
        }
    }

    /**
     * Extract the 'exp' claim from a JWT token.
     * JWT format: header.payload.signature (each part is Base64URL encoded)
     */
    private fun getTokenExpiryTime(token: String): Long? {
        return try {
            // Split the token into parts
            val parts = token.split(".")
            if (parts.size != 3) {
                return null
            }

            // Decode the payload (second part)
            // Base64URL uses '-' instead of '+' and '_' instead of '/'
            val payloadBase64 = parts[1]
                .replace("-", "+")
                .replace("_", "/")

            // Add padding if needed
            val paddedPayload = when (payloadBase64.length % 4) {
                2 -> "$payloadBase64=="
                3 -> "$payloadBase64="
                else -> payloadBase64
            }

            val payloadJson = String(Base64.decode(paddedPayload, Base64.DEFAULT))
            
            // Parse the JSON and extract 'exp' claim
            val jsonObject = JSONObject(payloadJson)
            jsonObject.optLong("exp", -1).takeIf { it != -1L }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // 30-second buffer to avoid sending requests with tokens that expire during the request
        private const val TOKEN_EXPIRY_BUFFER_SECONDS = 30L
    }
}