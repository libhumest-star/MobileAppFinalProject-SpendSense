package com.spendsense.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Abstract class (not interface) so the @Transaction methods can have bodies. */
@Dao
abstract class ExpenseDao {

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY date DESC, id DESC"
    )
    abstract fun observeBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY date DESC, id DESC"
    )
    abstract suspend fun getBetween(startEpochDay: Long, endEpochDay: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    abstract suspend fun getById(id: Long): ExpenseEntity?

    @Upsert
    abstract suspend fun upsert(expense: ExpenseEntity)

    @Insert
    abstract suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    abstract suspend fun deleteAllExpenses()

    @Query("DELETE FROM insights")
    abstract suspend fun deleteAllInsights()

    /** Expenses typed in the new home currency get their exact amount back; others are multiplied. */
    @Query(
        "UPDATE expenses SET amountHome = CASE " +
            "WHEN originalCurrency = :newHome THEN originalAmount " +
            "ELSE ROUND(amountHome * :rate, 2) END"
    )
    abstract suspend fun reconvertAmounts(newHome: String, rate: Double)

    /** Saved insights are in the old currency, so they are deleted too. */
    @Transaction
    open suspend fun reconvertAll(newHome: String, rate: Double) {
        reconvertAmounts(newHome, rate)
        deleteAllInsights()
    }

    @Transaction
    open suspend fun replaceAll(expenses: List<ExpenseEntity>) {
        deleteAllExpenses()
        deleteAllInsights()
        insertAll(expenses)
    }

    @Transaction
    open suspend fun clearAll() {
        deleteAllExpenses()
        deleteAllInsights()
    }
}
