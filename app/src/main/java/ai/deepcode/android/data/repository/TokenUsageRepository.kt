package ai.deepcode.android.data.repository

import ai.deepcode.android.data.local.LifetimeTotals
import ai.deepcode.android.data.local.ModelPriceProvider
import ai.deepcode.android.data.local.SessionTokenSummary
import ai.deepcode.android.data.local.TokenEventDao
import ai.deepcode.android.data.local.TokenEventEntity
import ai.deepcode.android.data.local.TokenEventSummary
import ai.deepcode.android.data.local.TokenUsageDao
import ai.deepcode.android.data.local.TokenUsageEntity
import ai.deepcode.android.data.local.TurnTokenUsage
import ai.deepcode.android.domain.ContextAction
import ai.deepcode.android.domain.ContextWindowManager
import ai.deepcode.android.domain.ContextWindowStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class ContextWindowResult(
    val sessionId: String,
    val modelId: String = "unknown",
    val usageTokens: Int,
    val limitTokens: Int,
    val usagePercent: Double,
    val remainingTokens: Int,
    val action: ContextAction,
    val cumulativeTokens: Long = 0L,
    val costUsd: Double = 0.0,
    val turnCount: Int = 0
) {
    val formattedUsage: String = when {
        limitTokens >= 1_000_000 -> "${"%.1f".format(usageTokens / 1_000_000.0)}M / ${"%.1f".format(limitTokens / 1_000_000.0)}M"
        limitTokens >= 1_000 -> "${"%.1f".format(usageTokens / 1_000.0)}K / ${"%.1f".format(limitTokens / 1_000.0)}K"
        else -> "$usageTokens / $limitTokens"
    }
}

