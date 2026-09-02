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
    val avatarUrl: String
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

    private fun buildRequest(endpoint: String, method: String = "GET", body: String? = null): Request {
        val builder = Request.Builder()
            .url("$baseUrl${endpoint.trimStart('/')}")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
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

    fun validateToken(): Result<GitHubUser> {
        val request = buildRequest("user")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            GitHubUser(
                login = jsonStr(json.get("login")),
                name = jsonStr(json.get("name")).ifEmpty { jsonStr(json.get("login")) },
                email = jsonStr(json.get("email")),
                avatarUrl = jsonStr(json.get("avatar_url"))
            )
        }
    }

    private fun jsonStr(el: com.google.gson.JsonElement?): String {
        return if (el != null && !el.isJsonNull) el.asString else ""
    }

    fun listRepos(type: String = "all", perPage: Int = 50): Result<List<GitHubRepo>> {
        val request = buildRequest("user/repos?type=$type&per_page=$perPage&sort=updated")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
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

    fun getRepoContents(owner: String, repo: String, path: String = ""): Result<List<GitHubFile>> {
        val endpoint = if (path.isEmpty()) "repos/$owner/$repo/contents" else "repos/$owner/$repo/contents/$path"
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

    fun getFileContent(owner: String, repo: String, path: String): Result<GitHubFile> {
        val request = buildRequest("repos/$owner/$repo/contents/$path")
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

    fun getFileTextContent(owner: String, repo: String, path: String): Result<String> {
        val fileResult = getFileContent(owner, repo, path)
        return fileResult.fold(
            onSuccess = { file ->
                if (file.downloadUrl != null) {
                    try {
                        val req = Request.Builder().url(file.downloadUrl).header("User-Agent", "DeepCode-Android").get().build()
                        val resp = client.newCall(req).execute()
                        Result.success(resp.body?.string() ?: "")
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                } else {
                    Result.failure(Exception("Cannot retrieve file content (no download URL)"))
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    fun createOrUpdateFile(owner: String, repo: String, path: String, content: String, message: String, sha: String? = null, branch: String? = "main"): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("message", message)
            addProperty("content", android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP))
            if (sha != null) addProperty("sha", sha)
            if (branch != null) addProperty("branch", branch)
        }
        val request = buildRequest("repos/$owner/$repo/contents/$path", "PUT", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("content")?.asJsonObject?.get("html_url")?.asString ?: "File updated"
        }
    }

    fun deleteFile(owner: String, repo: String, path: String, message: String, sha: String): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("message", message)
            addProperty("sha", sha)
        }
        val request = buildRequest("repos/$owner/$repo/contents/$path", "DELETE", bodyJson.toString())
        return execute(request).map { "File deleted: $path" }
    }

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

    fun listPullRequests(owner: String, repo: String, state: String = "open"): Result<List<GitHubPullRequest>> {
        val request = buildRequest("repos/$owner/$repo/pulls?state=$state&per_page=20")
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

    fun createPullRequest(owner: String, repo: String, title: String, head: String, base: String, body: String = ""): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("title", title)
            addProperty("head", head)
            addProperty("base", base)
            addProperty("body", body)
        }
        val request = buildRequest("repos/$owner/$repo/pulls", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("html_url")?.asString ?: "PR created"
        }
    }

    fun mergePullRequest(owner: String, repo: String, number: Int): Result<String> {
        val request = buildRequest("repos/$owner/$repo/pulls/$number/merge", "PUT")
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("message")?.asString ?: "PR #$number merged"
        }
    }

    fun listIssues(owner: String, repo: String, state: String = "open"): Result<List<GitHubIssue>> {
        val request = buildRequest("repos/$owner/$repo/issues?state=$state&per_page=20")
        return execute(request).map { body ->
            val arr = gson.fromJson(body, JsonArray::class.java)
            arr.map { el ->
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

    fun createIssue(owner: String, repo: String, title: String, body: String = ""): Result<String> {
        val bodyJson = JsonObject().apply {
            addProperty("title", title)
            addProperty("body", body)
        }
        val request = buildRequest("repos/$owner/$repo/issues", "POST", bodyJson.toString())
        return execute(request).map { resp ->
            val json = gson.fromJson(resp, JsonObject::class.java)
            json.get("html_url")?.asString ?: "Issue created"
        }
    }

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

    fun listRepoContents(owner: String, repo: String, path: String = ""): Result<String> {
        return getRepoContents(owner, repo, path).map { files ->
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
        return execute(request).map { "Repository '$owner/$repo' deleted." }
    }

    fun listWorkflowRuns(owner: String, repo: String, branch: String = "main", perPage: Int = 5): Result<String> {
        val request = buildRequest("repos/$owner/$repo/actions/runs?branch=$branch&per_page=$perPage&status=completed")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val runs = json.getAsJsonArray("workflow_runs") ?: JsonArray()
            if (runs.size() == 0) return@map "No completed workflow runs found."
            runs.map { run ->
                val obj = run.asJsonObject
                val id = obj.get("id")?.asLong ?: 0
                val status = obj.get("status")?.asString ?: "unknown"
                val conclusion = obj.get("conclusion")?.asString ?: "unknown"
                val name = obj.get("name")?.asString ?: "workflow"
                val createdAt = obj.get("created_at")?.asString ?: ""
                "Run #$id: $name — $conclusion ($createdAt)"
            }.joinToString("\n")
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

    fun checkWorkflowStatus(owner: String, repo: String, runId: Long): Result<String> {
        val request = buildRequest("repos/$owner/$repo/actions/runs/$runId")
        return execute(request).map { body ->
            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString ?: "unknown"
            val conclusion = json.get("conclusion")?.asString ?: "unknown"
            "Workflow run #$runId: status=$status, conclusion=$conclusion"
        }
    }
}
