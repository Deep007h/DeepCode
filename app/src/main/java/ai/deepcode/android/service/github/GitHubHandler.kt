package ai.deepcode.android.service.github

import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitHubHandler(private val context: Context) {

    data class GitHubQuery(
        val action: String,    // "repos", "issues", "prs", "search_repo"
        val owner: String?,
        val repo: String?,
        val query: String?
    )

    fun parse(text: String): GitHubQuery? {
        val lower = text.lowercase().trim()

        val githubWords = listOf("github", "repo", "repository", "repos")
        val hasGithubWord = githubWords.any { lower.contains(it) }
        if (!hasGithubWord) return null

        val actionWords = listOf("list", "show", "get", "find", "search", "check", "view", "what", "fetch", "my")
        val hasActionWord = actionWords.any { Regex("\\b${it}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
        if (!hasActionWord) return null

        val action = when {
            lower.contains("issue") -> "issues"
            lower.contains("pr") || lower.contains("pull request") -> "prs"
            lower.contains("search") || lower.contains("find") -> "search_repo"
            else -> "repos"
        }

        // Extract owner/repo from patterns like "repo owner/name" or "from owner/name"
        val repoMatch = Regex("""(\w[\w.-]+/\w[\w.-]+)""").find(text)
        val owner = repoMatch?.groupValues?.get(1)?.substringBefore("/") ?: null
        val repo = repoMatch?.groupValues?.get(1)?.substringAfter("/") ?: null

        val query = when (action) {
            "search_repo" -> {
                val parts = text.split(Regex("\\b(search|find)\\b", RegexOption.IGNORE_CASE), 2)
                if (parts.size >= 3) parts[2].trim().take(100) else null
            }
            else -> null
        }

        return GitHubQuery(action, owner, repo, query)
    }

    suspend fun fetch(text: String): String {
        val parsed = parse(text) ?: return ""
        return withContext(Dispatchers.IO) {
            try {
                val repo = IntegrationRepository(context)
                val integration = repo.getIntegrationByAppId("github")
                if (integration == null || integration.status != "connected" || integration.accessToken.isBlank()) {
                    return@withContext "GitHub is not connected. Go to Connections → GitHub → Connect and paste your Personal Access Token."
                }

                val token = integration.accessToken
                val service = GitHubService(token)

                when (parsed.action) {
                    "repos" -> {
                        val result = service.listRepos()
                        if (result.isFailure) "GitHub error: ${result.exceptionOrNull()?.message}"
                        else {
                            val repos = result.getOrThrow()
                            if (repos.isEmpty()) "No repositories found."
                            else repos.joinToString("\n") { r ->
                                val icon = if (r.private) "🔒" else "🔓"
                                "$icon ${r.fullName}${if (r.description.isNotEmpty()) " — ${r.description}" else ""}"
                            }
                        }
                    }
                    "issues" -> {
                        if (parsed.owner == null || parsed.repo == null) {
                            "Please specify a repository, e.g. \"list issues in owner/repo\""
                        } else {
                            val result = service.listIssues(parsed.owner, parsed.repo)
                            if (result.isFailure) "GitHub error: ${result.exceptionOrNull()?.message}"
                            else {
                                val issues = result.getOrThrow()
                                if (issues.isEmpty()) "No open issues in ${parsed.owner}/${parsed.repo}."
                                else issues.joinToString("\n") { i ->
                                    "• #${i.number} [${i.state}] ${i.title}"
                                }
                            }
                        }
                    }
                    "prs" -> {
                        if (parsed.owner == null || parsed.repo == null) {
                            "Please specify a repository, e.g. \"list PRs in owner/repo\""
                        } else {
                            val result = service.listPullRequests(parsed.owner, parsed.repo)
                            if (result.isFailure) "GitHub error: ${result.exceptionOrNull()?.message}"
                            else {
                                val prs = result.getOrThrow()
                                if (prs.isEmpty()) "No open pull requests in ${parsed.owner}/${parsed.repo}."
                                else prs.joinToString("\n") { pr ->
                                    "• PR #${pr.number} [${pr.state}] ${pr.title}"
                                }
                            }
                        }
                    }
                    "search_repo" -> {
                        if (parsed.query.isNullOrBlank()) {
                            "What repository are you looking for?"
                        } else {
                            val result = service.searchRepositories(parsed.query)
                            if (result.isFailure) "GitHub error: ${result.exceptionOrNull()?.message}"
                            else {
                                val repos = result.getOrThrow()
                                if (repos.isEmpty()) "No repositories found for \"${parsed.query}\"."
                                else repos.joinToString("\n") { r ->
                                    "• ${r.fullName}${if (r.description.isNotEmpty()) " — ${r.description}" else ""}"
                                }
                            }
                        }
                    }
                    else -> "Unknown action."
                }
            } catch (e: Exception) {
                AppLogger.e("GitHubHandler", "Failed", e)
                "GitHub error: ${e.message}"
            }
        }
    }
}
