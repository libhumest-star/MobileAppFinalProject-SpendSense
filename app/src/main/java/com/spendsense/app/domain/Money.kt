package com.spendsense.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

private val currencySymbols = mapOf(
    "THB" to "฿", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "KRW" to "₩",
    "INR" to "₹", "CNY" to "CN¥", "PHP" to "₱", "TRY" to "₺", "ILS" to "₪",
)

/** Rounds half-up to 2 decimals, so 0.1 + 0.2 is stored as 0.3. */
fun round2(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

/** "฿1,234.50", "$12.00", "¥1,500" (JPY has no decimals), "CHF 10.00" (no known symbol). */
fun formatMoney(amount: Double, currencyCode: String): String {
    val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
        .getOrDefault(2)
        .coerceAtLeast(0)
    val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
    val number = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(amount)
    val symbol = currencySymbols[currencyCode]
    return if (symbol != null) "$symbol$number" else "$currencyCode $number"
}

/** "$12.00 USD": used to show what was originally typed next to the converted amount. */
fun formatOriginal(amount: Double, currencyCode: String): String =
    "${formatMoney(amount, currencyCode)} $currencyCode"
