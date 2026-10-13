package com.spendsense.app.testing

import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.round2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.LocalDate
import java.time.YearMonth

/** Makes viewModelScope run on a test dispatcher. Add `@get:Rule val mainRule = MainDispatcherRule()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeExpenseRepository(initial: List<Expense> = emptyList()) : ExpenseRepository {
    val expenses = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeMonth(month: YearMonth): Flow<List<Expense>> =
        expenses.map { list -> list.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date } }

    override suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense> =
        expenses.value.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

    override suspend fun getById(id: Long): Expense? = expenses.value.firstOrNull { it.id == id }

    override suspend fun save(expense: Expense) {
        val saved = if (expense.id == 0L) expense.copy(id = nextId++) else expense
        expenses.value = expenses.value.filterNot { it.id == saved.id } + saved
    }

    override suspend fun delete(id: Long) {
        expenses.value = expenses.value.filterNot { it.id == id }
    }

    override suspend fun reconvertAll(newHome: String, rate: Double) {
        expenses.value = expenses.value.map {
            it.copy(amountHome = if (it.originalCurrency == newHome) it.originalAmount else round2(it.amountHome * rate))
        }
    }

    override suspend fun replaceAll(expenses: List<Expense>) {
        this.expenses.value = expenses.mapIndexed { index, e -> e.copy(id = index + 1L) }
    }

    override suspend fun clearAll() {
        expenses.value = emptyList()
    }
}

/** [rates] keys look like "USD->THB". Set [offline] to make every uncached rate fail. */
class FakeCurrencyRepository(
    private val rates: Map<String, Double> = mapOf("USD->THB" to 33.5, "THB->USD" to 0.03),
    var offline: Boolean = false,
) : CurrencyRepository {
    var rateCalls = 0

    override suspend fun getRate(from: String, to: String): Result<Double> {
        if (from == to) return Result.success(1.0)
        rateCalls++
        if (offline) return Result.failure(AppError.NoInternet)
        return rates["$from->$to"]?.let { Result.success(it) } ?: Result.failure(AppError.Unknown)
    }

    override suspend fun getCurrencies(): Map<String, String> =
        sortedMapOf("THB" to "Thai Baht", "USD" to "United States Dollar", "EUR" to "Euro")
}

class FakeSettingsRepository(initial: String = "THB") : SettingsRepository {
    override val homeCurrency = MutableStateFlow(initial)
    override suspend fun setHomeCurrency(code: String) {
        homeCurrency.value = code
    }
}
