package ai.deepcode.android.data.remote

import ai.deepcode.android.domain.model.AIModel

/**
 * Single source of truth for Zen AI endpoints, model definitions, and defaults.
 */
object ZenModels {
    const val BASE_URL = "https://opencode.ai/zen/v1"
    const val DEFAULT_FREE = "deepseek-v4-flash-free"
    const val CLIENT_HEADER_NAME = "X-OpenCode-Client"
    const val CLIENT_HEADER_VALUE = "android/1.0.0"

    val KNOWN_FREE_IDS = listOf(
        "deepseek-v4-flash-free",
        "muse-spark-1.2-contributor-free",
        "mimo-v2.5-free",
        "ling-3.0-flash-fin-free",
        "nemotron-3-ultra-free",
        "nemotron-3.5-lightning-free",
        "laguna-s-2.1-free"
    )

    val KNOWN_PAID_IDS = listOf(
        "claude-fable-5",
        "claude-opus-5",
        "claude-sonnet-5",
        "claude-sonnet-4-6",
        "gemini-3.7-flash",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gpt-5.6-sol",
        "gpt-5.5",
        "gpt-5.4",
        "grok-4.6",
        "deepseek-v4-flash",
        "deepseek-v4-pro",
        "glm-5.2",
        "minimax-m3",
        "kimi-k3",
        "qwen3.6-plus"
    )

    val ALL_KNOWN_IDS: Set<String> = (KNOWN_FREE_IDS + KNOWN_PAID_IDS).toSet()

    /**
     * Map legacy or non-existent model IDs (such as "big-pickle" or "north-mini-code")
     * to active, valid Zen model IDs.
     */
    fun sanitize(requested: String?, available: List<AIModel>? = null): String {
        if (requested.isNullOrBlank()) return DEFAULT_FREE
        val clean = requested.trim()

        // Direct match with available or known models
        if (available != null && available.any { it.id.equals(clean, ignoreCase = true) }) {
            return available.first { it.id.equals(clean, ignoreCase = true) }.id
        }
        if (ALL_KNOWN_IDS.contains(clean)) return clean

        // Legacy mapping
        return when (clean.lowercase()) {
            "big-pickle", "north-mini-code", "pickle" -> DEFAULT_FREE
            "deepseek-v4-flash" -> "deepseek-v4-flash-free"
            "mimo-v2.5" -> "mimo-v2.5-free"
            "nemotron-3-ultra" -> "nemotron-3-ultra-free"
            "nemotron-3.5-lightning" -> "nemotron-3.5-lightning-free"
            "muse-spark-1.2" -> "muse-spark-1.2-contributor-free"
            "ling-3.0-flash" -> "ling-3.0-flash-fin-free"
            "laguna-s-2.1" -> "laguna-s-2.1-free"
            else -> {
                // If it contains "free", return default free
                if (clean.contains("free", ignoreCase = true)) DEFAULT_FREE
                else available?.firstOrNull { it.isFree }?.id ?: DEFAULT_FREE
            }
        }
    }
}
