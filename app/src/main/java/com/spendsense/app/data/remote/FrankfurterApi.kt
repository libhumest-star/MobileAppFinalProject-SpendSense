package com.spendsense.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v1/"

/** Free exchange rates from the European Central Bank. No API key. */
interface FrankfurterApi {
    /** e.g. latest?base=USD&symbols=THB. Never call with base == symbols (API returns an error). */
    @GET("latest")
    suspend fun latest(@Query("base") base: String, @Query("symbols") symbols: String): LatestRatesDto

    /** {"AUD": "Australian Dollar", ...} */
    @GET("currencies")
    suspend fun currencies(): Map<String, String>
}

/** {"amount":1.0,"base":"USD","date":"2026-10-12","rates":{"THB":33.615}} */
@Serializable
data class LatestRatesDto(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)
