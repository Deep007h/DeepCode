package ai.deepcode.android.connector.notion

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.service.notion.NotionService
import ai.deepcode.android.ui.connections.ConnectionStatus
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotionConnector(private val context: Context) : ConnectorBridge {

    override val connectorId = "notion"
    override val displayName = "Notion"
    override val description = "Search pages, read documents, and query databases in Notion"

    override val requiredScopes = emptyList<String>()

    private val securePrefs = EncryptedPrefs.getInstance(context)
    private val integrationRepo = IntegrationRepository(context)

    companion object {
        private const val TAG = "NotionConnector"
    }

    private suspend fun getToken(): String {
        var token = securePrefs.getSetting("notion_token", "").trim()
        if (token.isEmpty()) {
            token = integrationRepo.getIntegrationByAppId("notion")?.accessToken?.trim() ?: ""
        }
        return token
    }

    override suspend fun authenticate(context: Context): AuthResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext AuthResult.Failure(
                "Notion token not configured. Please connect in Settings → Connections.",
                isRecoverable = true
            )
        }
        val service = NotionService(token)
        val result = service.validateToken()
        result.fold(
            onSuccess = { user ->
                integrationRepo.updateStatusAndSyncTime("notion", ConnectionStatus.CONNECTED, System.currentTimeMillis())
                AuthResult.Success(user.name)
            },
            onFailure = { error ->
                AppLogger.e(TAG, "Notion authentication failed", error)
                val isExpired = error.message?.contains("401") == true || error.message?.contains("unauthorized") == true
                integrationRepo.updateStatusOnly("notion", if (isExpired) ConnectionStatus.EXPIRED else ConnectionStatus.ERROR)
                AuthResult.Failure(error.message ?: "Notion validation failed")
            }
        )
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext false
        val service = NotionService(token)
        service.validateToken().isSuccess
    }

    override suspend fun refreshTokenIfNeeded(): Boolean {
        return isAuthenticated()
    }

    override suspend fun revokeAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            securePrefs.saveSetting("notion_token", "")
            try {
                ai.deepcode.android.data.repository.DeepCodeRepository.getInstance(context).updateNotionToken(null)
            } catch (_: Exception) {}
            val integration = integrationRepo.getIntegrationByAppId("notion")
            if (integration != null) {
                integrationRepo.insertIntegration(
                    integration.copy(
                        status = ConnectionStatus.DISCONNECTED,
                        accessToken = "",
                        refreshToken = "",
                        connectedAt = 0L,
                        lastSyncedAt = 0L
                    )
                )
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to revoke Notion access", e)
            false
        }
    }

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "notion_search",
            description = "Search Notion pages and databases by query keyword.",
            parameters = mapOf(
                "query" to ParameterDef("Search query keyword", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "notion_read",
            description = "Read the content of a Notion page by its ID.",
            parameters = mapOf(
                "page_id" to ParameterDef("Notion page ID", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "notion_list_databases",
            description = "List all Notion databases in the workspace.",
            parameters = emptyMap()
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext ConnectorResult.Error(
                ConnectorError.AuthExpired("Notion not connected. Please connect your Notion token in Connections.")
            )
        }
        val service = NotionService(token)
        try {
            when (action.actionName) {
                "notion_search" -> {
                    val query = action.parameters["query"] ?: ""
                    val result = service.search(query)
                    result.fold(
                        onSuccess = { pages ->
                            if (pages.isEmpty()) {
                                ConnectorResult.Success("No Notion pages found matching query: \"$query\"")
                            } else {
                                val text = buildString {
                                    appendLine("Found ${pages.size} Notion item(s):")
                                    for (page in pages) {
                                        appendLine("• [${page.objectType}] ${page.title} (${page.url})")
                                    }
                                }
                                ConnectorResult.Success(text.trimEnd())
                            }
                        },
                        onFailure = { error ->
                            if (error.message?.contains("401") == true) {
                                ConnectorResult.Error(ConnectorError.AuthExpired("Notion token expired: ${error.message}"))
                            } else {
                                ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to search Notion"))
                            }
                        }
                    )
                }
                "notion_read" -> {
                    val rawPageId = action.parameters["page_id"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "page_id is required"))
                    val pageId = rawPageId.trim().replace("-", "").take(64)
                    val result = service.getPageContent(pageId)
                    result.fold(
                        onSuccess = { ConnectorResult.Success(it) },
                        onFailure = { error ->
                            ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to read Notion page"))
                        }
                    )
                }
                "notion_list_databases" -> {
                    val result = service.listDatabases()
                    result.fold(
                        onSuccess = { dbs ->
                            if (dbs.isEmpty()) {
                                ConnectorResult.Success("No Notion databases found in workspace.")
                            } else {
                                val text = buildString {
                                    appendLine("Found ${dbs.size} Notion database(s):")
                                    for (db in dbs) {
                                        appendLine("• **${db.title}** (${db.url})")
                                    }
                                }
                                ConnectorResult.Success(text.trimEnd())
                            }
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to list databases"))
                        }
                    )
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "Notion action error"))
        }
    }
}
