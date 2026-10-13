# SpendSense — Design Spec

**Date:** 2026-10-13
**Course:** Mobile Application Development, Final Project
**Deadline:** under 1 week (live demo in class)

## 1. Overview

**SpendSense** is an Android expense tracker with an AI assistant. Users log
expenses in any major currency. The app converts them to the user's home
currency at live exchange rates, shows where the money goes by category, and
uses Google Gemini to (a) write a monthly spending analysis with saving tips and
(b) answer questions about the user's spending in a chat.

### Issues & motivation (for the presentation)

- Small daily purchases (coffee, rides, snacks) add up unnoticed. People only
  see the total at the end of the month, when it's too late to change anything.
- Spending in several currencies (USD subscriptions, travel) makes totals hard to
  track by hand.
- Charts show *what* happened but not *what to do about it*. An AI that reads
  your own data can explain patterns and suggest concrete savings in plain
  language.

### Course requirements mapping

| Requirement | How SpendSense meets it |
|---|---|
| 1. Multiple screens | 5 screens: Expenses, Add/Edit Expense, Insights, Ask AI, Settings |
| 2. Data persistence | Room (`expenses`, `insights` tables) + DataStore (home currency) |
| 3. External API (coroutines) | Frankfurter exchange-rate API via Retrofit `suspend` functions |
| 4. AI-integrated functions | Gemini: monthly AI Insights + "Ask your spending" chat |

## 2. Scope

**In scope:** everything in sections 3–10.

**Out of scope (do not build):** receipt scanning, quick-add by text, budgets and
alerts, charts beyond simple category bars, accounts/login/cloud sync, persisting
chat history, home-screen widgets, dark-mode-specific design work (Material 3
defaults are fine), localization beyond English UI.

## 3. Screens & navigation

Single Activity, Jetpack Compose + Material 3, Navigation Compose with type-safe
routes. A bottom navigation bar has 4 tabs: **Expenses · Insights · Ask AI ·
Settings**. Add/Edit Expense is pushed on top (no bottom bar). Primary color:
green (money theme).

Mockups: `.superpowers/brainstorm/*/content/screens.html` (local only, not committed).

### 3.1 Expenses (start destination)
- Top bar: month switcher (‹ October 2026 ›), defaults to the current month.
- Summary card: total spent in the month (home currency) and expense count.
- List grouped by day ("Today", "Yesterday", otherwise "Mon, 6 Oct"), newest
  first. Each row: category icon/color, title, category name, home amount. If the
  original currency differs from home, a second line shows the original amount
  (e.g. "$12.00 USD").
- Tap a row → Add/Edit in edit mode.
- Swipe a row to delete → snackbar with **Undo**.
- FAB **+** → Add/Edit in add mode.
- Empty state: "No expenses this month" with a hint to tap + or load demo data in
  Settings.

### 3.2 Add / Edit Expense
- Fields: Amount (decimal keyboard), Currency (dropdown, default = home
  currency), Title, Category (chips), Date (date picker, default today), Note
  (optional).
