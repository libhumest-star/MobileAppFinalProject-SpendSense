package com.spendsense.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "October 2026" */
fun YearMonth.label(): String = format(monthFormatter)

/** "12 Oct 2026" */
fun LocalDate.fullLabel(): String = format(fullDateFormatter)

/** Weeks run Monday–Sunday. */
fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** "Today", "Yesterday", otherwise "Sat, 10 Oct". */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayFormatter)
}
