package ai.deepcode.android.service.github

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GitHubUser(
    val login: String,
    val name: String,
    val email: String,
    val avatarUrl: String,
    val bio: String = "",
    val publicRepos: Int = 0,
    val totalPrivateRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val htmlUrl: String = "",
    val scopes: String = ""
)

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val description: String,
    val url: String,
    val defaultBranch: String,
    val private: Boolean,
    val fork: Boolean
)

data class GitHubFile(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val downloadUrl: String?,
    val sha: String
)

data class GitHubBranch(
    val name: String,
    val sha: String
)

data class GitHubPullRequest(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val url: String
)

data class GitHubIssue(
    val number: Int,
    val title: String,
    val body: String,
    val state: String,
    val url: String
)

class GitHubService(private val token: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val baseUrl = "https://api.github.com/"

    var cachedScopes: String = ""
        private set

    private fun buildRequest(
        endpoint: String,
        method: String = "GET",
        body: String? = null,
        acceptHeader: String = "application/vnd.github.v3+json"
    ): Request {
        val cleanToken = token.trim().removePrefix("Bearer ").removePrefix("token ").trim()
        val builder = Request.Builder()
            .url("$baseUrl${endpoint.trimStart('/')}")
            .header("Authorization", "Bearer $cleanToken")
            .header("Accept", acceptHeader)
            .header("User-Agent", "DeepCode-Android")
        when (method) {
            "POST" -> builder.post(body?.toRequestBody(jsonMediaType) ?: "".toRequestBody(jsonMediaType))
            "PUT" -> builder.put(body?.toRequestBody(jsonMediaType) ?: "".toRequestBody(jsonMediaType))
            "DELETE" -> builder.delete(body?.toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(body?.toRequestBody(jsonMediaType) ?: "".toRequestBody(jsonMediaType))
            else -> builder.get()
        }
        return builder.build()
    }

    private fun execute(request: Request): Result<String> {
        return try {
            val response = client.newCall(request).execute()
            val scopes = response.header("X-OAuth-Scopes") ?: response.header("x-oauth-scopes")
            if (!scopes.isNullOrBlank()) {
                cachedScopes = scopes
            }
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val msg = try {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    json.get("message")?.asString ?: body
                } catch (_: Exception) { body }
                return Result.failure(Exception("GitHub API error (HTTP ${response.code}): $msg"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun jsonStr(el: com.google.gson.JsonElement?): String {
        return if (el != null && !el.isJsonNull) el.asString else ""
    }

    private fun jsonInt(el: com.google.gson.JsonElement?, default: Int = 0): Int {
        return if (el != null && !el.isJsonNull) {
            try { el.asInt } catch (_: Exception) { default }
        } else default
    }

    // ── User & Account ──

    fun validateToken(): Result<GitHubUser> {
        val request = buildRequest("user")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            GitHubUser(
                login = jsonStr(json.get("login")),
                name = jsonStr(json.get("name")).ifEmpty { jsonStr(json.get("login")) },
                email = jsonStr(json.get("email")),
                avatarUrl = jsonStr(json.get("avatar_url")),
                bio = jsonStr(json.get("bio")),
                publicRepos = jsonInt(json.get("public_repos")),
                totalPrivateRepos = jsonInt(json.get("total_private_repos")),
                followers = jsonInt(json.get("followers")),
                following = jsonInt(json.get("following")),
                htmlUrl = jsonStr(json.get("html_url")),
                scopes = cachedScopes
            )
        }
    }

    fun getUser(username: String? = null): Result<String> {
        val endpoint = if (username.isNullOrBlank()) "user" else "users/${username.trim()}"
        val request = buildRequest(endpoint)
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val login = jsonStr(json.get("login"))
            val name = jsonStr(json.get("name")).ifEmpty { login }
            val bio = jsonStr(json.get("bio"))
            val company = jsonStr(json.get("company"))
            val location = jsonStr(json.get("location"))
            val email = jsonStr(json.get("email"))
            val publicRepos = jsonInt(json.get("public_repos"))
            val privateRepos = jsonInt(json.get("total_private_repos"))
            val followers = jsonInt(json.get("followers"))
            val following = jsonInt(json.get("following"))
            val htmlUrl = jsonStr(json.get("html_url"))

            buildString {
                appendLine("👤 **GitHub User:** $name (@$login)")
                if (bio.isNotEmpty()) appendLine("📝 *\"$bio\"*")
                if (email.isNotEmpty()) appendLine("📧 Email: $email")
                if (company.isNotEmpty()) appendLine("🏢 Company: $company")
                if (location.isNotEmpty()) appendLine("📍 Location: $location")
                appendLine("📦 Repositories: $publicRepos public" + if (privateRepos > 0) ", $privateRepos private" else "")
                appendLine("👥 Followers: $followers | Following: $following")
                appendLine("🔗 Profile: $htmlUrl")
                if (username.isNullOrBlank() && cachedScopes.isNotEmpty()) {
                    appendLine("🔑 Token Scopes: `$cachedScopes`")
                }
            }.trimEnd()
        }
    }

    // ── Repositories ──

    fun listRepos(type: String = "all", perPage: Int = 50): Result<List<GitHubRepo>> {
        val endpoint = if (type == "all" || type.isBlank()) "user/repos?per_page=$perPage&sort=updated"
        else "user/repos?type=$type&per_page=$perPage&sort=updated"
        val request = buildRequest(endpoint)
        val firstAttempt = execute(request)
        val respResult = if (firstAttempt.isFailure) {
            val fallbackReq = buildRequest("user/repos?per_page=$perPage")
            execute(fallbackReq)
        } else firstAttempt

        return respResult.map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
                val obj = el.asJsonObject
                GitHubRepo(
                    name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    fullName = obj.get("full_name")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    url = obj.get("html_url")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    defaultBranch = obj.get("default_branch")?.takeIf { !it.isJsonNull }?.asString ?: "main",
                    private = obj.get("private")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    fork = obj.get("fork")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                )
            }
        }
    }

    fun getRepo(owner: String, repo: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val fullName = jsonStr(json.get("full_name"))
            val desc = jsonStr(json.get("description")).ifEmpty { "No description" }
            val isPrivate = json.get("private")?.asBoolean ?: false
            val defaultBranch = jsonStr(json.get("default_branch")).ifEmpty { "main" }
            val stars = jsonInt(json.get("stargazers_count"))
            val forks = jsonInt(json.get("forks_count"))
            val openIssues = jsonInt(json.get("open_issues_count"))
            val language = jsonStr(json.get("language")).ifEmpty { "Unknown" }
            val license = json.get("license")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("spdx_id")?.asString ?: "None"
            val htmlUrl = jsonStr(json.get("html_url"))
            val cloneUrl = jsonStr(json.get("clone_url"))

            buildString {
                appendLine("${if (isPrivate) "🔒" else "🌍"} **$fullName**")
                appendLine("📝 $desc")
                appendLine("🌿 Default Branch: `$defaultBranch` | 💻 Language: $language | 📜 License: $license")
                appendLine("⭐ Stars: $stars | 🍴 Forks: $forks | ⚠️ Open Issues: $openIssues")
                appendLine("🔗 Web: $htmlUrl")
                appendLine("📥 Clone: `$cloneUrl`")
            }.trimEnd()
        }
    }

    fun createRepo(name: String, description: String = "", isPrivate: Boolean = true, autoInit: Boolean = false): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("name", name)
            addProperty("description", description)
            addProperty("private", isPrivate)
            addProperty("auto_init", autoInit)
        }
        val request = buildRequest("user/repos", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("clone_url")?.asString ?: json.get("html_url")?.asString ?: "Repo created"
        }
    }

    fun deleteRepo(owner: String, repo: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo", "DELETE")
        return execute(request).map { "Repository '$owner/$repo' deleted successfully." }
    }

    fun forkRepo(owner: String, repo: String, organization: String? = null): Result<String> {
        val bodyJson = JsonObject().apply {
            if (!organization.isNullOrBlank()) addProperty("organization", organization.trim())
        }
        val request = buildRequest("repos/$owner/$repo/forks", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            val fullName = jsonStr(json.get("full_name"))
            val url = jsonStr(json.get("html_url"))
            "Fork created: $fullName ($url)"
        }
    }

    // ── Repository Files & Contents ──

    fun getRepoContents(owner: String, repo: String, path: String = "", ref: String? = null): Result<List<GitHubFile>> {
        val baseEndpoint = if (path.isEmpty()) "repos/$owner/$repo/contents" else "repos/$owner/$repo/contents/$path"
        val endpoint = if (ref != null) "$baseEndpoint?ref=$ref" else baseEndpoint
        val request = buildRequest(endpoint)
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
                val obj = el.asJsonObject
                GitHubFile(
                    name = obj.get("name")?.asString ?: "",
                    path = obj.get("path")?.asString ?: "",
                    type = obj.get("type")?.asString ?: "file",
                    size = obj.get("size")?.asLong ?: 0L,
                    downloadUrl = obj.get("download_url")?.asString,
                    sha = obj.get("sha")?.asString ?: ""
                )
            }
        }
    }

    fun listRepoContents(owner: String, repo: String, path: String = "", ref: String? = null): Result<String> {
        return getRepoContents(owner, repo, path, ref).map { files ->
            files.joinToString("\n") { f ->
                val icon = when (f.type) {
                    "dir" -> "📁"
                    "file" -> "📄"
                    "symlink" -> "🔗"
                    "submodule" -> "📦"
                    else -> " "
                }
                "$icon ${f.name} (${f.size} bytes)"
            }
        }
    }

    fun getFileContent(owner: String, repo: String, path: String, ref: String? = null): Result<GitHubFile> {
        val baseEndpoint = "repos/$owner/$repo/contents/$path"
        val endpoint = if (ref != null) "$baseEndpoint?ref=$ref" else baseEndpoint
        val request = buildRequest(endpoint)
        return execute(request).map { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            GitHubFile(
                name = obj.get("name")?.asString ?: "",
                path = obj.get("path")?.asString ?: "",
                type = obj.get("type")?.asString ?: "file",
                size = obj.get("size")?.asLong ?: 0L,
                downloadUrl = obj.get("download_url")?.asString,
                sha = obj.get("sha")?.asString ?: ""
            )
        }
    }

    fun getFileTextContent(owner: String, repo: String, path: String, ref: String? = null): Result<String> {
        // Strategy 1: Fetch raw content directly with authentication (works for public and private repos)
        val baseEndpoint = "repos/$owner/$repo/contents/$path"
        val endpoint = if (ref != null) "$baseEndpoint?ref=$ref" else baseEndpoint
        val rawReq = buildRequest(endpoint, "GET", null, acceptHeader = "application/vnd.github.v3.raw")
        try {
            val resp = client.newCall(rawReq).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                return Result.success(body)
            }
        } catch (_: Exception) {}

        // Strategy 2: Fetch JSON metadata and decode base64 content
        val jsonReq = buildRequest(endpoint)
        return execute(jsonReq).mapCatching { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            val encoding = obj.get("encoding")?.asString
            val content = obj.get("content")?.asString
            if (encoding == "base64" && content != null) {
                val clean = content.replace("\n", "").replace("\r", "").trim()
                String(android.util.Base64.decode(clean, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } else {
                val downloadUrl = obj.get("download_url")?.asString
                if (!downloadUrl.isNullOrEmpty()) {
                    val dReq = Request.Builder()
                        .url(downloadUrl)
                        .header("Authorization", "Bearer $token")
                        .header("User-Agent", "DeepCode-Android")
                        .get()
                        .build()
                    val dResp = client.newCall(dReq).execute()
                    dResp.body?.string() ?: ""
                } else {
                    body
                }
            }
        }
    }

    fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String? = null,
        branch: String? = "main"
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("message", message)
            addProperty("content", android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            if (sha != null) addProperty("sha", sha)
            if (branch != null) addProperty("branch", branch)
        }
        val request = buildRequest("repos/$owner/$repo/contents/$path", "PUT", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("content")?.asJsonObject?.get("html_url")?.asString ?: "File updated successfully"
        }
    }

    fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        message: String,
        sha: String,
        branch: String? = "main"
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("message", message)
            addProperty("sha", sha)
            if (branch != null) addProperty("branch", branch)
        }
        val request = buildRequest("repos/$owner/$repo/contents/$path", "DELETE", bodyJson.toString())
        return execute(request).map { "File deleted: $path" }
    }

    // ── Branches & Commits ──

    fun listBranches(owner: String, repo: String): Result<List<GitHubBranch>> {
        val request = buildRequest("repos/$owner/$repo/branches?per_page=50")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
                val obj = el.asJsonObject
                GitHubBranch(
                    name = obj.get("name")?.asString ?: "",
                    sha = obj.get("commit")?.asJsonObject?.get("sha")?.asString ?: ""
                )
            }
        }
    }

    fun createBranch(owner: String, repo: String, branchName: String, sourceBranch: String = "main"): Result<String> {
        val branchesResult = listBranches(owner, repo)
        return branchesResult.fold(
            onSuccess = { branches ->
                val source = branches.find { it.name == sourceBranch }
                    ?: return Result.failure(Exception("Source branch '$sourceBranch' not found"))
                val bodyJson = JsonObject().apply {
                    addProperty("ref", "refs/heads/$branchName")
                    addProperty("sha", source.sha)
                }
                val request = buildRequest("repos/$owner/$repo/git/refs", "POST", bodyJson.toString())
                execute(request).map { "Branch '$branchName' created from '$sourceBranch'" }
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun listCommits(
        owner: String,
        repo: String,
        sha: String? = null,
        path: String? = null,
        perPage: Int = 15
    ): Result<String> {
        val q = StringBuilder("repos/$owner/$repo/commits?per_page=$perPage")
        if (!sha.isNullOrBlank()) q.append("&sha=${sha.trim()}")
        if (!path.isNullOrBlank()) q.append("&path=${path.trim()}")
        val request = buildRequest(q.toString())
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            if (arr.size() == 0) return@map "No commits found in $owner/$repo."
            buildString {
                appendLine("Commit history for $owner/$repo (showing ${arr.size()}):")
                arr.forEach { el ->
                    val obj = el.asJsonObject
                    val commitSha = jsonStr(obj.get("sha")).take(7)
                    val commitObj = obj.getAsJsonObject("commit")
                    val message = jsonStr(commitObj?.get("message")).lines().firstOrNull() ?: ""
                    val authorName = jsonStr(commitObj?.getAsJsonObject("author")?.get("name"))
                    val date = jsonStr(commitObj?.getAsJsonObject("author")?.get("date")).take(10)
                    appendLine("• `$commitSha` - $message ($authorName, $date)")
                }
            }.trimEnd()
        }
    }

    fun getCommit(owner: String, repo: String, ref: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo/commits/$ref")
        return execute(request).map { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            val sha = jsonStr(obj.get("sha"))
            val commitObj = obj.getAsJsonObject("commit")
            val message = jsonStr(commitObj?.get("message"))
            val authorObj = commitObj?.getAsJsonObject("author")
            val author = "${jsonStr(authorObj?.get("name"))} <${jsonStr(authorObj?.get("email"))}>"
            val date = jsonStr(authorObj?.get("date"))
            val stats = obj.getAsJsonObject("stats")
            val total = jsonInt(stats?.get("total"))
            val add = jsonInt(stats?.get("additions"))
            val del = jsonInt(stats?.get("deletions"))
            val filesArr = obj.getAsJsonArray("files") ?: JsonArray()

            buildString {
                appendLine("📜 **Commit:** `$sha`")
                appendLine("👤 Author: $author on $date")
                appendLine("📊 Stats: +$add / -$del (total changes: $total)")
                appendLine("📝 Message:\n$message\n")
                appendLine("📁 Files changed (${filesArr.size()}):")
                filesArr.take(20).forEach { fileEl ->
                    val fileObj = fileEl.asJsonObject
                    val filename = jsonStr(fileObj.get("filename"))
                    val status = jsonStr(fileObj.get("status"))
                    val fAdd = jsonInt(fileObj.get("additions"))
                    val fDel = jsonInt(fileObj.get("deletions"))
                    appendLine("- `$filename` [$status] (+$fAdd / -$fDel)")
                }
            }.trimEnd()
        }
    }

    // ── Pull Requests ──

    fun listPullRequests(owner: String, repo: String, state: String = "open", perPage: Int = 20): Result<List<GitHubPullRequest>> {
        val request = buildRequest("repos/$owner/$repo/pulls?state=$state&per_page=$perPage")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
                val obj = el.asJsonObject
                GitHubPullRequest(
                    number = obj.get("number")?.asInt ?: 0,
                    title = obj.get("title")?.asString ?: "",
                    body = obj.get("body")?.asString ?: "",
                    state = obj.get("state")?.asString ?: "",
                    url = obj.get("html_url")?.asString ?: ""
                )
            }
        }
    }

    fun getPullRequest(owner: String, repo: String, number: Int): Result<String> {
        val request = buildRequest("repos/$owner/$repo/pulls/$number")
        return execute(request).map { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            val title = jsonStr(obj.get("title"))
            val prBody = jsonStr(obj.get("body")).ifEmpty { "No description" }
            val state = jsonStr(obj.get("state"))
            val mergeable = obj.get("mergeable")?.asBoolean ?: false
            val headRef = jsonStr(obj.getAsJsonObject("head")?.get("ref"))
            val baseRef = jsonStr(obj.getAsJsonObject("base")?.get("ref"))
            val author = jsonStr(obj.getAsJsonObject("user")?.get("login"))
            val add = jsonInt(obj.get("additions"))
            val del = jsonInt(obj.get("deletions"))
            val changedFiles = jsonInt(obj.get("changed_files"))
            val url = jsonStr(obj.get("html_url"))

            buildString {
                appendLine("🔀 **PR #$number: $title**")
                appendLine("📌 State: `$state` | Mergeable: $mergeable | Author: @$author")
                appendLine("🌿 Branch: `$headRef` ➔ `$baseRef`")
                appendLine("📊 Changes: +$add / -$del across $changedFiles files")
                appendLine("🔗 URL: $url\n")
                appendLine("📝 Description:\n$prBody")
            }.trimEnd()
        }
    }

    fun getPullRequestDiff(owner: String, repo: String, number: Int): Result<String> {
        val request = buildRequest("repos/$owner/$repo/pulls/$number", "GET", null, acceptHeader = "application/vnd.github.v3.diff")
        return execute(request)
    }

    fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String = "",
        draft: Boolean = false
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("title", title)
            addProperty("head", head)
            addProperty("base", base)
            addProperty("body", body)
            addProperty("draft", draft)
        }
        val request = buildRequest("repos/$owner/$repo/pulls", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("html_url")?.asString ?: "PR created"
        }
    }

    fun updatePullRequest(
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        body: String? = null,
        state: String? = null,
        base: String? = null
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            if (title != null) addProperty("title", title)
            if (body != null) addProperty("body", body)
            if (state != null) addProperty("state", state)
            if (base != null) addProperty("base", base)
        }
        val request = buildRequest("repos/$owner/$repo/pulls/$number", "PATCH", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            "PR #$number updated: ${jsonStr(json.get("html_url"))}"
        }
    }

    fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: String = "merge"
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            if (commitTitle != null) addProperty("commit_title", commitTitle)
            if (commitMessage != null) addProperty("commit_message", commitMessage)
            addProperty("merge_method", mergeMethod) // "merge", "squash", or "rebase"
        }
        val request = buildRequest("repos/$owner/$repo/pulls/$number/merge", "PUT", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("message")?.asString ?: "PR #$number merged"
        }
    }

    // ── Issues & Comments ──

    fun listIssues(owner: String, repo: String, state: String = "open", perPage: Int = 20): Result<List<GitHubIssue>> {
        val request = buildRequest("repos/$owner/$repo/issues?state=$state&per_page=$perPage")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.filter { !it.asJsonObject.has("pull_request") }.map { el ->
                val obj = el.asJsonObject
                GitHubIssue(
                    number = obj.get("number")?.asInt ?: 0,
                    title = obj.get("title")?.asString ?: "",
                    body = obj.get("body")?.asString ?: "",
                    state = obj.get("state")?.asString ?: "",
                    url = obj.get("html_url")?.asString ?: ""
                )
            }
        }
    }

    fun getIssue(owner: String, repo: String, number: Int): Result<String> {
        val request = buildRequest("repos/$owner/$repo/issues/$number")
        return execute(request).map { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            val title = jsonStr(obj.get("title"))
            val issueBody = jsonStr(obj.get("body")).ifEmpty { "No description" }
            val state = jsonStr(obj.get("state"))
            val author = jsonStr(obj.getAsJsonObject("user")?.get("login"))
            val comments = jsonInt(obj.get("comments"))
            val labelsArr = obj.getAsJsonArray("labels") ?: JsonArray()
            val labels = labelsArr.map { jsonStr(it.asJsonObject.get("name")) }.joinToString(", ")
            val url = jsonStr(obj.get("html_url"))

            buildString {
                appendLine("❗ **Issue #$number: $title**")
                appendLine("📌 State: `$state` | Author: @$author | Comments: $comments")
                if (labels.isNotEmpty()) appendLine("🏷️ Labels: $labels")
                appendLine("🔗 URL: $url\n")
                appendLine("📝 Body:\n$issueBody")
            }.trimEnd()
        }
    }

    fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String = "",
        labels: List<String>? = null,
        assignees: List<String>? = null
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("title", title)
            addProperty("body", body)
            if (!labels.isNullOrEmpty()) {
                val arr = JsonArray()
                labels.forEach { arr.add(it) }
                add("labels", arr)
            }
            if (!assignees.isNullOrEmpty()) {
                val arr = JsonArray()
                assignees.forEach { arr.add(it) }
                add("assignees", arr)
            }
        }
        val request = buildRequest("repos/$owner/$repo/issues", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("html_url")?.asString ?: "Issue created"
        }
    }

    fun updateIssue(
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        body: String? = null,
        state: String? = null,
        labels: List<String>? = null
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            if (title != null) addProperty("title", title)
            if (body != null) addProperty("body", body)
            if (state != null) addProperty("state", state)
            if (labels != null) {
                val arr = JsonArray()
                labels.forEach { arr.add(it) }
                add("labels", arr)
            }
        }
        val request = buildRequest("repos/$owner/$repo/issues/$number", "PATCH", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            "Issue #$number updated: ${jsonStr(json.get("html_url"))}"
        }
    }

    fun listIssueComments(owner: String, repo: String, issueNumber: Int, perPage: Int = 20): Result<String> {
        val request = buildRequest("repos/$owner/$repo/issues/$issueNumber/comments?per_page=$perPage")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            if (arr.size() == 0) return@map "No comments found on #$issueNumber."
            buildString {
                appendLine("💬 Comments on $owner/$repo#$issueNumber (${arr.size()}):")
                arr.forEach { el ->
                    val obj = el.asJsonObject
                    val author = jsonStr(obj.getAsJsonObject("user")?.get("login"))
                    val date = jsonStr(obj.get("created_at")).take(10)
                    val commentBody = jsonStr(obj.get("body")).trim()
                    appendLine("---")
                    appendLine("👤 **@$author** ($date):")
                    appendLine(commentBody)
                }
            }.trimEnd()
        }
    }

    fun createIssueComment(owner: String, repo: String, issueNumber: Int, body: String): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("body", body)
        }
        val request = buildRequest("repos/$owner/$repo/issues/$issueNumber/comments", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            "Comment added: ${jsonStr(json.get("html_url"))}"
        }
    }

    // ── Releases ──

    fun listReleases(owner: String, repo: String, perPage: Int = 10): Result<String> {
        val request = buildRequest("repos/$owner/$repo/releases?per_page=$perPage")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            if (arr.size() == 0) return@map "No releases found for $owner/$repo."
            buildString {
                appendLine("🏷️ Releases for $owner/$repo:")
                arr.forEach { el ->
                    val obj = el.asJsonObject
                    val tag = jsonStr(obj.get("tag_name"))
                    val name = jsonStr(obj.get("name")).ifEmpty { tag }
                    val prerelease = obj.get("prerelease")?.asBoolean ?: false
                    val draft = obj.get("draft")?.asBoolean ?: false
                    val publishedAt = jsonStr(obj.get("published_at")).take(10)
                    val url = jsonStr(obj.get("html_url"))
                    appendLine("• **$name** (`$tag`)${if (prerelease) " [Pre-release]" else ""}${if (draft) " [Draft]" else ""} — $publishedAt")
                    appendLine("  $url")
                }
            }.trimEnd()
        }
    }

    fun getLatestRelease(owner: String, repo: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo/releases/latest")
        return execute(request).map { body ->
            val obj = gson.fromJson(body, JsonObject::class.java)
            val tag = jsonStr(obj.get("tag_name"))
            val name = jsonStr(obj.get("name")).ifEmpty { tag }
            val releaseBody = jsonStr(obj.get("body"))
            val url = jsonStr(obj.get("html_url"))
            val assetsArr = obj.getAsJsonArray("assets") ?: JsonArray()

            buildString {
                appendLine("🚀 **Latest Release: $name** (`$tag`)")
                appendLine("🔗 $url")
                if (assetsArr.size() > 0) {
                    appendLine("📦 Assets:")
                    assetsArr.forEach { a ->
                        val aObj = a.asJsonObject
                        val aName = jsonStr(aObj.get("name"))
                        val aSize = jsonInt(aObj.get("size"))
                        val dUrl = jsonStr(aObj.get("browser_download_url"))
                        appendLine("- $aName ($aSize bytes): $dUrl")
                    }
                }
                if (releaseBody.isNotEmpty()) {
                    appendLine("\n📝 Release Notes:\n$releaseBody")
                }
            }.trimEnd()
        }
    }

    fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String = tagName,
        body: String = "",
        targetCommitish: String = "main",
        draft: Boolean = false,
        prerelease: Boolean = false
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("tag_name", tagName)
            addProperty("target_commitish", targetCommitish)
            addProperty("name", name)
            addProperty("body", body)
            addProperty("draft", draft)
            addProperty("prerelease", prerelease)
        }
        val request = buildRequest("repos/$owner/$repo/releases", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            "Release created: ${jsonStr(json.get("html_url"))}"
        }
    }

    // ── Workflows & Actions ──

    fun listWorkflows(owner: String, repo: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo/actions/workflows")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val arr = json.getAsJsonArray("workflows") ?: JsonArray()
            if (arr.size() == 0) return@map "No GitHub Actions workflows found in $owner/$repo."
            buildString {
                appendLine("⚙️ Workflows for $owner/$repo (${arr.size()}):")
                arr.forEach { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.asLong ?: 0
                    val name = jsonStr(obj.get("name"))
                    val state = jsonStr(obj.get("state"))
                    val path = jsonStr(obj.get("path"))
                    appendLine("• **$name** (ID: `$id`, state: `$state`, path: `$path`)")
                }
            }.trimEnd()
        }
    }

    fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowIdOrFilename: String,
        ref: String = "main",
        inputs: Map<String, Any>? = null
    ): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("ref", ref)
            if (!inputs.isNullOrEmpty()) {
                val inpObj = JsonObject()
                inputs.forEach { (k, v) -> inpObj.addProperty(k, v.toString()) }
                add("inputs", inpObj)
            }
        }
        val request = buildRequest("repos/$owner/$repo/actions/workflows/$workflowIdOrFilename/dispatches", "POST", bodyJson.toString())
        return execute(request).map {
            "Workflow '$workflowIdOrFilename' dispatched successfully on branch '$ref'."
        }
    }

    fun listWorkflowRuns(owner: String, repo: String, branch: String = "main", perPage: Int = 5): Result<String> {
        val endpoint = if (branch.isNotBlank()) {
            "repos/$owner/$repo/actions/runs?branch=$branch&per_page=$perPage"
        } else {
            "repos/$owner/$repo/actions/runs?per_page=$perPage"
        }
        val request = buildRequest(endpoint)
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val runs = json.getAsJsonArray("workflow_runs") ?: JsonArray()
            if (runs.size() == 0) return@map "No workflow runs found."
            runs.map { run ->
                val obj = run.asJsonObject
                val id = obj.get("id")?.asLong ?: 0
                val status = obj.get("status")?.asString ?: "unknown"
                val conclusion = obj.get("conclusion")?.asString ?: "in_progress"
                val name = obj.get("name")?.asString ?: "workflow"
                val createdAt = obj.get("created_at")?.asString ?: ""
                "Run #$id: $name — $status ($conclusion) at $createdAt"
            }.joinToString("\n")
        }
    }

    fun checkWorkflowStatus(owner: String, repo: String, runId: Long): Result<String> {
        val request = buildRequest("repos/$owner/$repo/actions/runs/$runId")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString ?: "unknown"
            val conclusion = json.get("conclusion")?.asString ?: "in_progress"
            val url = jsonStr(json.get("html_url"))
            "Workflow run #$runId: status=$status, conclusion=$conclusion\nURL: $url"
        }
    }

    fun getLatestArtifact(owner: String, repo: String): Result<JsonObject> {
        val request = buildRequest("repos/$owner/$repo/actions/artifacts?per_page=1")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val artifacts = json.getAsJsonArray("artifacts") ?: JsonArray()
            if (artifacts.size() == 0) throw Exception("No artifacts found.")
            artifacts.get(0).asJsonObject
        }
    }

    fun downloadArtifact(owner: String, repo: String, artifactId: Long, savePath: String): Result<String> {
        val request = buildRequest("repos/$owner/$repo/actions/artifacts/$artifactId/zip")
        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                return Result.failure(Exception("GitHub API error (HTTP ${response.code}): $body"))
            }
            val bytes = response.body?.bytes() ?: return Result.failure(Exception("Empty artifact response"))
            val file = java.io.File(savePath)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            Result.success("Artifact downloaded to: ${file.absolutePath} (${bytes.size} bytes)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadLatestArtifact(owner: String, repo: String, savePath: String): Result<String> {
        return getLatestArtifact(owner, repo).fold(
            onSuccess = { artifact ->
                val id = artifact.get("id")?.asLong ?: return Result.failure(Exception("Missing artifact ID"))
                val name = artifact.get("name")?.asString ?: "artifact"
                val apkDir = java.io.File(savePath).parentFile ?: java.io.File(".")
                val finalPath = java.io.File(apkDir, "$name.zip").absolutePath
                downloadArtifact(owner, repo, id, finalPath)
            },
            onFailure = { Result.failure(it) }
        )
    }

    // ── Gists ──

    fun listGists(perPage: Int = 20): Result<String> {
        val request = buildRequest("gists?per_page=$perPage")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            if (arr.size() == 0) return@map "No gists found."
            buildString {
                appendLine("📄 Your GitHub Gists (${arr.size()}):")
                arr.forEach { el ->
                    val obj = el.asJsonObject
                    val id = jsonStr(obj.get("id"))
                    val desc = jsonStr(obj.get("description")).ifEmpty { "No description" }
                    val isPublic = obj.get("public")?.asBoolean ?: false
                    val url = jsonStr(obj.get("html_url"))
                    val files = obj.getAsJsonObject("files")?.keySet()?.joinToString(", ") ?: ""
                    appendLine("• `$id`: $desc [${if (isPublic) "Public" else "Secret"}] ($files)\n  $url")
                }
            }.trimEnd()
        }
    }

    fun createGist(description: String, files: Map<String, String>, isPublic: Boolean = false): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("description", description)
            addProperty("public", isPublic)
            val filesObj = JsonObject()
            files.forEach { (fname, fcontent) ->
                val singleFile = JsonObject().apply { addProperty("content", fcontent) }
                filesObj.add(fname, singleFile)
            }
            add("files", filesObj)
        }
        val request = buildRequest("gists", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            "Gist created: ${jsonStr(json.get("html_url"))}"
        }
    }

    // ── Search ──

    fun searchCode(query: String, perPage: Int = 10): Result<String> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = buildRequest("search/code?q=$encoded&per_page=$perPage")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val total = json.get("total_count")?.asInt ?: 0
            val items = json.getAsJsonArray("items") ?: JsonArray()
            val results = items.map { item ->
                val obj = item.asJsonObject
                val repo = obj.get("repository")?.asJsonObject
                "${repo?.get("full_name")?.asString ?: "?"}: ${obj.get("path")?.asString ?: "?"} - ${obj.get("html_url")?.asString ?: ""}"
            }
            buildString {
                appendLine("Found $total results (showing ${results.size}):")
                results.forEach { appendLine(it) }
            }.trimEnd()
        }
    }

    fun searchRepositories(query: String, perPage: Int = 10): Result<List<GitHubRepo>> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = buildRequest("search/repositories?q=$encoded&per_page=$perPage&sort=stars")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val items = json.getAsJsonArray("items") ?: JsonArray()
            items.map { el ->
                val obj = el.asJsonObject
                GitHubRepo(
                    name = obj.get("name")?.asString ?: "",
                    fullName = obj.get("full_name")?.asString ?: "",
                    description = obj.get("description")?.asString ?: "",
                    url = obj.get("html_url")?.asString ?: "",
                    defaultBranch = obj.get("default_branch")?.asString ?: "main",
                    private = obj.get("private")?.asBoolean ?: false,
                    fork = obj.get("fork")?.asBoolean ?: false
                )
            }
        }
    }

    fun searchRepositoriesFormatted(query: String, perPage: Int = 10): Result<String> {
        return searchRepositories(query, perPage).map { repos ->
            if (repos.isEmpty()) return@map "No repositories found matching \"$query\"."
            buildString {
                appendLine("🔍 Repositories matching \"$query\":")
                repos.forEach { r ->
                    appendLine("• **${r.fullName}** (${r.defaultBranch}) — ${r.description}")
                    appendLine("  ${r.url}")
                }
            }.trimEnd()
        }
    }

    fun searchIssues(query: String, perPage: Int = 15): Result<String> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = buildRequest("search/issues?q=$encoded&per_page=$perPage")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val total = jsonInt(json.get("total_count"))
            val items = json.getAsJsonArray("items") ?: JsonArray()
            if (items.size() == 0) return@map "No issues or pull requests found for \"$query\"."
            buildString {
                appendLine("Found $total issues/PRs (showing ${items.size()}):")
                items.forEach { item ->
                    val obj = item.asJsonObject
                    val number = jsonInt(obj.get("number"))
                    val title = jsonStr(obj.get("title"))
                    val state = jsonStr(obj.get("state"))
                    val htmlUrl = jsonStr(obj.get("html_url"))
                    val isPr = obj.has("pull_request")
                    val type = if (isPr) "PR" else "Issue"
                    appendLine("• $type #$number [$state]: $title\n  $htmlUrl")
                }
            }.trimEnd()
        }
    }
}
