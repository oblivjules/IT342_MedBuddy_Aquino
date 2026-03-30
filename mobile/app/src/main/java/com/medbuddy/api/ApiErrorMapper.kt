package com.medbuddy.api

import android.content.Context
import com.google.gson.JsonSyntaxException
import com.medbuddy.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONObject
import retrofit2.HttpException

object ApiErrorMapper {

    fun toUserMessage(
        context: Context,
        throwable: Throwable,
        fallbackResId: Int = R.string.error_generic
    ): String {
        return when (throwable) {
            is HttpException -> mapHttpError(context, throwable)
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is SSLException,
            is IOException -> context.getString(R.string.error_network_unreachable)
            is JsonSyntaxException -> context.getString(R.string.error_unexpected_response)
            else -> context.getString(fallbackResId)
        }
    }

    private fun mapHttpError(context: Context, exception: HttpException): String {
        val statusCode = exception.code()
        val errorBody = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
            ?.trim()
            .orEmpty()

        if (looksLikeHtml(errorBody)) {
            return context.getString(R.string.error_unexpected_response)
        }

        val serverMessage = extractJsonMessage(errorBody)

        return when (statusCode) {
            400 -> serverMessage ?: context.getString(R.string.error_bad_request)
            401 -> context.getString(R.string.error_unauthorized)
            403 -> context.getString(R.string.error_forbidden)
            404 -> context.getString(R.string.error_not_found)
            in 500..599 -> context.getString(R.string.error_server_unavailable)
            else -> serverMessage ?: context.getString(R.string.error_generic)
        }
    }

    private fun looksLikeHtml(body: String): Boolean {
        if (body.isBlank()) return false
        val normalized = body.trimStart().lowercase()
        return normalized.startsWith("<!doctype") ||
            normalized.startsWith("<html") ||
            normalized.contains("<script")
    }

    private fun extractJsonMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val json = JSONObject(body)
            json.optString("detail").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optString("error").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

