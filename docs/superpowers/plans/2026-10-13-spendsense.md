# SpendSense Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build SpendSense, an Android expense tracker with 5 Compose screens. It saves data with Room/DataStore, converts currencies live (Frankfurter API) and adds AI insights plus an "Ask your spending" chat (Gemini).

**Architecture:** Single-module MVVM with manual dependency injection. Compose screens observe one `StateFlow<UiState>` per ViewModel. ViewModels call 4 repositories (`ExpenseRepository`, `CurrencyRepository`, `SettingsRepository`, `AiRepository`), which own Room, DataStore and two Retrofit clients. An `AppContainer` in the `Application` class creates everything once, and each ViewModel has a companion `Factory` that pulls from it.

**Tech Stack:** Kotlin 2.2.10 (built into AGP 9.3.2), Gradle 9.5.0, Jetpack Compose (BOM 2026.09.00, Material 3 1.4), Navigation Compose 2.10.2 (type-safe routes), Room 2.8.4 + KSP 2.3.4, DataStore Preferences 1.2.1, Retrofit 3.0.0 + kotlinx.serialization 1.9.0, OkHttp logging 5.3.2, JUnit4 + kotlinx-coroutines-test 1.10.2.

**Spec:** `docs/superpowers/specs/2026-10-13-spendsense-design.md`. Read it before starting. This plan implements it, and the spec explains *why*.

**Pre-verified:** On 2026-10-13, every code block in this plan was compiled and unit-tested in a scratch copy of the project on this machine, task by task (Tasks 1–11 cumulative). If a step fails, suspect a typo in transcription before suspecting the code.

## Global Constraints

- App name **SpendSense**; `namespace` and `applicationId` are both **`com.spendsense.app`**.
- AGP **9.3.2** with **built-in Kotlin**: do **not** apply `org.jetbrains.kotlin.android`. Use Kotlin plugins `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` at **2.2.10**, KSP **2.3.4**, Gradle wrapper **9.5.0**.
- **minSdk 26**, **compileSdk 37**, **targetSdk 37**, Java/JVM target 11.
- Every Gradle command runs from the project root in **Git Bash** as `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew <tasks>`. The shell doesn't remember env vars between agent commands, so always keep the prefix.
- Android SDK is at `/c/Users/poohk/AppData/Local/Android/Sdk`. Use `adb` = `/c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe` and emulator AVD **`Medium_Phone`**.
- Gemini: model **`gemini-3.8-flash`**, `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`, header `x-goog-api-key`. **No temperature** (Gemini 3 default); `generationConfig.thinkingConfig.thinkingLevel = "low"`.
- The Gemini key lives only in `local.properties` as `GEMINI_API_KEY=...` and reaches code as `BuildConfig.GEMINI_API_KEY`. **The user adds the key themselves.** Agents never type, print, echo or commit it.
- Frankfurter base URL **`https://api.frankfurter.dev/v1/`**. Never call it with `base == symbols` (it returns HTTP 422); same currency means rate 1.0 locally.
- Money is stored as `Double` rounded with `round2` (half-up, 2 decimals) and displayed with `formatMoney`. Dates use `java.time`; Room stores `LocalDate.toEpochDay()`; weeks run Monday–Sunday.
- User-facing error texts are exactly those in spec §9 and `AppError`.
- No Hilt or other DI framework. No features from spec §2 "Out of scope".
- Commit after every task. Every commit message ends with the trailer line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

These inputs aren't spelled out in the spec's screen descriptions but will hit real users and the live demo. Each one has a test in the owning task.

1. **Amounts typed as `12,5`, ` 12.5 `, `0`, `NaN` or `Infinity`.** The comma decimal is accepted as 12.5; the others are rejected. `NaN` is never saved. Pinned by `AddEditViewModelTest.parseAmount_acceptsCommaAndDot_rejectsZeroNaNInfinity` (Task 6).
2. **Changing home currency while offline.** Nothing changes: the setting and every amount stay as they were, and the user sees an error. Pinned by `SettingsViewModelTest.changeHomeCurrencyOffline_changesNothing` (Task 9).
3. **Editing a USD expense offline without touching amount or currency.** It saves using the stored home amount with no network call. Pinned by `AddEditViewModelTest.editUnchangedForeignExpenseOffline_keepsAmountWithoutNetwork` (Task 6).
4. **Gemini returns fenced JSON, plain text, or no candidates (blocked).** Insights still render (summary fallback) or show "The AI couldn't answer that…", and nothing is saved. Pinned by `GeminiAiRepositoryTest.insightContent_parsesJson_fencedJson_andFallsBackToPlainText` and `blockedOrEmptyReply_isEmptyAiResponseError_andNothingSaved` (Task 8).
5. **Expenses on the first or last day of a month.** They appear in exactly that month. Pinned by `ExpenseDaoTest.observeBetween_includesFirstAndLastDayOfMonth_newestFirst` (Task 3).

---

## File Structure

Everything lives under `app/src/main/java/com/spendsense/app/` unless noted otherwise.

| File | Responsibility | Task |
|---|---|---|
| `MainActivity.kt` | Edge-to-edge, sets theme + nav host | 1 |
| `SpendSenseApp.kt` | `Application`; owns the `AppContainer` | 1 → 4 |
| `AppContainer.kt` | Builds DB, DataStore, Retrofit clients, repositories | 4 → 8 |
| `ui/theme/Color.kt`, `Theme.kt` | Green Material 3 light/dark scheme | 1 |
| `ui/navigation/Routes.kt` | Type-safe route objects | 1 |
| `ui/navigation/SpendSenseNavHost.kt` | Bottom bar (4 tabs) + NavHost | 1 |
| `ui/common/Common.kt` | `spendSenseApp()` factory helper, `MonthTopBar`, `CategoryBadge` | 5 |
| `domain/Category.kt`, `Expense.kt` | Core model | 2 |
| `domain/Money.kt`, `Dates.kt` | `round2`, `formatMoney`, month/day labels, week start | 2 |
| `domain/SupportedCurrencies.kt` | Frankfurter currency list + default home currency | 2 |
| `domain/AppError.kt` | Friendly error types/messages | 4 |
| `domain/ExpenseContextBuilder.kt` | Expenses → prompt text + month hash | 7 |
| `domain/Insight.kt` | `InsightContent` (JSON parse), `SavedInsight`, `ChatTurn` | 8 |
| `domain/DemoData.kt` | Seeded sample expenses | 9 |
| `data/local/*` | Room entities, DAOs, database | 3 |
| `data/remote/SafeApiCall.kt` | `safeApiCall` + exception → `AppError` | 4 |
| `data/remote/NetworkModule.kt` | Json, OkHttp, Retrofit builders | 4 |
| `data/remote/FrankfurterApi.kt` | Rates API + DTO | 4 |
| `data/remote/GeminiApi.kt` | Gemini API, DTOs, `text()` | 8 |
| `data/repository/ExpenseRepository.kt` | Interface + Room impl | 3 |
| `data/repository/CurrencyRepository.kt` | Interface + cached network impl | 4 |
| `data/repository/SettingsRepository.kt` | Interface + DataStore impl | 4 |
| `data/repository/AiPrompts.kt`, `AiRepository.kt` | Prompts, schema, Gemini impl | 8 |
| `ui/expenses/*` | Expenses screen + VM | 1 (placeholder) → 5 |
| `ui/addedit/*` | Add/Edit screen + VM | 1 (placeholder) → 6 |
| `ui/settings/*` | Settings screen + VM | 1 (placeholder) → 9 |
| `ui/insights/*` | Insights screen + VM | 1 (placeholder) → 10 |
| `ui/askai/*` | Ask AI screen + VM | 1 (placeholder) → 11 |
| `app/src/test/.../testing/Fakes.kt` | Fake repositories + `MainDispatcherRule` | 6 |

---

### Task 1: Fresh project scaffold, theme and navigation shell

Replaces the cloned OpenRouter client with an empty SpendSense app: build config, theme, a bottom bar with 4 tabs and placeholder screens. Deliverable: the app builds, installs and switches tabs.

**Files:**
- Delete: `app/` (whole folder), `assets/`, `README.md`, `README.ru.md`, `LICENSE`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.idea/`, `.gradle/`
- Keep: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `local.properties`, `.gitignore`, `docs/`, `.superpowers/`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/.gitignore`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/{strings,themes,colors}.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/res/drawable/{ic_launcher_foreground,ic_insights,ic_ai}.xml`, `MainActivity.kt`, `SpendSenseApp.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/navigation/Routes.kt`, `ui/navigation/SpendSenseNavHost.kt`, and placeholders `ui/expenses/ExpensesScreen.kt`, `ui/addedit/AddEditExpenseScreen.kt`, `ui/insights/InsightsScreen.kt`, `ui/askai/AskAiScreen.kt`, `ui/settings/SettingsScreen.kt`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nothing.
- Produces: routes `ExpensesRoute`, `InsightsRoute`, `AskAiRoute`, `SettingsRoute` (data objects) and `AddEditRoute(expenseId: Long = 0L)`. Screen signatures that later tasks keep, adding only a trailing `viewModel` parameter with a default: `ExpensesScreen(onAddExpense: () -> Unit, onEditExpense: (Long) -> Unit)`, `AddEditExpenseScreen(onDone: () -> Unit)`, `InsightsScreen()`, `AskAiScreen()`, `SettingsScreen()`. Also `SpendSenseTheme { }`, and `R.drawable.ic_insights` / `R.drawable.ic_ai`.

- [ ] **Step 1: Close the project in Android Studio** (File → Close Project) so it doesn't fight over `.idea/` and `.gradle/`. If an agent is executing, ask the user to do this and wait for confirmation.

- [ ] **Step 2: Delete the cloned app**

```bash
rm -rf app assets README.md README.ru.md LICENSE build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml .idea .gradle
ls -a
```

Expected remaining: `.git .gitignore .superpowers docs gradle gradlew gradlew.bat local.properties`

- [ ] **Step 3: Point the wrapper at Gradle 9.5.0**

`gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 4: Root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpendSense"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.3.2"
kotlin = "2.2.10"
ksp = "2.3.4"
composeBom = "2026.09.00"
coreKtx = "1.17.0"
activityCompose = "1.12.4"
lifecycle = "2.10.0"
navigation = "2.10.2"
room = "2.8.4"
datastore = "1.2.1"
retrofit = "3.0.0"
okhttp = "5.3.2"
serializationJson = "1.9.0"
coroutines = "1.10.2"
materialIcons = "1.7.8"
junit = "4.13.2"
androidxJunit = "1.3.0"
androidxTestCore = "1.7.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core", version.ref = "materialIcons" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxJunit" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 5: App module build file**

`app/build.gradle.kts`:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.spendsense.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.spendsense.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

`app/.gitignore`:

```gitignore
/build
```

Append to the root `.gitignore`:

```gitignore
# Kotlin 2.x compiler caches
.kotlin/
```

- [ ] **Step 6: Manifest and resources**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".SpendSenseApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.SpendSense">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">SpendSense</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.SpendSense" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="ic_launcher_background">#2E7D32</color>
</resources>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Three rising bars: "your spending, made sense of". Kept inside the 72dp safe zone. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M34,58h10v16h-10z M49,46h10v28h-10z M64,34h10v40h-10z" />
</vector>
```

`app/src/main/res/drawable/ic_insights.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M4,10h4v10H4z M10,4h4v16h-4z M16,13h4v7h-4z" />
</vector>
```

`app/src/main/res/drawable/ic_ai.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z" />
</vector>
```

- [ ] **Step 7: Theme**

`app/src/main/java/com/spendsense/app/ui/theme/Color.kt`:

```kotlin
package com.spendsense.app.ui.theme

import androidx.compose.ui.graphics.Color

// Light
val Green40 = Color(0xFF2E7D32)
val Green90 = Color(0xFFC5E8C0)
val Green10 = Color(0xFF0B3D0F)
val Sage40 = Color(0xFF52634F)
val Sage90 = Color(0xFFD5E8CF)
val Teal40 = Color(0xFF00696E)
val Teal90 = Color(0xFF9CF0F5)
val SurfaceLight = Color(0xFFF7FAF6)
val SurfaceContainerLight = Color(0xFFEBF0E8)
val OnSurfaceLight = Color(0xFF1A1C19)
val OnSurfaceVariantLight = Color(0xFF424940)
val OutlineLight = Color(0xFF72796F)

// Dark
val Green80 = Color(0xFF8FD88A)
val Green20 = Color(0xFF003909)
val Green30 = Color(0xFF1B5E20)
val Sage80 = Color(0xFFB9CCB4)
val Sage30 = Color(0xFF3A4B39)
val Teal80 = Color(0xFF80D4D9)
val Teal30 = Color(0xFF004F53)
val SurfaceDark = Color(0xFF101410)
val SurfaceContainerDark = Color(0xFF1D211C)
val OnSurfaceDark = Color(0xFFE0E4DC)
val OnSurfaceVariantDark = Color(0xFFC2C9BD)
val OutlineDark = Color(0xFF8C9388)
```

`app/src/main/java/com/spendsense/app/ui/theme/Theme.kt`:

```kotlin
package com.spendsense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = Sage90,
    onSecondaryContainer = Green10,
    tertiary = Teal40,
    tertiaryContainer = Teal90,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = Sage90,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFE5EBE2),
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Sage80,
    onSecondary = Green20,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Teal80,
    tertiaryContainer = Teal30,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = Sage30,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainerHigh = Color(0xFF272B26),
    outline = OutlineDark,
)

@Composable
fun SpendSenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
```

- [ ] **Step 8: Application, Activity and navigation**

`app/src/main/java/com/spendsense/app/SpendSenseApp.kt` (first version; Task 4 adds the container):

```kotlin
package com.spendsense.app

import android.app.Application

/** Gets its AppContainer in Task 4. */
class SpendSenseApp : Application()
```

`app/src/main/java/com/spendsense/app/MainActivity.kt`:

```kotlin
package com.spendsense.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spendsense.app.ui.navigation.SpendSenseNavHost
import com.spendsense.app.ui.theme.SpendSenseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SpendSenseTheme {
                SpendSenseNavHost()
            }
        }
    }
}
```

`app/src/main/java/com/spendsense/app/ui/navigation/Routes.kt`:

```kotlin
package com.spendsense.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object ExpensesRoute
@Serializable data object InsightsRoute
@Serializable data object AskAiRoute
@Serializable data object SettingsRoute

/** [expenseId] = 0 means "add a new expense"; Room ids start at 1. */
@Serializable data class AddEditRoute(val expenseId: Long = 0L)
```

`app/src/main/java/com/spendsense/app/ui/navigation/SpendSenseNavHost.kt`:

```kotlin
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
```

Why the outer `Scaffold` uses zero insets plus `consumeWindowInsets`: each screen has its own `Scaffold` (with a top bar). Consuming the outer padding stops inner screens from adding the navigation-bar inset a second time above the bottom bar.

- [ ] **Step 9: Placeholder screens** (same signatures as the final screens)

`app/src/main/java/com/spendsense/app/ui/expenses/ExpensesScreen.kt`:

```kotlin
package com.spendsense.app.ui.expenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder, replaced in Task 5. */
@Composable
fun ExpensesScreen(onAddExpense: () -> Unit, onEditExpense: (Long) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onAddExpense) { Text("Expenses: open Add expense") }
    }
}
```

`app/src/main/java/com/spendsense/app/ui/addedit/AddEditExpenseScreen.kt`:

```kotlin
package com.spendsense.app.ui.addedit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder, replaced in Task 6. */
@Composable
fun AddEditExpenseScreen(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onDone) { Text("Add/Edit: go back") }
    }
}
```

`app/src/main/java/com/spendsense/app/ui/insights/InsightsScreen.kt`:

```kotlin
package com.spendsense.app.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder, replaced in Task 10. */
@Composable
fun InsightsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Insights") }
}
```

`app/src/main/java/com/spendsense/app/ui/askai/AskAiScreen.kt`:

```kotlin
package com.spendsense.app.ui.askai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder, replaced in Task 11. */
@Composable
fun AskAiScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Ask AI") }
}
```

`app/src/main/java/com/spendsense/app/ui/settings/SettingsScreen.kt`:

```kotlin
package com.spendsense.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Placeholder, replaced in Task 9. */
@Composable
fun SettingsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Settings") }
}
```

- [ ] **Step 10: Build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (the first run downloads Gradle 9.5.0 and dependencies, which takes a few minutes)

- [ ] **Step 11: Run on the emulator**

Start the emulator (agents: as a background command), wait for it, install, launch:

```bash
/c/Users/poohk/AppData/Local/Android/Sdk/emulator/emulator.exe -avd Medium_Phone -no-snapshot-save
```

```bash
/c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe wait-for-device && JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity
```

Expected: the app opens on "Expenses" with a bottom bar of Expenses · Insights · Ask AI · Settings. Each tab switches. The "Expenses: open Add expense" button opens a screen without the bottom bar, and its button goes back. The launcher icon shows three white bars on green. (Agents can check with `adb exec-out screencap -p > <scratchpad>/t1.png` and view the image.)

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "chore: replace cloned project with SpendSense Compose scaffold" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Domain model, money and date helpers

Plain-Kotlin building blocks that everything else uses. Deliverable: domain types plus passing unit tests.

**Files:**
- Create: `domain/Category.kt`, `domain/Expense.kt`, `domain/Money.kt`, `domain/Dates.kt`, `domain/SupportedCurrencies.kt`
- Test: `app/src/test/java/com/spendsense/app/domain/MoneyAndDatesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class Category(label: String, emoji: String, color: Long)` with `FOOD, TRANSPORT, SHOPPING, BILLS, FUN, HEALTH, OTHER` and `Category.fromName(name: String): Category`
  - `data class Expense(id: Long = 0, title: String, category: Category, amountHome: Double, originalAmount: Double, originalCurrency: String, date: LocalDate, note: String? = null)`
  - `fun round2(value: Double): Double`, `fun formatMoney(amount: Double, currencyCode: String): String`, `fun formatOriginal(amount: Double, currencyCode: String): String`
  - `fun YearMonth.label(): String`, `fun LocalDate.fullLabel(): String`, `fun LocalDate.startOfWeek(): LocalDate`, `fun dayLabel(date: LocalDate, today: LocalDate): String`
  - `object SupportedCurrencies { val fallback: Map<String, String>; fun defaultHomeCurrency(locale: Locale): String }`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/domain/MoneyAndDatesTest.kt`:

