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
