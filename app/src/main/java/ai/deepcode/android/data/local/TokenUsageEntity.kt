package ai.deepcode.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ─────────────────────────────────────────────────────────────────────────────
// Room entity — one row per conversation session
// Mirrors OpenCode's session table columns exactly.
// ─────────────────────────────────────────────────────────────────────────────
@Entity(
    tableName = "token_usage",
    indices = [Index("session_id"), Index("time_updated")]
)
data class TokenUsageEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    /** Model identifier, e.g. "deepseek-v4-flash-free" */
    @ColumnInfo(name = "model_id")
    val modelId: String,

    /** Provider name, e.g. "Zen (Free)", "Anthropic" */
    @ColumnInfo(name = "provider_name")
    val providerName: String,

    // ── Cumulative token counters (accumulated across all turns) ──────────────

    /** Total prompt/input tokens sent across all turns */
    @ColumnInfo(name = "tokens_input")
    val tokensInput: Long = 0L,

    /** Total completion/output tokens received across all turns */
    @ColumnInfo(name = "tokens_output")
    val tokensOutput: Long = 0L,

    /** Total reasoning/thinking tokens (DeepSeek R-series, o-series, etc.) */
    @ColumnInfo(name = "tokens_reasoning")
    val tokensReasoning: Long = 0L,

    /** Total prompt-cache HIT tokens (reduces cost when supported) */
    @ColumnInfo(name = "tokens_cache_read")
    val tokensCacheRead: Long = 0L,

    /** Total prompt-cache WRITE tokens */
    @ColumnInfo(name = "tokens_cache_write")
    val tokensCacheWrite: Long = 0L,

    /** Accumulated estimated cost in USD */
    @ColumnInfo(name = "cost_usd")
    val costUsd: Double = 0.0,

    /** Number of complete AI turns recorded in this session */
    @ColumnInfo(name = "turn_count")
    val turnCount: Int = 0,

    /** Unix ms — when the session was first created */
    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis(),

    /** Unix ms — last update (updated after every turn) */
    @ColumnInfo(name = "time_updated")
    val timeUpdated: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────
// Per-turn token snapshot (what a single API response returned)
// This is NOT persisted to Room — it's a transient carrier between the
// provider and the repository.  We keep it in the domain layer.
// ─────────────────────────────────────────────────────────────────────────────
data class TurnTokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val cacheWriteTokens: Int = 0
) {
    val totalTokens: Int get() = inputTokens + outputTokens + reasoningTokens
    val isEmpty: Boolean get() = totalTokens == 0
}

// ─────────────────────────────────────────────────────────────────────────────
// UI-friendly summary (aggregated across the session)
// ─────────────────────────────────────────────────────────────────────────────
data class SessionTokenSummary(
    val sessionId: String,
    val modelId: String,
    val providerName: String,
    val tokensInput: Long,
    val tokensOutput: Long,
    val tokensReasoning: Long,
    val tokensCacheRead: Long,
    val tokensCacheWrite: Long,
    val costUsd: Double,
    val turnCount: Int,
    val timeCreated: Long,
    val timeUpdated: Long
) {
    val totalTokens: Long get() = tokensInput + tokensOutput + tokensReasoning

    fun formattedCost(): String = when {
        costUsd == 0.0 -> "Free"
        costUsd < 0.001 -> "< $0.001"
        costUsd < 1.0 -> "$%.4f".format(costUsd)
        else -> "$%.3f".format(costUsd)
    }

    fun formattedTokens(): String = when {
        totalTokens >= 1_000_000 -> "%.1fM tokens".format(totalTokens / 1_000_000.0)
        totalTokens >= 1_000 -> "%.1fK tokens".format(totalTokens / 1_000.0)
        else -> "$totalTokens tokens"
    }
}
