package com.spendsense.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.spendsense.app.data.local.AppDatabase
import com.spendsense.app.data.remote.FRANKFURTER_BASE_URL
import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.GEMINI_BASE_URL
import com.spendsense.app.data.remote.GeminiApi
import com.spendsense.app.data.remote.NetworkModule
import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.DataStoreSettingsRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.GeminiAiRepository
import com.spendsense.app.data.repository.NetworkCurrencyRepository
import com.spendsense.app.data.repository.RoomExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.SupportedCurrencies
import java.util.Locale

/** Creates every dependency once. ViewModels get theirs from here (see each ViewModel's Factory). */
class AppContainer(context: Context) {

    private val database = AppDatabase.create(context)

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("settings") },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(
        dataStore = dataStore,
        defaultCurrency = SupportedCurrencies.defaultHomeCurrency(Locale.getDefault()),
    )

    val expenseRepository: ExpenseRepository = RoomExpenseRepository(database.expenseDao())

    val currencyRepository: CurrencyRepository = NetworkCurrencyRepository(
        NetworkModule.retrofit(
            FRANKFURTER_BASE_URL,
            NetworkModule.okHttpClient(readTimeoutSeconds = 15, enableLogging = BuildConfig.DEBUG),
        ).create(FrankfurterApi::class.java)
    )

    val aiRepository: AiRepository = GeminiAiRepository(
        api = NetworkModule.retrofit(
            GEMINI_BASE_URL,
            NetworkModule.okHttpClient(readTimeoutSeconds = 30, enableLogging = BuildConfig.DEBUG),
        ).create(GeminiApi::class.java),
        apiKey = BuildConfig.GEMINI_API_KEY,
        expenseRepository = expenseRepository,
        settingsRepository = settingsRepository,
        insightDao = database.insightDao(),
    )
}
