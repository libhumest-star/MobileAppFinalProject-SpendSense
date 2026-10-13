package com.spendsense.app.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.round2
import com.spendsense.app.ui.common.spendSenseApp
import com.spendsense.app.ui.navigation.AddEditRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

sealed interface RateState {
    /** Currency is the home currency: no conversion. */
    data object NotNeeded : RateState
    data object Loading : RateState
    data class Ready(val rate: Double) : RateState
    data object Failed : RateState
}

data class AddEditUiState(
    val isEdit: Boolean = false,
    val isLoaded: Boolean = false,
    val amountText: String = "",
    val currency: String = "",
    val homeCurrency: String = "",
    val title: String = "",
    val category: Category = Category.FOOD,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val currencies: List<String> = emptyList(),
    val rate: RateState = RateState.NotNeeded,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isDone: Boolean = false,
) {
    val amount: Double? get() = parseAmount(amountText)
    val isValid: Boolean get() = isValidExpenseInput(amountText, title)

    /** The amount in home currency, once a foreign currency's rate is known. */
    val convertedAmount: Double?
        get() {
            val ready = rate as? RateState.Ready ?: return null
            return amount?.let { round2(it * ready.rate) }
        }
}

/** Accepts "12", "12.5" and "12,5". Rejects 0, negatives, NaN and Infinity. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

fun isValidExpenseInput(amountText: String, title: String): Boolean =
    parseAmount(amountText) != null && title.isNotBlank()

class AddEditViewModel(
    /** 0 = new expense. */
    private val expenseId: Long,
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState(isEdit = expenseId != 0L, date = today))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    /** The expense as it was loaded (edit mode), to avoid re-converting an unchanged amount. */
    private var original: Expense? = null
    private var rateJob: Job? = null

    init {
        viewModelScope.launch {
            val home = settingsRepository.homeCurrency.first()
            val existing = if (expenseId != 0L) expenseRepository.getById(expenseId) else null
            original = existing
            _uiState.update { state ->
                if (existing == null) {
                    state.copy(isLoaded = true, homeCurrency = home, currency = home, currencies = listOf(home))
                } else {
                    state.copy(
                        isLoaded = true,
                        homeCurrency = home,
                        currency = existing.originalCurrency,
                        amountText = BigDecimal.valueOf(existing.originalAmount).stripTrailingZeros().toPlainString(),
                        title = existing.title,
                        category = existing.category,
                        date = existing.date,
                        note = existing.note.orEmpty(),
                        currencies = listOf(home, existing.originalCurrency).distinct(),
                    )
                }
            }
            refreshRate()
            launch {
                val codes = currencyRepository.getCurrencies().keys
                _uiState.update { it.copy(currencies = (codes + it.currency + it.homeCurrency).distinct().sorted()) }
            }
        }
    }

    fun onAmountChange(text: String) = _uiState.update { it.copy(amountText = text, errorMessage = null) }
    fun onTitleChange(text: String) = _uiState.update { it.copy(title = text) }
    fun onCategoryChange(category: Category) = _uiState.update { it.copy(category = category) }
    /** No future dates: the month switchers stop at the current month, so the expense would be unreachable. */
    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = minOf(date, today)) }
    fun onNoteChange(text: String) = _uiState.update { it.copy(note = text) }

    fun onCurrencyChange(code: String) {
        _uiState.update { it.copy(currency = code, errorMessage = null) }
        refreshRate()
    }

    /** Fetches the rate for the selected currency. A newer selection cancels an older fetch. */
    private fun refreshRate() {
        rateJob?.cancel()
        val state = _uiState.value
        if (state.currency == state.homeCurrency) {
            _uiState.update { it.copy(rate = RateState.NotNeeded) }
            return
        }
        _uiState.update { it.copy(rate = RateState.Loading) }
        rateJob = viewModelScope.launch {
            val result = currencyRepository.getRate(state.currency, state.homeCurrency)
            _uiState.update {
                it.copy(rate = result.fold(onSuccess = { rate -> RateState.Ready(rate) }, onFailure = { RateState.Failed }))
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val amount = state.amount
        // isDone: the screen is still tappable while it animates away after finishing.
        if (amount == null || !state.isValid || state.isSaving || state.isDone) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val originalAmount = round2(amount)
            val previous = original
            val amountHome: Double? =
                if (previous != null && previous.originalCurrency == state.currency && previous.originalAmount == originalAmount) {
                    previous.amountHome // unchanged amount: keep it, no network needed
                } else {
                    currencyRepository.getRate(state.currency, state.homeCurrency).getOrNull()
                        ?.let { rate -> round2(originalAmount * rate) }
                }
            if (amountHome == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Can't get exchange rate — check your connection or enter the amount in ${state.homeCurrency}",
                    )
                }
                return@launch
            }
            expenseRepository.save(
                Expense(
                    id = expenseId,
                    title = state.title.trim(),
                    category = state.category,
                    amountHome = amountHome,
                    originalAmount = originalAmount,
                    originalCurrency = state.currency,
                    date = state.date,
                    note = state.note.trim().ifBlank { null },
                )
            )
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    fun delete() {
        if (expenseId == 0L || _uiState.value.isSaving || _uiState.value.isDone) return
        viewModelScope.launch {
            expenseRepository.delete(expenseId)
            _uiState.update { it.copy(isDone = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                AddEditViewModel(
                    expenseId = createSavedStateHandle().toRoute<AddEditRoute>().expenseId,
                    expenseRepository = container.expenseRepository,
                    currencyRepository = container.currencyRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
