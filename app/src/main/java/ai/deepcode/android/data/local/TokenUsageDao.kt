package ai.deepcode.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenUsageDao {

    // ── Upsert / accumulate ───────────────────────────────────────────────────

    /**
     * Insert a new session row.
     * Use IGNORE so we never accidentally overwrite an existing session.
     * Accumulation is done via [accumulateTurn].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: TokenUsageEntity)

    /**
     * Atomically add the per-turn token counts and cost to the session row.
     * This is the hot path — called after every AI response.
     *
     * Using raw SQL addition so we avoid a read-modify-write race condition
     * when two coroutines update the same session concurrently.
     */
    @Query("""
        UPDATE token_usage SET
            tokens_input       = tokens_input       + :inputTokens,
            tokens_output      = tokens_output      + :outputTokens,
            tokens_reasoning   = tokens_reasoning   + :reasoningTokens,
            tokens_cache_read  = tokens_cache_read  + :cacheReadTokens,
            tokens_cache_write = tokens_cache_write + :cacheWriteTokens,
            cost_usd           = cost_usd           + :costUsd,
            turn_count         = turn_count         + 1,
            time_updated       = :nowMs
        WHERE session_id = :sessionId
    """)
    suspend fun accumulateTurn(
        sessionId: String,
        inputTokens: Int,
        outputTokens: Int,
        reasoningTokens: Int,
        cacheReadTokens: Int,
        cacheWriteTokens: Int,
        costUsd: Double,
        nowMs: Long
    )

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM token_usage WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: String): TokenUsageEntity?

    @Query("SELECT * FROM token_usage WHERE session_id = :sessionId")
    fun observeSession(sessionId: String): Flow<TokenUsageEntity?>

    /** All sessions, most recent first — for a usage history screen */
    @Query("SELECT * FROM token_usage ORDER BY time_updated DESC")
    fun observeAllSessions(): Flow<List<TokenUsageEntity>>

    /** Aggregated totals across all sessions for a lifetime stats view */
    @Query("""
        SELECT 
            SUM(tokens_input)       AS totalInput,
            SUM(tokens_output)      AS totalOutput,
            SUM(tokens_reasoning)   AS totalReasoning,
            SUM(tokens_cache_read)  AS totalCacheRead,
            SUM(tokens_cache_write) AS totalCacheWrite,
            SUM(cost_usd)           AS totalCost,
            SUM(turn_count)         AS totalTurns,
            COUNT(*)                AS totalSessions
        FROM token_usage
    """)
    suspend fun getLifetimeTotals(): LifetimeTotals?

    @Query("""
        SELECT 
            SUM(tokens_input)       AS totalInput,
            SUM(tokens_output)      AS totalOutput,
            SUM(tokens_reasoning)   AS totalReasoning,
            SUM(tokens_cache_read)  AS totalCacheRead,
            SUM(tokens_cache_write) AS totalCacheWrite,
            SUM(cost_usd)           AS totalCost,
            SUM(turn_count)         AS totalTurns,
            COUNT(*)                AS totalSessions
        FROM token_usage
    """)
    fun observeLifetimeTotals(): Flow<LifetimeTotals?>

    @Query("SELECT * FROM token_usage")
    suspend fun getAllSessionsList(): List<TokenUsageEntity>

    @Query("UPDATE token_usage SET cost_usd = :costUsd WHERE session_id = :sessionId")
    suspend fun updateCost(sessionId: String, costUsd: Double)

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Query("DELETE FROM token_usage WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /** Prune sessions older than [cutoffMs] — for storage housekeeping */
    @Query("DELETE FROM token_usage WHERE time_updated < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM token_usage")
    suspend fun deleteAll()
}

/** Aggregated query result — Room maps this automatically */
data class LifetimeTotals(
    val totalInput: Long,
    val totalOutput: Long,
    val totalReasoning: Long,
    val totalCacheRead: Long,
    val totalCacheWrite: Long,
    val totalCost: Double,
    val totalTurns: Long,
    val totalSessions: Int
) {
    val totalTokens: Long get() = totalInput + totalOutput + totalReasoning

    fun formattedCost(): String = when {
        totalCost == 0.0 -> "$0.00"
        totalCost < 0.001 -> "< $0.001"
        totalCost < 1.0 -> "$%.4f".format(totalCost)
        else -> "$%.2f".format(totalCost)
    }
}
