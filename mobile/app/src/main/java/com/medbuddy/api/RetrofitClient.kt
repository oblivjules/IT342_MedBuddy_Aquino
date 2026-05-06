package com.medbuddy.api

import android.content.Context
import android.content.Intent
import com.google.gson.GsonBuilder
import com.medbuddy.BuildConfig
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

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager))
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401) {
                    tokenManager.clearToken()
                    context.sendBroadcast(
                        Intent(AppConstants.Auth.ACTION_SESSION_EXPIRED).apply {
                            setPackage(context.packageName)
                        }
                    )
                }
                response
            }
            .addInterceptor(loggingInterceptor)
            .build()

        val gson = GsonBuilder().create()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
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

