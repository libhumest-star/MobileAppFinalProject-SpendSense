package com.spendsense.app.data.repository

import com.spendsense.app.data.local.InsightDao
import com.spendsense.app.data.local.InsightEntity
import com.spendsense.app.data.remote.CandidateDto
import com.spendsense.app.data.remote.ContentDto
import com.spendsense.app.data.remote.GeminiApi
import com.spendsense.app.data.remote.GenerateContentRequest
import com.spendsense.app.data.remote.GenerateContentResponse
import com.spendsense.app.data.remote.NetworkModule
import com.spendsense.app.data.remote.PartDto
import com.spendsense.app.data.remote.text
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.testing.FakeExpenseRepository
import com.spendsense.app.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

private class FakeGeminiApi(var reply: GenerateContentResponse) : GeminiApi {
    val requests = mutableListOf<GenerateContentRequest>()
    override suspend fun generateContent(model: String, apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
        requests += request
        return reply
    }
}

private class FakeInsightDao : InsightDao {
    val rows = MutableStateFlow<Map<String, InsightEntity>>(emptyMap())
    override fun observe(month: String): Flow<InsightEntity?> = rows.map { it[month] }
    override suspend fun upsert(insight: InsightEntity) {
        rows.value = rows.value + (insight.month to insight)
    }
}

private fun replyWith(vararg parts: PartDto) =
    GenerateContentResponse(candidates = listOf(CandidateDto(content = ContentDto(role = "model", parts = parts.toList()))))

class GeminiAiRepositoryTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val october = YearMonth.of(2026, 10)
    private val lunch = Expense(1, "Lunch", Category.FOOD, 85.0, 85.0, "THB", LocalDate.of(2026, 10, 12))
    private val expenses = FakeExpenseRepository(listOf(lunch))
    private val insightDao = FakeInsightDao()

    private fun repository(api: GeminiApi, apiKey: String = "test-key") = GeminiAiRepository(
        api = api,
        apiKey = apiKey,
        expenseRepository = expenses,
        settingsRepository = FakeSettingsRepository("THB"),
        insightDao = insightDao,
        model = "test-model",
        today = { today },
        now = { 1000L },
    )

    @Test
    fun missingKey_failsWithoutCallingApi() = runTest {
        val api = FakeGeminiApi(replyWith(PartDto(text = "hi")))
        val repo = repository(api, apiKey = " ")
        assertFalse(repo.isConfigured)
        assertEquals(AppError.InvalidApiKey, repo.ask("q", emptyList()).exceptionOrNull())
        assertEquals(AppError.InvalidApiKey, repo.generateInsight(october).exceptionOrNull())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun generateInsight_requestsJsonAndSavesResultWithDataHash() = runTest {
        val json = """{"summary":"Food is 100%.","tips":["Cook at home"]}"""
        val api = FakeGeminiApi(replyWith(PartDto(text = json)))
        val repo = repository(api)

        assertTrue(repo.generateInsight(october).isSuccess)

        val request = api.requests.single()
        assertEquals("application/json", request.generationConfig?.responseMimeType)
        assertTrue(request.contents.single().parts.single().text!!.contains("Lunch"))
        val saved = repo.observeInsight(october).first()!!
        assertEquals(InsightContent("Food is 100%.", listOf("Cook at home")), saved.content)
        assertEquals(ExpenseContextBuilder.monthHash(listOf(lunch), october, "THB"), saved.dataHash)
        assertEquals(1000L, saved.generatedAt)
    }

    @Test
    fun blockedOrEmptyReply_isEmptyAiResponseError_andNothingSaved() = runTest {
        val repo = repository(FakeGeminiApi(GenerateContentResponse(candidates = emptyList())))
        assertEquals(AppError.EmptyAiResponse, repo.generateInsight(october).exceptionOrNull())
        assertEquals(AppError.EmptyAiResponse, repo.ask("q", emptyList()).exceptionOrNull())
        assertNull(repo.observeInsight(october).first())
    }

    @Test
    fun ask_sendsExpenseContextAndHistory_startingWithUserTurn() = runTest {
        val api = FakeGeminiApi(replyWith(PartDto(text = "You spent **฿85** on food.")))
        val repo = repository(api)
        // Odd-length history: the leading model turn must be dropped.
        val history = listOf(
            ChatTurn("old answer", fromUser = false),
            ChatTurn("q1", fromUser = true),
            ChatTurn("a1", fromUser = false),
        )

        val answer = repo.ask("How much on food?", history).getOrThrow()

        assertEquals("You spent ฿85 on food.", answer) // ** stripped
        val request = api.requests.single()
        assertEquals(listOf("user", "model", "user"), request.contents.map { it.role })
        assertEquals("How much on food?", request.contents.last().parts.single().text)
        assertTrue(request.systemInstruction!!.parts.single().text!!.contains("2026-10-12 | Lunch | FOOD | 85.00"))
    }

    @Test
    fun responseText_skipsThoughtParts() {
        val response = replyWith(PartDto(text = "thinking...", thought = true), PartDto(text = "Answer"))
        assertEquals("Answer", response.text())
    }

    @Test
    fun requestJson_omitsNullFields() {
        val request = GenerateContentRequest(contents = listOf(ContentDto(role = "user", parts = listOf(PartDto(text = "hi")))))
        assertEquals(
            """{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}""",
            NetworkModule.json.encodeToString(GenerateContentRequest.serializer(), request),
        )
    }

    @Test
    fun insightContent_parsesJson_fencedJson_andFallsBackToPlainText() {
        assertEquals(InsightContent("S", listOf("T")), InsightContent.parse("""{"summary":"S","tips":["T"]}"""))
        assertEquals(InsightContent("S", listOf("T")), InsightContent.parse("```json\n{\"summary\":\"S\",\"tips\":[\"T\"]}\n```"))
        assertEquals(InsightContent("Just some text"), InsightContent.parse("Just some text"))
    }
}
