package com.spendsense.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.dayLabel
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.domain.formatOriginal
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

data class ExpenseRow(
    val expense: Expense,
    /** "฿389.40" */
    val amountText: String,
    /** "$12.00 USD" when typed in another currency, else null. */
    val originalText: String?,
)

data class DayGroup(val label: String, val rows: List<ExpenseRow>)

data class ExpensesUiState(
    val month: YearMonth,
    val totalText: String = "",
    val count: Int = 0,
    val groups: List<DayGroup> = emptyList(),
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
)

/** Newest day first; inside a day, newest entry first. */
fun groupByDay(expenses: List<Expense>, homeCurrency: String, today: LocalDate): List<DayGroup> =
    expenses
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
        .groupBy { it.date }
        .map { (date, list) ->
            DayGroup(
                label = dayLabel(date, today),
                rows = list.map { expense ->
                    ExpenseRow(
                        expense = expense,
                        amountText = formatMoney(expense.amountHome, homeCurrency),
                        originalText = if (expense.originalCurrency != homeCurrency) {
                            formatOriginal(expense.originalAmount, expense.originalCurrency)
                        } else {
                            null
                        },
                    )
                },
            )
        }

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenseRepository: ExpenseRepository,
    settingsRepository: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))

    val uiState: StateFlow<ExpensesUiState> = month
        .flatMapLatest { m ->
            combine(expenseRepository.observeMonth(m), settingsRepository.homeCurrency) { expenses, home ->
                ExpensesUiState(
                    month = m,
                    totalText = formatMoney(expenses.sumOf { it.amountHome }, home),
                    count = expenses.size,
                    groups = groupByDay(expenses, home, today()),
                    canGoNext = m < YearMonth.from(today()),
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState(month = month.value))

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.from(today())) it.plusMonths(1) else it }

    fun delete(expense: Expense) {
        viewModelScope.launch { expenseRepository.delete(expense.id) }
    }

    /**
     * Undo: re-inserts the expense as a new row. A fresh id gives the list item a fresh key, so it
     * doesn't inherit the swiped row's saved "dismissed" state (which would delete it again).
     */
    fun restore(expense: Expense) {
        viewModelScope.launch { expenseRepository.save(expense.copy(id = 0)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                ExpensesViewModel(container.expenseRepository, container.settingsRepository)
            }
        }
    }
}
