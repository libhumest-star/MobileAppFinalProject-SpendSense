package com.spendsense.app.data.repository

import com.spendsense.app.data.local.ExpenseDao
import com.spendsense.app.data.local.toDomain
import com.spendsense.app.data.local.toEntity
import com.spendsense.app.domain.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

interface ExpenseRepository {
    /** Newest first. */
    fun observeMonth(month: YearMonth): Flow<List<Expense>>
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense>
    suspend fun getById(id: Long): Expense?
    /** Inserts when id == 0, otherwise updates (also used to undo a delete). */
    suspend fun save(expense: Expense)
    suspend fun delete(id: Long)
    /** Multiplies every amountHome by [rate] (exact restore for [newHome] expenses) and deletes insights. */
    suspend fun reconvertAll(newHome: String, rate: Double)
    /** Deletes all expenses + insights, then inserts [expenses]. */
    suspend fun replaceAll(expenses: List<Expense>)
    suspend fun clearAll()
}

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {

    override fun observeMonth(month: YearMonth): Flow<List<Expense>> =
        dao.observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense> =
        dao.getBetween(start.toEpochDay(), end.toEpochDay()).map { it.toDomain() }

    override suspend fun getById(id: Long): Expense? = dao.getById(id)?.toDomain()

    override suspend fun save(expense: Expense) = dao.upsert(expense.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun reconvertAll(newHome: String, rate: Double) = dao.reconvertAll(newHome, rate)

    override suspend fun replaceAll(expenses: List<Expense>) =
        dao.replaceAll(expenses.map { it.toEntity() })

    override suspend fun clearAll() = dao.clearAll()
}
