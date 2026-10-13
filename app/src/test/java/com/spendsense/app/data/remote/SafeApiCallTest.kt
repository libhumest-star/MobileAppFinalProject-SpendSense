package com.spendsense.app.data.remote

import com.spendsense.app.domain.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class SafeApiCallTest {

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun success_isWrapped() = runTest {
        assertEquals(Result.success(5), safeApiCall { 5 })
    }

    @Test
    fun errors_mapToFriendlyAppErrors() {
        assertEquals(AppError.NoInternet, IOException().toAppError())
        assertEquals(AppError.NoInternet, SocketTimeoutException().toAppError())
        assertEquals(AppError.RateLimited, httpError(429).toAppError())
        assertEquals(AppError.InvalidApiKey, httpError(400).toAppError())
        assertEquals(AppError.InvalidApiKey, httpError(403).toAppError())
        assertEquals(AppError.Unknown, httpError(500).toAppError())
        assertEquals(AppError.EmptyAiResponse, AppError.EmptyAiResponse.toAppError())
        assertEquals(AppError.Unknown, IllegalStateException().toAppError())
    }

    @Test
    fun failure_isReturnedNotThrown() = runTest {
        val result = safeApiCall<Int> { throw IOException("offline") }
        assertEquals(AppError.NoInternet, result.exceptionOrNull())
    }

    @Test
    fun cancellation_isRethrown() = runTest {
        try {
            safeApiCall<Int> { throw CancellationException("left screen") }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
