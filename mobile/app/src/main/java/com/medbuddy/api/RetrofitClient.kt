package com.medbuddy.api

import android.content.Context
import com.google.gson.GsonBuilder
import com.medbuddy.auth.TokenManager
import com.medbuddy.constants.AppConstants
import java.util.concurrent.TimeUnit

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient private constructor(context: Context) {

    val apiService: ApiService

    init {
        val tokenManager = TokenManager(context.applicationContext)
        val publicPaths = listOf(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/health",
            "/api/specializations"
        )

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                val path = original.url.encodedPath
                val isPublic = publicPaths.any { path == it || path.startsWith("$it/") }

                tokenManager.getToken()?.let { token ->
                    if (!isPublic) {
                        builder.addHeader("Authorization", "Bearer $token")
                    }
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()

        val gson = GsonBuilder().create()

        val retrofit = Retrofit.Builder()
            .baseUrl(AppConstants.apiBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        apiService = retrofit.create(ApiService::class.java)
    }

    companion object {
        @Volatile
        private var instance: RetrofitClient? = null

        fun getInstance(context: Context): RetrofitClient {
            return instance ?: synchronized(this) {
                instance ?: RetrofitClient(context).also { instance = it }
            }
        }
    }
}