```kotlin
package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MoneyAndDatesTest {

    @Test
    fun formatMoney_usesSymbolAndTwoDecimals() {
        assertEquals("฿1,234.50", formatMoney(1234.5, "THB"))
        assertEquals("$12.00", formatMoney(12.0, "USD"))
    }

    @Test
    fun formatMoney_jpyHasNoDecimals_unknownSymbolUsesCode() {
        assertEquals("¥1,500", formatMoney(1500.0, "JPY"))
        assertEquals("CHF 10.00", formatMoney(10.0, "CHF"))
    }

    @Test
    fun formatOriginal_appendsCode() {
        assertEquals("$12.00 USD", formatOriginal(12.0, "USD"))
    }

    @Test
    fun round2_roundsHalfUpAndFixesFloatNoise() {
        assertEquals(2.35, round2(2.345), 0.0)
        assertEquals(0.3, round2(0.1 + 0.2), 0.0)
    }

    @Test
    fun monthLabel_isEnglishMonthAndYear() {
        assertEquals("October 2026", YearMonth.of(2026, 10).label())
    }

    @Test
    fun startOfWeek_isMonday() {
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14).startOfWeek()) // Wed
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12).startOfWeek()) // Mon
        assertEquals(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18).startOfWeek()) // Sun
    }

    @Test
    fun dayLabel_todayYesterdayOtherwiseShortDate() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(LocalDate.of(2026, 10, 12), today))
        assertEquals("Sat, 10 Oct", dayLabel(LocalDate.of(2026, 10, 10), today))
    }

    @Test
    fun defaultHomeCurrency_usesLocaleWhenSupportedElseUsd() {
        assertEquals("THB", SupportedCurrencies.defaultHomeCurrency(Locale.forLanguageTag("th-TH")))
        assertEquals("JPY", SupportedCurrencies.defaultHomeCurrency(Locale.JAPAN))
        assertEquals("USD", SupportedCurrencies.defaultHomeCurrency(Locale.forLanguageTag("es-AR"))) // ARS unsupported
        assertEquals("USD", SupportedCurrencies.defaultHomeCurrency(Locale.ENGLISH)) // no country
    }

    @Test
    fun categoryFromName_unknownFallsBackToOther() {
        assertEquals(Category.FOOD, Category.fromName("FOOD"))
        assertEquals(Category.OTHER, Category.fromName("GROCERIES"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.MoneyAndDatesTest"`
Expected: FAIL, compilation errors `Unresolved reference 'formatMoney'` (and others)

- [ ] **Step 3: Implement**

`app/src/main/java/com/spendsense/app/domain/Category.kt`:

```kotlin
package com.spendsense.app.domain

/** [color] is an ARGB value; the UI wraps it with `Color(category.color)`. */
enum class Category(val label: String, val emoji: String, val color: Long) {
    FOOD("Food", "🍜", 0xFFFB8C00),
    TRANSPORT("Transport", "🚕", 0xFF1E88E5),
    SHOPPING("Shopping", "🛍️", 0xFF43A047),
    BILLS("Bills", "🧾", 0xFF8E24AA),
    FUN("Fun", "🎮", 0xFFE53935),
    HEALTH("Health", "💊", 0xFF00897B),
    OTHER("Other", "📦", 0xFF757575);

    companion object {
        /** Unknown names (e.g. from an older database) fall back to [OTHER] instead of crashing. */
        fun fromName(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}
```

`app/src/main/java/com/spendsense/app/domain/Expense.kt`:

```kotlin
package com.spendsense.app.domain

import java.time.LocalDate

/**
 * One expense. [amountHome] is in the user's current home currency;
 * [originalAmount]/[originalCurrency] are what the user typed (e.g. 12.00 USD).
 */
data class Expense(
    val id: Long = 0,
    val title: String,
    val category: Category,
    val amountHome: Double,
    val originalAmount: Double,
    val originalCurrency: String,
    val date: LocalDate,
    val note: String? = null,
)
```

`app/src/main/java/com/spendsense/app/domain/Money.kt`:

```kotlin
package com.spendsense.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

private val currencySymbols = mapOf(
    "THB" to "฿", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "KRW" to "₩",
    "INR" to "₹", "CNY" to "CN¥", "PHP" to "₱", "TRY" to "₺", "ILS" to "₪",
)

/** Rounds half-up to 2 decimals, so 0.1 + 0.2 is stored as 0.3. */
fun round2(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

/** "฿1,234.50", "$12.00", "¥1,500" (JPY has no decimals), "CHF 10.00" (no known symbol). */
fun formatMoney(amount: Double, currencyCode: String): String {
    val digits = runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
        .getOrDefault(2)
        .coerceAtLeast(0)
    val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
    val number = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(amount)
    val symbol = currencySymbols[currencyCode]
    return if (symbol != null) "$symbol$number" else "$currencyCode $number"
}

/** "$12.00 USD": used to show what was originally typed next to the converted amount. */
fun formatOriginal(amount: Double, currencyCode: String): String =
    "${formatMoney(amount, currencyCode)} $currencyCode"
```

`app/src/main/java/com/spendsense/app/domain/Dates.kt`:

```kotlin
package com.spendsense.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val dayFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "October 2026" */
fun YearMonth.label(): String = format(monthFormatter)

/** "12 Oct 2026" */
fun LocalDate.fullLabel(): String = format(fullDateFormatter)

/** Weeks run Monday–Sunday. */
fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** "Today", "Yesterday", otherwise "Sat, 10 Oct". */
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dayFormatter)
}
```

`app/src/main/java/com/spendsense/app/domain/SupportedCurrencies.kt`:

```kotlin
package com.spendsense.app.domain

import java.util.Currency
import java.util.Locale

object SupportedCurrencies {
    /**
     * The currencies Frankfurter (ECB rates) supports. Used for the first-launch default
     * (works offline) and as a fallback when GET /currencies fails.
     */
    val fallback: Map<String, String> = linkedMapOf(
        "AUD" to "Australian Dollar", "BRL" to "Brazilian Real", "CAD" to "Canadian Dollar",
        "CHF" to "Swiss Franc", "CNY" to "Chinese Renminbi Yuan", "CZK" to "Czech Koruna",
        "DKK" to "Danish Krone", "EUR" to "Euro", "GBP" to "British Pound",
        "HKD" to "Hong Kong Dollar", "HUF" to "Hungarian Forint", "IDR" to "Indonesian Rupiah",
        "ILS" to "Israeli New Shekel", "INR" to "Indian Rupee", "ISK" to "Icelandic Króna",
        "JPY" to "Japanese Yen", "KRW" to "South Korean Won", "MXN" to "Mexican Peso",
        "MYR" to "Malaysian Ringgit", "NOK" to "Norwegian Krone", "NZD" to "New Zealand Dollar",
        "PHP" to "Philippine Peso", "PLN" to "Polish Złoty", "RON" to "Romanian Leu",
        "SEK" to "Swedish Krona", "SGD" to "Singapore Dollar", "THB" to "Thai Baht",
        "TRY" to "Turkish Lira", "USD" to "United States Dollar", "ZAR" to "South African Rand",
    )

    /** The phone's local currency if Frankfurter supports it, otherwise USD. */
    fun defaultHomeCurrency(locale: Locale): String {
        val code = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        return if (code != null && code in fallback) code else "USD"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.MoneyAndDatesTest"`
Expected: `BUILD SUCCESSFUL`, 9 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: domain model with money and date helpers" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Room persistence and ExpenseRepository

Stores expenses and saved insights. Deliverable: Room DB, DAO transactions (re-convert, replace all, clear) and an instrumented DAO test passing on the emulator.

**Files:**
- Create: `data/local/ExpenseEntity.kt`, `data/local/InsightEntity.kt`, `data/local/ExpenseDao.kt`, `data/local/InsightDao.kt`, `data/local/AppDatabase.kt`, `data/repository/ExpenseRepository.kt`
- Test: `app/src/androidTest/java/com/spendsense/app/data/local/ExpenseDaoTest.kt`

**Interfaces:**
- Consumes: `Expense`, `Category.fromName` (Task 2).
- Produces:
  - `ExpenseEntity(id, title, category: String, amountHome, originalAmount, originalCurrency, date: Long /*epochDay*/, note)` with `toDomain()` / `Expense.toEntity()`
  - `InsightEntity(month: String /*"YYYY-MM"*/, json: String, dataHash: Int, generatedAt: Long)`
  - `abstract class ExpenseDao` with `observeBetween(startEpochDay, endEpochDay): Flow<List<ExpenseEntity>>`, `getBetween(...)`, `getById(id)`, `upsert(e)`, `insertAll(list)`, `deleteById(id)`, `reconvertAll(newHome, rate)`, `replaceAll(list)`, `clearAll()`
  - `interface InsightDao { fun observe(month: String): Flow<InsightEntity?>; suspend fun upsert(insight: InsightEntity) }`
  - `AppDatabase.create(context)`, `expenseDao()`, `insightDao()`
  - `interface ExpenseRepository { observeMonth(YearMonth): Flow<List<Expense>>; getBetween(LocalDate, LocalDate); getById(Long); save(Expense); delete(Long); reconvertAll(String, Double); replaceAll(List<Expense>); clearAll() }` and `class RoomExpenseRepository(dao: ExpenseDao)`

- [ ] **Step 1: Write the failing instrumented test**

`app/src/androidTest/java/com/spendsense/app/data/local/ExpenseDaoTest.kt`:

```kotlin
package com.spendsense.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ExpenseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExpenseDao

    private fun entity(id: Long, date: String, amountHome: Double, original: Double = amountHome, currency: String = "THB") =
        ExpenseEntity(id, "E$id", "FOOD", amountHome, original, currency, LocalDate.parse(date).toEpochDay(), null)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        dao = db.expenseDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeBetween_includesFirstAndLastDayOfMonth_newestFirst() = runTest {
        dao.insertAll(
            listOf(
                entity(1, "2026-09-30", 1.0),
                entity(2, "2026-10-01", 2.0),
                entity(3, "2026-10-31", 3.0),
                entity(4, "2026-11-01", 4.0),
            )
        )
        val october = dao.observeBetween(
            LocalDate.parse("2026-10-01").toEpochDay(),
            LocalDate.parse("2026-10-31").toEpochDay(),
        ).first()
        assertEquals(listOf(3L, 2L), october.map { it.id })
    }

    @Test
    fun reconvertAll_multipliesAmounts_restoresExactNewHomeAmounts_andDeletesInsights() = runTest {
        dao.insertAll(
            listOf(
                entity(1, "2026-10-01", amountHome = 100.0), // typed in THB
                entity(2, "2026-10-02", amountHome = 402.0, original = 12.0, currency = "USD"),
            )
        )
        db.insightDao().upsert(InsightEntity("2026-10", "{}", 1, 0L))

        dao.reconvertAll(newHome = "USD", rate = 0.0297)

        assertEquals(2.97, dao.getById(1)!!.amountHome, 0.001)
        assertEquals(12.0, dao.getById(2)!!.amountHome, 0.0)
        assertNull(db.insightDao().observe("2026-10").first())
    }

    @Test
    fun replaceAll_swapsAllRows() = runTest {
        dao.insertAll(listOf(entity(1, "2026-10-01", 1.0)))
        dao.replaceAll(listOf(entity(0, "2026-10-02", 2.0), entity(0, "2026-10-03", 3.0)))
        val all = dao.getBetween(0, Long.MAX_VALUE)
        assertEquals(listOf(3.0, 2.0), all.map { it.amountHome })
    }
}
```

- [ ] **Step 2: Verify it fails to compile**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebugAndroidTest`
Expected: FAIL with `Unresolved reference 'AppDatabase'`

- [ ] **Step 3: Implement the Room layer**

`app/src/main/java/com/spendsense/app/data/local/ExpenseEntity.kt`:

```kotlin
package com.spendsense.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import java.time.LocalDate

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val amountHome: Double,
    val originalAmount: Double,
    val originalCurrency: String,
    /** LocalDate.toEpochDay(), so date range queries are simple number comparisons. */
    val date: Long,
    val note: String?,
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    title = title,
    category = Category.fromName(category),
    amountHome = amountHome,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    date = LocalDate.ofEpochDay(date),
    note = note,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    title = title,
    category = category.name,
    amountHome = amountHome,
    originalAmount = originalAmount,
    originalCurrency = originalCurrency,
    date = date.toEpochDay(),
    note = note,
)
```

`app/src/main/java/com/spendsense/app/data/local/InsightEntity.kt`:

```kotlin
package com.spendsense.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One saved AI analysis per month. */
@Entity(tableName = "insights")
data class InsightEntity(
    /** "YYYY-MM", i.e. YearMonth.toString() */
    @PrimaryKey val month: String,
    /** Raw Gemini JSON: {"summary": "...", "tips": ["..."]} */
    val json: String,
    /** Hash of the month's expense data when generated; a mismatch means "outdated". */
    val dataHash: Int,
    val generatedAt: Long,
)
```

`app/src/main/java/com/spendsense/app/data/local/ExpenseDao.kt`:

```kotlin
package com.spendsense.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Abstract class (not interface) so the @Transaction methods can have bodies. */
@Dao
abstract class ExpenseDao {

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY date DESC, id DESC"
    )
    abstract fun observeBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT * FROM expenses WHERE date BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY date DESC, id DESC"
    )
    abstract suspend fun getBetween(startEpochDay: Long, endEpochDay: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    abstract suspend fun getById(id: Long): ExpenseEntity?

    @Upsert
    abstract suspend fun upsert(expense: ExpenseEntity)

    @Insert
    abstract suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("DELETE FROM expenses WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    abstract suspend fun deleteAllExpenses()

    @Query("DELETE FROM insights")
    abstract suspend fun deleteAllInsights()

    /** Expenses typed in the new home currency get their exact amount back; others are multiplied. */
    @Query(
        "UPDATE expenses SET amountHome = CASE " +
            "WHEN originalCurrency = :newHome THEN originalAmount " +
            "ELSE ROUND(amountHome * :rate, 2) END"
    )
    abstract suspend fun reconvertAmounts(newHome: String, rate: Double)

    /** Saved insights are in the old currency, so they are deleted too. */
    @Transaction
    open suspend fun reconvertAll(newHome: String, rate: Double) {
        reconvertAmounts(newHome, rate)
        deleteAllInsights()
    }

    @Transaction
    open suspend fun replaceAll(expenses: List<ExpenseEntity>) {
        deleteAllExpenses()
        deleteAllInsights()
        insertAll(expenses)
    }

    @Transaction
    open suspend fun clearAll() {
        deleteAllExpenses()
        deleteAllInsights()
    }
}
```

`app/src/main/java/com/spendsense/app/data/local/InsightDao.kt`:

```kotlin
package com.spendsense.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InsightDao {
    @Query("SELECT * FROM insights WHERE month = :month")
    fun observe(month: String): Flow<InsightEntity?>

    @Upsert
    suspend fun upsert(insight: InsightEntity)
}
```

`app/src/main/java/com/spendsense/app/data/local/AppDatabase.kt`:

```kotlin
package com.spendsense.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExpenseEntity::class, InsightEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun insightDao(): InsightDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "spendsense.db").build()
    }
}
```

`app/src/main/java/com/spendsense/app/data/repository/ExpenseRepository.kt`:

```kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.local.ExpenseDao
import com.spendsense.app.data.local.toDomain
import com.spendsense.app.data.local.toEntity
import com.spendsense.app.domain.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

