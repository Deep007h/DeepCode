package ai.deepcode.android.service.drive

import ai.deepcode.android.service.google.DriveService
import ai.deepcode.android.service.google.DriveFile
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveHandler(private val context: Context) {

    data class DriveQuery(
        val action: String,
        val query: String?,
        val maxResults: Int
    )

    fun parse(text: String): DriveQuery? {
        val lower = text.lowercase().trim()
        val driveWords = listOf("drive", "file", "folder", "google drive", "document")
        val hasDriveWord = driveWords.any { lower.contains(it) }
        if (!hasDriveWord) return null

        val actionWords = listOf("list", "show", "read", "get", "find", "search", "look", "check", "open")
        val hasActionWord = actionWords.any { Regex("\\b${it}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
        if (!hasActionWord) return null

        val action = when {
            lower.contains("search") || lower.contains("find") -> "search"
            lower.contains("upload") || lower.contains("save") || lower.contains("create") -> "upload"
            else -> "list"
        }

        val query = if (action == "search") {
            val idx = maxOf(
                lower.indexOf("search"),
                lower.indexOf("find")
            ).let { if (it < 0) 0 else it + "search".length }
            text.substring(idx).trim()
                .replace(Regex("\\b(?:for|about|in|my|file|files|drive|document)\\b", RegexOption.IGNORE_CASE), "")
                .trim()
                .take(100)
        } else null

        val match = Regex("\\b(\\d+)\\s*(file|document|result)\\b", RegexOption.IGNORE_CASE).find(lower)
        val maxResults = when {
            match?.groupValues?.get(1)?.toIntOrNull() != null -> match.groupValues[1].toInt()
            else -> 10
        }.coerceIn(1, 50)

        return DriveQuery(action, query, maxResults)
    }

    suspend fun process(text: String): String {
        val parsed = parse(text) ?: return ""
        return withContext(Dispatchers.IO) {
            try {
                val repo = IntegrationRepository(context)
                val integration = repo.getIntegrationByAppId("google_drive")
                val account = repo.getIntegrationByAppId("google_account")
                val isConnected = (integration != null && integration.status == "connected") ||
                        (account != null && account.status == "connected")
                if (!isConnected) {
                    return@withContext "Google Drive is not connected. Go to Connections to authorize Google Account or Drive."
                }

                val service = DriveService(context)
                val result = when (parsed.action) {
                    "search" -> service.searchFiles(parsed.query ?: "", parsed.maxResults)
                    else -> service.listRootFiles(parsed.maxResults)
                }

                if (result.isFailure) {
                    val err = result.exceptionOrNull()
                    "Drive error: ${err?.message ?: "Unknown"}"
                } else {
                    val files = result.getOrThrow()
                    if (files.isEmpty()) "No files found."
                    else files.joinToString("\n───\n") { f ->
                        val icon = if (f.isFolder) "📁" else "📄"
                        val size = if (f.size > 0) "${f.size / 1024}KB" else "-"
                        "$icon ${f.name}\n  ID: ${f.id}\n  Size: $size\n  Modified: ${f.modifiedTime}"
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DriveHandler", "Failed to process drive request", e)
                "Error accessing Drive: ${e.message}"
            }
        }
    }

    suspend fun readFile(fileId: String): Result<String> {
        return DriveService(context).getFileContent(fileId)
    }

    suspend fun uploadFile(name: String, content: String, mimeType: String = "text/plain"): Result<String> {
        return DriveService(context).uploadFile(name, content, mimeType)
    }
}
