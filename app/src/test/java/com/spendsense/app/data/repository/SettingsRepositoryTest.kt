package com.spendsense.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class InMemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class SettingsRepositoryTest {

    @Test
    fun localeDefault_isSavedOnFirstRead_soChangingPhoneLanguageLaterKeepsIt() = runTest {
        val store = InMemoryPreferencesStore()
        // First launch on a Thai phone.
        assertEquals("THB", DataStoreSettingsRepository(store, defaultCurrency = "THB").homeCurrency.first())
        // Later the phone language is switched to English (US): the saved currency must win.
        assertEquals("THB", DataStoreSettingsRepository(store, defaultCurrency = "USD").homeCurrency.first())
    }

    @Test
    fun explicitChoice_isReturned() = runTest {
        val repo = DataStoreSettingsRepository(InMemoryPreferencesStore(), defaultCurrency = "THB")
        repo.setHomeCurrency("EUR")
        assertEquals("EUR", repo.homeCurrency.first())
    }
}
