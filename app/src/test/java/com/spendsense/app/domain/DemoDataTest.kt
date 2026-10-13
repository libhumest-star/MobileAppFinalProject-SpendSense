package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DemoDataTest {

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun sameInputs_giveSameData() {
        assertEquals(
            DemoData.generate(today, "THB", 1.0, 33.6),
            DemoData.generate(today, "THB", 1.0, 33.6),
        )
    }

    @Test
    fun coversPreviousMonthStartUntilToday_withAtLeastOneExpensePerDay() {
        val data = DemoData.generate(today, "THB", 1.0, 33.6)
        assertEquals(LocalDate.of(2026, 9, 1), data.minOf { it.date })
        assertEquals(today, data.maxOf { it.date })
        assertEquals(43, data.map { it.date }.distinct().size) // 30 days of Sept + 13 of Oct
        assertTrue(data.all { it.amountHome > 0 })
    }

    @Test
    fun usdSubscriptions_keepUsdOriginal_whenHomeIsThb() {
        val netflix = DemoData.generate(today, "THB", 1.0, 33.6).first { it.title == "Netflix" }
        assertEquals("USD", netflix.originalCurrency)
        assertEquals(12.0, netflix.originalAmount, 0.0)
        assertEquals(403.2, netflix.amountHome, 0.0)
    }

    @Test
    fun thbAmounts_areConvertedAndStoredInHomeCurrency() {
        val data = DemoData.generate(today, "USD", thbToHome = 0.03, usdToHome = 1.0)
        val phone = data.first { it.title == "Phone bill" }
        assertEquals("USD", phone.originalCurrency)
        assertEquals(17.97, phone.amountHome, 0.0) // 599 THB * 0.03
        assertTrue(data.all { it.originalCurrency == "USD" })
    }
}
