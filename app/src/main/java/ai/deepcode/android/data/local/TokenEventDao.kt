package ai.deepcode.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TokenEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TokenEventEntity)

    @Query("""
        SELECT * FROM token_event
        WHERE session_id = :sessionId
        ORDER BY seq ASC
    """)
    suspend fun getEvents(sessionId: String): List<TokenEventEntity>

    @Query("""
        SELECT COALESCE(MAX(seq), 0) FROM token_event
        WHERE session_id = :sessionId
    """)
    suspend fun getNextSeq(sessionId: String): Int

    @Query("""
        SELECT
            session_id                    AS sessionId,
            COUNT(*)                      AS totalEvents,
            COALESCE(SUM(tokens_input), 0)      AS totalInput,
            COALESCE(SUM(tokens_output), 0)     AS totalOutput,
            COALESCE(SUM(tokens_reasoning), 0)  AS totalReasoning,
            COALESCE(SUM(tokens_cache_read), 0) AS totalCacheRead,
            COALESCE(SUM(tokens_cache_write), 0)AS totalCacheWrite,
            COALESCE(SUM(cost_usd), 0.0)        AS totalCost,
            COALESCE(MIN(time_created), 0)      AS timeFirst,
            COALESCE(MAX(time_created), 0)      AS timeLast
        FROM token_event
        WHERE session_id = :sessionId
    """)
    suspend fun getEventSummary(sessionId: String): TokenEventSummary?

    @Query("""
        SELECT
            COALESCE(SUM(tokens_input), 0)      AS totalInput,
            COALESCE(SUM(tokens_output), 0)     AS totalOutput,
            COALESCE(SUM(tokens_reasoning), 0)  AS totalReasoning,
            COALESCE(SUM(tokens_cache_read), 0) AS totalCacheRead,
            COALESCE(SUM(tokens_cache_write), 0)AS totalCacheWrite,
            COALESCE(SUM(cost_usd), 0.0)        AS totalCost
        FROM token_event
    """)
    suspend fun getLifetimeEventTotals(): TokenEventTotals

    @Query("DELETE FROM token_event WHERE session_id = :sessionId")
    suspend fun deleteEvents(sessionId: String)

    @Query("DELETE FROM token_event WHERE time_created < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("DELETE FROM token_event")
    suspend fun deleteAll()
}

data class TokenEventTotals(
    val totalInput: Long,
    val totalOutput: Long,
    val totalReasoning: Long,
    val totalCacheRead: Long,
    val totalCacheWrite: Long,
    val totalCost: Double
)
