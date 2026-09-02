package ai.deepcode.android.agent

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.data.local.MessageEntity
import ai.deepcode.android.data.local.SessionEntity
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.ChatSession
import ai.deepcode.android.ui.automations.AutomationEntity
import ai.deepcode.android.ui.connections.IntegrationEntity
import ai.deepcode.android.memory.MemoryChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AgentRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val sessionDao = database.sessionDao()
    private val messageDao = database.messageDao()
    private val integrationDao = database.integrationDao()
    private val memoryDao = database.memoryDao()

    suspend fun getMessagesListForSession(sessionId: String): List<Message> {
        return try {
            messageDao.getMessagesListForSession(sessionId).map { it.toDomain() }
        } catch (e: Exception) {
            if (e.message?.contains("CursorWindow") == true || e.message?.contains("Row too big") == true) {
                cleanupOversizedMessages(sessionId)
                messageDao.getMessagesListForSession(sessionId).map { it.toDomain() }
            } else throw e
        }
    }

    private fun cleanupOversizedMessages(sessionId: String) {
        val db = database.openHelper.writableDatabase
        val maxLen = 20_000
        val suffix = "\n...[truncated]"
        db.execSQL("""
            UPDATE messages SET 
                content = CASE WHEN length(content) > $maxLen THEN substr(content, 1, $maxLen) || '$suffix' ELSE content END,
                toolCallsJson = CASE WHEN length(toolCallsJson) > $maxLen THEN substr(toolCallsJson, 1, $maxLen) || '$suffix' ELSE toolCallsJson END,
                toolResultsJson = CASE WHEN length(toolResultsJson) > $maxLen THEN substr(toolResultsJson, 1, $maxLen) || '$suffix' ELSE toolResultsJson END
            WHERE sessionId = ?
        """.trimIndent(), arrayOf(sessionId))
    }

    suspend fun insertMessage(message: Message) {
        messageDao.insertMessage(MessageEntity.fromDomain(message))
    }

    suspend fun deleteMessagesAfterTimestamp(sessionId: String, afterTimestamp: Long) {
        messageDao.deleteMessagesAfterTimestamp(sessionId, afterTimestamp)
    }

    suspend fun createSession(title: String): String {
        val id = UUID.randomUUID().toString()
        val session = SessionEntity(id, title, System.currentTimeMillis())
        sessionDao.insertSession(session)
        return id
    }

    suspend fun getIntegrationByAppId(appId: String): IntegrationEntity? {
        return integrationDao.getIntegrationByAppId(appId)
    }

    suspend fun searchMemory(query: String): List<MemoryChunk> {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return emptyList()
        val sanitized = cleaned
            .replace("'", "''")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" AND ")
        return try {
            memoryDao.searchMemory(sanitized)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun insertMemoryChunk(chunk: MemoryChunk) {
        memoryDao.insertMemoryChunk(chunk)
    }

    suspend fun getAllAutomations(): List<AutomationEntity> {
        return database.automationDao().getAllAutomations()
    }

    suspend fun insertAutomation(automation: AutomationEntity) {
        database.automationDao().insertAutomation(automation)
    }

    suspend fun deleteAutomation(id: String) {
        database.automationDao().deleteAutomation(id)
    }

    suspend fun insertIntegration(integration: IntegrationEntity) {
        integrationDao.insertIntegration(integration)
    }

    suspend fun updateSessionTokens(sessionId: String, inputTokens: Int, outputTokens: Int, cost: Double) {
        sessionDao.accumulateSessionTokens(sessionId, inputTokens.toLong(), outputTokens.toLong(), cost)
    }

    suspend fun updateCompactionState(sessionId: String, summary: String?, tailMessageId: String?, compacting: Boolean) {
        sessionDao.updateCompactionState(sessionId, summary, tailMessageId, compacting)
    }

    suspend fun getSessionById(sessionId: String): ChatSession? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }
}