- When currency ≠ home: fetch the rate when the currency changes (a newer
  selection cancels an older fetch; no network call per keystroke, because the
  rate doesn't depend on the amount) and show "≈ ฿389.40 · 1 USD = 32.45 THB"
  below the amount.
- Validation: amount > 0; title not blank. Save button disabled until valid.
- Edit mode: top bar has a Delete action (with confirm dialog). If the amount and
  currency are unchanged, saving keeps the stored home amount (no re-conversion,
  works offline).
- Save → back to the previous screen.

### 3.3 Insights
- Own month switcher (defaults to current month).
- Card: month total + one horizontal bar per category that has spending, sorted
  descending, showing amount and percentage.
- AI card:
  - No saved insight → button **"✨ Generate AI insights"**.
  - Loading → progress indicator + "Analyzing your spending…".
  - Saved insight → summary paragraph + "Tips" bullet list + small "Regenerate"
    button.
  - Saved but outdated (data changed since generation) → same content with an
    "Outdated" label and a prominent **Regenerate** button.
  - Error → message + Retry.
- Month with no expenses → no AI button; message "No expenses to analyze".

### 3.4 Ask AI
- Chat list (user bubbles right/green, AI bubbles left/white), auto-scroll to
  newest.
- Greeting bubble from the AI when empty.
- Suggestion chips above the input: "How much did I spend on food this week?",
  "What was my biggest expense this month?", "Where can I save money?". Tapping a
  chip sends it.
- Input field + send button. Send is disabled while waiting. A "typing…" bubble
  shows while loading.
- Failed request → error bubble; Retry is offered when it is the last message.
- Small footer text: "Your expense data is sent to Google Gemini to answer."
- History lives in the ViewModel only (resets on app restart).

### 3.5 Settings
- **Home currency**: shows the current code and opens a list of supported
  currencies (code + name). Changing it opens a confirm dialog: "Convert all
  existing expenses to EUR at today's rate?" (flow in §7.3).
- **Load demo data**: confirm dialog "This replaces all expenses with sample
  data" → loads (§8).
- **Clear all expenses**: confirm dialog → deletes all expenses and insights.
- **About**: AI model name, Gemini API key Set/Missing, "Exchange rates:
  Frankfurter API", version.

## 4. Architecture

Single Gradle module, MVVM, manual dependency injection.

```
Compose screens  ──observe StateFlow<UiState>──▶  ViewModels (viewModelScope)
                                                     │ suspend funs / Flows
                                                     ▼
       ExpenseRepository · CurrencyRepository · AiRepository · SettingsRepository
                                                     │
              Room DB · DataStore · Retrofit(Frankfurter) · Retrofit(Gemini)
```

- **`AppContainer`** is created once in `SpendSenseApp : Application`. It builds
  the Room DB, DataStore, OkHttp/Retrofit clients and the repositories.
  ViewModels get dependencies through `viewModelFactory { initializer { … } }`.
- Each screen has one ViewModel exposing `StateFlow<XxxUiState>` and public
  functions for user events.
- Screens never touch Room/Retrofit directly, only ViewModels → repositories.

### 4.1 Package layout (`com.spendsense.app`)

```
SpendSenseApp.kt, MainActivity.kt, AppContainer.kt
data/local/        AppDatabase, ExpenseDao, InsightDao, entities + mappers
data/remote/       FrankfurterApi, GeminiApi, DTOs, NetworkModule, safeApiCall
data/repository/   ExpenseRepository, CurrencyRepository, AiRepository, SettingsRepository, AiPrompts
domain/            Expense, Category, Money, Dates, SupportedCurrencies, ExpenseContextBuilder,
                   Insight, AppError, DemoData
ui/navigation/     routes, NavHost, bottom bar
ui/expenses/       ExpensesScreen, ExpensesViewModel
ui/addedit/        AddEditExpenseScreen, AddEditViewModel
ui/insights/       InsightsScreen, InsightsViewModel
ui/askai/          AskAiScreen, AskAiViewModel
ui/settings/       SettingsScreen, SettingsViewModel
ui/theme/          Material 3 theme (green)
ui/common/         ViewModel factory helper, shared composables
```

## 5. Data model

### 5.1 `Category` enum
`FOOD, TRANSPORT, SHOPPING, BILLS, FUN, HEALTH, OTHER`. Each has a display name,
an emoji/icon and a color. Stored in Room as the enum name.

### 5.2 Room table `expenses`

| Column | Type | Notes |
|---|---|---|
| `id` | Long PK, autoGenerate | |
| `title` | String | |
| `category` | String (Category name) | |
| `amountHome` | Double | in current home currency, rounded to 2 decimals |
| `originalAmount` | Double | as entered |
| `originalCurrency` | String | ISO code, e.g. "USD" |
| `date` | Long (LocalDate.toEpochDay) | converted in the entity mappers |
| `note` | String? | |

DAO: `observeBetween(start, end): Flow<List<ExpenseEntity>>`,
`getBetween(start, end)`, `upsert`, `delete`, `deleteAll`, `insertAll`,
`reconvert(newHome, rate)` (§7.3).

### 5.3 Room table `insights`

| Column | Type | Notes |
|---|---|---|
| `month` | String PK | "YYYY-MM" |
| `json` | String | raw Gemini JSON `{summary, tips[]}` |
| `dataHash` | Int | hash of the month's expense context string at generation time |
| `generatedAt` | Long | epoch millis |

An insight is **outdated** when `dataHash` ≠ hash of the month's current context
string (computed by `ExpenseContextBuilder`).

### 5.4 DataStore (Preferences)
- `home_currency`: String. Default: currency of `Locale.getDefault()` if
  Frankfurter supports it, otherwise "USD". Supported codes for this default check
  come from a hard-coded fallback list of Frankfurter's currencies (so the first
  launch works offline).