interface ExpenseRepository {
    /** Newest first. */
    fun observeMonth(month: YearMonth): Flow<List<Expense>>
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense>
    suspend fun getById(id: Long): Expense?
    /** Inserts when id == 0, otherwise updates (also used to undo a delete). */
    suspend fun save(expense: Expense)
    suspend fun delete(id: Long)
    /** Multiplies every amountHome by [rate] (exact restore for [newHome] expenses) and deletes insights. */
    suspend fun reconvertAll(newHome: String, rate: Double)
    /** Deletes all expenses + insights, then inserts [expenses]. */
    suspend fun replaceAll(expenses: List<Expense>)
    suspend fun clearAll()
}

class RoomExpenseRepository(private val dao: ExpenseDao) : ExpenseRepository {

    override fun observeMonth(month: YearMonth): Flow<List<Expense>> =
        dao.observeBetween(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense> =
        dao.getBetween(start.toEpochDay(), end.toEpochDay()).map { it.toDomain() }

    override suspend fun getById(id: Long): Expense? = dao.getById(id)?.toDomain()

    override suspend fun save(expense: Expense) = dao.upsert(expense.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun reconvertAll(newHome: String, rate: Double) = dao.reconvertAll(newHome, rate)

    override suspend fun replaceAll(expenses: List<Expense>) =
        dao.replaceAll(expenses.map { it.toEntity() })

    override suspend fun clearAll() = dao.clearAll()
}
```

- [ ] **Step 4: Run the instrumented test on the emulator**

The emulator must be running (Task 1, Step 11).
Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, 3 tests passed (`ExpenseDaoTest`)

If no emulator can be started, run `:app:assembleDebugAndroidTest` (must succeed) and record that the device test is still pending.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: Room database and expense repository" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Networking, currency rates, settings and AppContainer

Retrofit + Frankfurter with caching and friendly errors, the DataStore home-currency setting, and the container that wires it together. Deliverable: tested `safeApiCall`/`CurrencyRepository`, and an app that still launches with the container.

**Files:**
- Create: `domain/AppError.kt`, `data/remote/SafeApiCall.kt`, `data/remote/NetworkModule.kt`, `data/remote/FrankfurterApi.kt`, `data/repository/CurrencyRepository.kt`, `data/repository/SettingsRepository.kt`, `AppContainer.kt`
- Modify: `SpendSenseApp.kt` (replace whole file)
- Test: `app/src/test/java/com/spendsense/app/data/remote/SafeApiCallTest.kt`, `app/src/test/java/com/spendsense/app/data/repository/CurrencyRepositoryTest.kt`

**Interfaces:**
- Consumes: `SupportedCurrencies` (Task 2), `AppDatabase`, `RoomExpenseRepository` (Task 3).
- Produces:
  - `sealed class AppError(val userMessage: String) : Exception` with `NoInternet`, `RateLimited`, `InvalidApiKey`, `EmptyAiResponse`, `Unknown`; `fun Throwable.userMessage(): String`
  - `suspend fun <T> safeApiCall(block: suspend () -> T): Result<T>`, `fun Throwable.toAppError(): AppError`
  - `object NetworkModule { val json: Json; fun okHttpClient(readTimeoutSeconds: Long, enableLogging: Boolean): OkHttpClient; fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit }`
  - `FRANKFURTER_BASE_URL`, `interface FrankfurterApi`, `LatestRatesDto`
  - `interface CurrencyRepository { suspend fun getRate(from: String, to: String): Result<Double>; suspend fun getCurrencies(): Map<String, String> }`, `class NetworkCurrencyRepository(api: FrankfurterApi)`
  - `interface SettingsRepository { val homeCurrency: Flow<String>; suspend fun setHomeCurrency(code: String) }`, `class DataStoreSettingsRepository(dataStore, defaultCurrency)`
  - `class AppContainer(context)` exposing `settingsRepository`, `expenseRepository`, `currencyRepository`; `SpendSenseApp.container`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/spendsense/app/data/remote/SafeApiCallTest.kt`:

```kotlin
package com.spendsense.app.data.remote

import com.spendsense.app.domain.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class SafeApiCallTest {

    private fun httpError(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody(null)))

    @Test
    fun success_isWrapped() = runTest {
        assertEquals(Result.success(5), safeApiCall { 5 })
    }

    @Test
    fun errors_mapToFriendlyAppErrors() {
        assertEquals(AppError.NoInternet, IOException().toAppError())
        assertEquals(AppError.NoInternet, SocketTimeoutException().toAppError())
        assertEquals(AppError.RateLimited, httpError(429).toAppError())
        assertEquals(AppError.InvalidApiKey, httpError(400).toAppError())
        assertEquals(AppError.InvalidApiKey, httpError(403).toAppError())
        assertEquals(AppError.Unknown, httpError(500).toAppError())
        assertEquals(AppError.EmptyAiResponse, AppError.EmptyAiResponse.toAppError())
        assertEquals(AppError.Unknown, IllegalStateException().toAppError())
    }

    @Test
    fun failure_isReturnedNotThrown() = runTest {
        val result = safeApiCall<Int> { throw IOException("offline") }
        assertEquals(AppError.NoInternet, result.exceptionOrNull())
    }

    @Test
    fun cancellation_isRethrown() = runTest {
        try {
            safeApiCall<Int> { throw CancellationException("left screen") }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // expected
        }
    }
}
```

`app/src/test/java/com/spendsense/app/data/repository/CurrencyRepositoryTest.kt`:

```kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.LatestRatesDto
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.SupportedCurrencies
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private class FakeFrankfurterApi(
    private val rates: Map<String, Double> = mapOf("USD->THB" to 33.6),
    var offline: Boolean = false,
) : FrankfurterApi {
    var latestCalls = 0

    override suspend fun latest(base: String, symbols: String): LatestRatesDto {
        latestCalls++
        if (offline) throw IOException("offline")
        return LatestRatesDto(base, "2026-10-12", mapOf(symbols to rates.getValue("$base->$symbols")))
    }

    override suspend fun currencies(): Map<String, String> {
        if (offline) throw IOException("offline")
        return mapOf("USD" to "United States Dollar", "THB" to "Thai Baht")
    }
}

class CurrencyRepositoryTest {

    @Test
    fun sameCurrency_isOneWithoutNetwork() = runTest {
        val api = FakeFrankfurterApi()
        val repo = NetworkCurrencyRepository(api)
        assertEquals(1.0, repo.getRate("THB", "THB").getOrThrow(), 0.0)
        assertEquals(0, api.latestCalls)
    }

    @Test
    fun rate_isFetchedOnceThenCached() = runTest {
        val api = FakeFrankfurterApi()
        val repo = NetworkCurrencyRepository(api)
        assertEquals(33.6, repo.getRate("USD", "THB").getOrThrow(), 0.0)
        api.offline = true // cached value must still work offline
        assertEquals(33.6, repo.getRate("USD", "THB").getOrThrow(), 0.0)
        assertEquals(1, api.latestCalls)
    }

    @Test
    fun offline_returnsNoInternetFailure() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi(offline = true))
        assertEquals(AppError.NoInternet, repo.getRate("USD", "THB").exceptionOrNull())
    }

    @Test
    fun currencies_fallBackToBuiltInListWhenOffline() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi(offline = true))
        assertEquals(SupportedCurrencies.fallback.keys.sorted(), repo.getCurrencies().keys.toList())
    }

    @Test
    fun currencies_comeFromApiSortedByCode() = runTest {
        val repo = NetworkCurrencyRepository(FakeFrankfurterApi())
        assertEquals(listOf("THB", "USD"), repo.getCurrencies().keys.toList())
    }
}
```

- [ ] **Step 2: Verify they fail**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.data.*"`
Expected: FAIL with `Unresolved reference 'safeApiCall'` / `'FrankfurterApi'`

- [ ] **Step 3: Implement errors and networking**

`app/src/main/java/com/spendsense/app/domain/AppError.kt`:

```kotlin
package com.spendsense.app.domain

/** Every network failure is turned into one of these, each with a message that is safe to show. */
sealed class AppError(val userMessage: String) : Exception(userMessage) {
    data object NoInternet : AppError("No internet connection")
    data object RateLimited : AppError("AI is busy (free-tier limit). Try again in a minute.")
    data object InvalidApiKey : AppError("Gemini API key missing or invalid. See README.")
    data object EmptyAiResponse : AppError("The AI couldn't answer that. Try rephrasing.")
    data object Unknown : AppError("Something went wrong. Please try again.")
}

/** The message to show for any failure, including ones that are not [AppError]. */
fun Throwable.userMessage(): String = (this as? AppError)?.userMessage ?: AppError.Unknown.userMessage
```

`app/src/main/java/com/spendsense/app/data/remote/SafeApiCall.kt`:

```kotlin
package com.spendsense.app.data.remote

import com.spendsense.app.domain.AppError
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a network call and never throws: failures come back as Result.failure(AppError).
 * Cancellation is re-thrown so leaving a screen still cancels its coroutines.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toAppError())
    }

fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is IOException -> AppError.NoInternet // includes timeouts and "no route to host"
    is HttpException -> when (code()) {
        429 -> AppError.RateLimited
        400, 401, 403 -> AppError.InvalidApiKey
        else -> AppError.Unknown
    }
    else -> AppError.Unknown
}
```

`app/src/main/java/com/spendsense/app/data/remote/NetworkModule.kt`:

```kotlin
package com.spendsense.app.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    /**
     * ignoreUnknownKeys: APIs add fields over time.
     * explicitNulls = false: null request fields are left out of the JSON.
     * encodeDefaults = true: non-null fields with default values are still sent.
     */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun okHttpClient(readTimeoutSeconds: Long, enableLogging: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .apply {
                if (enableLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                            redactHeader("x-goog-api-key")
                        }
                    )
                }
            }
            .build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
```

`app/src/main/java/com/spendsense/app/data/remote/FrankfurterApi.kt`:

```kotlin
package com.spendsense.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/v1/"

/** Free exchange rates from the European Central Bank. No API key. */
interface FrankfurterApi {
    /** e.g. latest?base=USD&symbols=THB. Never call with base == symbols (API returns an error). */
    @GET("latest")
    suspend fun latest(@Query("base") base: String, @Query("symbols") symbols: String): LatestRatesDto

    /** {"AUD": "Australian Dollar", ...} */
    @GET("currencies")
    suspend fun currencies(): Map<String, String>
}

/** {"amount":1.0,"base":"USD","date":"2026-10-12","rates":{"THB":33.615}} */
@Serializable
data class LatestRatesDto(
    val base: String,
    val date: String,
    val rates: Map<String, Double>,
)
```

- [ ] **Step 4: Implement the repositories**

`app/src/main/java/com/spendsense/app/data/repository/CurrencyRepository.kt`:

```kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.safeApiCall
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.SupportedCurrencies
import java.util.concurrent.ConcurrentHashMap

interface CurrencyRepository {
    /** How many [to] one [from] is worth. Same currency = 1.0 without a network call. */
    suspend fun getRate(from: String, to: String): Result<Double>

    /** Code -> name, sorted by code. Never fails: falls back to the built-in list. */
    suspend fun getCurrencies(): Map<String, String>
}

class NetworkCurrencyRepository(private val api: FrankfurterApi) : CurrencyRepository {

    /** Rates are cached for the app session: one network call per currency pair. */
    private val rateCache = ConcurrentHashMap<String, Double>()
    @Volatile private var currencies: Map<String, String>? = null

    override suspend fun getRate(from: String, to: String): Result<Double> {
        if (from == to) return Result.success(1.0)
        val key = "$from->$to"
        rateCache[key]?.let { return Result.success(it) }
        return safeApiCall { api.latest(base = from, symbols = to).rates[to] ?: throw AppError.Unknown }
            .onSuccess { rateCache[key] = it }
    }

    override suspend fun getCurrencies(): Map<String, String> {
        currencies?.let { return it }
        val fromApi = safeApiCall { api.currencies() }.getOrNull()
        return if (fromApi.isNullOrEmpty()) {
            SupportedCurrencies.fallback.toSortedMap()
        } else {
            fromApi.toSortedMap().also { currencies = it }
        }
    }
}
```

`app/src/main/java/com/spendsense/app/data/repository/SettingsRepository.kt`:

```kotlin
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

    override val homeCurrency: Flow<String> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[homeCurrencyKey] ?: defaultCurrency }
        .distinctUntilChanged()

    override suspend fun setHomeCurrency(code: String) {
        dataStore.edit { prefs -> prefs[homeCurrencyKey] = code }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.data.*"`
Expected: `BUILD SUCCESSFUL`, 9 tests passed

- [ ] **Step 6: Wire the container**

`app/src/main/java/com/spendsense/app/AppContainer.kt` (first version; Task 8 adds `aiRepository`):

```kotlin
package com.spendsense.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.spendsense.app.data.local.AppDatabase
import com.spendsense.app.data.remote.FRANKFURTER_BASE_URL
import com.spendsense.app.data.remote.FrankfurterApi
import com.spendsense.app.data.remote.NetworkModule
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.DataStoreSettingsRepository
import com.spendsense.app.data.repository.ExpenseRepository
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
}
```

Replace `app/src/main/java/com/spendsense/app/SpendSenseApp.kt`:

```kotlin
package com.spendsense.app

import android.app.Application

class SpendSenseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 7: Build, run all unit tests, launch**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Expected: tests pass; the app launches as in Task 1 (no crash from the container).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: Frankfurter currency rates, settings store and app container" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Expenses screen

The home screen: month switcher, total card, list grouped by day, swipe to delete with Undo, and a FAB. Deliverable: a working list screen backed by Room.

**Files:**
- Create: `ui/common/Common.kt`, `ui/expenses/ExpensesViewModel.kt`
- Modify: `ui/expenses/ExpensesScreen.kt` (replace placeholder entirely)
- Test: `app/src/test/java/com/spendsense/app/ui/expenses/GroupByDayTest.kt`

**Interfaces:**
- Consumes: `ExpenseRepository`, `SettingsRepository`, `AppContainer` (Tasks 3–4); `formatMoney`, `formatOriginal`, `dayLabel`, `label` (Task 2).
- Produces:
  - `fun CreationExtras.spendSenseApp(): SpendSenseApp` (used by every ViewModel `Factory`)
  - `@Composable fun MonthTopBar(month: YearMonth, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit)`, `@Composable fun CategoryBadge(category: Category, modifier: Modifier = Modifier)`
  - `fun groupByDay(expenses: List<Expense>, homeCurrency: String, today: LocalDate): List<DayGroup>`; `ExpensesViewModel` with `uiState`, `previousMonth()`, `nextMonth()`, `delete(Expense)`, `restore(Expense)`, `Factory`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/ui/expenses/GroupByDayTest.kt`:

```kotlin
package com.spendsense.app.ui.expenses

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GroupByDayTest {

    private val today = LocalDate.of(2026, 10, 13)

    private fun expense(id: Long, date: LocalDate, amount: Double, currency: String = "THB", original: Double = amount) =
        Expense(id, "E$id", Category.FOOD, amount, original, currency, date)

    @Test
    fun groupsNewestDayFirst_newestEntryFirstWithinDay() {
        val groups = groupByDay(
            listOf(
                expense(1, today.minusDays(3), 10.0),
                expense(2, today, 20.0),
                expense(3, today, 30.0),
                expense(4, today.minusDays(1), 40.0),
            ),
            homeCurrency = "THB",
            today = today,
        )
        assertEquals(listOf("Today", "Yesterday", "Sat, 10 Oct"), groups.map { it.label })
        assertEquals(listOf(3L, 2L), groups[0].rows.map { it.expense.id })
    }

    @Test
    fun foreignExpense_showsOriginalAmount_homeExpenseDoesNot() {
        val rows = groupByDay(
            listOf(expense(1, today, 403.2, "USD", 12.0), expense(2, today, 85.0)),
            homeCurrency = "THB",
            today = today,
        ).single().rows
        val usd = rows.first { it.expense.id == 1L }
        assertEquals("฿403.20", usd.amountText)
        assertEquals("$12.00 USD", usd.originalText)
        assertNull(rows.first { it.expense.id == 2L }.originalText)
    }
}
```

- [ ] **Step 2: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.expenses.GroupByDayTest"`
Expected: FAIL with `Unresolved reference 'groupByDay'`

- [ ] **Step 3: Shared UI helpers**

`app/src/main/java/com/spendsense/app/ui/common/Common.kt`:

```kotlin
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
```

- [ ] **Step 4: ViewModel**

`app/src/main/java/com/spendsense/app/ui/expenses/ExpensesViewModel.kt`:

```kotlin
package com.spendsense.app.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.dayLabel
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.domain.formatOriginal
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class ExpenseRow(
    val expense: Expense,
    /** "฿389.40" */
    val amountText: String,
    /** "$12.00 USD" when typed in another currency, else null. */
    val originalText: String?,
)

data class DayGroup(val label: String, val rows: List<ExpenseRow>)

data class ExpensesUiState(
    val month: YearMonth,
    val totalText: String = "",
    val count: Int = 0,
    val groups: List<DayGroup> = emptyList(),
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
)

/** Newest day first; inside a day, newest entry first. */
fun groupByDay(expenses: List<Expense>, homeCurrency: String, today: LocalDate): List<DayGroup> =
    expenses
        .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
        .groupBy { it.date }
        .map { (date, list) ->
            DayGroup(
                label = dayLabel(date, today),
                rows = list.map { expense ->
                    ExpenseRow(
                        expense = expense,
                        amountText = formatMoney(expense.amountHome, homeCurrency),
                        originalText = if (expense.originalCurrency != homeCurrency) {
                            formatOriginal(expense.originalAmount, expense.originalCurrency)
                        } else {
                            null
                        },
                    )
                },
            )
        }

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenseRepository: ExpenseRepository,
    settingsRepository: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))

    val uiState: StateFlow<ExpensesUiState> = month
        .flatMapLatest { m ->
            combine(expenseRepository.observeMonth(m), settingsRepository.homeCurrency) { expenses, home ->
                ExpensesUiState(
                    month = m,
                    totalText = formatMoney(expenses.sumOf { it.amountHome }, home),
                    count = expenses.size,
                    groups = groupByDay(expenses, home, today()),
                    canGoNext = m < YearMonth.from(today()),
                    isLoading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState(month = month.value))

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.from(today())) it.plusMonths(1) else it }

    fun delete(expense: Expense) {
        viewModelScope.launch { expenseRepository.delete(expense.id) }
    }

    /** Undo: saving with the same id puts the row back. */
    fun restore(expense: Expense) {
        viewModelScope.launch { expenseRepository.save(expense) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                ExpensesViewModel(container.expenseRepository, container.settingsRepository)
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.expenses.GroupByDayTest"`
Expected: `BUILD SUCCESSFUL`, 2 tests passed

- [ ] **Step 6: Screen** (replace the placeholder)

`app/src/main/java/com/spendsense/app/ui/expenses/ExpensesScreen.kt`:

```kotlin
package com.spendsense.app.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendsense.app.ui.common.CategoryBadge
import com.spendsense.app.ui.common.MonthTopBar
import kotlinx.coroutines.launch

@Composable
fun ExpensesScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (Long) -> Unit,
    viewModel: ExpensesViewModel = viewModel(factory = ExpensesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MonthTopBar(
                month = state.month,
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExpense) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "summary") { SummaryCard(totalText = state.totalText, count = state.count) }

            if (!state.isLoading && state.groups.isEmpty()) {
                item(key = "empty") { EmptyState() }
            }

            state.groups.forEach { group ->
                item(key = "header-${group.label}") {
                    Text(
                        text = group.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(group.rows, key = { it.expense.id }) { row ->
                    SwipeToDeleteRow(
                        row = row,
                        onClick = { onEditExpense(row.expense.id) },
                        onDelete = {
                            viewModel.delete(row.expense)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Deleted \"${row.expense.title}\"",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.restore(row.expense)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(totalText: String, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge)
            Text(totalText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("$count expense${if (count == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        text = "No expenses this month.\nTap + to add one, or load demo data in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
    )
}

@Composable
private fun SwipeToDeleteRow(row: ExpenseRow, onClick: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        ExpenseRowItem(row = row, onClick = onClick)
    }
}

@Composable
private fun ExpenseRowItem(row: ExpenseRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryBadge(row.expense.category)
            Column(Modifier.weight(1f)) {
                Text(
                    row.expense.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.expense.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(row.amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                row.originalText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
```

- [ ] **Step 7: Build and check on the emulator**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Expected: the green "Spent this month ฿0.00 / 0 expenses" card (or your locale's currency) and the empty-state text. ‹ goes to previous months; › is disabled on the current month. + opens the Add placeholder.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: expenses list screen with month switcher and swipe-to-delete" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Add/Edit expense screen with live currency conversion

The form: amount + currency, live rate, title, category chips, date picker and note, plus delete in edit mode. Deliverable: you can add, edit and delete expenses (THB and foreign), backed by ViewModel tests.

**Files:**
- Create: `ui/addedit/AddEditViewModel.kt`
- Modify: `ui/addedit/AddEditExpenseScreen.kt` (replace placeholder entirely)
- Test: `app/src/test/java/com/spendsense/app/testing/Fakes.kt`, `app/src/test/java/com/spendsense/app/ui/addedit/AddEditViewModelTest.kt`

**Interfaces:**
- Consumes: `ExpenseRepository`, `CurrencyRepository`, `SettingsRepository`, `AddEditRoute`, `spendSenseApp()`, `round2`, `formatMoney`, `fullLabel`.
- Produces:
  - `sealed interface RateState { NotNeeded; Loading; Ready(rate: Double); Failed }`, `data class AddEditUiState(...)` with `amount`, `isValid`, `convertedAmount`
  - `fun parseAmount(text: String): Double?`, `fun isValidExpenseInput(amountText: String, title: String): Boolean`
  - `class AddEditViewModel(expenseId: Long, expenseRepository, currencyRepository, settingsRepository, today: LocalDate = LocalDate.now())` with `onAmountChange`, `onCurrencyChange`, `onTitleChange`, `onCategoryChange`, `onDateChange`, `onNoteChange`, `save()`, `delete()`, `Factory`
  - Test helpers used by Tasks 8–9: `MainDispatcherRule`, `FakeExpenseRepository(initial)` (public `expenses: MutableStateFlow<List<Expense>>`), `FakeCurrencyRepository(rates, offline)` (keys like `"USD->THB"`; defaults USD->THB 33.5, THB->USD 0.03), `FakeSettingsRepository(initial = "THB")`

- [ ] **Step 1: Write the test helpers**

`app/src/test/java/com/spendsense/app/testing/Fakes.kt`:

```kotlin
package com.spendsense.app.testing

import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.round2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.LocalDate
import java.time.YearMonth

/** Makes viewModelScope run on a test dispatcher. Add `@get:Rule val mainRule = MainDispatcherRule()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeExpenseRepository(initial: List<Expense> = emptyList()) : ExpenseRepository {
    val expenses = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeMonth(month: YearMonth): Flow<List<Expense>> =
        expenses.map { list -> list.filter { YearMonth.from(it.date) == month }.sortedByDescending { it.date } }

    override suspend fun getBetween(start: LocalDate, end: LocalDate): List<Expense> =
        expenses.value.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

    override suspend fun getById(id: Long): Expense? = expenses.value.firstOrNull { it.id == id }

    override suspend fun save(expense: Expense) {
        val saved = if (expense.id == 0L) expense.copy(id = nextId++) else expense
        expenses.value = expenses.value.filterNot { it.id == saved.id } + saved
    }

    override suspend fun delete(id: Long) {
        expenses.value = expenses.value.filterNot { it.id == id }
    }

    override suspend fun reconvertAll(newHome: String, rate: Double) {
        expenses.value = expenses.value.map {
            it.copy(amountHome = if (it.originalCurrency == newHome) it.originalAmount else round2(it.amountHome * rate))
        }
    }

    override suspend fun replaceAll(expenses: List<Expense>) {
        this.expenses.value = expenses.mapIndexed { index, e -> e.copy(id = index + 1L) }
    }

    override suspend fun clearAll() {
        expenses.value = emptyList()
    }
}

/** [rates] keys look like "USD->THB". Set [offline] to make every uncached rate fail. */
class FakeCurrencyRepository(
    private val rates: Map<String, Double> = mapOf("USD->THB" to 33.5, "THB->USD" to 0.03),
    var offline: Boolean = false,
) : CurrencyRepository {
    var rateCalls = 0

    override suspend fun getRate(from: String, to: String): Result<Double> {
        if (from == to) return Result.success(1.0)
        rateCalls++
        if (offline) return Result.failure(AppError.NoInternet)
        return rates["$from->$to"]?.let { Result.success(it) } ?: Result.failure(AppError.Unknown)
    }

    override suspend fun getCurrencies(): Map<String, String> =
        sortedMapOf("THB" to "Thai Baht", "USD" to "United States Dollar", "EUR" to "Euro")
}

class FakeSettingsRepository(initial: String = "THB") : SettingsRepository {
    override val homeCurrency = MutableStateFlow(initial)
    override suspend fun setHomeCurrency(code: String) {
        homeCurrency.value = code
    }
}
```

- [ ] **Step 2: Write the failing test**

`app/src/test/java/com/spendsense/app/ui/addedit/AddEditViewModelTest.kt`:

```kotlin
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

    @Test
    fun delete_removesExpenseAndFinishes() {
        expenses.expenses.value = listOf(Expense(3, "Lunch", Category.FOOD, 85.0, 85.0, "THB", today))
        val vm = viewModel(expenseId = 3)
        vm.delete()
        assertTrue(expenses.expenses.value.isEmpty())
        assertTrue(vm.uiState.value.isDone)
    }
}
```

- [ ] **Step 3: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.addedit.AddEditViewModelTest"`
Expected: FAIL with `Unresolved reference 'AddEditViewModel'` / `'parseAmount'`

- [ ] **Step 4: ViewModel**

`app/src/main/java/com/spendsense/app/ui/addedit/AddEditViewModel.kt`:

```kotlin
package com.spendsense.app.ui.addedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.round2
import com.spendsense.app.ui.common.spendSenseApp
import com.spendsense.app.ui.navigation.AddEditRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

sealed interface RateState {
    /** Currency is the home currency: no conversion. */
    data object NotNeeded : RateState
    data object Loading : RateState
    data class Ready(val rate: Double) : RateState
    data object Failed : RateState
}

data class AddEditUiState(
    val isEdit: Boolean = false,
    val isLoaded: Boolean = false,
    val amountText: String = "",
    val currency: String = "",
    val homeCurrency: String = "",
    val title: String = "",
    val category: Category = Category.FOOD,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    val currencies: List<String> = emptyList(),
    val rate: RateState = RateState.NotNeeded,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isDone: Boolean = false,
) {
    val amount: Double? get() = parseAmount(amountText)
    val isValid: Boolean get() = isValidExpenseInput(amountText, title)

    /** The amount in home currency, once a foreign currency's rate is known. */
    val convertedAmount: Double?
        get() {
            val ready = rate as? RateState.Ready ?: return null
            return amount?.let { round2(it * ready.rate) }
        }
}

/** Accepts "12", "12.5" and "12,5". Rejects 0, negatives, NaN and Infinity. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

fun isValidExpenseInput(amountText: String, title: String): Boolean =
    parseAmount(amountText) != null && title.isNotBlank()

class AddEditViewModel(
    /** 0 = new expense. */
    private val expenseId: Long,
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState(isEdit = expenseId != 0L, date = today))
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    /** The expense as it was loaded (edit mode), to avoid re-converting an unchanged amount. */
    private var original: Expense? = null
    private var rateJob: Job? = null

    init {
        viewModelScope.launch {
            val home = settingsRepository.homeCurrency.first()
            val existing = if (expenseId != 0L) expenseRepository.getById(expenseId) else null
            original = existing
            _uiState.update { state ->
                if (existing == null) {
                    state.copy(isLoaded = true, homeCurrency = home, currency = home, currencies = listOf(home))
                } else {
                    state.copy(
                        isLoaded = true,
                        homeCurrency = home,
                        currency = existing.originalCurrency,
                        amountText = BigDecimal.valueOf(existing.originalAmount).stripTrailingZeros().toPlainString(),
                        title = existing.title,
                        category = existing.category,
                        date = existing.date,
                        note = existing.note.orEmpty(),
                        currencies = listOf(home, existing.originalCurrency).distinct(),
                    )
                }
            }
            refreshRate()
            launch {
                val codes = currencyRepository.getCurrencies().keys
                _uiState.update { it.copy(currencies = (codes + it.currency + it.homeCurrency).distinct().sorted()) }
            }
        }
    }

    fun onAmountChange(text: String) = _uiState.update { it.copy(amountText = text, errorMessage = null) }
    fun onTitleChange(text: String) = _uiState.update { it.copy(title = text) }
    fun onCategoryChange(category: Category) = _uiState.update { it.copy(category = category) }
    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }
    fun onNoteChange(text: String) = _uiState.update { it.copy(note = text) }

    fun onCurrencyChange(code: String) {
        _uiState.update { it.copy(currency = code, errorMessage = null) }
        refreshRate()
    }

    /** Fetches the rate for the selected currency. A newer selection cancels an older fetch. */
    private fun refreshRate() {
        rateJob?.cancel()
        val state = _uiState.value
        if (state.currency == state.homeCurrency) {
            _uiState.update { it.copy(rate = RateState.NotNeeded) }
            return
        }
        _uiState.update { it.copy(rate = RateState.Loading) }
        rateJob = viewModelScope.launch {
            val result = currencyRepository.getRate(state.currency, state.homeCurrency)
            _uiState.update {
                it.copy(rate = result.fold(onSuccess = { rate -> RateState.Ready(rate) }, onFailure = { RateState.Failed }))
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val amount = state.amount
        if (amount == null || !state.isValid || state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val originalAmount = round2(amount)
            val previous = original
            val amountHome: Double? =
                if (previous != null && previous.originalCurrency == state.currency && previous.originalAmount == originalAmount) {
                    previous.amountHome // unchanged amount: keep it, no network needed
                } else {
                    currencyRepository.getRate(state.currency, state.homeCurrency).getOrNull()
                        ?.let { rate -> round2(originalAmount * rate) }
                }
            if (amountHome == null) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Can't get exchange rate — check your connection or enter the amount in ${state.homeCurrency}",
                    )
                }
                return@launch
            }
            expenseRepository.save(
                Expense(
                    id = expenseId,
                    title = state.title.trim(),
                    category = state.category,
                    amountHome = amountHome,
                    originalAmount = originalAmount,
                    originalCurrency = state.currency,
                    date = state.date,
                    note = state.note.trim().ifBlank { null },
                )
            )
            _uiState.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    fun delete() {
        if (expenseId == 0L) return
        viewModelScope.launch {
            expenseRepository.delete(expenseId)
            _uiState.update { it.copy(isDone = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                AddEditViewModel(
                    expenseId = createSavedStateHandle().toRoute<AddEditRoute>().expenseId,
                    expenseRepository = container.expenseRepository,
                    currencyRepository = container.currencyRepository,
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.addedit.AddEditViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 7 tests passed

- [ ] **Step 6: Screen** (replace the placeholder)

`app/src/main/java/com/spendsense/app/ui/addedit/AddEditExpenseScreen.kt`:

```kotlin
package com.spendsense.app.ui.addedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.domain.fullLabel
import java.time.LocalDate
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditExpenseScreen(
    onDone: () -> Unit,
    viewModel: AddEditViewModel = viewModel(factory = AddEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit expense" else "Add expense") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                CurrencyPicker(
                    selected = state.currency,
                    options = state.currencies,
                    onSelect = viewModel::onCurrencyChange,
                )
            }

            ConversionInfo(state)

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.onCategoryChange(category) },
                        label = { Text("${category.emoji} ${category.label}") },
                    )
                }
            }

            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Text("  ${state.date.fullLabel()}")
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = state.isValid && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toEpochDay() * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete expense?") },
            text = { Text("\"${state.title}\" will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CurrencyPicker(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose currency")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConversionInfo(state: AddEditUiState) {
    val text = when (val rate = state.rate) {
        RateState.NotNeeded -> return
        RateState.Loading -> "Getting live exchange rate…"
        RateState.Failed -> "Couldn't get the exchange rate (offline?). You can still save once you're online."
        is RateState.Ready -> {
            val converted = state.convertedAmount?.let { "≈ ${formatMoney(it, state.homeCurrency)} · " } ?: ""
            val rateText = String.format(Locale.US, "%.4f", rate.rate)
            "${converted}1 ${state.currency} = $rateText ${state.homeCurrency}\nLive rate from Frankfurter API"
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 7: Check on the emulator**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Check:
1. + → amount `85`, title `Lunch`, Food → Save → the row appears under "TODAY" and the total updates.
2. + → amount `12`, currency USD → the card shows "≈ ฿… · 1 USD = … THB, Live rate from Frankfurter API" → title `Netflix`, Bills → Save → the row shows the home amount plus "$12.00 USD".
3. Tap Netflix → edit the title → Save → updated. Tap again → 🗑 → Delete → gone.
4. Swipe a row right-to-left → snackbar "Deleted …" → Undo → the row is back.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add/edit expense with live currency conversion" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: ExpenseContextBuilder (prompt data)

A pure function that turns expenses into compact text with pre-calculated totals for Gemini, plus the month fingerprint used for "Outdated". Deliverable: tested builder.

**Files:**
- Create: `domain/ExpenseContextBuilder.kt`
- Test: `app/src/test/java/com/spendsense/app/domain/ExpenseContextBuilderTest.kt`

**Interfaces:**
- Consumes: `Expense`, `Category`, `startOfWeek` (Task 2).
- Produces: `object ExpenseContextBuilder` with `line(expense, homeCurrency): String`, `totals(expenses): String`, `monthContext(monthExpenses, month, homeCurrency): String`, `monthHash(monthExpenses, month, homeCurrency): Int`, `insightContext(expenses, month, homeCurrency, today): String`, `askContext(expenses, today, homeCurrency): String`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/domain/ExpenseContextBuilderTest.kt`:

```kotlin
package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpenseContextBuilderTest {

    private val october = YearMonth.of(2026, 10)

    private fun e(
        id: Long,
        date: String,
        title: String,
        category: Category,
        home: Double,
        original: Double = home,
        currency: String = "THB",
    ) = Expense(id, title, category, home, original, currency, LocalDate.parse(date))

    @Test
    fun line_showsOriginalOnlyForForeignCurrency() {
        assertEquals(
            "2026-10-12 | Netflix | BILLS | 402.00 (orig 12.00 USD)",
            ExpenseContextBuilder.line(e(1, "2026-10-12", "Netflix", Category.BILLS, 402.0, 12.0, "USD"), "THB"),
        )
        assertEquals(
            "2026-10-12 | Lunch | FOOD | 85.00",
            ExpenseContextBuilder.line(e(2, "2026-10-12", "Lunch", Category.FOOD, 85.0), "THB"),
        )
    }

    @Test
    fun totals_sumPerCategoryLargestFirst() {
        val text = ExpenseContextBuilder.totals(
            listOf(
                e(1, "2026-10-01", "A", Category.FOOD, 50.0),
                e(2, "2026-10-01", "B", Category.TRANSPORT, 120.0),
                e(3, "2026-10-02", "C", Category.FOOD, 30.5),
            )
        )
        assertEquals("Total: 200.50 (3 expenses)\n- TRANSPORT: 120.00\n- FOOD: 80.50\n", text)
    }

    @Test
    fun totals_emptyAndSingular() {
        assertEquals("Total: 0.00 (0 expenses)\n", ExpenseContextBuilder.totals(emptyList()))
        assertEquals(
            "Total: 5.00 (1 expense)\n- FUN: 5.00\n",
            ExpenseContextBuilder.totals(listOf(e(1, "2026-10-01", "A", Category.FUN, 5.0))),
        )
    }

    @Test
    fun monthContext_listsTopFiveLargestOnly() {
        val expenses = (1..7).map { i -> e(i.toLong(), "2026-10-0$i", "E$i", Category.FOOD, i * 10.0) }
        val text = ExpenseContextBuilder.monthContext(expenses, october, "THB")
        val topLines = text.lines().filter { it.startsWith("* ") }
        assertEquals(5, topLines.size)
        assertTrue(topLines.first().contains("E7"))
        assertTrue(text.contains("2026-10-01 | E1 | FOOD | 10.00")) // every expense is still listed
    }

    @Test
    fun monthHash_ignoresOrder_butChangesWhenDataChanges() {
        val a = e(1, "2026-10-01", "A", Category.FOOD, 50.0)
        val b = e(2, "2026-10-02", "B", Category.FUN, 70.0)
        val hash = ExpenseContextBuilder.monthHash(listOf(a, b), october, "THB")
        assertEquals(hash, ExpenseContextBuilder.monthHash(listOf(b, a), october, "THB"))
        assertNotEquals(hash, ExpenseContextBuilder.monthHash(listOf(a, b.copy(amountHome = 71.0)), october, "THB"))
        assertNotEquals(hash, ExpenseContextBuilder.monthHash(listOf(a), october, "THB"))
    }

    @Test
    fun insightContext_hasPreviousMonthTotalsButNotItsLines() {
        val text = ExpenseContextBuilder.insightContext(
            listOf(
                e(1, "2026-10-05", "Lunch", Category.FOOD, 100.0),
                e(2, "2026-09-20", "Old dinner", Category.FOOD, 300.0),
            ),
            october,
            "THB",
            LocalDate.of(2026, 10, 13),
        )
        assertTrue(text.contains("Today: 2026-10-13"))
        assertTrue(text.contains("MONTH 2026-10"))
        assertTrue(text.contains("PREVIOUS MONTH 2026-09 totals:\nTotal: 300.00 (1 expense)\n- FOOD: 300.00"))
        assertFalse(text.contains("Old dinner"))
    }

    @Test
    fun insightContext_saysNoDataWhenPreviousMonthEmpty() {
        val text = ExpenseContextBuilder.insightContext(
            listOf(e(1, "2026-10-05", "Lunch", Category.FOOD, 100.0)),
            october,
            "THB",
            LocalDate.of(2026, 10, 13),
        )
        assertTrue(text.contains("PREVIOUS MONTH 2026-09: no data"))
    }

    @Test
    fun askContext_splitsWeeksMondayToSunday_andListsMonthsNewestFirst() {
        val today = LocalDate.of(2026, 10, 14) // Wednesday
        val text = ExpenseContextBuilder.askContext(
            listOf(
                e(1, "2026-10-11", "Sunday lunch", Category.FOOD, 100.0), // last week
                e(2, "2026-10-12", "Monday lunch", Category.FOOD, 40.0), // this week
                e(3, "2026-10-14", "Grab", Category.TRANSPORT, 60.0), // this week
                e(4, "2026-10-04", "Old movie", Category.FUN, 999.0), // neither week
                e(5, "2026-09-15", "September thing", Category.OTHER, 10.0),
            ),
            today,
            "THB",
        )
        assertTrue(text.startsWith("Today: 2026-10-14 (Wednesday). Home currency: THB."))
        assertTrue(text.contains("THIS WEEK (2026-10-12 to 2026-10-18):\nTotal: 100.00 (2 expenses)"))
        assertTrue(text.contains("LAST WEEK (2026-10-05 to 2026-10-11):\nTotal: 100.00 (1 expense)"))
        assertTrue(text.indexOf("MONTH 2026-10") < text.indexOf("MONTH 2026-09"))
    }
}
```

- [ ] **Step 2: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.ExpenseContextBuilderTest"`
Expected: FAIL with `Unresolved reference 'ExpenseContextBuilder'`

- [ ] **Step 3: Implement**

`app/src/main/java/com/spendsense/app/domain/ExpenseContextBuilder.kt`:

```kotlin
package com.spendsense.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns expenses into compact text for Gemini prompts.
 * Totals are pre-calculated here because language models are bad at adding long lists of numbers.
 */
object ExpenseContextBuilder {

    /** "2026-10-12 | Netflix | BILLS | 402.00 (orig 12.00 USD)" */
    fun line(expense: Expense, homeCurrency: String): String = buildString {
        append("${expense.date} | ${expense.title} | ${expense.category.name} | ${amount(expense.amountHome)}")
        if (expense.originalCurrency != homeCurrency) {
            append(" (orig ${amount(expense.originalAmount)} ${expense.originalCurrency})")
        }
    }

    /** "Total: 200.50 (3 expenses)" then one "- CATEGORY: amount" line per category, largest first. */
    fun totals(expenses: List<Expense>): String = buildString {
        val count = expenses.size
        appendLine("Total: ${amount(expenses.sumOf { it.amountHome })} ($count expense${if (count == 1) "" else "s"})")
        expenses.groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amountHome } }
            .sortedWith(compareByDescending<Pair<Category, Double>> { it.second }.thenBy { it.first.ordinal })
            .forEach { (category, sum) -> appendLine("- ${category.name}: ${amount(sum)}") }
    }

    /** Totals, top 5 and every expense line for one month. Same data in any order gives the same text. */
    fun monthContext(monthExpenses: List<Expense>, month: YearMonth, homeCurrency: String): String {
        val sorted = monthExpenses.sortedWith(compareBy({ it.date }, { it.id }))
        return buildString {
            appendLine("MONTH $month (amounts in $homeCurrency)")
            append(totals(sorted))
            appendLine("Top expenses:")
            sorted.sortedByDescending { it.amountHome }.take(5).forEach {
                appendLine("* ${line(it, homeCurrency)}")
            }
            appendLine("All expenses (date | title | category | amount):")
            sorted.forEach { appendLine(line(it, homeCurrency)) }
        }
    }

    /** Fingerprint of a month's data; a saved insight with a different hash is "outdated". */
    fun monthHash(monthExpenses: List<Expense>, month: YearMonth, homeCurrency: String): Int =
        monthContext(monthExpenses, month, homeCurrency).hashCode()

    /** For AI Insights: the full month plus only the totals of the previous month. */
    fun insightContext(
        expenses: List<Expense>,
        month: YearMonth,
        homeCurrency: String,
        today: LocalDate,
    ): String {
        val previous = month.minusMonths(1)
        val inMonth = expenses.filter { YearMonth.from(it.date) == month }
        val inPrevious = expenses.filter { YearMonth.from(it.date) == previous }
        return buildString {
            appendLine("Today: $today")
            appendLine(monthContext(inMonth, month, homeCurrency))
            if (inPrevious.isEmpty()) {
                appendLine("PREVIOUS MONTH $previous: no data")
            } else {
                appendLine("PREVIOUS MONTH $previous totals:")
                append(totals(inPrevious))
            }
        }
    }

    /** For Ask AI: this week, last week (Mon–Sun), then each month newest first. */
    fun askContext(expenses: List<Expense>, today: LocalDate, homeCurrency: String): String {
        val weekStart = today.startOfWeek()
        val weekEnd = weekStart.plusDays(6)
        val lastWeekStart = weekStart.minusWeeks(1)
        val lastWeekEnd = weekStart.minusDays(1)
        val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return buildString {
            appendLine("Today: $today ($dayName). Home currency: $homeCurrency.")
            appendLine()
            appendLine("THIS WEEK ($weekStart to $weekEnd):")
            append(totals(expenses.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }))
            appendLine()
            appendLine("LAST WEEK ($lastWeekStart to $lastWeekEnd):")
            append(totals(expenses.filter { !it.date.isBefore(lastWeekStart) && !it.date.isAfter(lastWeekEnd) }))
            expenses.groupBy { YearMonth.from(it.date) }
                .toSortedMap(reverseOrder())
                .forEach { (month, list) ->
                    appendLine()
                    append(monthContext(list, month, homeCurrency))
                }
        }
    }

    private fun amount(value: Double): String = String.format(Locale.US, "%.2f", value)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.ExpenseContextBuilderTest"`
Expected: `BUILD SUCCESSFUL`, 8 tests passed

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: expense context builder for AI prompts" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Gemini client and AiRepository

Talks to Gemini: structured-JSON monthly insights (saved to Room with a data hash) and context-grounded chat answers. Deliverable: a tested repository wired into the container. The UI comes in Tasks 10–11.

**Files:**
- Create: `domain/Insight.kt`, `data/remote/GeminiApi.kt`, `data/repository/AiPrompts.kt`, `data/repository/AiRepository.kt`
- Modify: `AppContainer.kt` (replace whole file)
- Test: `app/src/test/java/com/spendsense/app/data/repository/GeminiAiRepositoryTest.kt`

**Interfaces:**
- Consumes: `ExpenseContextBuilder` (Task 7), `ExpenseRepository`, `InsightDao`, `InsightEntity` (Task 3), `SettingsRepository`, `safeApiCall`, `NetworkModule`, `AppError` (Task 4); test fakes (Task 6).
- Produces:
  - `@Serializable data class InsightContent(summary: String, tips: List<String> = emptyList())` + `InsightContent.parse(raw: String)`; `data class SavedInsight(content, dataHash: Int, generatedAt: Long)`; `data class ChatTurn(text: String, fromUser: Boolean)`
  - `GEMINI_BASE_URL`, `GeminiConfig.MODEL`, `interface GeminiApi`, DTOs (`GenerateContentRequest`, `ContentDto`, `PartDto`, `GenerationConfigDto`, `ThinkingConfigDto`, `SchemaDto`, `GenerateContentResponse`, `CandidateDto`, `PromptFeedbackDto`), `fun GenerateContentResponse.text(): String?`
  - `interface AiRepository { val isConfigured: Boolean; fun observeInsight(month: YearMonth): Flow<SavedInsight?>; suspend fun generateInsight(month: YearMonth): Result<Unit>; suspend fun ask(question: String, history: List<ChatTurn>): Result<String> }`, `class GeminiAiRepository(...)`
  - `AppContainer.aiRepository`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/data/repository/GeminiAiRepositoryTest.kt`:

````kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.local.InsightDao
import com.spendsense.app.data.local.InsightEntity
import com.spendsense.app.data.remote.CandidateDto
import com.spendsense.app.data.remote.ContentDto
import com.spendsense.app.data.remote.GeminiApi
import com.spendsense.app.data.remote.GenerateContentRequest
import com.spendsense.app.data.remote.GenerateContentResponse
import com.spendsense.app.data.remote.NetworkModule
import com.spendsense.app.data.remote.PartDto
import com.spendsense.app.data.remote.text
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.testing.FakeExpenseRepository
import com.spendsense.app.testing.FakeSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

private class FakeGeminiApi(var reply: GenerateContentResponse) : GeminiApi {
    val requests = mutableListOf<GenerateContentRequest>()
    override suspend fun generateContent(model: String, apiKey: String, request: GenerateContentRequest): GenerateContentResponse {
        requests += request
        return reply
    }
}

private class FakeInsightDao : InsightDao {
    val rows = MutableStateFlow<Map<String, InsightEntity>>(emptyMap())
    override fun observe(month: String): Flow<InsightEntity?> = rows.map { it[month] }
    override suspend fun upsert(insight: InsightEntity) {
        rows.value = rows.value + (insight.month to insight)
    }
}

private fun replyWith(vararg parts: PartDto) =
    GenerateContentResponse(candidates = listOf(CandidateDto(content = ContentDto(role = "model", parts = parts.toList()))))

class GeminiAiRepositoryTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val october = YearMonth.of(2026, 10)
    private val lunch = Expense(1, "Lunch", Category.FOOD, 85.0, 85.0, "THB", LocalDate.of(2026, 10, 12))
    private val expenses = FakeExpenseRepository(listOf(lunch))
    private val insightDao = FakeInsightDao()

    private fun repository(api: GeminiApi, apiKey: String = "test-key") = GeminiAiRepository(
        api = api,
        apiKey = apiKey,
        expenseRepository = expenses,
        settingsRepository = FakeSettingsRepository("THB"),
        insightDao = insightDao,
        model = "test-model",
        today = { today },
        now = { 1000L },
    )

    @Test
    fun missingKey_failsWithoutCallingApi() = runTest {
        val api = FakeGeminiApi(replyWith(PartDto(text = "hi")))
        val repo = repository(api, apiKey = " ")
        assertFalse(repo.isConfigured)
        assertEquals(AppError.InvalidApiKey, repo.ask("q", emptyList()).exceptionOrNull())
        assertEquals(AppError.InvalidApiKey, repo.generateInsight(october).exceptionOrNull())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun generateInsight_requestsJsonAndSavesResultWithDataHash() = runTest {
        val json = """{"summary":"Food is 100%.","tips":["Cook at home"]}"""
        val api = FakeGeminiApi(replyWith(PartDto(text = json)))
        val repo = repository(api)

        assertTrue(repo.generateInsight(october).isSuccess)

        val request = api.requests.single()
        assertEquals("application/json", request.generationConfig?.responseMimeType)
        assertTrue(request.contents.single().parts.single().text!!.contains("Lunch"))
        val saved = repo.observeInsight(october).first()!!
        assertEquals(InsightContent("Food is 100%.", listOf("Cook at home")), saved.content)
        assertEquals(ExpenseContextBuilder.monthHash(listOf(lunch), october, "THB"), saved.dataHash)
        assertEquals(1000L, saved.generatedAt)
    }

    @Test
    fun blockedOrEmptyReply_isEmptyAiResponseError_andNothingSaved() = runTest {
        val repo = repository(FakeGeminiApi(GenerateContentResponse(candidates = emptyList())))
        assertEquals(AppError.EmptyAiResponse, repo.generateInsight(october).exceptionOrNull())
        assertEquals(AppError.EmptyAiResponse, repo.ask("q", emptyList()).exceptionOrNull())
        assertNull(repo.observeInsight(october).first())
    }

    @Test
    fun ask_sendsExpenseContextAndHistory_startingWithUserTurn() = runTest {
        val api = FakeGeminiApi(replyWith(PartDto(text = "You spent **฿85** on food.")))
        val repo = repository(api)
        // Odd-length history: the leading model turn must be dropped.
        val history = listOf(
            ChatTurn("old answer", fromUser = false),
            ChatTurn("q1", fromUser = true),
            ChatTurn("a1", fromUser = false),
        )

        val answer = repo.ask("How much on food?", history).getOrThrow()

        assertEquals("You spent ฿85 on food.", answer) // ** stripped
        val request = api.requests.single()
        assertEquals(listOf("user", "model", "user"), request.contents.map { it.role })
        assertEquals("How much on food?", request.contents.last().parts.single().text)
        assertTrue(request.systemInstruction!!.parts.single().text!!.contains("2026-10-12 | Lunch | FOOD | 85.00"))
    }

    @Test
    fun responseText_skipsThoughtParts() {
        val response = replyWith(PartDto(text = "thinking...", thought = true), PartDto(text = "Answer"))
        assertEquals("Answer", response.text())
    }

    @Test
    fun requestJson_omitsNullFields() {
        val request = GenerateContentRequest(contents = listOf(ContentDto(role = "user", parts = listOf(PartDto(text = "hi")))))
        assertEquals(
            """{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}""",
            NetworkModule.json.encodeToString(GenerateContentRequest.serializer(), request),
        )
    }

    @Test
    fun insightContent_parsesJson_fencedJson_andFallsBackToPlainText() {
        assertEquals(InsightContent("S", listOf("T")), InsightContent.parse("""{"summary":"S","tips":["T"]}"""))
        assertEquals(InsightContent("S", listOf("T")), InsightContent.parse("```json\n{\"summary\":\"S\",\"tips\":[\"T\"]}\n```"))
        assertEquals(InsightContent("Just some text"), InsightContent.parse("Just some text"))
    }
}
````

- [ ] **Step 2: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.data.repository.GeminiAiRepositoryTest"`
Expected: FAIL with `Unresolved reference 'GeminiApi'`

- [ ] **Step 3: Insight model**

`app/src/main/java/com/spendsense/app/domain/Insight.kt`:

````kotlin
package com.spendsense.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the Insights card shows. Gemini returns it as JSON. */
@Serializable
data class InsightContent(
    val summary: String,
    val tips: List<String> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Never fails: if the text isn't valid JSON, the whole text becomes the summary. */
        fun parse(raw: String): InsightContent {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            return runCatching { json.decodeFromString<InsightContent>(cleaned) }
                .getOrElse { InsightContent(summary = cleaned) }
        }
    }
}

