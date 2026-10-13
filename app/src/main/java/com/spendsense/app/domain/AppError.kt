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
