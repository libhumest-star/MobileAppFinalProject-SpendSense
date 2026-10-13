package com.spendsense.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {
    @Query("SELECT * FROM insights WHERE month = :month")
    fun observe(month: String): Flow<InsightEntity?>

    @Upsert
    suspend fun upsert(insight: InsightEntity)
}