data class SavedInsight(
    val content: InsightContent,
    val dataHash: Int,
    val generatedAt: Long,
)

/** One message of chat history sent to Gemini. */
data class ChatTurn(val text: String, val fromUser: Boolean)
````

- [ ] **Step 4: Gemini API and DTOs**

`app/src/main/java/com/spendsense/app/data/remote/GeminiApi.kt`:

```kotlin
package com.spendsense.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

object GeminiConfig {
    /** Free-tier Flash model for new projects (Oct 2026). Change here if Google retires it. */
    const val MODEL = "gemini-3.8-flash"
}

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest,
    ): GenerateContentResponse
}

// ---- Request ----

@Serializable
data class GenerateContentRequest(
    val contents: List<ContentDto>,
    val systemInstruction: ContentDto? = null,
    val generationConfig: GenerationConfigDto? = null,
)

/** role is "user" or "model"; leave it null for systemInstruction. */
@Serializable
data class ContentDto(
    val role: String? = null,
    val parts: List<PartDto>,
)

@Serializable
data class PartDto(
    val text: String? = null,
    /** true for the model's internal "thinking" parts, which are not part of the answer. */
    val thought: Boolean? = null,
)

/** Gemini 3 should run at its default temperature (1.0), so temperature is not set here. */
@Serializable
data class GenerationConfigDto(
    val responseMimeType: String? = null,
    val responseSchema: SchemaDto? = null,
    val thinkingConfig: ThinkingConfigDto? = null,
)

/** "low" keeps answers fast; gemini-3.8-flash supports low, medium, high. */
@Serializable
data class ThinkingConfigDto(val thinkingLevel: String)

/** Subset of the OpenAPI schema Gemini uses for structured output. Types are upper-case. */
@Serializable
data class SchemaDto(
    val type: String,
    val properties: Map<String, SchemaDto>? = null,
    val items: SchemaDto? = null,
    val required: List<String>? = null,
)

// ---- Response ----

@Serializable
data class GenerateContentResponse(
    val candidates: List<CandidateDto> = emptyList(),
    val promptFeedback: PromptFeedbackDto? = null,
)

@Serializable
data class CandidateDto(
    val content: ContentDto? = null,
    val finishReason: String? = null,
)

@Serializable
data class PromptFeedbackDto(val blockReason: String? = null)

/** The answer text, without thinking parts. Null when blocked or empty. */
fun GenerateContentResponse.text(): String? =
    candidates.firstOrNull()?.content?.parts.orEmpty()
        .filter { it.thought != true }
        .mapNotNull { it.text }
        .joinToString("")
        .trim()
        .ifBlank { null }
```

