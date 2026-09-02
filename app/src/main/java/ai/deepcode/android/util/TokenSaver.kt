package ai.deepcode.android.util

import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.data.local.EncryptedPrefs
import android.content.Context

object TokenSaver {
    
    /**
     * Gets the configured maximum history turns. Default is 8.
     * A value of -1 represents unlimited (full history).
     */
    fun getMaxHistoryTurns(context: Context): Int {
        val prefs = EncryptedPrefs.getInstance(context)
        val setting = prefs.getSetting("max_history_turns", "8")
        return setting.toIntOrNull() ?: 8
    }

    fun setMaxHistoryTurns(context: Context, turns: Int) {
        val prefs = EncryptedPrefs.getInstance(context)
        prefs.saveSetting("max_history_turns", turns.toString())
    }

    /**
     * Trims the conversation history to only include the last N user turns.
     * Trimming starts from the end and counts 'user' role messages.
     * Everything before the N-th user message is safely discarded.
     * This avoids breaking intermediate tool calls/results since we only truncate
     * at a clean user turn boundary.
     */
    fun trimHistory(messages: List<Message>, maxTurns: Int): List<Message> {
        if (messages.isEmpty()) return messages
        val limit = if (maxTurns <= 0) 8 else maxTurns

        var userMessagesCount = 0
        var cutoffIndex = -1

        // Scan backwards to find the user turn boundary
        for (i in messages.indices.reversed()) {
            if (messages[i].role == "user") {
                userMessagesCount++
                if (userMessagesCount > limit) {
                    cutoffIndex = i
                    break
                }
            }
        }

        val subList = if (cutoffIndex == -1) {
            messages
        } else {
            messages.subList(cutoffIndex + 1, messages.size)
        }

        // Truncate extremely large intermediate history contents to prevent request payload bloat (e.g. Zen API 400 Errors)
        return subList.mapIndexed { index, msg ->
            if (index < subList.size - 1 && msg.content.length > 4000) {
                val prefix = msg.content.take(3800)
                val suffix = "\n\n... [Content Truncated for Context Length to Prevent API Payload Overflow] ..."
                msg.copy(content = prefix + suffix)
            } else {
                msg
            }
        }
    }
}
