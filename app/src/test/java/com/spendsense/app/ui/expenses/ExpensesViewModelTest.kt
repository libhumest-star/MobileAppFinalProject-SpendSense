package com.spendsense.app.ui.expenses

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.testing.FakeExpenseRepository
import com.spendsense.app.testing.FakeSettingsRepository
import com.spendsense.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ExpensesViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun undo_restoresDeletedExpenseUnderNewId() {
        // The swiped row's saved state is "dismissed" under its old id; reusing that id
        // would make the restored row delete itself again.
        val lunch = Expense(5, "Lunch", Category.FOOD, 85.0, 85.0, "THB", today)
        val repo = FakeExpenseRepository(listOf(lunch))
        val vm = ExpensesViewModel(repo, FakeSettingsRepository(), today = { today })

        vm.delete(lunch)
        vm.restore(lunch)

        val restored = repo.expenses.value.single()
        assertNotEquals(5L, restored.id)
        assertEquals(lunch.copy(id = restored.id), restored)
    }
}
