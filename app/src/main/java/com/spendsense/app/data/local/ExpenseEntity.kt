package com.spendsense.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import java.time.LocalDate

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val amountHome: Double,
    val originalAmount: Double,
    val originalCurrency: String,
    /** LocalDate.toEpochDay(), so date range queries are simple number comparisons. */
    val date: Long,
    val note: String?,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    title = title,
    category = Category.fromName(category),
    amountHome = amountHome,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    date = LocalDate.ofEpochDay(date),
    note = note,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    title = title,
    category = category.name,
    amountHome = amountHome,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    date = date.toEpochDay(),
    note = note,
)
