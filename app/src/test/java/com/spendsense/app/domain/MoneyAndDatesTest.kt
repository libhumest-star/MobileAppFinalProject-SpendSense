package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MoneyAndDatesTest {

    @Test
    fun formatMoney_usesSymbolAndTwoDecimals() {
        assertEquals("฿1,234.50", formatMoney(1234.5, "THB"))
        assertEquals("$12.00", formatMoney(12.0, "USD"))
    }

    @Test
    fun formatMoney_jpyHasNoDecimals_unknownSymbolUsesCode() {
        assertEquals("¥1,500", formatMoney(1500.0, "JPY"))
        assertEquals("CHF 10.00", formatMoney(10.0, "CHF"))
    }

    @Test
    fun formatOriginal_appendsCode() {
        assertEquals("$12.00 USD", formatOriginal(12.0, "USD"))
    }

    @Test
    fun round2_roundsHalfUpAndFixesFloatNoise() {
        assertEquals(2.35, round2(2.345), 0.0)
        assertEquals(0.3, round2(0.1 + 0.2), 0.0)
    }

    @Test
    fun monthLabel_isEnglishMonthAndYear() {
        assertEquals("October 2026", YearMonth.of(2026, 10).label())
    }

    @Test
    fun startOfWeek_isMonday() {
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14).startOfWeek()) // Wed
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12).startOfWeek()) // Mon
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18).startOfWeek()) // Sun
    }

    @Test
    fun dayLabel_todayYesterdayOtherwiseShortDate() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(LocalDate.of(2026, 10, 12), today))
        assertEquals("Sat, 10 Oct", dayLabel(LocalDate.of(2026, 10, 10), today))
    }

    @Test
    fun defaultHomeCurrency_usesLocaleWhenSupportedElseUsd() {
        assertEquals("THB", SupportedCurrencies.defaultHomeCurrency(Locale.forLanguageTag("th-TH")))
        assertEquals("JPY", SupportedCurrencies.defaultHomeCurrency(Locale.JAPAN))
        assertEquals("USD", SupportedCurrencies.defaultHomeCurrency(Locale.forLanguageTag("es-AR"))) // ARS unsupported
        assertEquals("USD", SupportedCurrencies.defaultHomeCurrency(Locale.ENGLISH)) // no country
    }

    @Test
    fun categoryFromName_unknownFallsBackToOther() {
        assertEquals(Category.FOOD, Category.fromName("FOOD"))
        assertEquals(Category.OTHER, Category.fromName("GROCERIES"))
    }
}
