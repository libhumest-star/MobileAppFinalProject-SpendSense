package com.spendsense.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.domain.userMessage
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CategoryTotal(val category: Category, val amount: Double, val fraction: Float)

sealed interface AiCardState {
    data object NoData : AiCardState
    data object NotConfigured : AiCardState
    data object NotGenerated : AiCardState
    data object Loading : AiCardState
    /** [errorMessage] is set when a regenerate failed but an older insight is still shown. */
    data class Ready(val content: InsightContent, val outdated: Boolean, val errorMessage: String? = null) : AiCardState
    data class Error(val message: String) : AiCardState
}

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data object Running : GenerationStatus
    data class Failed(val message: String) : GenerationStatus
}

data class InsightsUiState(
    val month: YearMonth,
    val totalText: String = "",
    val homeCurrency: String = "",
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val ai: AiCardState = AiCardState.NoData,
    val canGoNext: Boolean = false,
)

/** Largest category first; fraction is the share of the month total (0..1). */
fun categoryTotals(expenses: List<Expense>): List<CategoryTotal> {
    val total = expenses.sumOf { it.amountHome }
    if (total <= 0.0) return emptyList()
    return expenses.groupBy { it.category }
        .map { (category, list) ->
            val sum = list.sumOf { it.amountHome }
            CategoryTotal(category, sum, (sum / total).toFloat())
        }
        .sortedByDescending { it.amount }
}

fun aiCardState(
    hasExpenses: Boolean,
    isConfigured: Boolean,
    status: GenerationStatus,
    saved: SavedInsight?,
    currentHash: Int,
): AiCardState = when {
    !hasExpenses -> AiCardState.NoData
    !isConfigured -> AiCardState.NotConfigured
    status is GenerationStatus.Running -> AiCardState.Loading
    saved != null -> AiCardState.Ready(
        content = saved.content,
        outdated = saved.dataHash != currentHash,
        errorMessage = (status as? GenerationStatus.Failed)?.message,
    )
    status is GenerationStatus.Failed -> AiCardState.Error(status.message)
    else -> AiCardState.NotGenerated
}

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val aiRepository: AiRepository,
    settingsRepository: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val status = MutableStateFlow<GenerationStatus>(GenerationStatus.Idle)

    val uiState: StateFlow<InsightsUiState> = month
        .flatMapLatest { m ->
            combine(
                expenseRepository.observeMonth(m),
                aiRepository.observeInsight(m),
                settingsRepository.homeCurrency,
                status,
            ) { expenses, saved, home, currentStatus ->
                InsightsUiState(
                    month = m,
                    totalText = formatMoney(expenses.sumOf { it.amountHome }, home),
                    homeCurrency = home,
                    categoryTotals = categoryTotals(expenses),
                    ai = aiCardState(
                        hasExpenses = expenses.isNotEmpty(),
                        isConfigured = aiRepository.isConfigured,
                        status = currentStatus,
                        saved = saved,
                        currentHash = ExpenseContextBuilder.monthHash(expenses, m, home),
                    ),
                    canGoNext = m < YearMonth.from(today()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(month = month.value))

    fun previousMonth() {
        month.update { it.minusMonths(1) }
        status.value = GenerationStatus.Idle
    }

    fun nextMonth() {
        month.update { if (it < YearMonth.from(today())) it.plusMonths(1) else it }
        status.value = GenerationStatus.Idle
    }

    fun generate() {
        if (status.value is GenerationStatus.Running) return
        val requested = month.value
        status.value = GenerationStatus.Running
        viewModelScope.launch {
            val result = aiRepository.generateInsight(requested)
            if (month.value != requested) return@launch // user switched month meanwhile
            status.value = result.fold(
                onSuccess = { GenerationStatus.Idle },
                onFailure = { GenerationStatus.Failed(it.userMessage()) },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                InsightsViewModel(container.expenseRepository, container.aiRepository, container.settingsRepository)
            }
        }
    }
}
