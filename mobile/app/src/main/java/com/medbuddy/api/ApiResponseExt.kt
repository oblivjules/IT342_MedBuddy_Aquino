package com.medbuddy.api

import retrofit2.HttpException
import retrofit2.Response

fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) {
        throw HttpException(this)
    }

    return body() ?: throw IllegalStateException("Empty response body")
}

fun Response<Unit>.ensureSuccess() {
    if (!isSuccessful) {
        throw HttpException(this)
    }
}