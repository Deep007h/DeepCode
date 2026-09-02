package ai.deepcode.android.domain

enum class ContextAction {
    OK,
    WARN,
    COMPACT,
    FORK_SESSION
}

data class ContextWindowStatus(
    val usageTokens: Int,
    val limitTokens: Int,
    val usagePercent: Double,
    val remainingTokens: Int,
    val action: ContextAction
)

data class CompactionSuggestion(
    val shouldCompact: Boolean,
    val usagePercent: Double,
    val sessionId: String,
    val reason: String
)

object ContextWindowManager {

    private const val COMPACT_THRESHOLD = 0.95
    private const val WARN_THRESHOLD = 0.80

    fun check(
        actualPromptTokens: Int,
        contextLimit: Int
    ): ContextWindowStatus {
        val limit = if (contextLimit > 0) contextLimit else 128_000
        val usagePercent = (actualPromptTokens.toDouble() / limit.toDouble()).coerceIn(0.0, 1.0)
        val remaining = (limit - actualPromptTokens).coerceAtLeast(0)

        val action = when {
            actualPromptTokens >= limit -> ContextAction.FORK_SESSION
            usagePercent >= COMPACT_THRESHOLD -> ContextAction.COMPACT
            usagePercent >= WARN_THRESHOLD -> ContextAction.WARN
            else -> ContextAction.OK
        }

        return ContextWindowStatus(
            usageTokens = actualPromptTokens,
            limitTokens = limit,
            usagePercent = usagePercent,
            remainingTokens = remaining,
            action = action
        )
    }

    fun shouldCompact(
        sessionId: String,
        actualPromptTokens: Int,
        contextLimit: Int
    ): CompactionSuggestion {
        val limit = if (contextLimit > 0) contextLimit else 128_000
        val usagePercent = actualPromptTokens.toDouble() / limit.toDouble()

        return when {
            actualPromptTokens >= limit -> CompactionSuggestion(
                shouldCompact = true,
                usagePercent = 1.0,
                sessionId = sessionId,
                reason = "Prompt (${formatCompact(actualPromptTokens)}) exceeds limit (${formatCompact(limit)})"
            )
            usagePercent >= COMPACT_THRESHOLD -> CompactionSuggestion(
                shouldCompact = true,
                usagePercent = usagePercent,
                sessionId = sessionId,
                reason = "Context at ${"%.0f".format(usagePercent * 100)}% (${formatCompact(actualPromptTokens)} / ${formatCompact(limit)})"
            )
            else -> CompactionSuggestion(
                shouldCompact = false,
                usagePercent = usagePercent,
                sessionId = sessionId,
                reason = ""
            )
        }
    }

    fun estimatePromptTokens(
        messageCount: Int,
        averageTokensPerMessage: Int = 500,
        systemPromptTokens: Int = 2000,
        toolTokens: Int = 1000
    ): Int {
        return systemPromptTokens + toolTokens + (messageCount * averageTokensPerMessage)
    }

    fun estimatePromptFromBytes(bytes: Int, tokensPerByte: Double = 0.25): Int {
        return (bytes.toDouble() * tokensPerByte).toInt()
    }

    private fun formatCompact(value: Int): String = when {
        value >= 1_000_000 -> "${"%.1f".format(value / 1_000_000.0)}M"
        value >= 1_000 -> "${"%.1f".format(value / 1_000.0)}K"
        else -> value.toString()
    }
}