class TokenUsageRepository(
    private val tokenUsageDao: TokenUsageDao,
    private val tokenEventDao: TokenEventDao
) {

    fun observeAllSessions(): Flow<List<TokenUsageEntity>> =
        tokenUsageDao.observeAllSessions()

    fun observeSession(sessionId: String): Flow<TokenUsageEntity?> =
        tokenUsageDao.observeSession(sessionId)

    suspend fun getSession(sessionId: String): TokenUsageEntity? =
        tokenUsageDao.getSession(sessionId)

    suspend fun getLifetimeTotals(): LifetimeTotals? =
        tokenUsageDao.getLifetimeTotals()

    fun observeLifetimeTotals(): Flow<LifetimeTotals?> =
        tokenUsageDao.observeLifetimeTotals()

    suspend fun getAllSessionsList(): List<TokenUsageEntity> =
        tokenUsageDao.getAllSessionsList()

    suspend fun updateSessionCost(sessionId: String, costUsd: Double) =
        tokenUsageDao.updateCost(sessionId, costUsd)

    suspend fun startSession(
        sessionId: String,
        modelId: String,
        providerName: String
    ) {
        tokenUsageDao.insertIfAbsent(
            TokenUsageEntity(
                sessionId = sessionId,
                modelId = modelId,
                providerName = providerName
            )
        )

        tokenEventDao.insert(
            TokenEventEntity(
                eventId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                seq = 0,
                type = "session_start",
                modelId = modelId,
                timeCreated = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordTurn(
        sessionId: String,
        modelId: String,
        turnTokens: TurnTokenUsage,
        promptBytes: Int? = null
    ) {
        val nowMs = System.currentTimeMillis()
        val cost = ModelPriceProvider.calculateTurnCost(
            modelId = modelId,
            inputTokens = turnTokens.inputTokens,
            outputTokens = turnTokens.outputTokens,
            reasoningTokens = turnTokens.reasoningTokens,
            cacheReadTokens = turnTokens.cacheReadTokens,
            cacheWriteTokens = turnTokens.cacheWriteTokens
        )

        // Ensure session row exists so accumulateTurn UPDATE doesn't fail on new sessions
        tokenUsageDao.insertIfAbsent(
            TokenUsageEntity(
                sessionId = sessionId,
                modelId = modelId,
                providerName = ModelPriceProvider.resolveProvider(modelId),
                timeCreated = nowMs,
                timeUpdated = nowMs
            )
        )

        tokenUsageDao.accumulateTurn(
            sessionId = sessionId,
            inputTokens = turnTokens.inputTokens,
            outputTokens = turnTokens.outputTokens,
            reasoningTokens = turnTokens.reasoningTokens,
            cacheReadTokens = turnTokens.cacheReadTokens,
            cacheWriteTokens = turnTokens.cacheWriteTokens,
            costUsd = cost,
            nowMs = nowMs
        )

        val nextSeq = tokenEventDao.getNextSeq(sessionId) + 1
        tokenEventDao.insert(
            TokenEventEntity(
                eventId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                seq = nextSeq,
                type = "turn_complete",
                modelId = modelId,
                tokensInput = turnTokens.inputTokens,
                tokensOutput = turnTokens.outputTokens,
                tokensReasoning = turnTokens.reasoningTokens,
                tokensCacheRead = turnTokens.cacheReadTokens,
                tokensCacheWrite = turnTokens.cacheWriteTokens,
                costUsd = cost,
                promptBytes = promptBytes
            )
        )
    }

    suspend fun checkContextWindow(
        sessionId: String,
        actualPromptTokens: Int
    ): ContextWindowResult {
        val session = tokenUsageDao.getSession(sessionId)
        val contextLimit = session?.let {
            ModelPriceProvider.getContextLimit(it.modelId)
        } ?: 128_000

        val status = ContextWindowManager.check(actualPromptTokens, contextLimit)

        return ContextWindowResult(
            sessionId = sessionId,
            modelId = session?.modelId ?: "unknown",
            usageTokens = status.usageTokens,
            limitTokens = status.limitTokens,
            usagePercent = status.usagePercent,
            remainingTokens = status.remainingTokens,
            action = status.action,
            cumulativeTokens = session?.let {
                it.tokensInput + it.tokensOutput + it.tokensReasoning +
                it.tokensCacheRead + it.tokensCacheWrite
            } ?: 0L,
            costUsd = session?.costUsd ?: 0.0,
            turnCount = session?.turnCount ?: 0
        )
    }

    suspend fun getSessionEvents(sessionId: String): List<TokenEventEntity> =
        tokenEventDao.getEvents(sessionId)

    suspend fun getSessionEventSummary(sessionId: String): TokenEventSummary? =
        tokenEventDao.getEventSummary(sessionId)

    suspend fun deleteSession(sessionId: String) {
        tokenUsageDao.deleteSession(sessionId)
        tokenEventDao.deleteEvents(sessionId)
    }

    suspend fun deleteOlderThan(cutoffMs: Long) {
        tokenUsageDao.deleteOlderThan(cutoffMs)
        tokenEventDao.deleteOlderThan(cutoffMs)
    }

    suspend fun deleteAll() {
        tokenUsageDao.deleteAll()
        tokenEventDao.deleteAll()
    }

    fun registerCustomModel(modelId: String, contextLimit: Int, promptPrice: Double, completionPrice: Double) {
        ModelPriceProvider.register(
            modelId,
            ai.deepcode.android.data.local.ModelPricing(
                promptPricePerMillion = promptPrice,
                completionPricePerMillion = completionPrice,
                contextLimit = contextLimit
            )
        )
    }

    suspend fun toSummary(entity: TokenUsageEntity): SessionTokenSummary =
        SessionTokenSummary(
            sessionId = entity.sessionId,
            modelId = entity.modelId,
            providerName = entity.providerName,
            tokensInput = entity.tokensInput,
            tokensOutput = entity.tokensOutput,
            tokensReasoning = entity.tokensReasoning,
            tokensCacheRead = entity.tokensCacheRead,
            tokensCacheWrite = entity.tokensCacheWrite,
            costUsd = entity.costUsd,
            turnCount = entity.turnCount,
            timeCreated = entity.timeCreated,
            timeUpdated = entity.timeUpdated
        )

    suspend fun formatLifetimeCost(): String =
        getLifetimeTotals()?.formattedCost() ?: "$0.00"
}
