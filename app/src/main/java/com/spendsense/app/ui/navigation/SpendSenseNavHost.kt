package com.spendsense.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendsense.app.R
import com.spendsense.app.ui.addedit.AddEditExpenseScreen
import com.spendsense.app.ui.askai.AskAiScreen
import com.spendsense.app.ui.expenses.ExpensesScreen
import com.spendsense.app.ui.insights.InsightsScreen
import com.spendsense.app.ui.settings.SettingsScreen

enum class TopLevelDestination(val route: Any, val label: String) {
    EXPENSES(ExpensesRoute, "Expenses"),
    INSIGHTS(InsightsRoute, "Insights"),
    ASK_AI(AskAiRoute, "Ask AI"),
    SETTINGS(SettingsRoute, "Settings"),
}

@Composable
private fun TopLevelDestination.icon(): Painter = when (this) {
    TopLevelDestination.EXPENSES -> rememberVectorPainter(Icons.AutoMirrored.Filled.List)
    TopLevelDestination.INSIGHTS -> painterResource(R.drawable.ic_insights)
    TopLevelDestination.ASK_AI -> painterResource(R.drawable.ic_ai)
    TopLevelDestination.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
}

@Composable
fun SpendSenseNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hasRoute(destination.route::class) == true
    }

    Scaffold(
        // Each screen has its own Scaffold that handles the status bar; this outer one only adds the bottom bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.route::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon(), contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ExpensesRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<ExpensesRoute> {
                ExpensesScreen(
                    onAddExpense = { navController.navigate(AddEditRoute()) },
                    onEditExpense = { id -> navController.navigate(AddEditRoute(expenseId = id)) },
                )
            }
            composable<AddEditRoute> {
                AddEditExpenseScreen(onDone = { navController.popBackStack() })
            }
            composable<InsightsRoute> { InsightsScreen() }
            composable<AskAiRoute> { AskAiScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
