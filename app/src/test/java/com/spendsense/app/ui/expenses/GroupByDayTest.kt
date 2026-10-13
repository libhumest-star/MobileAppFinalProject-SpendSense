package com.spendsense.app.ui.expenses

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GroupByDayTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun expense(id: Long, date: LocalDate, amount: Double, currency: String = "THB", original: Double = amount) =
        Expense(id, "E$id", Category.FOOD, amount, original, currency, date)

    @Test
    fun groupsNewestDayFirst_newestEntryFirstWithinDay() {
        val groups = groupByDay(
            listOf(
                expense(1, today.minusDays(3), 10.0),
                expense(2, today, 20.0),
                expense(3, today, 30.0),
                expense(4, today.minusDays(1), 40.0),
            ),
            homeCurrency = "THB",
            today = today,
        )
        assertEquals(listOf("Today", "Yesterday", "Sat, 10 Oct"), groups.map { it.label })
        assertEquals(listOf(3L, 2L), groups[0].rows.map { it.expense.id })
    }

    @Test
    fun foreignExpense_showsOriginalAmount_homeExpenseDoesNot() {
        val rows = groupByDay(
            listOf(expense(1, today, 403.2, "USD", 12.0), expense(2, today, 85.0)),
            homeCurrency = "THB",
            today = today,
        ).single().rows
        val usd = rows.first { it.expense.id == 1L }
        assertEquals("฿403.20", usd.amountText)
        assertEquals("$12.00 USD", usd.originalText)
        assertNull(rows.first { it.expense.id == 2L }.originalText)
    }
}