## 6. External APIs

Both use Retrofit with the kotlinx.serialization converter, OkHttp with logging
in debug builds, and `suspend` functions called from `viewModelScope`.

### 6.1 Frankfurter (exchange rates, no key)
- Base URL: `https://api.frankfurter.dev/v1/`
- `GET latest?base={from}&symbols={to}` → `{ "base": "USD", "date": "...", "rates": { "THB": 32.45 } }`
- `GET currencies` → `{ "USD": "United States Dollar", ... }`
- `CurrencyRepository` caches rates in memory per `(from, to)` pair for the app
  session, and caches the currency list (falling back to the hard-coded list if
  the call fails).
- Same currency → rate 1.0, no network call.

### 6.2 Gemini (AI)
- Endpoint: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- Auth header: `x-goog-api-key: BuildConfig.GEMINI_API_KEY`
- Model: constant `GeminiConfig.MODEL = "gemini-3.8-flash"`. Verified 2026-10-13:
  `gemini-2.5-flash` is limited to existing users, and Google recommends 3.8 Flash
  (free tier) for new projects.
- Request uses `systemInstruction`, `contents` (role `user`/`model`) and
  `generationConfig` with `thinkingConfig.thinkingLevel = "low"` (fast replies).
  Temperature is left at the Gemini 3 default (1.0), as Google recommends. For
  Insights, also `responseMimeType: "application/json"` + `responseSchema`.
- Timeout: 30 s read timeout on the Gemini OkHttp client.

## 7. Feature flows

