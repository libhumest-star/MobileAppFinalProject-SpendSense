package com.spendsense.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    private fun entity(id: Long, date: String, amountHome: Double, original: Double = amountHome, currency: String = "THB") =
        ExpenseEntity(id, "E$id", "FOOD", amountHome, original, currency, LocalDate.parse(date).toEpochDay(), null)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.expenseDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeBetween_includesFirstAndLastDayOfMonth_newestFirst() = runTest {
        dao.insertAll(
            listOf(
                entity(1, "2026-09-30", 1.0),
                entity(2, "2026-10-01", 2.0),
                entity(3, "2026-10-31", 3.0),
                entity(4, "2026-11-01", 4.0),
            )
        )
        val october = dao.observeBetween(
            LocalDate.parse("2026-10-01").toEpochDay(),
            LocalDate.parse("2026-10-31").toEpochDay(),
        ).first()
        assertEquals(listOf(3L, 2L), october.map { it.id })
    }

    @Test
    fun reconvertAll_multipliesAmounts_restoresExactNewHomeAmounts_andDeletesInsights() = runTest {
        dao.insertAll(
            listOf(
                entity(1, "2026-10-01", amountHome = 100.0), // typed in THB
                entity(2, "2026-10-02", amountHome = 402.0, original = 12.0, currency = "USD"),
            )
        )
        db.insightDao().upsert(InsightEntity("2026-10", "{}", 1, 0L))

        dao.reconvertAll(newHome = "USD", rate = 0.0297)

        assertEquals(2.97, dao.getById(1)!!.amountHome, 0.001)
        assertEquals(12.0, dao.getById(2)!!.amountHome, 0.0)
        assertNull(db.insightDao().observe("2026-10").first())
    }

    @Test
    fun replaceAll_swapsAllRows() = runTest {
        dao.insertAll(listOf(entity(1, "2026-10-01", 1.0)))
        dao.replaceAll(listOf(entity(0, "2026-10-02", 2.0), entity(0, "2026-10-03", 3.0)))
        val all = dao.getBetween(0, Long.MAX_VALUE)
        assertEquals(listOf(3.0, 2.0), all.map { it.amountHome })
    }
}
