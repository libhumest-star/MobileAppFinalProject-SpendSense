package com.spendsense.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewmodel.CreationExtras
import com.spendsense.app.SpendSenseApp
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.label
import java.time.YearMonth

/** Used inside each ViewModel's Factory to reach the AppContainer. */
fun CreationExtras.spendSenseApp(): SpendSenseApp =
    this[AndroidViewModelFactory.APPLICATION_KEY] as SpendSenseApp

/** "‹ October 2026 ›" top bar shared by Expenses and Insights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthTopBar(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(month.label()) },
        navigationIcon = {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
        },
        actions = {
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        },
    )
}

/** Round colored badge with the category emoji. */
@Composable
fun CategoryBadge(category: Category, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(Color(category.color).copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, fontSize = 20.sp)
    }
}
