package com.spendsense.app.ui.insights

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsLogicTest {

    private val content = InsightContent("Summary", listOf("Tip"))
    private val saved = SavedInsight(content, dataHash = 42, generatedAt = 0L)

    @Test
    fun categoryTotals_largestFirstWithFractions() {
        val date = LocalDate.of(2026, 10, 1)
        val totals = categoryTotals(
            listOf(
                Expense(1, "A", Category.FOOD, 30.0, 30.0, "THB", date),
                Expense(2, "B", Category.FUN, 60.0, 60.0, "THB", date),
                Expense(3, "C", Category.FOOD, 10.0, 10.0, "THB", date),
            )
        )
        assertEquals(listOf(Category.FUN, Category.FOOD), totals.map { it.category })
        assertEquals(0.6f, totals[0].fraction, 0.0001f)
        assertEquals(40.0, totals[1].amount, 0.0)
        assertTrue(categoryTotals(emptyList()).isEmpty())
    }

    @Test
    fun aiCard_noExpenses_winsOverEverything() {
        assertEquals(AiCardState.NoData, aiCardState(false, true, GenerationStatus.Idle, saved, 42))
    }

    @Test
    fun aiCard_missingKey() {
        assertEquals(AiCardState.NotConfigured, aiCardState(true, false, GenerationStatus.Idle, null, 42))
    }

    @Test
    fun aiCard_runningShowsLoading() {
        assertEquals(AiCardState.Loading, aiCardState(true, true, GenerationStatus.Running, saved, 42))
    }

    @Test
    fun aiCard_savedInsight_isOutdatedOnlyWhenHashDiffers() {
        assertEquals(AiCardState.Ready(content, outdated = false), aiCardState(true, true, GenerationStatus.Idle, saved, 42))
        assertEquals(AiCardState.Ready(content, outdated = true), aiCardState(true, true, GenerationStatus.Idle, saved, 7))
    }

    @Test
    fun aiCard_failedRegenerate_keepsOldInsightWithError() {
        assertEquals(
            AiCardState.Ready(content, outdated = false, errorMessage = "No internet connection"),
            aiCardState(true, true, GenerationStatus.Failed("No internet connection"), saved, 42),
        )
    }

    @Test
    fun aiCard_failedFirstTime_showsError_elseNotGenerated() {
        assertEquals(AiCardState.Error("boom"), aiCardState(true, true, GenerationStatus.Failed("boom"), null, 42))
        assertEquals(AiCardState.NotGenerated, aiCardState(true, true, GenerationStatus.Idle, null, 42))
    }
}
