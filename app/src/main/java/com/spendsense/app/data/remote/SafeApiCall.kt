package com.spendsense.app.data.remote

import com.spendsense.app.domain.AppError
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a network call and never throws: failures come back as Result.failure(AppError).
 * Cancellation is re-thrown so leaving a screen still cancels its coroutines.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is IOException -> AppError.NoInternet // includes timeouts and "no route to host"
    is HttpException -> when (code()) {
        429 -> AppError.RateLimited
        400, 401, 403 -> AppError.InvalidApiKey
        else -> AppError.Unknown
    }
    else -> AppError.Unknown
}
