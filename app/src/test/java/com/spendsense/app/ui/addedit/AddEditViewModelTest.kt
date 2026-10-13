package com.spendsense.app.ui.addedit

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.testing.FakeCurrencyRepository
import com.spendsense.app.testing.FakeExpenseRepository
import com.spendsense.app.testing.FakeSettingsRepository
import com.spendsense.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AddEditViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)
    private val expenses = FakeExpenseRepository()
    private val currencies = FakeCurrencyRepository()
    private val settings = FakeSettingsRepository("THB")

    private fun viewModel(expenseId: Long = 0L) =
        AddEditViewModel(expenseId, expenses, currencies, settings, today)

    @Test
    fun parseAmount_acceptsCommaAndDot_rejectsZeroNaNInfinity() {
        assertEquals(12.5, parseAmount("12.5")!!, 0.0)
        assertEquals(12.5, parseAmount(" 12,5 ")!!, 0.0)
        assertNull(parseAmount("0"))
        assertNull(parseAmount("-3"))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount("NaN"))
        assertNull(parseAmount("Infinity"))
    }

    @Test
    fun newExpense_defaultsToHomeCurrency_andIsInvalidUntilAmountAndTitle() {
        val vm = viewModel()
        assertEquals("THB", vm.uiState.value.currency)
        assertEquals(RateState.NotNeeded, vm.uiState.value.rate)
        assertFalse(vm.uiState.value.isValid)
        vm.onAmountChange("85")
        assertFalse(vm.uiState.value.isValid)
        vm.onTitleChange("Lunch")
        assertTrue(vm.uiState.value.isValid)
    }

    @Test
    fun foreignCurrency_showsLiveConversion() {
        val vm = viewModel()
        vm.onAmountChange("12")
        vm.onCurrencyChange("USD")
        assertEquals(RateState.Ready(33.5), vm.uiState.value.rate)
        assertEquals(402.0, vm.uiState.value.convertedAmount!!, 0.0)
    }

    @Test
    fun saveForeignExpense_storesOriginalAndConvertedAmounts() {
        val vm = viewModel()
        vm.onAmountChange("12")
        vm.onCurrencyChange("USD")
        vm.onTitleChange("  Netflix ")
        vm.onCategoryChange(Category.BILLS)
        vm.save()
        val saved = expenses.expenses.value.single()
        assertEquals("Netflix", saved.title)
        assertEquals(12.0, saved.originalAmount, 0.0)
        assertEquals("USD", saved.originalCurrency)
        assertEquals(402.0, saved.amountHome, 0.0)
        assertEquals(Category.BILLS, saved.category)
        assertNull(saved.note)
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun saveOffline_inForeignCurrency_showsErrorAndSavesNothing() {
        currencies.offline = true
        val vm = viewModel()
        vm.onAmountChange("12")
        vm.onCurrencyChange("USD")
        vm.onTitleChange("Netflix")
        vm.save()
        assertTrue(expenses.expenses.value.isEmpty())
        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun editUnchangedForeignExpenseOffline_keepsAmountWithoutNetwork() {
        expenses.expenses.value = listOf(
            Expense(7, "Netflix", Category.BILLS, 389.4, 12.0, "USD", LocalDate.of(2026, 10, 8)),
        )
        currencies.offline = true
        val vm = viewModel(expenseId = 7)
        assertEquals("12", vm.uiState.value.amountText)
        assertEquals("USD", vm.uiState.value.currency)
        vm.onTitleChange("Netflix Premium")
        vm.save()
        val saved = expenses.expenses.value.single()
        assertEquals("Netflix Premium", saved.title)
        assertEquals(389.4, saved.amountHome, 0.0)
        assertTrue(vm.uiState.value.isDone)
    }

    // Month switchers stop at the current month, so a future-dated expense could never be found again.
    @Test
    fun futureDate_isClampedToToday() {
        val vm = viewModel()
        vm.onDateChange(today.plusDays(3))
        assertEquals(today, vm.uiState.value.date)
        vm.onDateChange(today.minusDays(2))
        assertEquals(today.minusDays(2), vm.uiState.value.date)
    }

    // The screen stays tappable for ~700 ms while it animates away after finishing.
    @Test
    fun doubleTapSave_savesOnlyOnce() {
        val vm = viewModel()
        vm.onAmountChange("85")
        vm.onTitleChange("Lunch")
        vm.save()
        vm.save()
        assertEquals(1, expenses.expenses.value.size)
    }

    @Test
    fun saveAfterDelete_doesNotBringExpenseBack() {
        expenses.expenses.value = listOf(Expense(3, "Lunch", Category.FOOD, 85.0, 85.0, "THB", today))
        val vm = viewModel(expenseId = 3)
        vm.delete()
        vm.save()
        assertTrue(expenses.expenses.value.isEmpty())
    }

    @Test
    fun deleteAfterSave_keepsExpense() {
        expenses.expenses.value = listOf(Expense(3, "Lunch", Category.FOOD, 85.0, 85.0, "THB", today))
        val vm = viewModel(expenseId = 3)
        vm.onTitleChange("Big lunch")
        vm.save()
        vm.delete()
        assertEquals("Big lunch", expenses.expenses.value.single().title)
    }

    @Test
    fun delete_removesExpenseAndFinishes() {
        expenses.expenses.value = listOf(Expense(3, "Lunch", Category.FOOD, 85.0, 85.0, "THB", today))
        val vm = viewModel(expenseId = 3)
        vm.delete()
        assertTrue(expenses.expenses.value.isEmpty())
        assertTrue(vm.uiState.value.isDone)
    }
}
