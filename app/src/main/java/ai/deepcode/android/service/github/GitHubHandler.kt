package ai.deepcode.android.service.github

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitHubHandler(private val context: Context) {

    data class GitHubQuery(
        val action: String,    // "user", "repos", "issues", "prs", "commits", "releases", "workflows", "gists", "search_repo"
        val owner: String?,
        val repo: String?,
        val query: String?
    )

    fun parse(text: String): GitHubQuery? {
        val lower = text.lowercase().trim()

        val githubWords = listOf("github", "repo", "repository", "repos", "gist", "gists")
        val hasGithubWord = githubWords.any { lower.contains(it) }
        if (!hasGithubWord) return null

        val actionWords = listOf("list", "show", "get", "find", "search", "check", "view", "what", "fetch", "my", "whoami", "profile")
        val hasActionWord = actionWords.any { Regex("\\b${it}\\b", RegexOption.IGNORE_CASE).containsMatchIn(lower) }
        if (!hasActionWord) return null

        val action = when {
            lower.contains("user") || lower.contains("profile") || lower.contains("whoami") || lower.contains("account") -> "user"
            lower.contains("commit") || lower.contains("history") || lower.contains("log") -> "commits"
            lower.contains("release") || lower.contains("releases") || lower.contains("tag") -> "releases"
            lower.contains("workflow") || lower.contains("action") || lower.contains("ci") -> "workflows"
            lower.contains("gist") -> "gists"
            lower.contains("issue") -> "issues"
            lower.contains("pr") || lower.contains("pull request") -> "prs"
            lower.contains("search") || lower.contains("find") -> "search_repo"
            else -> "repos"
        }

        // Extract owner/repo from patterns like "repo owner/name" or "from owner/name" or "in owner/name"
        val repoMatch = Regex("""([a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+)""").find(text)
        val owner = repoMatch?.groupValues?.get(1)?.substringBefore("/")
        val repo = repoMatch?.groupValues?.get(1)?.substringAfter("/")

        val query = when (action) {
            "search_repo" -> {
                val parts = text.split(Regex("\\b(search|find)\\b", RegexOption.IGNORE_CASE), 2)
                if (parts.size >= 2) parts[1].replace(Regex("(?i)for|repos?|repositories?|on github"), "").trim().take(100) else null
            }
            else -> null
        }

        return GitHubQuery(action, owner, repo, query)
    }

    suspend fun fetch(text: String): String {
        val parsed = parse(text) ?: return ""
        return withContext(Dispatchers.IO) {
            try {
                val token = EncryptedPrefs.getInstance(context).getSetting("github_token", "").trim()
                    .ifEmpty {
                        IntegrationRepository(context).getIntegrationByAppId("github")?.accessToken?.trim() ?: ""
                    }

                if (token.isBlank()) {
                    return@withContext "GitHub is not connected. Go to Connections → GitHub → Connect and paste your Personal Access Token."
                }

                val service = GitHubService(token)

                when (parsed.action) {
                    "user" -> {
                        service.getUser().getOrElse { "GitHub error: ${it.message}" }
                    }
                    "repos" -> {
                        val result = service.listRepos()
                        if (result.isFailure) "GitHub error: ${result.exceptionOrNull()?.message}"
                        else {
                            val repos = result.getOrThrow()
                            if (repos.isEmpty()) "No repositories found."
                            else buildString {
                                appendLine("📦 Your GitHub Repositories (${repos.size}):")
                                repos.forEach { r ->
                                    val icon = if (r.private) "🔒" else "🌍"
                                    appendLine("$icon **${r.fullName}** (${r.defaultBranch})${if (r.fork) " [fork]" else ""}${if (r.description.isNotEmpty()) " — ${r.description}" else ""}")
                                }
                            }.trimEnd()
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
                                else buildString {
                                    appendLine("❗ Issues in ${parsed.owner}/${parsed.repo} (${issues.size}):")
                                    issues.forEach { i ->
                                        appendLine("• #${i.number} [${i.state}] ${i.title} (${i.url})")
                                    }
                                }.trimEnd()
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
                                else buildString {
                                    appendLine("🔀 Pull Requests in ${parsed.owner}/${parsed.repo} (${prs.size}):")
                                    prs.forEach { pr ->
                                        appendLine("• PR #${pr.number} [${pr.state}] ${pr.title} (${pr.url})")
                                    }
                                }.trimEnd()
                            }
                        }
                    }
                    "commits" -> {
                        if (parsed.owner == null || parsed.repo == null) {
                            "Please specify a repository, e.g. \"show commits in owner/repo\""
                        } else {
                            service.listCommits(parsed.owner, parsed.repo).getOrElse { "GitHub error: ${it.message}" }
                        }
                    }
                    "releases" -> {
                        if (parsed.owner == null || parsed.repo == null) {
                            "Please specify a repository, e.g. \"show releases for owner/repo\""
                        } else {
                            service.listReleases(parsed.owner, parsed.repo).getOrElse { "GitHub error: ${it.message}" }
                        }
                    }
                    "workflows" -> {
                        if (parsed.owner == null || parsed.repo == null) {
                            "Please specify a repository, e.g. \"check workflows in owner/repo\""
                        } else {
                            service.listWorkflowRuns(parsed.owner, parsed.repo).getOrElse { "GitHub error: ${it.message}" }
                        }
                    }
                    "gists" -> {
                        service.listGists().getOrElse { "GitHub error: ${it.message}" }
                    }
                    "search_repo" -> {
                        if (parsed.query.isNullOrBlank()) {
                            "What repository are you looking for?"
                        } else {
                            service.searchRepositoriesFormatted(parsed.query).getOrElse { "GitHub error: ${it.message}" }
                        }
                    }
                    else -> "Unknown GitHub action."
                }
            } catch (e: Exception) {
                AppLogger.e("GitHubHandler", "Failed", e)
                "GitHub error: ${e.message}"
            }
        }
    }
}
