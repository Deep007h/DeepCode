package ai.deepcode.android.memory

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MemoryExtractor(private val context: Context) {
    private val memoryManager = MemoryManager(context)
    private val prefs = EncryptedPrefs.getInstance(context)

    data class Candidate(val title: String, val content: String, val tags: String)

    suspend fun extractAndSave(
        userMessage: String,
        assistantMessage: String,
        source: String
    ) = withContext(Dispatchers.IO) {
        try {
            if (!memoryManager.isMemoryEnabled()) return@withContext
            if (source.contains("telegram", ignoreCase = true) && !memoryManager.isTelegramMemoryEnabled()) return@withContext
            if ((source.contains("inapp", ignoreCase = true) || source.contains("chat", ignoreCase = true)) && !memoryManager.isInAppMemoryEnabled()) return@withContext

            val cleanUser = userMessage.trim()
            if (cleanUser.length < 5 || cleanUser.length > 3000) return@withContext

            // 1. Filter out trivial chit-chat / greetings
            if (isTrivialMessage(cleanUser)) return@withContext

            // 2. Extract candidate memory fact
            val candidate = analyzeMessageForMemory(cleanUser, assistantMessage, source) ?: return@withContext

            // 3. Deduplicate / Update existing memories if topic matches
            val existingMemories = memoryManager.getAllMemoryChunks()
            val existing = existingMemories.firstOrNull { m ->
                m.title.equals(candidate.title, ignoreCase = true) ||
                (m.tags.isNotEmpty() && candidate.tags.isNotEmpty() &&
                 m.tags.split(",").any { tag -> candidate.tags.split(",").contains(tag) && tag.length > 4 })
            }

            if (existing != null) {
                val updated = existing.copy(
                    content = candidate.content,
                    source = source,
                    updatedAt = System.currentTimeMillis()
                )
                memoryManager.insertMemoryChunk(updated)
                AppLogger.i("MemoryExtractor", "Updated memory [${updated.title}] from $source: ${candidate.content.take(60)}")
            } else {
                val newChunk = MemoryChunk(
                    id = UUID.randomUUID().toString(),
                    title = candidate.title,
                    content = candidate.content,
                    source = source,
                    tags = candidate.tags,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    embeddingHint = candidate.tags
                )
                memoryManager.insertMemoryChunk(newChunk)
                AppLogger.i("MemoryExtractor", "Created new memory [${newChunk.title}] from $source: ${candidate.content.take(60)}")
            }

            // Prune to keep database light & fast
            memoryManager.pruneIfNeeded(500)
        } catch (e: Exception) {
            AppLogger.w("MemoryExtractor", "extractAndSave failed: ${e.message}")
        }
    }

    private fun isTrivialMessage(text: String): Boolean {
        val lower = text.lowercase().trim()
        val trivialExact = setOf(
            "hi", "hello", "hey", "hola", "yo", "sup", "good morning", "good evening", "good afternoon",
            "ok", "okay", "k", "cool", "nice", "great", "awesome", "perfect", "done", "got it", "understood",
            "yes", "no", "yep", "nope", "sure", "thanks", "thank you", "thx", "ty", "bye", "goodbye",
            "test", "testing", "ping", "pong", "help", "/start", "/help", "/settings"
        )
        if (trivialExact.contains(lower)) return true

        // Filter out non-personal trivia / math / generic facts
        if (lower.startsWith("what is ") || lower.startsWith("who is ") || lower.startsWith("how does ") || lower.startsWith("explain ") || lower.startsWith("tell me about ")) {
            val hasPersonalIndicator = lower.contains("my ") || lower.contains("i ") || lower.contains("we ") || lower.contains("our ") || lower.contains("me ")
            if (!hasPersonalIndicator) return true
        }

        return false
    }

    private fun analyzeMessageForMemory(userText: String, assistantText: String, source: String): Candidate? {
        val lower = userText.lowercase()

        // 1. Explicit remember command
        val rememberMatch = Regex(
            """\b(?:remember(?:\s+that)?|keep in mind(?:\s+that)?|don't forget(?:\s+that)?|note that)\s*[:,-]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        ).find(userText)
        if (rememberMatch != null) {
            val fact = rememberMatch.groupValues[1].trim()
            if (fact.length > 5) {
                return Candidate(
                    title = "User Note / Instruction",
                    content = fact.replaceFirstChar { it.uppercase() },
                    tags = "user_note,instruction"
                )
            }
        }

        // 2. Ongoing activity / task / testing / project
        val activityRegex = Regex(
            """\b(?:i am|i'm|we are|we're|currently)\s+(?:working on|testing|adding|building|creating|developing|debugging|fixing|integrating|setting up|configuring|learning|writing)\s+([^.!?\n]{4,100})""",
            RegexOption.IGNORE_CASE
        )
        val activityMatch = activityRegex.find(userText)
        if (activityMatch != null) {
            val fullMatch = activityMatch.value.trim()
            val action = fullMatch
                .removePrefix("i am ").removePrefix("I am ")
                .removePrefix("i'm ").removePrefix("I'm ")
                .removePrefix("we are ").removePrefix("We are ")
                .removePrefix("we're ").removePrefix("We're ")
                .removePrefix("currently ").removePrefix("Currently ")
                .trim()
            return Candidate(
                title = "Current Activity",
                content = "User is $action.",
                tags = "activity,project,task"
            )
        }

        // 3. Identity / Personal profile
        if (lower.contains("my name is ") || lower.contains("i live in ") || lower.contains("i work as ") || lower.contains("i am a ")) {
            return Candidate(
                title = "User Profile",
                content = userText.trim(),
                tags = "identity,profile"
            )
        }

        // 4. User preferences
        val prefRegex = Regex(
            """\b(?:i prefer|i like|i love|i always use|i usually use|never use|don't like|always use)\s+([^.!?\n]{4,80})""",
            RegexOption.IGNORE_CASE
        )
        val prefMatch = prefRegex.find(userText)
        if (prefMatch != null) {
            return Candidate(
                title = "User Preference",
                content = userText.trim(),
                tags = "preference"
            )
        }

        // 5. App & Technical context (e.g. providers, models, deepcode app, tokens)
        if (lower.contains("provider") || lower.contains("model") || lower.contains("tokenharbor") || lower.contains("zen ai") || lower.contains("telegram bot") || lower.contains("apk") || lower.contains("api key")) {
            if (lower.contains("add") || lower.contains("test") || lower.contains("fix") || lower.contains("update") || lower.contains("setup") || lower.contains("switch")) {
                return Candidate(
                    title = "Development Task",
                    content = userText.trim(),
                    tags = "development,providers"
                )
            }
        }

        return null
    }
}
