package com.spendsense.app.ui.askai

import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.SavedInsight
import com.spendsense.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

private class FakeAiRepository(override val isConfigured: Boolean = true) : AiRepository {
    val replies = ArrayDeque<Result<String>>()
    val calls = mutableListOf<Pair<String, List<ChatTurn>>>()

    override fun observeInsight(month: YearMonth): Flow<SavedInsight?> = flowOf(null)
    override suspend fun generateInsight(month: YearMonth): Result<Unit> = Result.success(Unit)
    override suspend fun ask(question: String, history: List<ChatTurn>): Result<String> {
        calls += question to history
        return replies.removeFirst()
    }
}

class AskAiViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun buildHistory_keepsOnlyAnsweredPairs() {
        val messages = listOf(
            ChatMessage.User(1, "q1"), ChatMessage.Ai(2, "a1"),
            ChatMessage.User(3, "q2"), ChatMessage.Error(4, "No internet connection", "q2"),
            ChatMessage.User(5, "q3"), ChatMessage.Ai(6, "a3"),
        )
        assertEquals(
            listOf(ChatTurn("q1", true), ChatTurn("a1", false), ChatTurn("q3", true), ChatTurn("a3", false)),
            buildHistory(messages),
        )
    }

    @Test
    fun send_addsQuestionAndAnswer_andClearsInput() {
        val ai = FakeAiRepository().apply { replies += Result.success("You spent ฿742.") }
        val vm = AskAiViewModel(ai)
        vm.onInputChange("Food this week?")
        vm.send()
        val state = vm.uiState.value
        assertEquals("", state.input)
        assertFalse(state.isSending)
        assertEquals(listOf("Food this week?", "You spent ฿742."), state.messages.map {
            when (it) {
                is ChatMessage.User -> it.text
                is ChatMessage.Ai -> it.text
                is ChatMessage.Error -> it.message
            }
        })
    }

    @Test
    fun failure_showsErrorBubble_andRetryResendsWithoutDuplicateQuestion() {
        val ai = FakeAiRepository().apply {
            replies += Result.success("a1")
            replies += Result.failure(AppError.RateLimited)
            replies += Result.success("a2")
        }
        val vm = AskAiViewModel(ai)
        vm.send("q1")
        vm.send("q2")
        val error = vm.uiState.value.messages.last() as ChatMessage.Error
        assertEquals(AppError.RateLimited.userMessage, error.message)

        vm.retryLast()

        val messages = vm.uiState.value.messages
        assertEquals(4, messages.size) // q1, a1, q2, a2
        assertTrue(messages.last() is ChatMessage.Ai)
        assertEquals(listOf(ChatTurn("q1", true), ChatTurn("a1", false)), ai.calls.last().second)
        assertEquals("q2", ai.calls.last().first)
    }

    @Test
    fun blankInputOrMissingKey_sendsNothing() {
        val ai = FakeAiRepository(isConfigured = false)
        val vm = AskAiViewModel(ai)
        vm.send("   ")
        vm.send("real question")
        assertTrue(ai.calls.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }
}
