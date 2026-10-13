package com.spendsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.DemoData
import com.spendsense.app.domain.SupportedCurrencies
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val homeCurrency: String = "",
    val currencies: Map<String, String> = SupportedCurrencies.fallback.toSortedMap(),
    val isWorking: Boolean = false,
    /** One-off snackbar text; cleared by messageShown(). */
    val message: String? = null,
    val aiConfigured: Boolean = false,
)

class SettingsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    aiConfigured: Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(aiConfigured = aiConfigured))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.homeCurrency.collect { code -> _uiState.update { it.copy(homeCurrency = code) } }
        }
        viewModelScope.launch {
            val currencies = currencyRepository.getCurrencies()
            _uiState.update { it.copy(currencies = currencies) }
        }
    }

    /** Fetch old->new rate, convert every expense, then save the new currency. Nothing changes on failure. */
    fun changeHomeCurrency(newCode: String) = runWork {
        val old = settingsRepository.homeCurrency.first()
        if (newCode == old) return@runWork null
        currencyRepository.getRate(old, newCode).fold(
            onSuccess = { rate ->
                expenseRepository.reconvertAll(newCode, rate)
                settingsRepository.setHomeCurrency(newCode)
                "All expenses converted to $newCode"
            },
            onFailure = { "Couldn't get the exchange rate. Check your connection and try again." },
        )
    }

    fun loadDemoData() = runWork {
        val home = settingsRepository.homeCurrency.first()
        val thbToHome = currencyRepository.getRate("THB", home).getOrNull()
        val usdToHome = currencyRepository.getRate("USD", home).getOrNull()
        if (thbToHome == null || usdToHome == null) {
            "Couldn't load demo data: no internet connection"
        } else {
            expenseRepository.replaceAll(DemoData.generate(today(), home, thbToHome, usdToHome))
            "Demo data loaded"
        }
    }

    fun clearAll() = runWork {
        expenseRepository.clearAll()
        "All expenses deleted"
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    /** Runs one action at a time; [block] returns the snackbar message (or null for none). */
    private fun runWork(block: suspend () -> String?) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val message = block()
            _uiState.update { it.copy(isWorking = false, message = message) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                SettingsViewModel(
                    expenseRepository = container.expenseRepository,
                    currencyRepository = container.currencyRepository,
                    settingsRepository = container.settingsRepository,
                    aiConfigured = container.aiRepository.isConfigured,
                )
            }
        }
    }
}
