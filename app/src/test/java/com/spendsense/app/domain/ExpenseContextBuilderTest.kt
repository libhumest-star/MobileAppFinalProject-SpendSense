package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseContextBuilderTest {

    private val october = YearMonth.of(2026, 10)

    private fun e(
        id: Long,
        date: String,
        title: String,
        category: Category,
        home: Double,
        original: Double = home,
        currency: String = "THB",
    ) = Expense(id, title, category, home, original, currency, LocalDate.parse(date))

    @Test
    fun line_showsOriginalOnlyForForeignCurrency() {
        assertEquals(
            "2026-10-12 | Netflix | BILLS | 402.00 (orig 12.00 USD)",
            ExpenseContextBuilder.line(e(1, "2026-10-12", "Netflix", Category.BILLS, 402.0, 12.0, "USD"), "THB"),
        )
        assertEquals(
            "2026-10-12 | Lunch | FOOD | 85.00",
            ExpenseContextBuilder.line(e(2, "2026-10-12", "Lunch", Category.FOOD, 85.0), "THB"),
        )
    }

    @Test
    fun totals_sumPerCategoryLargestFirst() {
        val text = ExpenseContextBuilder.totals(
            listOf(
                e(1, "2026-10-01", "A", Category.FOOD, 50.0),
                e(2, "2026-10-01", "B", Category.TRANSPORT, 120.0),
                e(3, "2026-10-02", "C", Category.FOOD, 30.5),
            )
        )
        assertEquals("Total: 200.50 (3 expenses)\n- TRANSPORT: 120.00\n- FOOD: 80.50\n", text)
    }

    @Test
    fun totals_emptyAndSingular() {
        assertEquals("Total: 0.00 (0 expenses)\n", ExpenseContextBuilder.totals(emptyList()))
        assertEquals(
            "Total: 5.00 (1 expense)\n- FUN: 5.00\n",
            ExpenseContextBuilder.totals(listOf(e(1, "2026-10-01", "A", Category.FUN, 5.0))),
        )
    }

    @Test
    fun monthContext_listsTopFiveLargestOnly() {
        val expenses = (1..7).map { i -> e(i.toLong(), "2026-10-0$i", "E$i", Category.FOOD, i * 10.0) }
        val text = ExpenseContextBuilder.monthContext(expenses, october, "THB")
        val topLines = text.lines().filter { it.startsWith("* ") }
        assertEquals(5, topLines.size)
        assertTrue(topLines.first().contains("E7"))
        assertTrue(text.contains("2026-10-01 | E1 | FOOD | 10.00")) // every expense is still listed
    }

    @Test
    fun monthHash_ignoresOrder_butChangesWhenDataChanges() {
        val a = e(1, "2026-10-01", "A", Category.FOOD, 50.0)
        val b = e(2, "2026-10-02", "B", Category.FUN, 70.0)
        val hash = ExpenseContextBuilder.monthHash(listOf(a, b), october, "THB")
        assertEquals(hash, ExpenseContextBuilder.monthHash(listOf(b, a), october, "THB"))
        assertNotEquals(hash, ExpenseContextBuilder.monthHash(listOf(a, b.copy(amountHome = 71.0)), october, "THB"))
        assertNotEquals(hash, ExpenseContextBuilder.monthHash(listOf(a), october, "THB"))
    }

    @Test
    fun insightContext_hasPreviousMonthTotalsButNotItsLines() {
        val text = ExpenseContextBuilder.insightContext(
            listOf(
                e(1, "2026-10-05", "Lunch", Category.FOOD, 100.0),
                e(2, "2026-09-20", "Old dinner", Category.FOOD, 300.0),
            ),
            october,
            "THB",
            LocalDate.of(2026, 10, 13),
        )
        assertTrue(text.contains("Today: 2026-10-13"))
        assertTrue(text.contains("MONTH 2026-10"))
        assertTrue(text.contains("PREVIOUS MONTH 2026-09 totals:\nTotal: 300.00 (1 expense)\n- FOOD: 300.00"))
        assertFalse(text.contains("Old dinner"))
    }

    @Test
    fun insightContext_saysNoDataWhenPreviousMonthEmpty() {
        val text = ExpenseContextBuilder.insightContext(
            listOf(e(1, "2026-10-05", "Lunch", Category.FOOD, 100.0)),
            october,
            "THB",
            LocalDate.of(2026, 10, 13),
        )
        assertTrue(text.contains("PREVIOUS MONTH 2026-09: no data"))
    }

    @Test
    fun askContext_splitsWeeksMondayToSunday_andListsMonthsNewestFirst() {
        val today = LocalDate.of(2026, 10, 14) // Wednesday
        val text = ExpenseContextBuilder.askContext(
            listOf(
                e(1, "2026-10-11", "Sunday lunch", Category.FOOD, 100.0), // last week
                e(2, "2026-10-12", "Monday lunch", Category.FOOD, 40.0), // this week
                e(3, "2026-10-14", "Grab", Category.TRANSPORT, 60.0), // this week
                e(4, "2026-10-04", "Old movie", Category.FUN, 999.0), // neither week
                e(5, "2026-09-15", "September thing", Category.OTHER, 10.0),
            ),
            today,
            "THB",
        )
        assertTrue(text.startsWith("Today: 2026-10-14 (Wednesday). Home currency: THB."))
        assertTrue(text.contains("THIS WEEK (2026-10-12 to 2026-10-18):\nTotal: 100.00 (2 expenses)"))
        assertTrue(text.contains("LAST WEEK (2026-10-05 to 2026-10-11):\nTotal: 100.00 (1 expense)"))
        assertTrue(text.indexOf("MONTH 2026-10") < text.indexOf("MONTH 2026-09"))
    }
}
