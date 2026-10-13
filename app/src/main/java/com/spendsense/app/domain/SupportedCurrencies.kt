package com.spendsense.app.domain

import java.util.Currency
import java.util.Locale

object SupportedCurrencies {
    /**
     * The currencies Frankfurter (ECB rates) supports. Used for the first-launch default
     * (works offline) and as a fallback when GET /currencies fails.
     */
    val fallback: Map<String, String> = linkedMapOf(
        "AUD" to "Australian Dollar", "BRL" to "Brazilian Real", "CAD" to "Canadian Dollar",
        "CHF" to "Swiss Franc", "CNY" to "Chinese Renminbi Yuan", "CZK" to "Czech Koruna",
        "DKK" to "Danish Krone", "EUR" to "Euro", "GBP" to "British Pound",
        "HKD" to "Hong Kong Dollar", "HUF" to "Hungarian Forint", "IDR" to "Indonesian Rupiah",
        "ILS" to "Israeli New Shekel", "INR" to "Indian Rupee", "ISK" to "Icelandic Króna",
        "JPY" to "Japanese Yen", "KRW" to "South Korean Won", "MXN" to "Mexican Peso",
        "MYR" to "Malaysian Ringgit", "NOK" to "Norwegian Krone", "NZD" to "New Zealand Dollar",
        "PHP" to "Philippine Peso", "PLN" to "Polish Złoty", "RON" to "Romanian Leu",
        "SEK" to "Swedish Krona", "SGD" to "Singapore Dollar", "THB" to "Thai Baht",
        "TRY" to "Turkish Lira", "USD" to "United States Dollar", "ZAR" to "South African Rand",
    )

    /** The phone's local currency if Frankfurter supports it, otherwise USD. */
    fun defaultHomeCurrency(locale: Locale): String {
        val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        return if (code != null && code in fallback) code else "USD"
    }
}
