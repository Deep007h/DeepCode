package ai.deepcode.android.data.repository

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.local.MessageEntity
import ai.deepcode.android.data.local.SessionEntity
import ai.deepcode.android.data.local.TurnTokenUsage
import ai.deepcode.android.domain.model.ChatSession
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.service.git.GitInfo
import ai.deepcode.android.service.git.GitService
import ai.deepcode.android.service.storage.TelegramDriveService
import ai.deepcode.android.service.notion.NotionService
import ai.deepcode.android.service.github.GitHubService
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class DeepCodeRepository(context: Context) {
    val appContext = context.applicationContext
    private val database = AppDatabase.getDatabase(appContext)
    private val sessionDao = database.sessionDao()
    private val messageDao = database.messageDao()
    val securePrefs = EncryptedPrefs.getInstance(appContext)
    val telegramDrive = TelegramDriveService(appContext)
    val tokenRepository = TokenUsageRepository(
        database.tokenUsageDao(),
        database.tokenEventDao()
    )
    private val toolExecutor = ToolExecutor(appContext).also {
        it.telegramDrive = telegramDrive
        val notionToken = securePrefs.getSetting("notion_token", "")
        if (notionToken.isNotEmpty()) {
            it.notionService = NotionService(notionToken)
        }
        val githubToken = securePrefs.getSetting("github_token", "")
        if (githubToken.isNotEmpty()) {
            it.gitHubService = GitHubService(githubToken)
        }
    }
    private val gitService = GitService()

    companion object {
        private val prewarmedRepo = AtomicReference<DeepCodeRepository?>()
        private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JvmStatic
        fun prewarm(context: Context) {
            if (prewarmedRepo.get() != null) return
            prewarmScope.launch {
                try {
                    val repo = DeepCodeRepository(context.applicationContext)
                    if (prewarmedRepo.compareAndSet(null, repo)) {
                        AppLogger.i("DeepCodeRepository", "Prewarmed repository")
                    }
                } catch (e: Exception) {
                    AppLogger.e("DeepCodeRepository", "Prewarm failed", e)
                }
            }
        }

        @JvmStatic
        fun getInstance(context: Context): DeepCodeRepository {
            prewarmedRepo.get()?.let { return it }
            val repo = DeepCodeRepository(context.applicationContext)
            prewarmedRepo.set(repo)
            return repo
        }
    }

    fun getAllSessions(): Flow<List<ChatSession>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
                .filter { !it.id.startsWith("telegram_") && !it.title.startsWith("Automation:") }
        }
    }

    suspend fun createSession(title: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val session = SessionEntity(id, title, System.currentTimeMillis())
        sessionDao.insertSession(session)
        return id
    }

    suspend fun createSessionWithId(id: String, title: String): String {
        val session = SessionEntity(id, title, System.currentTimeMillis())
        sessionDao.insertSession(session)
        return id
    }

    suspend fun getSessionById(id: String): ChatSession? {
        return sessionDao.getSessionById(id)?.toDomain()
    }

    suspend fun deleteSession(id: String) {
        withContext(Dispatchers.IO) {
            val messages = messageDao.getMessagesListForSession(id)
            for (msg in messages) {
                val allText = listOfNotNull(msg.content, msg.toolResultsJson).joinToString(" ")
                Regex("""\[audio:([^\]]+)\]""").findAll(allText).forEach {
                    try { File(it.groupValues[1]).delete() } catch (_: Exception) {}
                }
                Regex("""\[image:([^\]]+)\]""").findAll(allText).forEach {
                    try { File(it.groupValues[1]).delete() } catch (_: Exception) {}
                }
                Regex("""file://([^\s\]]+)""").findAll(allText).forEach {
                    try { File(it.groupValues[1]).delete() } catch (_: Exception) {}
                }
            }
            val imgDir = File(appContext.filesDir, "session_images/$id")
            if (imgDir.exists()) imgDir.deleteRecursively()
            sessionDao.deleteSession(id)
            messageDao.deleteMessagesForSession(id)
        }
    }

    suspend fun renameSession(id: String, newTitle: String) {
        withContext(Dispatchers.IO) {
            sessionDao.renameSession(id, newTitle)
        }
    }

    fun getMessagesForSession(sessionId: String): Flow<List<Message>> {
        return messageDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getMessagesListForSession(sessionId: String): List<Message> {
        return messageDao.getMessagesListForSession(sessionId).map { it.toDomain() }
    }

    suspend fun insertMessage(message: Message) {
        withContext(Dispatchers.IO) {
            if (sessionDao.getSessionById(message.sessionId) == null) {
                val title = if (message.role == "user") {
                    message.content.lines().firstOrNull()?.take(28)?.ifBlank { "New Chat" } ?: "New Chat"
                } else "New Chat"
                sessionDao.insertSession(SessionEntity(message.sessionId, title, message.timestamp))
            }
            messageDao.insertMessage(MessageEntity.fromDomain(message))
        }
    }

    suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessageById(messageId)
    }

    suspend fun deleteToolMessagesForSession(sessionId: String) {
        messageDao.deleteToolMessagesForSession(sessionId)
    }

    fun getMessageCount(): Flow<Int> {
        return messageDao.getMessageCount()
    }

    fun executeTool(name: String, argsJson: String, workingDir: String): String {
        val rootMode = securePrefs.getBooleanSetting("root_mode", false)
        return toolExecutor.executeTool(name, argsJson, workingDir, rootMode)
    }

    fun getDeclaredTools() = toolExecutor.getDeclaredTools()

    fun updateGitHubToken(token: String?) {
        val cleanToken = token?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanToken != null) {
            securePrefs.saveSetting("github_token", cleanToken)
            toolExecutor.gitHubService = GitHubService(cleanToken)
        } else {
            securePrefs.saveSetting("github_token", "")
            toolExecutor.gitHubService = null
        }
    }

    fun getGitStatus(workingDir: String): GitInfo {
        return gitService.getGitStatus(workingDir)
    }

    fun getDefaultProjectPath(): String {
        return "/storage/emulated/0"
    }

    suspend fun recordTokenUsage(
        sessionId: String,
        modelId: String,
        providerName: String,
        usage: TurnTokenUsage
    ) {
        tokenRepository.recordTurn(sessionId, modelId, usage)

        val totalCost = ai.deepcode.android.data.local.ModelPriceProvider.calculateTurnCost(
            modelId = modelId,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            reasoningTokens = usage.reasoningTokens,
            cacheReadTokens = usage.cacheReadTokens,
            cacheWriteTokens = usage.cacheWriteTokens
        )

        sessionDao.accumulateSessionTokens(
            sessionId = sessionId,
            inputTokens = usage.inputTokens.toLong(),
            outputTokens = usage.outputTokens.toLong(),
            cost = totalCost
        )
    }

    suspend fun syncAndBackfillTokenUsage() = withContext(Dispatchers.IO) {
        try {
            // 1. Recalculate cost for existing token_usage rows that currently have 0.0 cost
            val allTokenSessions = tokenRepository.getAllSessionsList()
            allTokenSessions.forEach { entity ->
                if (entity.costUsd <= 0.0 && (entity.tokensInput > 0 || entity.tokensOutput > 0)) {
                    val computedCost = ai.deepcode.android.data.local.ModelPriceProvider.calculateTurnCost(
                        modelId = entity.modelId,
                        inputTokens = entity.tokensInput.toInt(),
                        outputTokens = entity.tokensOutput.toInt(),
                        reasoningTokens = entity.tokensReasoning.toInt(),
                        cacheReadTokens = entity.tokensCacheRead.toInt(),
                        cacheWriteTokens = entity.tokensCacheWrite.toInt()
                    )
                    if (computedCost > 0.0) {
                        tokenRepository.updateSessionCost(entity.sessionId, computedCost)
                    }
                }
            }

            // 2. Also backfill chat sessions table (sessions) if totalCost is 0.0
            val allChatSessions = sessionDao.getAllSessionsList()
            allChatSessions.forEach { sess ->
                if (sess.totalCost <= 0.0 && (sess.totalTokensInput > 0 || sess.totalTokensOutput > 0)) {
                    val matchingTokenSession = allTokenSessions.firstOrNull { it.sessionId == sess.id }
                    val modelId = matchingTokenSession?.modelId ?: "minimax-m3"
                    val computedCost = ai.deepcode.android.data.local.ModelPriceProvider.calculateTurnCost(
                        modelId = modelId,
                        inputTokens = sess.totalTokensInput.toInt(),
                        outputTokens = sess.totalTokensOutput.toInt()
                    )
                    if (computedCost > 0.0) {
                        sessionDao.updateCost(sess.id, computedCost)
                    }
                }
            }

            // 3. If token usage table was completely empty, backfill from message history
            val lifetime = tokenRepository.getLifetimeTotals()
            if (lifetime == null || lifetime.totalTokens == 0L) {
                val allMessages = messageDao.getAllMessagesList()
                val messagesBySession = allMessages.groupBy { it.sessionId }

                messagesBySession.forEach { (sessId, msgs) ->
                    val userChars = msgs.filter { it.role == "user" }.sumOf { it.content.length }
                    val assistantChars = msgs.filter { it.role == "assistant" }.sumOf { it.content.length }

                    val inputTokens = (userChars / 4).coerceAtLeast(15)
                    val outputTokens = (assistantChars / 4).coerceAtLeast(15)

                    if (inputTokens > 0 || outputTokens > 0) {
                        recordTokenUsage(
                            sessionId = sessId,
                            modelId = "deepseek-v4-flash",
                            providerName = "Zen AI",
                            usage = TurnTokenUsage(
                                inputTokens = inputTokens,
                                outputTokens = outputTokens,
                                reasoningTokens = 0
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("DeepCodeRepository", "Failed to backfill tokens", e)
        }
    }
}
