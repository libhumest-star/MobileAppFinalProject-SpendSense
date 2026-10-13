package com.spendsense.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the Insights card shows. Gemini returns it as JSON. */
@Serializable
data class InsightContent(
    val summary: String,
    val tips: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Never fails: if the text isn't valid JSON, the whole text becomes the summary. */
        fun parse(raw: String): InsightContent {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            return runCatching { json.decodeFromString<InsightContent>(cleaned) }
                .getOrElse { InsightContent(summary = cleaned) }
        }
    }
}

data class SavedInsight(
    val content: InsightContent,
    val dataHash: Int,
    val generatedAt: Long,
)

/** One message of chat history sent to Gemini. */
data class ChatTurn(val text: String, val fromUser: Boolean)
