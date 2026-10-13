package com.spendsense.app.ui.askai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.userMessage
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage
    data class Ai(override val id: Long, val text: String) : ChatMessage
    /** [question] is kept so the user can retry it. */
    data class Error(override val id: Long, val message: String, val question: String) : ChatMessage
}

data class AskAiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isConfigured: Boolean = true,
)

val SUGGESTED_QUESTIONS = listOf(
    "How much did I spend on food this week?",
    "What was my biggest expense this month?",
    "Where can I save money?",
)

/** Only answered questions go to Gemini: each User message directly followed by an Ai message. */
fun buildHistory(messages: List<ChatMessage>): List<ChatTurn> =
    messages.zipWithNext().flatMap { (first, second) ->
        if (first is ChatMessage.User && second is ChatMessage.Ai) {
            listOf(ChatTurn(first.text, fromUser = true), ChatTurn(second.text, fromUser = false))
        } else {
            emptyList()
        }
    }

/** Chat history lives only here, so it resets when the app restarts (by design). */
class AskAiViewModel(private val aiRepository: AiRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AskAiUiState(isConfigured = aiRepository.isConfigured))
    val uiState: StateFlow<AskAiUiState> = _uiState.asStateFlow()
    private var nextId = 0L

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text) }

    fun send(text: String = _uiState.value.input) {
        val question = text.trim()
        val state = _uiState.value
        if (question.isEmpty() || state.isSending || !state.isConfigured) return
        val history = buildHistory(state.messages)
        _uiState.update { it.copy(messages = it.messages + ChatMessage.User(nextId++, question), input = "") }
        request(question, history)
    }

    /** Retries the last question if the last message is an error. */
    fun retryLast() {
        val state = _uiState.value
        val error = state.messages.lastOrNull() as? ChatMessage.Error ?: return
        if (state.isSending) return
        val withoutError = state.messages.dropLast(1)
        _uiState.update { it.copy(messages = withoutError) }
        // History = everything before the failed question's own bubble.
        request(error.question, buildHistory(withoutError.dropLast(1)))
    }

    private fun request(question: String, history: List<ChatTurn>) {
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val reply = aiRepository.ask(question, history).fold(
                onSuccess = { answer -> ChatMessage.Ai(nextId++, answer) },
                onFailure = { e -> ChatMessage.Error(nextId++, e.userMessage(), question) },
            )
            _uiState.update { it.copy(messages = it.messages + reply, isSending = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AskAiViewModel(spendSenseApp().container.aiRepository) }
        }
    }
}
