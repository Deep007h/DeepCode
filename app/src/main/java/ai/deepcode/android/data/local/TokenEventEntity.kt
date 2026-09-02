package ai.deepcode.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "token_event",
    indices = [
        Index("session_id"),
        Index("session_id", "seq")
    ]
)
data class TokenEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "seq")
    val seq: Int,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "tokens_input")
    val tokensInput: Int = 0,

    @ColumnInfo(name = "tokens_output")
    val tokensOutput: Int = 0,

    @ColumnInfo(name = "tokens_reasoning")
    val tokensReasoning: Int = 0,

    @ColumnInfo(name = "tokens_cache_read")
    val tokensCacheRead: Int = 0,

    @ColumnInfo(name = "tokens_cache_write")
    val tokensCacheWrite: Int = 0,

    @ColumnInfo(name = "cost_usd")
    val costUsd: Double = 0.0,

    @ColumnInfo(name = "prompt_bytes")
    val promptBytes: Int? = null,

    @ColumnInfo(name = "time_created")
    val timeCreated: Long = System.currentTimeMillis()
)

data class TokenEventSummary(
    val sessionId: String,
    val totalEvents: Int,
    val totalInput: Long,
    val totalOutput: Long,
    val totalReasoning: Long,
    val totalCacheRead: Long,
    val totalCacheWrite: Long,
    val totalCost: Double,
    val timeFirst: Long,
    val timeLast: Long
)
