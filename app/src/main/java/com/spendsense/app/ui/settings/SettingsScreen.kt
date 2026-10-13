package com.spendsense.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendsense.app.BuildConfig
import com.spendsense.app.data.remote.GeminiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var pendingCurrency by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDemo by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionHeader("Currency")
            ListItem(
                headlineContent = { Text("Home currency") },
                supportingContent = { Text("All totals are shown in this currency") },
                trailingContent = { Text(state.homeCurrency, style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.clickable(enabled = !state.isWorking) { showPicker = true },
            )

            SectionHeader("Data")
            ListItem(
                headlineContent = { Text("Load demo data") },
                supportingContent = { Text("Replace all expenses with 2 months of sample data") },
                modifier = Modifier.clickable(enabled = !state.isWorking) { confirmDemo = true },
            )
            ListItem(
                headlineContent = { Text("Clear all expenses", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(enabled = !state.isWorking) { confirmClear = true },
            )

            SectionHeader("About")
            ListItem(headlineContent = { Text("AI model") }, trailingContent = { Text(GeminiConfig.MODEL) })
            ListItem(
                headlineContent = { Text("Gemini API key") },
                trailingContent = { Text(if (state.aiConfigured) "Set" else "Missing (see README)") },
            )
            ListItem(headlineContent = { Text("Exchange rates") }, trailingContent = { Text("Frankfurter API") })
            ListItem(headlineContent = { Text("Version") }, trailingContent = { Text(BuildConfig.VERSION_NAME) })
        }
    }

    if (showPicker) {
        CurrencyPickerDialog(
            current = state.homeCurrency,
            currencies = state.currencies,
            onDismiss = { showPicker = false },
            onPick = { code ->
                showPicker = false
                if (code != state.homeCurrency) pendingCurrency = code
            },
        )
    }

    pendingCurrency?.let { code ->
        ConfirmDialog(
            title = "Change home currency?",
            text = "Convert all existing expenses from ${state.homeCurrency} to $code at today's rate?",
            confirmLabel = "Convert",
            onConfirm = { viewModel.changeHomeCurrency(code) },
            onDismiss = { pendingCurrency = null },
        )
    }
    if (confirmDemo) {
        ConfirmDialog(
            title = "Load demo data?",
            text = "This replaces all expenses with sample data.",
            confirmLabel = "Load",
            onConfirm = viewModel::loadDemoData,
            onDismiss = { confirmDemo = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all expenses?",
            text = "This deletes every expense and saved AI insight.",
            confirmLabel = "Delete all",
            onConfirm = viewModel::clearAll,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun CurrencyPickerDialog(
    current: String,
    currencies: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home currency") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(currencies.entries.toList(), key = { it.key }) { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(code) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = code == current, onClick = { onPick(code) })
                        Text("$code · $name")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
