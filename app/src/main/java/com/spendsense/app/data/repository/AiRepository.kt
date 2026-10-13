package com.spendsense.app.data.repository

import com.spendsense.app.data.local.InsightDao
import com.spendsense.app.data.local.InsightEntity
import com.spendsense.app.data.remote.ContentDto
import com.spendsense.app.data.remote.GeminiApi
import com.spendsense.app.data.remote.GeminiConfig
import com.spendsense.app.data.remote.GenerateContentRequest
import com.spendsense.app.data.remote.GenerationConfigDto
import com.spendsense.app.data.remote.PartDto
import com.spendsense.app.data.remote.ThinkingConfigDto
import com.spendsense.app.data.remote.safeApiCall
import com.spendsense.app.data.remote.text
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

interface AiRepository {
    /** False when no Gemini API key was configured. */
    val isConfigured: Boolean
    fun observeInsight(month: YearMonth): Flow<SavedInsight?>
    /** Calls Gemini and saves the result for [month]. */
    suspend fun generateInsight(month: YearMonth): Result<Unit>
    /** [history] must be complete user/model pairs, oldest first. */
    suspend fun ask(question: String, history: List<ChatTurn>): Result<String>
}

class GeminiAiRepository(
    private val api: GeminiApi,
    private val apiKey: String,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val insightDao: InsightDao,
    private val model: String = GeminiConfig.MODEL,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : AiRepository {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override fun observeInsight(month: YearMonth): Flow<SavedInsight?> =
        insightDao.observe(month.toString()).map { entity ->
            entity?.let { SavedInsight(InsightContent.parse(it.json), it.dataHash, it.generatedAt) }
        }

    override suspend fun generateInsight(month: YearMonth): Result<Unit> {
        if (!isConfigured) return Result.failure(AppError.InvalidApiKey)
        val home = settingsRepository.homeCurrency.first()
        val expenses = expenseRepository.getBetween(month.minusMonths(1).atDay(1), month.atEndOfMonth())
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == month }
        val dataHash = ExpenseContextBuilder.monthHash(monthExpenses, month, home)
        val userText = "Here is my expense data:\n\n" +
            ExpenseContextBuilder.insightContext(expenses, month, home, today()) +
            "\nWrite my insights for $month."
        val request = GenerateContentRequest(
            contents = listOf(ContentDto(role = "user", parts = listOf(PartDto(text = userText)))),
            systemInstruction = ContentDto(parts = listOf(PartDto(text = AiPrompts.INSIGHTS_SYSTEM))),
            generationConfig = GenerationConfigDto(
                responseMimeType = "application/json",
                responseSchema = AiPrompts.INSIGHT_SCHEMA,
                thinkingConfig = ThinkingConfigDto(thinkingLevel = "low"),
            ),
        )
        return safeApiCall {
            api.generateContent(model, apiKey, request).text() ?: throw AppError.EmptyAiResponse
        }.map { json ->
            insightDao.upsert(InsightEntity(month.toString(), json, dataHash, now()))
        }
    }

    override suspend fun ask(question: String, history: List<ChatTurn>): Result<String> {
        if (!isConfigured) return Result.failure(AppError.InvalidApiKey)
        val home = settingsRepository.homeCurrency.first()
        val today = today()
        val currentMonth = YearMonth.from(today)
        val expenses = expenseRepository.getBetween(
            currentMonth.minusMonths(2).atDay(1),
            currentMonth.atEndOfMonth(),
        )
        val system = AiPrompts.ASK_SYSTEM + "\n\nEXPENSE DATA:\n" +
            ExpenseContextBuilder.askContext(expenses, today, home)
        val historyContents = history.takeLast(MAX_HISTORY)
            .dropWhile { !it.fromUser } // Gemini expects the conversation to start with a user turn
            .map { turn ->
                ContentDto(role = if (turn.fromUser) "user" else "model", parts = listOf(PartDto(text = turn.text)))
            }
        val request = GenerateContentRequest(
            contents = historyContents + ContentDto(role = "user", parts = listOf(PartDto(text = question))),
            systemInstruction = ContentDto(parts = listOf(PartDto(text = system))),
            generationConfig = GenerationConfigDto(thinkingConfig = ThinkingConfigDto(thinkingLevel = "low")),
        )
        return safeApiCall {
            api.generateContent(model, apiKey, request).text()?.replace("**", "")
                ?: throw AppError.EmptyAiResponse
        }
    }

    companion object {
        const val MAX_HISTORY = 10
    }
}
