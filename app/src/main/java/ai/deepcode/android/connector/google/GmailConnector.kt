package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.service.google.GoogleAuthService
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gmail connector implementing the [ConnectorBridge] interface.
 *
 * Provides:
 * - **Read**: list/search messages, fetch message content (MIME parsing, text + HTML extraction)
 * - **Send**: compose and send messages (uses gmail.send scope)
 * - **Labels**: apply/remove labels, mark read/unread
 *
 * Uses the Gmail REST API via OkHttp (matching the app's existing HTTP stack).
 * Error responses are classified into [ConnectorError] categories for proper
 * handling by the orchestrator (auth-expired → refresh, permission-denied → surface).
 */
class GmailConnector(context: Context) : GoogleConnectorBridge(context) {

    override val connectorId = "gmail"
    override val displayName = "Gmail"
    override val description = "Read, search, send emails and manage labels via Gmail"

    override val requiredScopes = listOf(
        "https://www.googleapis.com/auth/gmail.readonly",
        "https://www.googleapis.com/auth/gmail.send",
        "https://www.googleapis.com/auth/gmail.modify"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        private const val TAG = "GmailConnector"
        private const val BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me"
    }

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "gmail_list_messages",
            description = "List or search Gmail messages. Returns subject, sender, snippet, and unread status.",
            parameters = mapOf(
                "query" to ParameterDef("Gmail search query (e.g., 'is:unread', 'from:alice@example.com', 'subject:invoice'). Defaults to 'in:inbox'.", required = false),
                "max_results" to ParameterDef("Maximum number of messages to return (1-20). Defaults to 10.", type = "integer", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "gmail_read_message",
            description = "Fetch the full content of a specific Gmail message by ID. Returns the plain text body, or HTML body if no plain text.",
            parameters = mapOf(
                "message_id" to ParameterDef("The Gmail message ID to read.", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "gmail_send_message",
            description = "Send an email via Gmail.",
            parameters = mapOf(
                "to" to ParameterDef("Recipient email address.", required = true),
                "subject" to ParameterDef("Email subject line.", required = true),
                "body" to ParameterDef("Email body text (plain text).", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "gmail_manage_labels",
            description = "Add or remove labels from a message. Use to mark as read/unread or apply custom labels.",
            parameters = mapOf(
                "message_id" to ParameterDef("The Gmail message ID.", required = true),
                "add_labels" to ParameterDef("Comma-separated label IDs to add (e.g., 'UNREAD,STARRED').", required = false),
                "remove_labels" to ParameterDef("Comma-separated label IDs to remove (e.g., 'UNREAD').", required = false)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult {
        return try {
            when (action.actionName) {
                "gmail_list_messages" -> listMessages(action)
                "gmail_read_message" -> readMessage(action)
                "gmail_send_message" -> sendMessage(action)
                "gmail_manage_labels" -> manageLabels(action)
                else -> ConnectorResult.Error(
                    ConnectorError.ApiError(400, "Unknown action: ${action.actionName}")
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Action ${action.actionName} failed", e)
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "Unknown error"))
        }
    }

    // ════════════════════════════════════════════════
    // Action Implementations
    // ════════════════════════════════════════════════

    private suspend fun listMessages(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val query = action.parameters["query"] ?: "in:inbox"
        val maxResults = action.parameters["max_results"]?.toIntOrNull()?.coerceIn(1, 20) ?: 10

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/messages?q=$encodedQuery&maxResults=$maxResults"

        val response = executeRequest(url, token)
        if (response is ConnectorResult.Error) return@withContext response

        val body = (response as ConnectorResult.Success).data
        val json = gson.fromJson(body, JsonObject::class.java)
        val messages = json.getAsJsonArray("messages")

        if (messages == null || messages.size() == 0) {
            return@withContext ConnectorResult.Success("No messages found matching query: $query")
        }

        val results = StringBuilder()
        results.appendLine("📬 Found ${messages.size()} message(s) for: \"$query\"\n")

        for (i in 0 until minOf(messages.size(), maxResults)) {
            val msgId = messages[i].asJsonObject.get("id")?.asString ?: continue
            val details = fetchMessageMetadata(msgId, token)
            if (details != null) {
                val unreadBadge = if (details.isUnread) "🔴 UNREAD" else "✅"
                results.appendLine("$unreadBadge  **${details.subject}**")
                results.appendLine("   From: ${details.from}")
                results.appendLine("   Date: ${details.date}")
                results.appendLine("   Preview: ${details.snippet}")
                results.appendLine("   ID: ${details.id}")
                results.appendLine()
            }
        }

        ConnectorResult.Success(results.toString())
    }

    private suspend fun readMessage(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val messageId = action.parameters["message_id"]
            ?: return@withContext ConnectorResult.Error(
                ConnectorError.ApiError(400, "message_id is required")
            )

        val url = "$BASE_URL/messages/$messageId?format=full"
        val response = executeRequest(url, token)
        if (response is ConnectorResult.Error) return@withContext response

        val body = (response as ConnectorResult.Success).data
        val json = gson.fromJson(body, JsonObject::class.java)

        // Extract headers
        val payload = json.getAsJsonObject("payload")
        val headers = payload?.getAsJsonArray("headers")
        var from = ""
        var subject = ""
        var date = ""
        headers?.forEach { h ->
            val hObj = h.asJsonObject
            when (hObj.get("name")?.asString) {
                "From" -> from = hObj.get("value")?.asString ?: ""
                "Subject" -> subject = hObj.get("value")?.asString ?: ""
                "Date" -> date = hObj.get("value")?.asString ?: ""
            }
        }

        // Extract body — prefer plain text, fall back to HTML
        val textBody = extractBodyPart(payload, "text/plain")
        val htmlBody = if (textBody.isNullOrBlank()) extractBodyPart(payload, "text/html") else null
        val content = textBody ?: htmlBody?.let { stripHtml(it) } ?: json.get("snippet")?.asString ?: ""

        val result = buildString {
            appendLine("📧 **$subject**")
            appendLine("From: $from")
            appendLine("Date: $date")
            appendLine("---")
            appendLine(content)
        }

        ConnectorResult.Success(result)
    }

    private suspend fun sendMessage(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val to = action.parameters["to"]
            ?: return@withContext ConnectorResult.Error(
                ConnectorError.ApiError(400, "Recipient 'to' is required")
            )
        val subject = action.parameters["subject"] ?: "(no subject)"
        val body = action.parameters["body"] ?: ""

        // Build RFC 2822 message
        val rawMessage = buildString {
            appendLine("To: $to")
            appendLine("Subject: $subject")
            appendLine("Content-Type: text/plain; charset=utf-8")
            appendLine()
            append(body)
        }

        // Base64url encode
        val encoded = android.util.Base64.encodeToString(
            rawMessage.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )

        val jsonBody = JsonObject().apply { addProperty("raw", encoded) }
        val request = Request.Builder()
            .url("$BASE_URL/messages/send")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful) {
            return@withContext ConnectorResult.Error(classifyHttpError(response.code, respBody))
        }

        val respJson = gson.fromJson(respBody, JsonObject::class.java)
        val sentId = respJson.get("id")?.asString ?: "unknown"
        ConnectorResult.Success("✅ Email sent successfully to $to (ID: $sentId)")
    }

    private suspend fun manageLabels(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        val messageId = action.parameters["message_id"]
            ?: return@withContext ConnectorResult.Error(
                ConnectorError.ApiError(400, "message_id is required")
            )

        val addLabels = action.parameters["add_labels"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val removeLabels = action.parameters["remove_labels"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        if (addLabels.isEmpty() && removeLabels.isEmpty()) {
            return@withContext ConnectorResult.Error(
                ConnectorError.ApiError(400, "At least one of add_labels or remove_labels is required")
            )
        }

        val jsonBody = JsonObject().apply {
            if (addLabels.isNotEmpty()) {
                add("addLabelIds", gson.toJsonTree(addLabels))
            }
            if (removeLabels.isNotEmpty()) {
                add("removeLabelIds", gson.toJsonTree(removeLabels))
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/messages/$messageId/modify")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful) {
            return@withContext ConnectorResult.Error(classifyHttpError(response.code, respBody))
        }

        val changes = mutableListOf<String>()
        if (addLabels.isNotEmpty()) changes.add("added: ${addLabels.joinToString(", ")}")
        if (removeLabels.isNotEmpty()) changes.add("removed: ${removeLabels.joinToString(", ")}")
        ConnectorResult.Success("✅ Labels updated for message $messageId (${changes.joinToString("; ")})")
    }

    // ════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════

    private fun executeRequest(url: String, token: String): ConnectorResult {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        response.close()

        if (!response.isSuccessful) {
            return ConnectorResult.Error(classifyHttpError(response.code, body))
        }
        return ConnectorResult.Success(body)
    }

    private data class MessageMetadata(
        val id: String,
        val from: String,
        val subject: String,
        val snippet: String,
        val date: String,
        val isUnread: Boolean
    )

    private fun fetchMessageMetadata(messageId: String, token: String): MessageMetadata? {
        return try {
            val url = "$BASE_URL/messages/$messageId?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            response.close()
            if (!response.isSuccessful) return null

            val json = gson.fromJson(body, JsonObject::class.java)
            val payload = json.getAsJsonObject("payload")
            val headers = payload?.getAsJsonArray("headers") ?: return null
            var from = ""
            var subject = ""
            var date = ""
            val snippet = json.get("snippet")?.asString ?: ""
            val labelIds = json.getAsJsonArray("labelIds")
            val isUnread = labelIds?.any { it.asString == "UNREAD" } == true

            for (header in headers) {
                val hObj = header.asJsonObject
                when (hObj.get("name")?.asString) {
                    "From" -> from = hObj.get("value")?.asString ?: ""
                    "Subject" -> subject = hObj.get("value")?.asString ?: ""
                    "Date" -> date = hObj.get("value")?.asString ?: ""
                }
            }
            MessageMetadata(messageId, from, subject, snippet, date, isUnread)
        } catch (_: Exception) { null }
    }

    /**
     * Extract body content from a MIME message part tree.
     * Recursively searches for a part with the specified MIME type.
     */
    private fun extractBodyPart(payload: JsonObject?, mimeType: String): String? {
        if (payload == null) return null

        val payloadMime = payload.get("mimeType")?.asString ?: ""
        val bodyData = payload.getAsJsonObject("body")?.get("data")?.asString

        if (payloadMime == mimeType && bodyData != null) {
            return String(
                android.util.Base64.decode(bodyData, android.util.Base64.URL_SAFE),
                Charsets.UTF_8
            )
        }

        // Search in parts (multipart messages)
        val parts = payload.getAsJsonArray("parts")
        if (parts != null) {
            for (part in parts) {
                val result = extractBodyPart(part.asJsonObject, mimeType)
                if (result != null) return result
            }
        }
        return null
    }

    /** Strip HTML tags for plain-text fallback */
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }
}