- [ ] **Step 5: Prompts and repository**

`app/src/main/java/com/spendsense/app/data/repository/AiPrompts.kt`:

```kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.remote.SchemaDto

object AiPrompts {

    val INSIGHTS_SYSTEM = """
        You are SpendSense, a friendly personal-finance assistant inside an expense-tracking app.
        Analyze ONLY the expense data in the user's message. Never invent expenses.
        All amounts are in the user's home currency; write amounts with that currency.
        The current month may be incomplete: use "Today" when comparing with the previous month.
        Respond with JSON:
        - "summary": 2-3 sentences. Name the biggest category and its share of the total, and the
          change versus the previous month if previous-month data exists.
        - "tips": 2-3 short, specific tips, each with a concrete estimated saving based on the data.
        Plain text inside the strings: no markdown, no emoji.
    """.trimIndent()

    val ASK_SYSTEM = """
        You are SpendSense, a friendly assistant that answers questions about the user's own expenses.
        Use ONLY the expense data below. Totals are pre-calculated: use them instead of adding numbers yourself.
        If a week or month is not listed, the user had no expenses then.
        If the data cannot answer the question, say so briefly.
        Answer in 1-4 short sentences of plain text (no markdown, no bullet lists).
        Write amounts with the home currency.
    """.trimIndent()

    val INSIGHT_SCHEMA = SchemaDto(
        type = "OBJECT",
        properties = mapOf(
            "summary" to SchemaDto(type = "STRING"),
            "tips" to SchemaDto(type = "ARRAY", items = SchemaDto(type = "STRING")),
        ),
        required = listOf("summary", "tips"),
    )
}
```

