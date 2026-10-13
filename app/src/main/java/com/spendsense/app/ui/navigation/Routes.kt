package com.spendsense.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object ExpensesRoute
@Serializable data object InsightsRoute
@Serializable data object AskAiRoute
@Serializable data object SettingsRoute

/** [expenseId] = 0 means "add a new expense"; Room ids start at 1. */
@Serializable data class AddEditRoute(val expenseId: Long = 0L)
