package ai.deepcode.android.service.google

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class EmailSummary(
    val id: String,
    val from: String,
    val subject: String,
    val snippet: String,
    val date: String,
    val isUnread: Boolean
)

class GmailService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val authService = GoogleAuthService(context)

    suspend fun listUnread(maxResults: Int = 10): Result<List<EmailSummary>> {
        return listMessages("is:unread", maxResults)
    }

    suspend fun listInbox(maxResults: Int = 10): Result<List<EmailSummary>> {
        return listMessages("in:inbox", maxResults)
    }

    suspend fun search(query: String, maxResults: Int = 10): Result<List<EmailSummary>> {
        return listMessages(query, maxResults)
    }

    private suspend fun listMessages(query: String, maxResults: Int): Result<List<EmailSummary>> {
        val tokenResult = authService.getValidAccessToken("gmail")
        if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
        val token = tokenResult.getOrThrow()
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${java.net.URLEncoder.encode(query, "UTF-8")}&maxResults=$maxResults"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Gmail API error: HTTP ${response.code} - $body"))
                val json = gson.fromJson(body, JsonObject::class.java)
                val messages = json.getAsJsonArray("messages") ?: return@withContext Result.success(emptyList())
                val results = mutableListOf<EmailSummary>()
                for (msg in messages) {
                    val id = msg.asJsonObject.get("id")?.asString ?: continue
                    val details = getMessageDetails(id, token) ?: continue
                    results.add(details)
                    if (results.size >= maxResults) break
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getMessageDetails(messageId: String, token: String): EmailSummary? {
        return try {
            val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val headers = json.getAsJsonArray("payload")?.asJsonObject?.getAsJsonArray("headers") ?: return null
            var from = ""
            var subject = ""
            var date = ""
            val snippet = json.get("snippet")?.asString ?: ""
            val labelIds = json.getAsJsonArray("labelIds")
            val isUnread = labelIds?.any { it.asString == "UNREAD" } == true
            for (header in headers) {
                val name = header.asJsonObject.get("name")?.asString ?: continue
                val value = header.asJsonObject.get("value")?.asString ?: continue
                when (name) {
                    "From" -> from = value
                    "Subject" -> subject = value
                    "Date" -> date = value
                }
            }
            EmailSummary(
                id = messageId,
                from = from,
                subject = subject,
                snippet = snippet,
                date = date,
                isUnread = isUnread
            )
        } catch (_: Exception) { null }
    }
}