`app/src/main/java/com/spendsense/app/data/repository/AiRepository.kt`:

```kotlin
package com.spendsense.app.data.repository

import com.spendsense.app.data.local.InsightDao
import com.spendsense.app.data.local.InsightEntity
import com.spendsense.app.data.remote.ContentDto
import com.spendsense.app.data.remote.GeminiApi
import com.spendsense.app.data.remote.GeminiConfig
import com.spendsense.app.data.remote.GenerateContentRequest
import com.spendsense.app.data.remote.GenerationConfigDto
import com.spendsense.app.data.remote.PartDto
import com.spendsense.app.data.remote.ThinkingConfigDto
import com.spendsense.app.data.remote.safeApiCall
import com.spendsense.app.data.remote.text
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

interface AiRepository {
    /** False when no Gemini API key was configured. */
    val isConfigured: Boolean
    fun observeInsight(month: YearMonth): Flow<SavedInsight?>
    /** Calls Gemini and saves the result for [month]. */
    suspend fun generateInsight(month: YearMonth): Result<Unit>
    /** [history] must be complete user/model pairs, oldest first. */
    suspend fun ask(question: String, history: List<ChatTurn>): Result<String>
}

class GeminiAiRepository(
    private val api: GeminiApi,
    private val apiKey: String,
    private val expenseRepository: ExpenseRepository,
    private val settingsRepository: SettingsRepository,
    private val insightDao: InsightDao,
    private val model: String = GeminiConfig.MODEL,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : AiRepository {

    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override fun observeInsight(month: YearMonth): Flow<SavedInsight?> =
        insightDao.observe(month.toString()).map { entity ->
            entity?.let { SavedInsight(InsightContent.parse(it.json), it.dataHash, it.generatedAt) }
        }

    override suspend fun generateInsight(month: YearMonth): Result<Unit> {
        if (!isConfigured) return Result.failure(AppError.InvalidApiKey)
        val home = settingsRepository.homeCurrency.first()
        val expenses = expenseRepository.getBetween(month.minusMonths(1).atDay(1), month.atEndOfMonth())
        val monthExpenses = expenses.filter { YearMonth.from(it.date) == month }
        val dataHash = ExpenseContextBuilder.monthHash(monthExpenses, month, home)
        val userText = "Here is my expense data:\n\n" +
            ExpenseContextBuilder.insightContext(expenses, month, home, today()) +
            "\nWrite my insights for $month."
        val request = GenerateContentRequest(
            contents = listOf(ContentDto(role = "user", parts = listOf(PartDto(text = userText)))),
            systemInstruction = ContentDto(parts = listOf(PartDto(text = AiPrompts.INSIGHTS_SYSTEM))),
            generationConfig = GenerationConfigDto(
                responseMimeType = "application/json",
                responseSchema = AiPrompts.INSIGHT_SCHEMA,
                thinkingConfig = ThinkingConfigDto(thinkingLevel = "low"),
            ),
        )
        return safeApiCall {
            api.generateContent(model, apiKey, request).text() ?: throw AppError.EmptyAiResponse
        }.map { json ->
            insightDao.upsert(InsightEntity(month.toString(), json, dataHash, now()))
        }
    }

    override suspend fun ask(question: String, history: List<ChatTurn>): Result<String> {
        if (!isConfigured) return Result.failure(AppError.InvalidApiKey)
        val home = settingsRepository.homeCurrency.first()
        val today = today()
        val currentMonth = YearMonth.from(today)
        val expenses = expenseRepository.getBetween(
            currentMonth.minusMonths(2).atDay(1),
            currentMonth.atEndOfMonth(),
        )
        val system = AiPrompts.ASK_SYSTEM + "\n\nEXPENSE DATA:\n" +
            ExpenseContextBuilder.askContext(expenses, today, home)
        val historyContents = history.takeLast(MAX_HISTORY)
            .dropWhile { !it.fromUser } // Gemini expects the conversation to start with a user turn
            .map { turn ->
                ContentDto(role = if (turn.fromUser) "user" else "model", parts = listOf(PartDto(text = turn.text)))
            }
        val request = GenerateContentRequest(
            contents = historyContents + ContentDto(role = "user", parts = listOf(PartDto(text = question))),
            systemInstruction = ContentDto(parts = listOf(PartDto(text = system))),
            generationConfig = GenerationConfigDto(thinkingConfig = ThinkingConfigDto(thinkingLevel = "low")),
        )
        return safeApiCall {
            api.generateContent(model, apiKey, request).text()?.replace("**", "")
                ?: throw AppError.EmptyAiResponse
        }
    }

    companion object {
        const val MAX_HISTORY = 10
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.data.repository.GeminiAiRepositoryTest"`
Expected: `BUILD SUCCESSFUL`, 7 tests passed

- [ ] **Step 7: Wire into the container** (replace the whole file)

`app/src/main/java/com/spendsense/app/AppContainer.kt`:

```kotlin
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
```

- [ ] **Step 8: USER ACTION: add the Gemini API key**

Agents: stop here and ask the user to do this. Don't handle the key yourself.

1. Open https://aistudio.google.com/apikey, sign in, and click **Create API key** (free tier).
2. Add one line to `local.properties` in the project root (this file is git-ignored):
   ```properties
   GEMINI_API_KEY=paste-your-key-here
   ```
3. Optional smoke test in your own terminal (replace `YOUR_KEY`):
   ```bash
   curl -s "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent" -H "x-goog-api-key: YOUR_KEY" -H "Content-Type: application/json" -d '{"contents":[{"role":"user","parts":[{"text":"Say hi in 3 words"}]}],"generationConfig":{"thinkingConfig":{"thinkingLevel":"low"}}}'
   ```
   Expected: JSON with `"candidates"` and a short greeting. If you get 404 for the model, check https://ai.google.dev/gemini-api/docs/models for the current free Flash model and change `GeminiConfig.MODEL`.

Without a key the app still works. The AI screens show "Gemini API key missing or invalid. See README."

- [ ] **Step 9: Full unit-test run and build**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "feat: Gemini client and AI repository for insights and chat" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Settings screen and demo data

Home-currency picker (with re-conversion), Load demo data, Clear all and About. Deliverable: a complete non-AI app that's ready to demo with sample data.

**Files:**
- Create: `domain/DemoData.kt`, `ui/settings/SettingsViewModel.kt`
- Modify: `ui/settings/SettingsScreen.kt` (replace placeholder entirely)
- Test: `app/src/test/java/com/spendsense/app/domain/DemoDataTest.kt`, `app/src/test/java/com/spendsense/app/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `ExpenseRepository.reconvertAll/replaceAll/clearAll`, `CurrencyRepository`, `SettingsRepository`, `AppContainer.aiRepository.isConfigured`, `GeminiConfig.MODEL`, `round2`, test fakes.
- Produces: `DemoData.generate(today: LocalDate, homeCurrency: String, thbToHome: Double, usdToHome: Double, seed: Int = 42): List<Expense>`; `SettingsViewModel(expenseRepository, currencyRepository, settingsRepository, aiConfigured: Boolean, today: () -> LocalDate)` with `changeHomeCurrency(code)`, `loadDemoData()`, `clearAll()`, `messageShown()`, `Factory`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/spendsense/app/domain/DemoDataTest.kt`:

```kotlin
package com.spendsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DemoDataTest {

    private val today = LocalDate.of(2026, 10, 13)

    @Test
    fun sameInputs_giveSameData() {
        assertEquals(
            DemoData.generate(today, "THB", 1.0, 33.6),
            DemoData.generate(today, "THB", 1.0, 33.6),
        )
    }

    @Test
    fun coversPreviousMonthStartUntilToday_withAtLeastOneExpensePerDay() {
        val data = DemoData.generate(today, "THB", 1.0, 33.6)
        assertEquals(LocalDate.of(2026, 9, 1), data.minOf { it.date })
        assertEquals(today, data.maxOf { it.date })
        assertEquals(43, data.map { it.date }.distinct().size) // 30 days of Sept + 13 of Oct
        assertTrue(data.all { it.amountHome > 0 })
    }

    @Test
    fun usdSubscriptions_keepUsdOriginal_whenHomeIsThb() {
        val netflix = DemoData.generate(today, "THB", 1.0, 33.6).first { it.title == "Netflix" }
        assertEquals("USD", netflix.originalCurrency)
        assertEquals(12.0, netflix.originalAmount, 0.0)
        assertEquals(403.2, netflix.amountHome, 0.0)
    }

    @Test
    fun thbAmounts_areConvertedAndStoredInHomeCurrency() {
        val data = DemoData.generate(today, "USD", thbToHome = 0.03, usdToHome = 1.0)
        val phone = data.first { it.title == "Phone bill" }
        assertEquals("USD", phone.originalCurrency)
        assertEquals(17.97, phone.amountHome, 0.0) // 599 THB * 0.03
        assertTrue(data.all { it.originalCurrency == "USD" })
    }
}
```

