package com.medbuddy.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class PendingPaymentSession(
    val appointmentId: Long,
    val paymentIntentId: String,
    val clientKey: String
)

class PaymentSessionManager(context: Context) {

    private val prefs: SharedPreferences = run {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun savePendingPayment(appointmentId: Long, paymentIntentId: String?, clientKey: String?) {
        if (paymentIntentId.isNullOrBlank() || clientKey.isNullOrBlank()) {
            clearPendingPayment()
            return
        }

        prefs.edit()
            .putLong(KEY_APPOINTMENT_ID, appointmentId)
            .putString(KEY_PAYMENT_INTENT_ID, paymentIntentId)
            .putString(KEY_CLIENT_KEY, clientKey)
            .apply()
    }

    fun getPendingPayment(): PendingPaymentSession? {
        val appointmentId = prefs.getLong(KEY_APPOINTMENT_ID, -1L)
        val paymentIntentId = prefs.getString(KEY_PAYMENT_INTENT_ID, null)
        val clientKey = prefs.getString(KEY_CLIENT_KEY, null)

        if (appointmentId <= 0L || paymentIntentId.isNullOrBlank() || clientKey.isNullOrBlank()) {
            return null
        }

        return PendingPaymentSession(
            appointmentId = appointmentId,
            paymentIntentId = paymentIntentId,
            clientKey = clientKey
        )
    }

    fun clearPendingPayment() {
        prefs.edit()
            .remove(KEY_APPOINTMENT_ID)
            .remove(KEY_PAYMENT_INTENT_ID)
            .remove(KEY_CLIENT_KEY)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "medbuddy_payment_session"
        private const val KEY_APPOINTMENT_ID = "appointment_id"
        private const val KEY_PAYMENT_INTENT_ID = "payment_intent_id"
        private const val KEY_CLIENT_KEY = "client_key"
    }
}