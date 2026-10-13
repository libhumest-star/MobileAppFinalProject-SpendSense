package com.spendsense.app.data.repository

import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.safeApiCall
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.SupportedCurrencies
import java.util.concurrent.ConcurrentHashMap

interface CurrencyRepository {
    /** How many [to] one [from] is worth. Same currency = 1.0 without a network call. */
    suspend fun getRate(from: String, to: String): Result<Double>

    /** Code -> name, sorted by code. Never fails: falls back to the built-in list. */
    suspend fun getCurrencies(): Map<String, String>
}

class NetworkCurrencyRepository(private val api: FrankfurterApi) : CurrencyRepository {

    /** Rates are cached for the app session: one network call per currency pair. */
    private val rateCache = ConcurrentHashMap<String, Double>()
    @Volatile private var currencies: Map<String, String>? = null

    override suspend fun getRate(from: String, to: String): Result<Double> {
        if (from == to) return Result.success(1.0)
        val key = "$from->$to"
        rateCache[key]?.let { return Result.success(it) }
        return safeApiCall { api.latest(base = from, symbols = to).rates[to] ?: throw AppError.Unknown }
            .onSuccess { rateCache[key] = it }
    }

    override suspend fun getCurrencies(): Map<String, String> {
        currencies?.let { return it }
        val fromApi = safeApiCall { api.currencies() }.getOrNull()
        return if (fromApi.isNullOrEmpty()) {
            SupportedCurrencies.fallback.toSortedMap()
        } else {
            fromApi.toSortedMap().also { currencies = it }
        }
    }
}
