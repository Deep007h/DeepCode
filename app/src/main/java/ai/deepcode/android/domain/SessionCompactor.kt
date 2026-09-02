package ai.deepcode.android.domain

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.local.ModelPriceProvider
import ai.deepcode.android.data.local.TurnTokenUsage
import ai.deepcode.android.data.remote.AIProvider
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Session Compaction Engine
 *
 * Implements OpenCode-style session compaction: when the conversation context
 * approaches the model's token limit, older messages are summarized by the LLM
 * into a compact summary, while recent turns are preserved verbatim.
 *
 * Architecture:
 *   [System Prompt] + [Compaction Summary] + [Tail Messages (recent N turns)]
 *   instead of:
 *   [System Prompt] + [All Historical Messages]
 */
class SessionCompactor(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val sessionDao = database.sessionDao()
    private val messageDao = database.messageDao()
    private val securePrefs = EncryptedPrefs.getInstance(context)

    companion object {
        private const val TAG = "SessionCompactor"
        private const val DEFAULT_TAIL_TURNS = 2
        private const val CHARS_PER_TOKEN = 4
        private const val TOOL_OUTPUT_MAX_CHARS = 2000
        private const val MIN_COMPACTION_SAVINGS = 5000
    }

    private val SUMMARY_TEMPLATE = """
        |Output exactly the Markdown structure shown inside <template> and keep the section order unchanged.
        |Do not include the <template> tags in your response.
        |<template>
        |## Goal
        |- [single-sentence task summary]
        |
        |## Constraints & Preferences
        |- [user constraints, preferences, specs, or "(none)"]
        |
        |## Progress
        |### Done
        |- [completed work or "(none)"]
        |
        |### In Progress
        |- [current work or "(none)"]
        |
        |### Blocked
        |- [blockers or "(none)"]
        |
        |## Key Decisions
        |- [decision and why, or "(none)"]
        |
        |## Next Steps
        |- [ordered next actions or "(none)"]
        |
        |## Critical Context
        |- [important technical facts, errors, open questions, or "(none)"]
        |
        |## Relevant Files
        |- [file or directory path: why it matters, or "(none)"]
        |</template>
        |
        |Rules:
        |- Keep every section, even when empty.
        |- Use terse bullets, not prose paragraphs.
        |- Preserve exact file paths, commands, error strings, and identifiers when known.
        |- Do not mention the summary process or that context was compacted.
    """.trimMargin()

    fun buildCompactionPrompt(previousSummary: String?): String {
        val instruction = if (previousSummary != null) {
            """Update the anchored summary below using the conversation history above.
              |Preserve still-true details, remove stale details, and merge in the new facts.
              |<previous-summary>
              |$previousSummary
              |</previous-summary>""".trimMargin()
        } else {
            "Create a new anchored summary from the conversation history above."
        }
        return "$instruction\n\n$SUMMARY_TEMPLATE"
    }

    fun splitHeadTail(
        messages: List<Message>,
        tailTurns: Int = DEFAULT_TAIL_TURNS
    ): Pair<List<Message>, List<Message>> {
        if (messages.isEmpty()) return Pair(emptyList(), messages)

        var userTurnCount = 0
        var splitIndex = messages.size

        for (i in messages.indices.reversed()) {
            if (messages[i].role == "user") {
                userTurnCount++
                if (userTurnCount > tailTurns) {
                    splitIndex = i + 1
                    break
                }
                splitIndex = i
            }
        }

        if (userTurnCount <= tailTurns) {
            return Pair(emptyList(), messages)
        }

        val head = messages.subList(0, splitIndex)
        val tail = messages.subList(splitIndex, messages.size)
        return Pair(head, tail)
    }

    private fun prepareForCompaction(messages: List<Message>): List<Message> {
        return messages.map { msg ->
            if (msg.role == "tool" && msg.content.length > TOOL_OUTPUT_MAX_CHARS) {
                msg.copy(
                    content = msg.content.take(TOOL_OUTPUT_MAX_CHARS) +
                        "\n... [output truncated for compaction]"
                )
            } else {
                val cleaned = msg.content
                    .replace(Regex("\\[(?:audio|file|image|video):[^\\]]+\\]"), "")
                    .trim()
                if (cleaned != msg.content) msg.copy(content = cleaned) else msg
            }
        }
    }

    fun estimateTokens(messages: List<Message>): Int {
        val totalChars = messages.sumOf { it.content.length + it.role.length + 10 }
        return totalChars / CHARS_PER_TOKEN
    }

    suspend fun compact(
        sessionId: String,
        provider: AIProvider,
        modelId: String,
        apiKey: String,
        customBaseUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            sessionDao.setCompacting(sessionId, true)

            val session = sessionDao.getSessionById(sessionId)
            val allMessages = messageDao.getMessagesListForSession(sessionId)
                .map { it.toDomain() }
                .filter { it.role != "system" }

            if (allMessages.size < 4) {
                AppLogger.d(TAG, "Too few messages to compact (${allMessages.size})")
                sessionDao.setCompacting(sessionId, false)
                return@withContext null
            }

            val tailTurns = securePrefs.getSetting("compaction_tail_turns", "$DEFAULT_TAIL_TURNS").toIntOrNull() ?: DEFAULT_TAIL_TURNS
            val (head, tail) = splitHeadTail(allMessages, tailTurns)

            if (head.isEmpty()) {
                AppLogger.d(TAG, "Nothing to compact — all messages are in tail")
                sessionDao.setCompacting(sessionId, false)
                return@withContext null
            }

            val headTokens = estimateTokens(head)
            if (headTokens < MIN_COMPACTION_SAVINGS) {
                AppLogger.d(TAG, "Head too small to justify compaction ($headTokens tokens)")
                sessionDao.setCompacting(sessionId, false)
                return@withContext null
            }

            val preparedHead = prepareForCompaction(head)
            val previousSummary = session?.compactionSummary
            val compactionPrompt = buildCompactionPrompt(previousSummary)

            val compactionMessages = mutableListOf<Message>()
            compactionMessages.add(Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "system",
                content = "You are a conversation summarizer. Your task is to create a structured summary of the conversation history provided below.",
                timestamp = 0
            ))
            compactionMessages.addAll(preparedHead)
            compactionMessages.add(Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = compactionPrompt,
                timestamp = System.currentTimeMillis()
            ))

            val summaryAccumulator = StringBuilder()
            val done = CompletableDeferred<String>()

            provider.streamCompletion(
                messages = compactionMessages,
                model = modelId,
                tools = emptyList(),
                apiKey = apiKey,
                customBaseUrl = customBaseUrl,
                onToken = { token -> summaryAccumulator.append(token) },
                onToolCall = { },
                onComplete = { done.complete(it) },
                onError = { done.completeExceptionally(it) }
            )

            done.await()
            val summary = summaryAccumulator.toString().trim()

            if (summary.isEmpty()) {
                AppLogger.w(TAG, "Compaction produced empty summary")
                sessionDao.setCompacting(sessionId, false)
                return@withContext null
            }

            val tailStartMessageId = tail.firstOrNull()?.id

            sessionDao.updateCompactionState(
                sessionId = sessionId,
                summary = summary,
                tailMessageId = tailStartMessageId,
                compacting = false
            )

            AppLogger.i(TAG, "Compaction complete: ${head.size} messages -> ${summary.length} chars summary, tail starts at $tailStartMessageId")
            summary
        } catch (e: Exception) {
            AppLogger.e(TAG, "Compaction failed", e)
            try { sessionDao.setCompacting(sessionId, false) } catch (_: Exception) {}
            null
        }
    }

    suspend fun getEffectiveHistory(sessionId: String): List<Message> = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
        val allMessages = messageDao.getMessagesListForSession(sessionId).map { it.toDomain() }

        val summary = session?.compactionSummary
        val tailId = session?.compactionTailMessageId

        if (summary.isNullOrEmpty() || tailId == null) {
            return@withContext allMessages
        }

        val tailIndex = allMessages.indexOfFirst { it.id == tailId }
        if (tailIndex < 0) {
            return@withContext allMessages
        }

        val effectiveHistory = mutableListOf<Message>()

        effectiveHistory.add(Message(
            id = "compaction-summary",
            sessionId = sessionId,
            role = "user",
            content = "<context>\nHere is a summary of our conversation so far:\n\n$summary\n</context>\n\nPlease continue from where we left off.",
            timestamp = allMessages[tailIndex].timestamp - 1
        ))

        effectiveHistory.addAll(allMessages.subList(tailIndex, allMessages.size))

        effectiveHistory
    }

    fun shouldCompact(promptTokens: Int, modelId: String): Boolean {
        val contextLimit = ModelPriceProvider.getContextLimit(modelId)
        val suggestion = ContextWindowManager.shouldCompact("check", promptTokens, contextLimit)
        return suggestion.shouldCompact
    }
}
