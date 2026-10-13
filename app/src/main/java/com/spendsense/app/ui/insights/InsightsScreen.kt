package com.spendsense.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.ui.common.MonthTopBar
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MonthTopBar(
                month = state.month,
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BreakdownCard(state)
            AiCard(state.ai, onGenerate = viewModel::generate)
        }
    }
}

@Composable
private fun BreakdownCard(state: InsightsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.totalText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            state.categoryTotals.forEach { total ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${total.category.emoji} ${total.category.label}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatMoney(total.amount, state.homeCurrency)} · ${(total.fraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { total.fraction },
                        color = Color(total.category.color),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCard(ai: AiCardState, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ AI insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (ai) {
                AiCardState.NoData -> Text("No expenses to analyze for this month.")
                AiCardState.NotConfigured -> Text("Gemini API key missing or invalid. See README.")
                AiCardState.NotGenerated -> Button(onClick = onGenerate) { Text("✨ Generate AI insights") }
                AiCardState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Analyzing your spending…")
                }
                is AiCardState.Error -> {
                    Text(ai.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onGenerate) { Text("Retry") }
                }
                is AiCardState.Ready -> {
                    if (ai.outdated) {
                        Text(
                            "Outdated: your expenses changed since this was generated.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(ai.content.summary, style = MaterialTheme.typography.bodyMedium)
                    if (ai.content.tips.isNotEmpty()) {
                        Text("Tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ai.content.tips.forEach { tip -> Text("• $tip", style = MaterialTheme.typography.bodyMedium) }
                    }
                    ai.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (ai.outdated) {
                        Button(onClick = onGenerate) { Text("Regenerate") }
                    } else {
                        FilledTonalButton(onClick = onGenerate) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(" Regenerate")
                        }
                    }
                }
            }
        }
    }
}
