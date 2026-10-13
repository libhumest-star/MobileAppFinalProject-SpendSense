package com.spendsense.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One saved AI analysis per month. */
@Entity(tableName = "insights")
data class InsightEntity(
    /** "YYYY-MM", i.e. YearMonth.toString() */
    @PrimaryKey val month: String,
    /** Raw Gemini JSON: {"summary": "...", "tips": ["..."]} */
    val json: String,
    /** Hash of the month's expense data when generated; a mismatch means "outdated". */
    val dataHash: Int,
    val generatedAt: Long,
)
