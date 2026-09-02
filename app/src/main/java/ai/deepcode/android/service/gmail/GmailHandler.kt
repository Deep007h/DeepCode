package ai.deepcode.android.service.gmail

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.service.google.GmailService
import ai.deepcode.android.service.google.EmailSummary
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GmailHandler(private val context: Context) {

    data class GmailQuery(
        val action: String,  // "unread", "inbox", "search"
        val query: String?,
        val maxResults: Int
    )

    fun parse(text: String): GmailQuery? {
        val lower = text.lowercase().trim()

        // Must contain email-related keywords
        val emailWords = listOf("email", "mail", "inbox", "gmail", "message", "unread")
        val hasEmailWord = emailWords.any { lower.contains(it) }
        if (!hasEmailWord) return null

        // Must be a request to read/fetch/check (not about sending or settings)
        val actionWords = listOf("read", "get", "show", "check", "list", "fetch", "find", "search", "look", "view", "what")
        val hasActionWord = actionWords.any { Regex("\\b${it}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
        if (!hasActionWord) return null

        val action = when {
            lower.contains("unread") || lower.contains("new") -> "unread"
            lower.contains("search") -> "search"
            else -> "inbox"
        }

        val query = if (action == "search") {
            val searchIdx = lower.indexOf("search")
            text.substring(searchIdx + 6).trim()
                .replace(Regex("\\b(?:for|about|in|my|email|emails|inbox|gmail)\\b", RegexOption.IGNORE_CASE), "")
                .trim()
                .take(100)
        } else null

        val match = Regex("\\b(\\d+)\\s*(email|message|mail)\\b", RegexOption.IGNORE_CASE).find(lower)
        val maxResults = when {
            match?.groupValues?.get(1)?.toIntOrNull() != null -> match.groupValues[1].toInt()
            else -> 5
        }.coerceIn(1, 20)

        return GmailQuery(action, query, maxResults)
    }

    suspend fun fetch(text: String): String {
        val parsed = parse(text) ?: return ""
        return withContext(Dispatchers.IO) {
            try {
                // Check integration is connected
                val repo = IntegrationRepository(context)
                val integration = repo.getIntegrationByAppId("gmail")
                if (integration == null || integration.status != "connected") {
                    return@withContext "Gmail is not connected. Go to Connections → Gmail → Connect to authorize."
                }

                val service = GmailService(context)
                val result = when (parsed.action) {
                    "unread" -> service.listUnread(parsed.maxResults)
                    "search" -> service.search(parsed.query ?: "", parsed.maxResults)
                    else -> service.listInbox(parsed.maxResults)
                }

                if (result.isFailure) {
                    val err = result.exceptionOrNull()
                    val savedEmail = EncryptedPrefs.getInstance(context).getSetting("google_account_email", "")
                    val connectedAs = if (savedEmail.isNotBlank()) " as $savedEmail" else ""
                    "Gmail is linked$connectedAs, but the Gmail API token couldn't be retrieved. This device requires OAuth client credentials in Settings > Google API for Gmail access. Connected account: ${integration.displayName}"
                } else {
                    val emails = result.getOrThrow()
                    if (emails.isEmpty()) "No emails found."
                    else emails.joinToString("\n───\n") { e ->
                        val tag = if (e.isUnread) "🔴 UNREAD" else "✅"
                        "📩 $tag\nFrom: ${e.from}\nSubject: ${e.subject}\n${e.snippet}\n${e.date}"
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("GmailHandler", "Failed to fetch emails", e)
                "Error fetching emails: ${e.message}"
            }
        }
    }
}
