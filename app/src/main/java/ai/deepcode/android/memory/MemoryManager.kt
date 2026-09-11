package ai.deepcode.android.memory

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val memoryDao = database.memoryDao()
    private val prefs = EncryptedPrefs.getInstance(context)

    fun isMemoryEnabled(): Boolean {
        return prefs.getSetting("memory_enabled", "true") == "true"
    }

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.saveSetting("memory_enabled", enabled.toString())
    }

    fun isInAppMemoryEnabled(): Boolean {
        return prefs.getSetting("memory_inapp_enabled", "true") == "true"
    }

    fun setInAppMemoryEnabled(enabled: Boolean) {
        prefs.saveSetting("memory_inapp_enabled", enabled.toString())
    }

    fun isTelegramMemoryEnabled(): Boolean {
        return prefs.getSetting("memory_telegram_enabled", "true") == "true"
    }

    fun setTelegramMemoryEnabled(enabled: Boolean) {
        prefs.saveSetting("memory_telegram_enabled", enabled.toString())
    }

    fun getAllMemoryChunksFlow(): Flow<List<MemoryChunk>> = memoryDao.getAllMemoryChunksFlow()

    suspend fun getAllMemoryChunks(): List<MemoryChunk> = withContext(Dispatchers.IO) {
        memoryDao.getAllMemoryChunks()
    }

    suspend fun getRecentMemories(limit: Int = 5): List<MemoryChunk> = withContext(Dispatchers.IO) {
        try {
            memoryDao.getRecentMemories(limit)
        } catch (e: Exception) {
            AppLogger.w("MemoryManager", "getRecentMemories failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getMemoriesBySource(source: String): List<MemoryChunk> = withContext(Dispatchers.IO) {
        try {
            memoryDao.getMemoriesBySource(source)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMemoryCount(): Int = withContext(Dispatchers.IO) {
        try {
            memoryDao.getMemoryCount()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            memoryDao.clearAll()
        } catch (e: Exception) {
            AppLogger.w("MemoryManager", "clearAll failed: ${e.message}")
        }
    }

    suspend fun searchMemory(query: String): List<MemoryChunk> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return@withContext emptyList()

        val terms = cleaned
            .replace(Regex("[^a-zA-Z0-9_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

        if (terms.isEmpty()) return@withContext emptyList()

        // 1. Try SQLite FTS prefix search (e.g. "provid* AND test*")
        try {
            val ftsQuery = terms.joinToString(" AND ") { "$it*" }
            val ftsResults = memoryDao.searchMemory(ftsQuery)
            if (ftsResults.isNotEmpty()) return@withContext ftsResults
        } catch (e: Exception) {
            AppLogger.d("MemoryManager", "FTS search fallback: ${e.message}")
        }

        // 2. Fallback: in-memory substring matching across all memories
        try {
            val all = memoryDao.getAllMemoryChunks()
            all.filter { chunk ->
                val combined = "${chunk.title} ${chunk.content} ${chunk.tags}".lowercase()
                terms.any { combined.contains(it.lowercase()) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteMemoryChunk(id: String) = withContext(Dispatchers.IO) {
        memoryDao.deleteMemoryChunk(id)
    }

    suspend fun insertMemoryChunk(chunk: MemoryChunk) = withContext(Dispatchers.IO) {
        memoryDao.insertMemoryChunk(chunk)
    }

    suspend fun pruneIfNeeded(maxCount: Int = 500) = withContext(Dispatchers.IO) {
        try {
            memoryDao.pruneOldChunks(maxCount)
        } catch (e: Exception) {
            AppLogger.w("MemoryManager", "pruneIfNeeded failed: ${e.message}")
        }
    }

    /**
     * Builds a compact, high-value memory block to inject into the system prompt.
     * Includes recent cross-chat context + topic-relevant memories.
     */
    suspend fun getFormattedMemoriesForPrompt(userPrompt: String): String = withContext(Dispatchers.IO) {
        if (!isMemoryEnabled()) return@withContext ""

        try {
            val recalled = linkedMapOf<String, MemoryChunk>()

            // 1. Recent memories across all chats (captures ongoing tasks, recent project context)
            val recent = getRecentMemories(limit = 4)
            for (m in recent) {
                recalled[m.id] = m
            }

            // 2. Keyword-relevant memories matching the current user prompt
            val searchHits = searchMemory(userPrompt)
            for (m in searchHits.take(3)) {
                recalled[m.id] = m
            }

            if (recalled.isEmpty()) return@withContext ""

            val lines = StringBuilder()
            lines.appendLine("\n## RECALLED LONG-TERM MEMORY (CROSS-CHAT CONTEXT):")
            lines.appendLine("The following memories were recorded from recent interactions (both in-app and Telegram):")

            val now = System.currentTimeMillis()
            for (m in recalled.values.take(6)) {
                val ageMs = now - m.updatedAt
                val timeStr = when {
                    ageMs < 60_000L -> "just now"
                    ageMs < 3_600_000L -> "${ageMs / 60_000L}m ago"
                    ageMs < 86_400_000L -> "${ageMs / 3_600_000L}h ago"
                    else -> "${ageMs / 86_400_000L}d ago"
                }
                val sourceLabel = when (m.source.lowercase()) {
                    "telegram" -> "from Telegram"
                    "inapp", "inapp_chat" -> "from In-App Chat"
                    else -> "stored note"
                }
                lines.appendLine("- [${m.title}] ($sourceLabel, $timeStr): ${m.content}")
            }

            lines.appendLine("\nMEMORY USAGE GUIDELINES:")
            lines.appendLine("- These memories represent the user's ongoing projects, tasks, preferences, and context across sessions and platforms.")
            lines.appendLine("- When starting a new session or replying, naturally acknowledge or reference this ongoing context when relevant (e.g. ask how their current task is going, or build upon previous work).")
            lines.appendLine("- Do NOT mechanically recite or dump the memory list. Weave knowledge naturally and casually into your answer.")

            lines.toString()
        } catch (e: Exception) {
            AppLogger.w("MemoryManager", "getFormattedMemoriesForPrompt error: ${e.message}")
            ""
        }
    }
}
