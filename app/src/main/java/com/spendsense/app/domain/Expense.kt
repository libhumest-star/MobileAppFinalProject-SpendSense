package com.spendsense.app.domain

import java.time.LocalDate

/**
 * One expense. [amountHome] is in the user's current home currency;
 * [originalAmount]/[originalCurrency] are what the user typed (e.g. 12.00 USD).
 */
data class Expense(
    val id: Long = 0,
    val title: String,
    val category: Category,
    val amountHome: Double,
    val originalAmount: Double,
    val originalCurrency: String,
    val date: LocalDate,
    val note: String? = null,
)