### 7.1 `ExpenseContextBuilder` (pure Kotlin, unit-tested)
Turns expenses into compact text for prompts. Input: list of expenses, home
currency, today's date, and the months to include. Output contains:
1. Header: today's date (with weekday), home currency.
2. Pre-computed numbers (so the model doesn't have to do arithmetic):
   - per month: total and per-category totals,
   - this week and last week (Mon–Sun): total and per-category totals,
   - per month: top 5 expenses.
3. Raw expense lines: `2026-10-12 | Netflix | BILLS | 389.40 (orig 12.00 USD)`.

It also exposes `monthContext(month)` used for the Insights `dataHash`.

### 7.2 AI flows
**Insights** (`AiRepository.generateInsights(month)`):
- Context: selected month (full lines) + previous month (totals only).
- System instruction: friendly personal-finance assistant. Use only the data
  given. Amounts in home currency. Summary of 2–3 sentences mentioning the top
  category and the change vs last month if available. 2–3 tips, each with a
  concrete estimated saving. No markdown.
- Response schema: `{ summary: string, tips: string[] }`.
- On success, upsert into `insights` with the current `dataHash`.
- If JSON parsing fails, store and show the raw text as `summary` with no tips.

**Ask AI** (`AiRepository.ask(question, history)`):
- System instruction = assistant rules + `ExpenseContextBuilder` output for the
  current month and the 2 previous months, rebuilt on every send so it reflects
  the latest data.
- Rules: answer only from the data, say so if the data doesn't contain the
  answer, plain text, 1–4 sentences, amounts formatted with the currency. Weeks
  or months that aren't listed had no expenses.
- `contents` = the last 10 successful chat messages (user/model pairs only, so
  the list starts with a user turn), then the new question. The local greeting
  bubble and error bubbles are never sent.
- Strip any `**` from the reply defensively.

### 7.3 Currency flows
**Add/Edit conversion:** described in §3.2. Saving a foreign-currency expense
requires a rate. If the rate can't be fetched, show "Can't get exchange rate —
check your connection or enter the amount in {HOME}" and don't save.
`amountHome = round2(originalAmount × rate)`.

**Change home currency (old → new):**
1. Confirm dialog.
2. Fetch rate old → new. On failure, show error and keep the old currency.
3. In one Room transaction:
   `UPDATE expenses SET amountHome = CASE WHEN originalCurrency = :new THEN originalAmount ELSE ROUND(amountHome * :rate, 2) END`
   and `DELETE FROM insights`.
4. Save the new currency to DataStore.

## 8. Demo data

`DemoData` produces a fixed (seeded, reproducible) list of about 2 expenses per
day (at least one every day): the previous full month plus the current month up
to today, with realistic titles per category. Amounts are defined in THB, plus 2 USD subscriptions (e.g. Netflix
$12, Spotify $6).

Load flow (Settings): confirm → fetch THB→home and USD→home rates (rate 1.0 when
equal, so no network for a THB or USD home) → in one transaction delete all
expenses + insights and insert demo data. THB items are stored as home-currency
expenses (`originalCurrency = home`, amounts converted and rounded). USD items
keep `originalCurrency = USD`. If a needed rate fails → error message, nothing
changes.

## 9. Error handling

All network calls go through a `safeApiCall` helper that returns
`Result<T>`. Failures are mapped to an `AppError` with a user message:

| Cause | Message |
|---|---|
| `IOException` / timeout | "No internet connection" |
| HTTP 429 | "AI is busy (free-tier limit). Try again in a minute." |
| HTTP 400/401/403 from Gemini, or blank key | "Gemini API key missing or invalid. See README." |
| Gemini returns no candidates / blocked | "The AI couldn't answer that. Try rephrasing." |
| Anything else | "Something went wrong. Please try again." |

- Blank `GEMINI_API_KEY` at runtime: AI screens show the key message instead of
  calling the API. The rest of the app works normally.
- Async screens model `Loading / Content / Error` explicitly in their UiState.
  Error states offer Retry.

## 10. Testing

**JVM unit tests (JUnit4 + kotlinx-coroutines-test):**
- `ExpenseContextBuilder`: month totals, per-category totals, week boundaries
  (Mon–Sun), top-5, line formatting, stable hash for identical data.
- Insight outdated check.
- Re-conversion rule (same-currency exact restore vs rate multiply), via the
  repository with a fake DAO, or verified in the device test below.
- Error mapping (`IOException`, 429, 403, empty candidates).
- `AddEditViewModel` validation and conversion state, using fake repositories.

**Instrumented test (1):** Room in-memory DB, covering `observeBetween` month
filtering and the `reconvert` SQL.

**Manual demo checklist (run on a real device before the presentation):**
fresh install → Load demo data → browse months → add a USD expense and see the
conversion → Insights generate → edit an expense and see "Outdated" →
Regenerate → Ask AI with each suggestion chip → change home currency → airplane
mode: verify the error messages.

## 11. Project setup

- Remove the cloned OpenRouter client entirely (app sources, `assets/`,
  `README.ru.md`, its `README.md` and `LICENSE`), then create a fresh project
  with `applicationId`/`namespace` `com.spendsense.app`.
- Stack (verified to build on this machine, matching the Android Studio
  2026.1 template): AGP 9.3.2 with built-in Kotlin 2.2.10, Gradle 9.5.0, KSP
  2.3.4, Compose BOM 2026.09.00 (Material 3 1.4), Navigation Compose 2.10.2
  (type-safe routes), Room 2.8.4, Retrofit 3.0.0 + kotlinx.serialization
  converter, OkHttp logging 5.3.2, DataStore Preferences 1.2.1. minSdk 26,
  compileSdk/targetSdk 37.
- `GEMINI_API_KEY=...` in `local.properties` (git-ignored) → exposed as
  `BuildConfig.GEMINI_API_KEY`. The README explains how to get a free key from
  Google AI Studio.
- Git: repo initialized. `.gitignore` includes `.superpowers/` and
  `local.properties`.
- New `README.md`: app description, features, requirement mapping, setup steps.
