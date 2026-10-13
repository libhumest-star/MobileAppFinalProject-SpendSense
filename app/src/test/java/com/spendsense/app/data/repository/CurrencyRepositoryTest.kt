package com.spendsense.app.data.repository

import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.LatestRatesDto
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.SupportedCurrencies
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private class FakeFrankfurterApi(
    private val rates: Map<String, Double> = mapOf("USD->THB" to 33.6),
    var offline: Boolean = false,
) : FrankfurterApi {
    var latestCalls = 0

    override suspend fun latest(base: String, symbols: String): LatestRatesDto {
        latestCalls++
        if (offline) throw IOException("offline")
        return LatestRatesDto(base, "2026-10-12", mapOf(symbols to rates.getValue("$base->$symbols")))
    }

    override suspend fun currencies(): Map<String, String> {
        if (offline) throw IOException("offline")
        return mapOf("USD" to "United States Dollar", "THB" to "Thai Baht")
    }
}

class CurrencyRepositoryTest {

    @Test
    fun sameCurrency_isOneWithoutNetwork() = runTest {
        val api = FakeFrankfurterApi()
        val repo = NetworkCurrencyRepository(api)
        assertEquals(1.0, repo.getRate("THB", "THB").getOrThrow(), 0.0)
        assertEquals(0, api.latestCalls)
    }

    @Test
    fun rate_isFetchedOnceThenCached() = runTest {
        val api = FakeFrankfurterApi()
        val repo = NetworkCurrencyRepository(api)
        assertEquals(33.6, repo.getRate("USD", "THB").getOrThrow(), 0.0)
        api.offline = true // cached value must still work offline
        assertEquals(33.6, repo.getRate("USD", "THB").getOrThrow(), 0.0)
        assertEquals(1, api.latestCalls)
    }

    @Test
    fun offline_returnsNoInternetFailure() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi(offline = true))
        assertEquals(AppError.NoInternet, repo.getRate("USD", "THB").exceptionOrNull())
    }

    @Test
    fun currencies_fallBackToBuiltInListWhenOffline() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi(offline = true))
        assertEquals(SupportedCurrencies.fallback.keys.sorted(), repo.getCurrencies().keys.toList())
    }

    @Test
    fun currencies_comeFromApiSortedByCode() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi())
        assertEquals(listOf("THB", "USD"), repo.getCurrencies().keys.toList())
    }
}
