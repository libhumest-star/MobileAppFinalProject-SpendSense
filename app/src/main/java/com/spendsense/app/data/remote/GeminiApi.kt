package com.spendsense.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

object GeminiConfig {
    /** Free-tier Flash model for new projects (Oct 2026). Change here if Google retires it. */
    const val MODEL = "gemini-3.8-flash"
}

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest,
    ): GenerateContentResponse
}

// ---- Request ----

@Serializable
data class GenerateContentRequest(
    val contents: List<ContentDto>,
    val systemInstruction: ContentDto? = null,
    val generationConfig: GenerationConfigDto? = null,
)

/** role is "user" or "model"; leave it null for systemInstruction. */
@Serializable
data class ContentDto(
    val role: String? = null,
    val parts: List<PartDto>,
)

@Serializable
data class PartDto(
    val text: String? = null,
    /** true for the model's internal "thinking" parts, which are not part of the answer. */
    val thought: Boolean? = null,
)

/** Gemini 3 should run at its default temperature (1.0), so temperature is not set here. */
@Serializable
data class GenerationConfigDto(
    val responseMimeType: String? = null,
    val responseSchema: SchemaDto? = null,
    val thinkingConfig: ThinkingConfigDto? = null,
)

/** "low" keeps answers fast; gemini-3.8-flash supports low, medium, high. */
@Serializable
data class ThinkingConfigDto(val thinkingLevel: String)

/** Subset of the OpenAPI schema Gemini uses for structured output. Types are upper-case. */
@Serializable
data class SchemaDto(
    val type: String,
    val properties: Map<String, SchemaDto>? = null,
    val items: SchemaDto? = null,
    val required: List<String>? = null,
)

// ---- Response ----

@Serializable
data class GenerateContentResponse(
    val candidates: List<CandidateDto> = emptyList(),
    val promptFeedback: PromptFeedbackDto? = null,
)

@Serializable
data class CandidateDto(
    val content: ContentDto? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedbackDto(val blockReason: String? = null)

/** The answer text, without thinking parts. Null when blocked or empty. */
fun GenerateContentResponse.text(): String? =
    candidates.firstOrNull()?.content?.parts.orEmpty()
        .filter { it.thought != true }
        .mapNotNull { it.text }
        .joinToString("")
        .trim()
        .ifBlank { null }
