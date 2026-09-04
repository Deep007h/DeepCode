package ai.deepcode.android.service.schedule

import ai.deepcode.android.ui.automations.AutomationEntity
import ai.deepcode.android.ui.automations.AutomationRepository
import ai.deepcode.android.ui.automations.AutomationScheduler
import ai.deepcode.android.util.AppLogger
import android.content.Context
import com.google.gson.Gson
import java.util.UUID

class AutomationHandler(private val context: Context) {

    data class AutomationIntent(
        val name: String,
        val actionPrompt: String,
        val cron: String,
        val description: String,
        val category: String = "MESSAGING"
    )

    private fun parseCron(text: String): String? {
        val lower = text.lowercase()

        // "daily at 7:30 AM" or "every day at 07:30pm"
        var lastMatch: MatchResult? = null
        for (pat in listOf(
            Regex("""(?:every\s+)?daily\s+(?:at\s+)?(\d{1,2}):(\d{2})\s*(am|pm)""", RegexOption.IGNORE_CASE),
            Regex("""(?:every\s+)?(?:day|daily)\s+(?:at\s+)?(\d{1,2})\s*(am|pm)\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:every\s+)?daily\s*$""", RegexOption.IGNORE_CASE),
        )) {
            val m = pat.find(lower) ?: continue
            lastMatch = m
            val hourGroup = m.groups[1]?.value ?: continue
            var hour = hourGroup.toIntOrNull() ?: continue
            // Pattern 1: groups [hour, minutes, ampm]
            // Pattern 2: groups [hour, ampm] (minutes default to 0)
            // Pattern 3: no groups (default to 7:00 AM)
            val min = if (m.groups.size >= 3 && m.groups[2]?.value?.let { it.all { c -> c.isDigit() } } == true) {
                m.groups[2]!!.value.toIntOrNull() ?: 0
            } else 0
            val ampm = m.groups.firstOrNull { it != null && it.value in listOf("am", "pm") }
            if (ampm != null) {
                if (ampm.value == "pm" && hour < 12) hour += 12
                if (ampm.value == "am" && hour == 12) hour = 0
            }
            return "%02d %02d * * *".format(min, hour)
        }
        if (lastMatch != null) return "0 7 * * *" // matched but with unexpected groups; default 7 AM

        // "every N seconds"
        val secMatch = Regex("""every\s+(\d+)\s+sec(?:ond)?s?""", RegexOption.IGNORE_CASE).find(lower)
        if (secMatch != null) {
            val interval = secMatch.groupValues[1].toInt()
            return "*/$interval * * * * *"
        }

        // "every 30 minutes"
        val minMatch = Regex("""every\s+(\d+)\s+min(?:ute)?s?""", RegexOption.IGNORE_CASE).find(lower)
        if (minMatch != null) {
            val interval = minMatch.groupValues[1].toInt()
            return "*/$interval * * * *"
        }

        // "every N hours"
        val hourMatch = Regex("""every\s+(\d+)\s+hour(?:s)?""", RegexOption.IGNORE_CASE).find(lower)
        if (hourMatch != null) {
            val interval = hourMatch.groupValues[1].toInt()
            return "0 */$interval * * *"
        }

        // "hourly"
        if (Regex("""\bhourly\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return "0 * * * *"
        }

        // "weekly"
        if (Regex("""\bweekly\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return "0 7 * * 1"
        }

        // "daily" (no time specified)
        if (Regex("""\b(?:every\s+)?daily\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) {
            return "0 7 * * *"
        }

        return null
    }

    private fun extractActionName(text: String): String {
        val uncapitalized = text.trim()
            .replace(Regex("(?:set up|create|add|schedule|make|setup)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:every\s+)?daily\s+(?:at\s+)?(?:\d{1,2}):(?:\d{2})\s*(?:am|pm)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:every\s+)?(?:day|daily)\s+(?:at\s+)?(?:\d{1,2})\s*(?:am|pm)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""every\s+\d+\s+sec(?:ond)?s?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""every\s+\d+\s+min(?:ute)?s?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""every\s+\d+\s+hours?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bhourly\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bweekly\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bdaily\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\brecurring\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bautomation\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bschedule\b""", RegexOption.IGNORE_CASE), "")
            .trim()
        return uncapitalized.replaceFirstChar { it.uppercase() }
    }

    fun parse(text: String): AutomationIntent? {
        val lower = text.lowercase().trim()

        // Skip questions — user is asking about automations, not creating one
        val questionWords = listOf("where", "what", "why", "how", "when", "who", "which", "is it", "does it", "can you", "did you")
        if (questionWords.any { lower.startsWith(it) }) return null

        // Skip if message is very short (likely a general query, not an automation intent)
        if (lower.length < 15) return null

        val scheduleWords = listOf("daily", "weekly", "hourly", "every", "recurring", "schedule", "automation", "sec", "second")
        val hasScheduleWord = scheduleWords.any { lower.contains(it) }
        if (!hasScheduleWord) return null

        val cron = parseCron(text) ?: return null
        val name = extractActionName(text).take(50).ifEmpty { "Scheduled task" }
        val actionPrompt = when {
            lower.contains("news") || lower.contains("headline") ->
                "Use web_search to find the latest news and send a summary"
            lower.contains("weather") ->
                "Use web_search to find the current weather and send a summary"
            else -> name
        }

        return AutomationIntent(
            name = name,
            actionPrompt = actionPrompt,
            cron = cron,
            description = "Set up from Telegram: $text"
        )
    }

    suspend fun create(text: String, telegramChatId: String = ""): String {
        val intent = parse(text) ?: return ""

        val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
        val sessionId = UUID.randomUUID().toString()
        val session = ai.deepcode.android.data.local.SessionEntity(
            id = sessionId,
            title = "🤖 ${intent.name}",
            createdAt = System.currentTimeMillis()
        )
        db.sessionDao().insertSession(session)

        val configMap = mutableMapOf(
            "action_prompt" to intent.actionPrompt,
            "chat_session_id" to sessionId
        )
        if (telegramChatId.isNotBlank()) {
            configMap["telegram_chat_id"] = telegramChatId
        }
        val configJson = Gson().toJson(configMap)
        val nextRun = AutomationScheduler.computeNextRunAt(intent.cron)

        val entity = AutomationEntity(
            id = UUID.randomUUID().toString(),
            name = intent.name,
            description = intent.description,
            category = intent.category,
            isEnabled = true,
            cronExpression = intent.cron,
            lastRunAt = 0L,
            nextRunAt = nextRun,
            templateId = "custom",
            configJson = configJson,
            chatSessionId = sessionId
        )

        val repo = AutomationRepository(context)
        repo.insertAutomation(entity)
        AutomationScheduler(context).schedule(entity, forceRecalculate = true)

        val msg = "✅ Automation **${intent.name}** created! Runs on schedule: `${intent.cron}`\n\nI'll ${intent.actionPrompt.lowercase()} automatically in dedicated chat: **🤖 ${intent.name}**."
        AppLogger.d("AutomationHandler", msg)
        return msg
    }
}
