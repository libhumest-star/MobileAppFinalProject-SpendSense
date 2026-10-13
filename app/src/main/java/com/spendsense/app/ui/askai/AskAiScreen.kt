package com.spendsense.app.ui.askai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val GREETING = "Hi! Ask me anything about your expenses. 💬"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(viewModel: AskAiViewModel = viewModel(factory = AskAiViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // +1 for the greeting bubble, +1 for the "typing…" bubble.
    val itemCount = state.messages.size + 1 + if (state.isSending) 1 else 0
    LaunchedEffect(itemCount) { listState.animateScrollToItem(itemCount - 1) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ask your spending") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "greeting") { Bubble(GREETING, fromUser = false) }
                items(state.messages, key = { it.id }) { message ->
                    when (message) {
                        is ChatMessage.User -> Bubble(message.text, fromUser = true)
                        is ChatMessage.Ai -> Bubble(message.text, fromUser = false)
                        is ChatMessage.Error -> ErrorBubble(
                            message = message.message,
                            canRetry = message == state.messages.lastOrNull() && !state.isSending,
                            onRetry = viewModel::retryLast,
                        )
                    }
                }
                if (state.isSending) item(key = "typing") { Bubble("Typing…", fromUser = false) }
            }

            if (!state.isConfigured) {
                Text(
                    "Gemini API key missing or invalid. See README.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SUGGESTED_QUESTIONS.forEach { question ->
                    AssistChip(
                        onClick = { viewModel.send(question) },
                        label = { Text(question) },
                        enabled = state.isConfigured && !state.isSending,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text("Ask about your spending…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    enabled = state.isConfigured,
                    maxLines = 3,
                )
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.isConfigured && !state.isSending && state.input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
            Text(
                "Your expense data is sent to Google Gemini to answer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = text,
            color = if (fromUser) colors.onPrimary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (fromUser) colors.primary else colors.surfaceContainerHigh,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (fromUser) 18.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ErrorBubble(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        if (canRetry) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("Retry", color = Color.Unspecified)
            }
        }
    }
}
