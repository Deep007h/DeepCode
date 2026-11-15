package ai.deepcode.android.connector.github

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.service.github.GitHubService
import ai.deepcode.android.ui.connections.ConnectionStatus
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitHubConnector(private val context: Context) : ConnectorBridge {

    override val connectorId = "github"
    override val displayName = "GitHub"
    override val description = "Manage repositories, issues, pull requests, and files on GitHub"

    override val requiredScopes = listOf("repo", "user", "workflow")

    private val securePrefs = EncryptedPrefs.getInstance(context)
    private val integrationRepo = IntegrationRepository(context)

    companion object {
        private const val TAG = "GitHubConnector"
    }

    private suspend fun getToken(): String {
        var token = securePrefs.getSetting("github_token", "").trim()
        if (token.isEmpty()) {
            token = integrationRepo.getIntegrationByAppId("github")?.accessToken?.trim() ?: ""
        }
        return token
    }

    override suspend fun authenticate(context: Context): AuthResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext AuthResult.Failure(
                "GitHub token not configured. Please connect in Settings → Connections.",
                isRecoverable = true
            )
        }
        val service = GitHubService(token)
        val result = service.validateToken()
        result.fold(
            onSuccess = { user ->
                integrationRepo.updateStatusAndSyncTime("github", ConnectionStatus.CONNECTED, System.currentTimeMillis())
                AuthResult.Success(user.login)
            },
            onFailure = { error ->
                AppLogger.e(TAG, "GitHub authentication failed", error)
                val isExpired = error.message?.contains("401") == true || error.message?.contains("Bad credentials") == true
                integrationRepo.updateStatusOnly("github", if (isExpired) ConnectionStatus.EXPIRED else ConnectionStatus.ERROR)
                AuthResult.Failure(error.message ?: "GitHub validation failed")
            }
        )
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext false
        val service = GitHubService(token)
        service.validateToken().isSuccess
    }

    override suspend fun refreshTokenIfNeeded(): Boolean {
        return isAuthenticated()
    }

    override suspend fun revokeAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            securePrefs.saveSetting("github_token", "")
            try {
                ai.deepcode.android.data.repository.DeepCodeRepository.getInstance(context).updateGitHubToken(null)
            } catch (_: Exception) {}
            val integration = integrationRepo.getIntegrationByAppId("github")
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
            AppLogger.e(TAG, "Failed to revoke GitHub access", e)
            false
        }
    }

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "github_get_user",
            description = "Get profile information for the authenticated GitHub user.",
            parameters = emptyMap()
        ),
        ConnectorCapability(
            actionName = "github_list_repos",
            description = "List repositories accessible to the authenticated user.",
            parameters = mapOf(
                "max_results" to ParameterDef("Maximum repositories to return (defaults to 10)", type = "integer", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "github_list_issues",
            description = "List open issues in a GitHub repository.",
            parameters = mapOf(
                "owner" to ParameterDef("Repository owner/organization", required = true),
                "repo" to ParameterDef("Repository name", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "github_create_issue",
            description = "Create a new issue in a GitHub repository.",
            parameters = mapOf(
                "owner" to ParameterDef("Repository owner/organization", required = true),
                "repo" to ParameterDef("Repository name", required = true),
                "title" to ParameterDef("Issue title", required = true),
                "body" to ParameterDef("Issue body text / description", required = false)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext ConnectorResult.Error(
                ConnectorError.AuthExpired("GitHub not connected. Please connect your GitHub token in Connections.")
            )
        }
        val service = GitHubService(token)
        try {
            when (action.actionName) {
                "github_get_user" -> {
                    val result = service.getUser()
                    result.fold(
                        onSuccess = { ConnectorResult.Success(it) },
                        onFailure = { error ->
                            if (error.message?.contains("401") == true) {
                                ConnectorResult.Error(ConnectorError.AuthExpired("GitHub token expired: ${error.message}"))
                            } else {
                                ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to get user"))
                            }
                        }
                    )
                }
                "github_list_repos" -> {
                    val result = service.listRepos()
                    result.fold(
                        onSuccess = { repos ->
                            val max = action.parameters["max_results"]?.toIntOrNull() ?: 10
                            val formatted = service.formatReposAsTable(repos.take(max))
                            ConnectorResult.Success(formatted)
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to list repos"))
                        }
                    )
                }
                "github_list_issues" -> {
                    val owner = action.parameters["owner"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "owner is required"))
                    val repo = action.parameters["repo"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "repo is required"))
                    val result = service.listIssues(owner, repo)
                    result.fold(
                        onSuccess = { issues ->
                            if (issues.isEmpty()) {
                                ConnectorResult.Success("No open issues in $owner/$repo.")
                            } else {
                                val text = buildString {
                                    appendLine("Found ${issues.size} open issue(s) in `$owner/$repo`:")
                                    for (issue in issues) {
                                        appendLine("- #${issue.number}: ${issue.title} (${issue.url})")
                                    }
                                }
                                ConnectorResult.Success(text.trimEnd())
                            }
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to list issues"))
                        }
                    )
                }
                "github_create_issue" -> {
                    val owner = action.parameters["owner"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "owner is required"))
                    val repo = action.parameters["repo"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "repo is required"))
                    val title = action.parameters["title"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "title is required"))
                    val body = action.parameters["body"] ?: ""

                    val result = service.createIssue(owner, repo, title, body)
                    result.fold(
                        onSuccess = { issue ->
                            ConnectorResult.Success("✅ Created issue: $issue")
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(ConnectorError.ApiError(500, error.message ?: "Failed to create issue"))
                        }
                    )
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "GitHub action error"))
        }
    }
}
