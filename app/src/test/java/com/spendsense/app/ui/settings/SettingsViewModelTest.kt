package com.spendsense.app.ui.settings

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.testing.FakeCurrencyRepository
import com.spendsense.app.testing.FakeExpenseRepository
import com.spendsense.app.testing.FakeSettingsRepository
import com.spendsense.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)
    private val lunch = Expense(1, "Lunch", Category.FOOD, 100.0, 100.0, "THB", today)
    private val netflix = Expense(2, "Netflix", Category.BILLS, 402.0, 12.0, "USD", today)
    private val expenses = FakeExpenseRepository(listOf(lunch, netflix))
    private val currencies = FakeCurrencyRepository()
    private val settings = FakeSettingsRepository("THB")

    private fun viewModel() = SettingsViewModel(expenses, currencies, settings, aiConfigured = true, today = { today })

    @Test
    fun changeHomeCurrency_reconvertsExpensesAndSavesSetting() {
        val vm = viewModel()
        vm.changeHomeCurrency("USD")
        assertEquals("USD", settings.homeCurrency.value)
        assertEquals(3.0, expenses.expenses.value.first { it.id == 1L }.amountHome, 0.0) // 100 * 0.03
        assertEquals(12.0, expenses.expenses.value.first { it.id == 2L }.amountHome, 0.0) // exact USD original
        assertEquals("All expenses converted to USD", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun changeHomeCurrencyOffline_changesNothing() {
        currencies.offline = true
        val vm = viewModel()
        vm.changeHomeCurrency("USD")
        assertEquals("THB", settings.homeCurrency.value)
        assertEquals(listOf(lunch, netflix), expenses.expenses.value)
        assertTrue(vm.uiState.value.message!!.startsWith("Couldn't get the exchange rate"))
    }

    @Test
    fun loadDemoData_replacesExpenses() {
        val vm = viewModel()
        vm.loadDemoData()
        assertTrue(expenses.expenses.value.size > 43)
        assertEquals("Demo data loaded", vm.uiState.value.message)
    }

    @Test
    fun clearAll_deletesEverything() {
        val vm = viewModel()
        vm.clearAll()
        assertTrue(expenses.expenses.value.isEmpty())
    }
}
