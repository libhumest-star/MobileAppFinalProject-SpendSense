package com.spendsense.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns expenses into compact text for Gemini prompts.
 * Totals are pre-calculated here because language models are bad at adding long lists of numbers.
 */
object ExpenseContextBuilder {

    /** "2026-10-12 | Netflix | BILLS | 402.00 (orig 12.00 USD)" */
    fun line(expense: Expense, homeCurrency: String): String = buildString {
        append("${expense.date} | ${expense.title} | ${expense.category.name} | ${amount(expense.amountHome)}")
        if (expense.originalCurrency != homeCurrency) {
            append(" (orig ${amount(expense.originalAmount)} ${expense.originalCurrency})")
        }
    }

    /** "Total: 200.50 (3 expenses)" then one "- CATEGORY: amount" line per category, largest first. */
    fun totals(expenses: List<Expense>): String = buildString {
        val count = expenses.size
        appendLine("Total: ${amount(expenses.sumOf { it.amountHome })} ($count expense${if (count == 1) "" else "s"})")
        expenses.groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amountHome } }
            .sortedWith(compareByDescending<Pair<Category, Double>> { it.second }.thenBy { it.first.ordinal })
            .forEach { (category, sum) -> appendLine("- ${category.name}: ${amount(sum)}") }
    }

    /** Totals, top 5 and every expense line for one month. Same data in any order gives the same text. */
    fun monthContext(monthExpenses: List<Expense>, month: YearMonth, homeCurrency: String): String {
        val sorted = monthExpenses.sortedWith(compareBy({ it.date }, { it.id }))
        return buildString {
            appendLine("MONTH $month (amounts in $homeCurrency)")
            append(totals(sorted))
            appendLine("Top expenses:")
            sorted.sortedByDescending { it.amountHome }.take(5).forEach {
                appendLine("* ${line(it, homeCurrency)}")
            }
            appendLine("All expenses (date | title | category | amount):")
            sorted.forEach { appendLine(line(it, homeCurrency)) }
        }
    }

    /** Fingerprint of a month's data; a saved insight with a different hash is "outdated". */
    fun monthHash(monthExpenses: List<Expense>, month: YearMonth, homeCurrency: String): Int =
        monthContext(monthExpenses, month, homeCurrency).hashCode()

    /** For AI Insights: the full month plus only the totals of the previous month. */
    fun insightContext(
        expenses: List<Expense>,
        month: YearMonth,
        homeCurrency: String,
        today: LocalDate,
    ): String {
        val previous = month.minusMonths(1)
        val inMonth = expenses.filter { YearMonth.from(it.date) == month }
        val inPrevious = expenses.filter { YearMonth.from(it.date) == previous }
        return buildString {
            appendLine("Today: $today")
            appendLine(monthContext(inMonth, month, homeCurrency))
            if (inPrevious.isEmpty()) {
                appendLine("PREVIOUS MONTH $previous: no data")
            } else {
                appendLine("PREVIOUS MONTH $previous totals:")
                append(totals(inPrevious))
            }
        }
    }

    /** For Ask AI: this week, last week (Mon–Sun), then each month newest first. */
    fun askContext(expenses: List<Expense>, today: LocalDate, homeCurrency: String): String {
        val weekStart = today.startOfWeek()
        val weekEnd = weekStart.plusDays(6)
        val lastWeekStart = weekStart.minusWeeks(1)
        val lastWeekEnd = weekStart.minusDays(1)
        val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return buildString {
            appendLine("Today: $today ($dayName). Home currency: $homeCurrency.")
            appendLine()
            appendLine("THIS WEEK ($weekStart to $weekEnd):")
            append(totals(expenses.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }))
            appendLine()
            appendLine("LAST WEEK ($lastWeekStart to $lastWeekEnd):")
            append(totals(expenses.filter { !it.date.isBefore(lastWeekStart) && !it.date.isAfter(lastWeekEnd) }))
            expenses.groupBy { YearMonth.from(it.date) }
                .toSortedMap(reverseOrder())
                .forEach { (month, list) ->
                    appendLine()
                    append(monthContext(list, month, homeCurrency))
                }
        }
    }

    private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)
}