`app/src/test/java/com/spendsense/app/ui/settings/SettingsViewModelTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify they fail**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.DemoDataTest" --tests "com.spendsense.app.ui.settings.SettingsViewModelTest"`
Expected: FAIL with `Unresolved reference 'DemoData'` / `'SettingsViewModel'`

- [ ] **Step 3: Demo data**

`app/src/main/java/com/spendsense/app/domain/DemoData.kt`:

```kotlin
package com.spendsense.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Realistic sample expenses for the live demo: the whole previous month plus this month up to
 * today. Amounts are defined in THB (plus two USD subscriptions) and converted to the home currency.
 * The same inputs always give the same list (fixed random seed).
 */
object DemoData {

    private data class Template(val title: String, val category: Category, val minThb: Int, val maxThb: Int)

    private data class Bill(val dayOfMonth: Int, val title: String, val amount: Double, val currency: String)

    private val food = listOf(
        Template("Pad Thai lunch", Category.FOOD, 60, 90),
        Template("Iced latte", Category.FOOD, 55, 75),
        Template("Som tam & sticky rice", Category.FOOD, 50, 80),
        Template("7-Eleven snacks", Category.FOOD, 30, 90),
        Template("GrabFood delivery", Category.FOOD, 120, 260),
        Template("Bubble tea", Category.FOOD, 45, 70),
        Template("Khao man gai", Category.FOOD, 50, 70),
        Template("Shabu dinner", Category.FOOD, 250, 400),
    )
    private val transport = listOf(
        Template("BTS Skytrain", Category.TRANSPORT, 30, 60),
        Template("Grab to campus", Category.TRANSPORT, 90, 160),
        Template("Motorbike taxi", Category.TRANSPORT, 20, 40),
        Template("MRT", Category.TRANSPORT, 25, 45),
    )
    private val shopping = listOf(
        Template("Uniqlo shirt", Category.SHOPPING, 390, 790),
        Template("Shopee order", Category.SHOPPING, 150, 600),
        Template("Stationery", Category.SHOPPING, 40, 150),
    )
    private val entertainment = listOf(
        Template("Movie ticket", Category.FUN, 180, 260),
        Template("Karaoke night", Category.FUN, 200, 350),
        Template("Steam game", Category.FUN, 200, 600),
    )
    private val health = listOf(
        Template("Pharmacy", Category.HEALTH, 80, 250),
        Template("Gym day pass", Category.HEALTH, 150, 200),
    )
    private val bills = listOf(
        Bill(1, "Phone bill", 599.0, "THB"),
        Bill(5, "Electricity", 1250.0, "THB"),
        Bill(8, "Netflix", 12.0, "USD"),
        Bill(20, "Spotify", 6.0, "USD"),
    )

    fun generate(
        today: LocalDate,
        homeCurrency: String,
        thbToHome: Double,
        usdToHome: Double,
        seed: Int = 42,
    ): List<Expense> {
        val random = Random(seed)
        val result = mutableListOf<Expense>()
        var date = YearMonth.from(today).minusMonths(1).atDay(1)
        while (!date.isAfter(today)) {
            val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val picks = buildList {
                add(food.random(random))
                if (random.nextDouble() < 0.4) add(food.random(random))
                if (!weekend && random.nextDouble() < 0.6) add(transport.random(random))
                if (random.nextDouble() < 0.08) add(shopping.random(random))
                if (random.nextDouble() < if (weekend) 0.3 else 0.05) add(entertainment.random(random))
                if (random.nextDouble() < 0.04) add(health.random(random))
            }
            picks.forEach { template ->
                val thb = random.nextInt(template.minThb, template.maxThb + 1).toDouble()
                result += homeExpense(template.title, template.category, round2(thb * thbToHome), homeCurrency, date)
            }
            bills.filter { it.dayOfMonth == date.dayOfMonth }.forEach { bill ->
                result += if (bill.currency == "USD") {
                    Expense(
                        title = bill.title,
                        category = Category.BILLS,
                        amountHome = round2(bill.amount * usdToHome),
                        originalAmount = bill.amount,
                        originalCurrency = "USD",
                        date = date,
                    )
                } else {
                    homeExpense(bill.title, Category.BILLS, round2(bill.amount * thbToHome), homeCurrency, date)
                }
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun homeExpense(title: String, category: Category, amount: Double, home: String, date: LocalDate) =
        Expense(
            title = title,
            category = category,
            amountHome = amount,
            originalAmount = amount,
            originalCurrency = home,
            date = date,
        )
}
```

- [ ] **Step 4: ViewModel**

`app/src/main/java/com/spendsense/app/ui/settings/SettingsViewModel.kt`:

```kotlin
package com.spendsense.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.CurrencyRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.DemoData
import com.spendsense.app.domain.SupportedCurrencies
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val homeCurrency: String = "",
    val currencies: Map<String, String> = SupportedCurrencies.fallback.toSortedMap(),
    val isWorking: Boolean = false,
    /** One-off snackbar text; cleared by messageShown(). */
    val message: String? = null,
    val aiConfigured: Boolean = false,
)

class SettingsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
    aiConfigured: Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(aiConfigured = aiConfigured))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.homeCurrency.collect { code -> _uiState.update { it.copy(homeCurrency = code) } }
        }
        viewModelScope.launch {
            val currencies = currencyRepository.getCurrencies()
            _uiState.update { it.copy(currencies = currencies) }
        }
    }

    /** Fetch old->new rate, convert every expense, then save the new currency. Nothing changes on failure. */
    fun changeHomeCurrency(newCode: String) = runWork {
        val old = settingsRepository.homeCurrency.first()
        if (newCode == old) return@runWork null
        currencyRepository.getRate(old, newCode).fold(
            onSuccess = { rate ->
                expenseRepository.reconvertAll(newCode, rate)
                settingsRepository.setHomeCurrency(newCode)
                "All expenses converted to $newCode"
            },
            onFailure = { "Couldn't get the exchange rate. Check your connection and try again." },
        )
    }

    fun loadDemoData() = runWork {
        val home = settingsRepository.homeCurrency.first()
        val thbToHome = currencyRepository.getRate("THB", home).getOrNull()
        val usdToHome = currencyRepository.getRate("USD", home).getOrNull()
        if (thbToHome == null || usdToHome == null) {
            "Couldn't load demo data: no internet connection"
        } else {
            expenseRepository.replaceAll(DemoData.generate(today(), home, thbToHome, usdToHome))
            "Demo data loaded"
        }
    }

    fun clearAll() = runWork {
        expenseRepository.clearAll()
        "All expenses deleted"
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    /** Runs one action at a time; [block] returns the snackbar message (or null for none). */
    private fun runWork(block: suspend () -> String?) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val message = block()
            _uiState.update { it.copy(isWorking = false, message = message) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                SettingsViewModel(
                    expenseRepository = container.expenseRepository,
                    currencyRepository = container.currencyRepository,
                    settingsRepository = container.settingsRepository,
                    aiConfigured = container.aiRepository.isConfigured,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.domain.DemoDataTest" --tests "com.spendsense.app.ui.settings.SettingsViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 8 tests passed

- [ ] **Step 6: Screen** (replace the placeholder)

`app/src/main/java/com/spendsense/app/ui/settings/SettingsScreen.kt`:

```kotlin
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
```

- [ ] **Step 7: Check on the emulator**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Check:
1. Settings → Load demo data → Load → snackbar "Demo data loaded". Expenses shows this month's list; ‹ shows last month full of data, including Netflix/Spotify with "$… USD".
2. Settings → Home currency → USD → Convert → snackbar "All expenses converted to USD", and Expenses totals are now in $. Switch back to THB → amounts are back (USD subscriptions exactly $12/$6 when home is USD).
3. About shows `gemini-3.8-flash` and "Gemini API key: Set" (or "Missing (see README)").

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: settings with home currency conversion and demo data" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Insights screen (AI analysis)

Category breakdown bars plus the AI card (generate, loading, saved, outdated, error). Deliverable: AI insights working on the emulator with demo data.

**Files:**
- Create: `ui/insights/InsightsViewModel.kt`
- Modify: `ui/insights/InsightsScreen.kt` (replace placeholder entirely)
- Test: `app/src/test/java/com/spendsense/app/ui/insights/InsightsLogicTest.kt`

**Interfaces:**
- Consumes: `ExpenseRepository.observeMonth`, `AiRepository.observeInsight/generateInsight/isConfigured`, `ExpenseContextBuilder.monthHash`, `MonthTopBar`, `formatMoney`, `userMessage()`.
- Produces: `fun categoryTotals(expenses): List<CategoryTotal>`, `fun aiCardState(hasExpenses, isConfigured, status, saved, currentHash): AiCardState`, `InsightsViewModel` with `previousMonth()`, `nextMonth()`, `generate()`, `Factory`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/ui/insights/InsightsLogicTest.kt`:

```kotlin
package com.spendsense.app.ui.insights

import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsLogicTest {

    private val content = InsightContent("Summary", listOf("Tip"))
    private val saved = SavedInsight(content, dataHash = 42, generatedAt = 0L)

    @Test
    fun categoryTotals_largestFirstWithFractions() {
        val date = LocalDate.of(2026, 10, 1)
        val totals = categoryTotals(
            listOf(
                Expense(1, "A", Category.FOOD, 30.0, 30.0, "THB", date),
                Expense(2, "B", Category.FUN, 60.0, 60.0, "THB", date),
                Expense(3, "C", Category.FOOD, 10.0, 10.0, "THB", date),
            )
        )
        assertEquals(listOf(Category.FUN, Category.FOOD), totals.map { it.category })
        assertEquals(0.6f, totals[0].fraction, 0.0001f)
        assertEquals(40.0, totals[1].amount, 0.0)
        assertTrue(categoryTotals(emptyList()).isEmpty())
    }

    @Test
    fun aiCard_noExpenses_winsOverEverything() {
        assertEquals(AiCardState.NoData, aiCardState(false, true, GenerationStatus.Idle, saved, 42))
    }

    @Test
    fun aiCard_missingKey() {
        assertEquals(AiCardState.NotConfigured, aiCardState(true, false, GenerationStatus.Idle, null, 42))
    }

    @Test
    fun aiCard_runningShowsLoading() {
        assertEquals(AiCardState.Loading, aiCardState(true, true, GenerationStatus.Running, saved, 42))
    }

    @Test
    fun aiCard_savedInsight_isOutdatedOnlyWhenHashDiffers() {
        assertEquals(AiCardState.Ready(content, outdated = false), aiCardState(true, true, GenerationStatus.Idle, saved, 42))
        assertEquals(AiCardState.Ready(content, outdated = true), aiCardState(true, true, GenerationStatus.Idle, saved, 7))
    }

    @Test
    fun aiCard_failedRegenerate_keepsOldInsightWithError() {
        assertEquals(
            AiCardState.Ready(content, outdated = false, errorMessage = "No internet connection"),
            aiCardState(true, true, GenerationStatus.Failed("No internet connection"), saved, 42),
        )
    }

    @Test
    fun aiCard_failedFirstTime_showsError_elseNotGenerated() {
        assertEquals(AiCardState.Error("boom"), aiCardState(true, true, GenerationStatus.Failed("boom"), null, 42))
        assertEquals(AiCardState.NotGenerated, aiCardState(true, true, GenerationStatus.Idle, null, 42))
    }
}
```

- [ ] **Step 2: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.insights.InsightsLogicTest"`
Expected: FAIL with `Unresolved reference 'categoryTotals'`

- [ ] **Step 3: ViewModel**

`app/src/main/java/com/spendsense/app/ui/insights/InsightsViewModel.kt`:

```kotlin
package com.spendsense.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.data.repository.ExpenseRepository
import com.spendsense.app.data.repository.SettingsRepository
import com.spendsense.app.domain.Category
import com.spendsense.app.domain.Expense
import com.spendsense.app.domain.ExpenseContextBuilder
import com.spendsense.app.domain.InsightContent
import com.spendsense.app.domain.SavedInsight
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.domain.userMessage
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CategoryTotal(val category: Category, val amount: Double, val fraction: Float)

sealed interface AiCardState {
    data object NoData : AiCardState
    data object NotConfigured : AiCardState
    data object NotGenerated : AiCardState
    data object Loading : AiCardState
    /** [errorMessage] is set when a regenerate failed but an older insight is still shown. */
    data class Ready(val content: InsightContent, val outdated: Boolean, val errorMessage: String? = null) : AiCardState
    data class Error(val message: String) : AiCardState
}

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data object Running : GenerationStatus
    data class Failed(val message: String) : GenerationStatus
}

data class InsightsUiState(
    val month: YearMonth,
    val totalText: String = "",
    val homeCurrency: String = "",
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val ai: AiCardState = AiCardState.NoData,
    val canGoNext: Boolean = false,
)

/** Largest category first; fraction is the share of the month total (0..1). */
fun categoryTotals(expenses: List<Expense>): List<CategoryTotal> {
    val total = expenses.sumOf { it.amountHome }
    if (total <= 0.0) return emptyList()
    return expenses.groupBy { it.category }
        .map { (category, list) ->
            val sum = list.sumOf { it.amountHome }
            CategoryTotal(category, sum, (sum / total).toFloat())
        }
        .sortedByDescending { it.amount }
}

fun aiCardState(
    hasExpenses: Boolean,
    isConfigured: Boolean,
    status: GenerationStatus,
    saved: SavedInsight?,
    currentHash: Int,
): AiCardState = when {
    !hasExpenses -> AiCardState.NoData
    !isConfigured -> AiCardState.NotConfigured
    status is GenerationStatus.Running -> AiCardState.Loading
    saved != null -> AiCardState.Ready(
        content = saved.content,
        outdated = saved.dataHash != currentHash,
        errorMessage = (status as? GenerationStatus.Failed)?.message,
    )
    status is GenerationStatus.Failed -> AiCardState.Error(status.message)
    else -> AiCardState.NotGenerated
}

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(
    private val expenseRepository: ExpenseRepository,
    private val aiRepository: AiRepository,
    settingsRepository: SettingsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))
    private val status = MutableStateFlow<GenerationStatus>(GenerationStatus.Idle)

    val uiState: StateFlow<InsightsUiState> = month
        .flatMapLatest { m ->
            combine(
                expenseRepository.observeMonth(m),
                aiRepository.observeInsight(m),
                settingsRepository.homeCurrency,
                status,
            ) { expenses, saved, home, currentStatus ->
                InsightsUiState(
                    month = m,
                    totalText = formatMoney(expenses.sumOf { it.amountHome }, home),
                    homeCurrency = home,
                    categoryTotals = categoryTotals(expenses),
                    ai = aiCardState(
                        hasExpenses = expenses.isNotEmpty(),
                        isConfigured = aiRepository.isConfigured,
                        status = currentStatus,
                        saved = saved,
                        currentHash = ExpenseContextBuilder.monthHash(expenses, m, home),
                    ),
                    canGoNext = m < YearMonth.from(today()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState(month = month.value))

    fun previousMonth() {
        month.update { it.minusMonths(1) }
        status.value = GenerationStatus.Idle
    }

    fun nextMonth() {
        month.update { if (it < YearMonth.from(today())) it.plusMonths(1) else it }
        status.value = GenerationStatus.Idle
    }

    fun generate() {
        if (status.value is GenerationStatus.Running) return
        val requested = month.value
        status.value = GenerationStatus.Running
        viewModelScope.launch {
            val result = aiRepository.generateInsight(requested)
            if (month.value != requested) return@launch // user switched month meanwhile
            status.value = result.fold(
                onSuccess = { GenerationStatus.Idle },
                onFailure = { GenerationStatus.Failed(it.userMessage()) },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = spendSenseApp().container
                InsightsViewModel(container.expenseRepository, container.aiRepository, container.settingsRepository)
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.insights.InsightsLogicTest"`
Expected: `BUILD SUCCESSFUL`, 7 tests passed

