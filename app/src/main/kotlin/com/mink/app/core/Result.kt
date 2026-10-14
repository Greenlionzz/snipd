package com.mink.app.core

import java.lang.Exception

sealed class Result<out T> {
    data class Ok<out T>(val value: T) : Result<T>()
    data class Err(val message: String, val code: ErrorCode = ErrorCode.UNKNOWN, val cause: Throwable? = null) : Result<Nothing>()

    val isOk: Boolean get() = this is Ok
    val isErr: Boolean get() = this is Err

    fun getOrNull(): T? = when (this) {
        is Ok -> value
        is Err -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Ok -> value
        is Err -> default
    }

    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Ok -> transform(value)
        is Err -> this
    }

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Ok) action(value)
        return this
    }

    inline fun onError(action: (Err) -> Unit): Result<T> {
        if (this is Err) action(this)
        return this
    }

    companion object {
        fun <T> of(block: () -> T): Result<T> {
            return try {
                Ok(block())
            } catch (e: Exception) {
                Err(e.message ?: "Operation failed", cause = e)
            }
        }
    }
}

enum class ErrorCode {
    NO_KEY,
    BAD_KEY,
    RATE_LIMIT,
    NETWORK,
    TIMEOUT,
    CONTEXT_TOO_LONG,
    PARSE,
    NOT_FOUND,
    UNKNOWN
}
