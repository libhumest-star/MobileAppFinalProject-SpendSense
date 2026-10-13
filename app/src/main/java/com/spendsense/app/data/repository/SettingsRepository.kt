package com.spendsense.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException

interface SettingsRepository {
    val homeCurrency: Flow<String>
    suspend fun setHomeCurrency(code: String)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaultCurrency: String,
) : SettingsRepository {

    private val homeCurrencyKey = stringPreferencesKey("home_currency")

    /** The locale default is saved on first read, so changing the phone's language later can't relabel amounts. */
    override val homeCurrency: Flow<String> = dataStore.data
        .onStart { saveDefaultIfMissing() }
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[homeCurrencyKey] ?: defaultCurrency }
        .distinctUntilChanged()

    private suspend fun saveDefaultIfMissing() {
        dataStore.edit { prefs -> if (prefs[homeCurrencyKey] == null) prefs[homeCurrencyKey] = defaultCurrency }
    }

    override suspend fun setHomeCurrency(code: String) {
        dataStore.edit { prefs -> prefs[homeCurrencyKey] = code }
    }
}