- [ ] **Step 5: Screen** (replace the placeholder)

`app/src/main/java/com/spendsense/app/ui/insights/InsightsScreen.kt`:

```kotlin
package com.spendsense.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendsense.app.domain.formatMoney
import com.spendsense.app.ui.common.MonthTopBar
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            MonthTopBar(
                month = state.month,
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BreakdownCard(state)
            AiCard(state.ai, onGenerate = viewModel::generate)
        }
    }
}

@Composable
private fun BreakdownCard(state: InsightsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Total spent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(state.totalText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            state.categoryTotals.forEach { total ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${total.category.emoji} ${total.category.label}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${formatMoney(total.amount, state.homeCurrency)} · ${(total.fraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { total.fraction },
                        color = Color(total.category.color),
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiCard(ai: AiCardState, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✨ AI insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when (ai) {
                AiCardState.NoData -> Text("No expenses to analyze for this month.")
                AiCardState.NotConfigured -> Text("Gemini API key missing or invalid. See README.")
                AiCardState.NotGenerated -> Button(onClick = onGenerate) { Text("✨ Generate AI insights") }
                AiCardState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Analyzing your spending…")
                }
                is AiCardState.Error -> {
                    Text(ai.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onGenerate) { Text("Retry") }
                }
                is AiCardState.Ready -> {
                    if (ai.outdated) {
                        Text(
                            "Outdated: your expenses changed since this was generated.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(ai.content.summary, style = MaterialTheme.typography.bodyMedium)
                    if (ai.content.tips.isNotEmpty()) {
                        Text("Tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ai.content.tips.forEach { tip -> Text("• $tip", style = MaterialTheme.typography.bodyMedium) }
                    }
                    ai.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (ai.outdated) {
                        Button(onClick = onGenerate) { Text("Regenerate") }
                    } else {
                        FilledTonalButton(onClick = onGenerate) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text(" Regenerate")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Check on the emulator (needs the key from Task 8)**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Check (with demo data loaded):
1. Insights shows the total and colored bars per category, largest first, with percentages.
2. "✨ Generate AI insights" → "Analyzing your spending…" → a summary naming the top category and the change vs last month, plus 2–3 tips. Plain text, no `**`.
3. Leave and come back → the insight is still there instantly (saved in Room).
4. Add an expense this month → Insights shows "Outdated…" and a prominent Regenerate.
5. Airplane mode → Regenerate → the old insight stays with "No internet connection" under it.

If step 2 shows "Gemini API key missing or invalid" although the key is set: read the response body in Logcat (tag `okhttp.OkHttpClient`). A 400 that mentions an unknown field means the API changed: compare the request with https://ai.google.dev/api/generate-content.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: insights screen with AI analysis and saving tips" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Ask AI chat screen

Chat with Gemini about saved expenses: suggestion chips, a typing bubble, error bubbles with retry, and a privacy note. Deliverable: all 5 screens done.

**Files:**
- Create: `ui/askai/AskAiViewModel.kt`
- Modify: `ui/askai/AskAiScreen.kt` (replace placeholder entirely)
- Test: `app/src/test/java/com/spendsense/app/ui/askai/AskAiViewModelTest.kt`

**Interfaces:**
- Consumes: `AiRepository.ask/isConfigured`, `ChatTurn`, `userMessage()`, `MainDispatcherRule` (Task 6).
- Produces: `sealed interface ChatMessage { User; Ai; Error }`, `fun buildHistory(messages): List<ChatTurn>`, `SUGGESTED_QUESTIONS`, `AskAiViewModel(aiRepository)` with `onInputChange`, `send(text = input)`, `retryLast()`, `Factory`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/spendsense/app/ui/askai/AskAiViewModelTest.kt`:

```kotlin
package com.spendsense.app.ui.askai

import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.domain.AppError
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.SavedInsight
import com.spendsense.app.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.YearMonth

private class FakeAiRepository(override val isConfigured: Boolean = true) : AiRepository {
    val replies = ArrayDeque<Result<String>>()
    val calls = mutableListOf<Pair<String, List<ChatTurn>>>()

    override fun observeInsight(month: YearMonth): Flow<SavedInsight?> = flowOf(null)
    override suspend fun generateInsight(month: YearMonth): Result<Unit> = Result.success(Unit)
    override suspend fun ask(question: String, history: List<ChatTurn>): Result<String> {
        calls += question to history
        return replies.removeFirst()
    }
}

class AskAiViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun buildHistory_keepsOnlyAnsweredPairs() {
        val messages = listOf(
            ChatMessage.User(1, "q1"), ChatMessage.Ai(2, "a1"),
            ChatMessage.User(3, "q2"), ChatMessage.Error(4, "No internet connection", "q2"),
            ChatMessage.User(5, "q3"), ChatMessage.Ai(6, "a3"),
        )
        assertEquals(
            listOf(ChatTurn("q1", true), ChatTurn("a1", false), ChatTurn("q3", true), ChatTurn("a3", false)),
            buildHistory(messages),
        )
    }

    @Test
    fun send_addsQuestionAndAnswer_andClearsInput() {
        val ai = FakeAiRepository().apply { replies += Result.success("You spent ฿742.") }
        val vm = AskAiViewModel(ai)
        vm.onInputChange("Food this week?")
        vm.send()
        val state = vm.uiState.value
        assertEquals("", state.input)
        assertFalse(state.isSending)
        assertEquals(listOf("Food this week?", "You spent ฿742."), state.messages.map {
            when (it) {
                is ChatMessage.User -> it.text
                is ChatMessage.Ai -> it.text
                is ChatMessage.Error -> it.message
            }
        })
    }

    @Test
    fun failure_showsErrorBubble_andRetryResendsWithoutDuplicateQuestion() {
        val ai = FakeAiRepository().apply {
            replies += Result.success("a1")
            replies += Result.failure(AppError.RateLimited)
            replies += Result.success("a2")
        }
        val vm = AskAiViewModel(ai)
        vm.send("q1")
        vm.send("q2")
        val error = vm.uiState.value.messages.last() as ChatMessage.Error
        assertEquals(AppError.RateLimited.userMessage, error.message)

        vm.retryLast()

        val messages = vm.uiState.value.messages
        assertEquals(4, messages.size) // q1, a1, q2, a2
        assertTrue(messages.last() is ChatMessage.Ai)
        assertEquals(listOf(ChatTurn("q1", true), ChatTurn("a1", false)), ai.calls.last().second)
        assertEquals("q2", ai.calls.last().first)
    }

    @Test
    fun blankInputOrMissingKey_sendsNothing() {
        val ai = FakeAiRepository(isConfigured = false)
        val vm = AskAiViewModel(ai)
        vm.send("   ")
        vm.send("real question")
        assertTrue(ai.calls.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }
}
```

- [ ] **Step 2: Verify it fails**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.askai.AskAiViewModelTest"`
Expected: FAIL with `Unresolved reference 'AskAiViewModel'`

- [ ] **Step 3: ViewModel**

`app/src/main/java/com/spendsense/app/ui/askai/AskAiViewModel.kt`:

```kotlin
package com.spendsense.app.ui.askai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spendsense.app.data.repository.AiRepository
import com.spendsense.app.domain.ChatTurn
import com.spendsense.app.domain.userMessage
import com.spendsense.app.ui.common.spendSenseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage
    data class Ai(override val id: Long, val text: String) : ChatMessage
    /** [question] is kept so the user can retry it. */
    data class Error(override val id: Long, val message: String, val question: String) : ChatMessage
}

data class AskAiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isConfigured: Boolean = true,
)

val SUGGESTED_QUESTIONS = listOf(
    "How much did I spend on food this week?",
    "What was my biggest expense this month?",
    "Where can I save money?",
)

/** Only answered questions go to Gemini: each User message directly followed by an Ai message. */
fun buildHistory(messages: List<ChatMessage>): List<ChatTurn> =
    messages.zipWithNext().flatMap { (first, second) ->
        if (first is ChatMessage.User && second is ChatMessage.Ai) {
            listOf(ChatTurn(first.text, fromUser = true), ChatTurn(second.text, fromUser = false))
        } else {
            emptyList()
        }
    }

/** Chat history lives only here, so it resets when the app restarts (by design). */
class AskAiViewModel(private val aiRepository: AiRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AskAiUiState(isConfigured = aiRepository.isConfigured))
    val uiState: StateFlow<AskAiUiState> = _uiState.asStateFlow()
    private var nextId = 0L

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text) }

    fun send(text: String = _uiState.value.input) {
        val question = text.trim()
        val state = _uiState.value
        if (question.isEmpty() || state.isSending || !state.isConfigured) return
        val history = buildHistory(state.messages)
        _uiState.update { it.copy(messages = it.messages + ChatMessage.User(nextId++, question), input = "") }
        request(question, history)
    }

    /** Retries the last question if the last message is an error. */
    fun retryLast() {
        val state = _uiState.value
        val error = state.messages.lastOrNull() as? ChatMessage.Error ?: return
        if (state.isSending) return
        val withoutError = state.messages.dropLast(1)
        _uiState.update { it.copy(messages = withoutError) }
        // History = everything before the failed question's own bubble.
        request(error.question, buildHistory(withoutError.dropLast(1)))
    }

    private fun request(question: String, history: List<ChatTurn>) {
        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            val reply = aiRepository.ask(question, history).fold(
                onSuccess = { answer -> ChatMessage.Ai(nextId++, answer) },
                onFailure = { e -> ChatMessage.Error(nextId++, e.userMessage(), question) },
            )
            _uiState.update { it.copy(messages = it.messages + reply, isSending = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AskAiViewModel(spendSenseApp().container.aiRepository) }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.spendsense.app.ui.askai.AskAiViewModelTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed

- [ ] **Step 5: Screen** (replace the placeholder)

`app/src/main/java/com/spendsense/app/ui/askai/AskAiScreen.kt`:

```kotlin
package com.spendsense.app.ui.askai

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private const val GREETING = "Hi! Ask me anything about your expenses. 💬"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAiScreen(viewModel: AskAiViewModel = viewModel(factory = AskAiViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // +1 for the greeting bubble, +1 for the "typing…" bubble.
    val itemCount = state.messages.size + 1 + if (state.isSending) 1 else 0
    LaunchedEffect(itemCount) { listState.animateScrollToItem(itemCount - 1) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ask your spending") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "greeting") { Bubble(GREETING, fromUser = false) }
                items(state.messages, key = { it.id }) { message ->
                    when (message) {
                        is ChatMessage.User -> Bubble(message.text, fromUser = true)
                        is ChatMessage.Ai -> Bubble(message.text, fromUser = false)
                        is ChatMessage.Error -> ErrorBubble(
                            message = message.message,
                            canRetry = message == state.messages.lastOrNull() && !state.isSending,
                            onRetry = viewModel::retryLast,
                        )
                    }
                }
                if (state.isSending) item(key = "typing") { Bubble("Typing…", fromUser = false) }
            }

            if (!state.isConfigured) {
                Text(
                    "Gemini API key missing or invalid. See README.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SUGGESTED_QUESTIONS.forEach { question ->
                    AssistChip(
                        onClick = { viewModel.send(question) },
                        label = { Text(question) },
                        enabled = state.isConfigured && !state.isSending,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    placeholder = { Text("Ask about your spending…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    enabled = state.isConfigured,
                    maxLines = 3,
                )
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.isConfigured && !state.isSending && state.input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
            Text(
                "Your expense data is sent to Google Gemini to answer.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text = text,
            color = if (fromUser) colors.onPrimary else colors.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    color = if (fromUser) colors.primary else colors.surfaceContainerHigh,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (fromUser) 18.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 18.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ErrorBubble(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        if (canRetry) {
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("Retry", color = Color.Unspecified)
            }
        }
    }
}
```

- [ ] **Step 6: Check on the emulator (needs the key)**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:installDebug && /c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe shell am start -n com.spendsense.app/.MainActivity`
Check (demo data loaded):
1. Tap each suggestion chip → "Typing…" → a short plain-text answer with amounts in the home currency. Cross-check one number against the Insights screen.
2. Ask "compare to last week?" right after the food question → the answer uses the previous turn (history works).
3. The keyboard doesn't cover the input field; the list auto-scrolls to the newest message.
4. Airplane mode → ask → red bubble "No internet connection" with Retry → turn the network back on → Retry → the answer replaces the error.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: Ask AI chat about your spending" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: README, screenshots and final verification

Deliverable: a README for graders, screenshots for the "Screen Design" slide, the full test suite green, and the demo checklist run on the emulator.

**Files:**
- Create: `README.md`, `docs/screenshots/{expenses,add-expense,insights,ask-ai,settings}.png`

- [ ] **Step 1: Write the README**

`README.md`:

```markdown
# SpendSense

An Android expense tracker that makes sense of your spending with AI.

Log expenses in any major currency. SpendSense converts them to your home currency at live
exchange rates, shows where your money goes by category, and uses Google Gemini to write a
monthly analysis with saving tips and to answer questions about your spending in a chat.

## Features

- **Expenses**: month-by-month list grouped by day, swipe to delete with Undo
- **Add / Edit**: any of ~30 currencies, converted live with the Frankfurter API
- **AI Insights**: category breakdown + Gemini summary and saving tips, saved per month
- **Ask AI**: chat about your own expenses ("How much did I spend on food this week?")
- **Settings**: home currency (re-converts all expenses), demo data, clear all

## Course requirements

| Requirement | How SpendSense meets it |
|---|---|
| Multiple screens | Expenses, Add/Edit Expense, Insights, Ask AI, Settings |
| Data persistence | Room (expenses, AI insights) + DataStore (home currency) |
| External API (coroutines) | Frankfurter exchange-rate API via Retrofit `suspend` functions |
| AI-integrated functions | Gemini monthly insights + "Ask your spending" chat |

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · MVVM + StateFlow · Coroutines ·
Room · DataStore · Retrofit + kotlinx.serialization · Gemini API (`gemini-3.8-flash`) · Frankfurter API

Architecture: Compose screens → ViewModels → repositories → Room / DataStore / Retrofit,
wired by hand in `AppContainer` (no DI framework).

## Setup

1. Open the project in Android Studio (2026.1 or newer) and let Gradle sync.
2. Get a free Gemini API key at https://aistudio.google.com/apikey.
3. Add it to `local.properties` (never commit this file):
   `GEMINI_API_KEY=your-key-here`
4. Run the app. In **Settings → Load demo data** to get two months of sample expenses.

Without a key the app still works; the AI screens show a "key missing" message.

## Screenshots

| Expenses | Add expense | Insights | Ask AI | Settings |
|---|---|---|---|---|
| ![](docs/screenshots/expenses.png) | ![](docs/screenshots/add-expense.png) | ![](docs/screenshots/insights.png) | ![](docs/screenshots/ask-ai.png) | ![](docs/screenshots/settings.png) |

## Privacy

Your expenses are stored only on your device. When you use AI Insights or Ask AI,
the relevant expense data is sent to Google Gemini to produce the answer.
```

- [ ] **Step 2: Full automated test run**

With the emulator running:
Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. 61 unit tests and 3 instrumented tests pass.

- [ ] **Step 3: Manual demo checklist (spec §10)**

On the emulator, from a fresh install (`adb uninstall com.spendsense.app`, then `./gradlew :app:installDebug`):
1. Fresh app shows empty Expenses → Settings → Load demo data.
2. Browse ‹ › months.
3. Add a USD expense and see the live conversion.
4. Insights → Generate.
5. Edit an expense → "Outdated" → Regenerate.
6. Ask AI → tap all 3 chips.
7. Change home currency to EUR and back.
8. Airplane mode: Add (foreign currency) shows the rate error, Insights regenerate shows "No internet connection", and Ask AI shows an error bubble.

Write down anything that misbehaves and fix it before moving on.

- [ ] **Step 4: Screenshots for the presentation**

With demo data loaded (and an insight generated), open each screen and capture:

```bash
mkdir -p docs/screenshots
/c/Users/poohk/AppData/Local/Android/Sdk/platform-tools/adb.exe exec-out screencap -p > docs/screenshots/expenses.png
```

Repeat for `add-expense.png` (Add screen with a USD amount entered), `insights.png` (after Generate), `ask-ai.png` (after 2 questions) and `settings.png`. Open each PNG to confirm it shows the right screen.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "docs: README with setup, requirements mapping and screenshots" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
