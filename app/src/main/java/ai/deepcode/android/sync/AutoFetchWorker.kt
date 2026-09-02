package ai.deepcode.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.memory.MemoryIndexer
import ai.deepcode.android.util.AppLogger

class AutoFetchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val repository = IntegrationRepository(context)
    private val indexer = MemoryIndexer(context)

    override suspend fun doWork(): Result {
        AppLogger.i("AutoFetchWorker", "Starting auto-fetch background sync...")
        try {
            val connections = repository.getAllIntegrations()
            val active = connections.filter { it.status == "connected" }

            for (conn in active) {
                AppLogger.d("AutoFetchWorker", "Syncing integration: ${conn.appName}")

                // Fetch latest data (simulate or call real API)
                val rawData = fetchLatestDataForApp(conn.appId)

                // Pass to MemoryIndexer
                indexer.ingest(conn.appId, rawData)

                // Update last_synced_at
                val updated = conn.copy(lastSyncedAt = System.currentTimeMillis())
                repository.insertIntegration(updated)
            }
            AppLogger.i("AutoFetchWorker", "Auto-fetch sync successfully completed.")
            return Result.success()
        } catch (e: Exception) {
            AppLogger.e("AutoFetchWorker", "Error in auto-fetch sync", e)
            return Result.retry()
        }
    }

    private fun fetchLatestDataForApp(appId: String): String {
        return when (appId.lowercase()) {
            "gmail" -> {
                """
                    Sender: GitHub <alerts@github.com>
                    Subject: Unapproved dependencies found in security scan
                    Body: We discovered out-of-date packages in the master branch of your repository. Please update npm package versions to avoid vulnerable pathways.
                    ---
                    Sender: Alice Vance <alice@deepcode.ai>
                    Subject: Weekly update meetings
                    Body: Hey team, the sync meeting is rescheduled to Thursday at 2 PM. Please prepare your updates before the standup.
                """.trimIndent()
            }
            "github" -> {
                """
                    Repository: deepcode-android
                    Event: Pull Request #15 Opened by alice
                    Title: Refactor database layers to utilize clean migrations path.
                    Description: Resolves issue #98. Adds proper version schema verification and custom WorkManager triggers.
                    ---
                    Repository: deepcode-android
                    Event: Issue #202 Opened by bob
                    Title: Case-sensitive memory tree keyword searches
                """.trimIndent()
            }
            "google_drive", "drive" -> {
                """
                    File: workspace_architecture_briefing.pdf
                    Shared by: Alice Vance
                    Description: Project design layout for multi-agent terminal systems and Chrome Custom Tabs callback intent specifications.
                """.trimIndent()
            }
            "slack" -> {
                """
                    Channel: #general
                    User: bob
                    Message: Just pushed the latest sora-editor theme settings. Let me know if the font contrast looks correct on low light.
                    ---
                    Channel: #development
                    User: alice
                    Message: We need to register the WorkManager auto-fetch policy to KEEP to prevent rescheduling active jobs.
                """.trimIndent()
            }
            else -> {
                "Latest activity data retrieved from connected integration '$appId' at ${System.currentTimeMillis()}"
            }
        }
    }
}
