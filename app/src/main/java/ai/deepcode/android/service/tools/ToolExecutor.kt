package ai.deepcode.android.service.tools

import android.content.Context
import ai.deepcode.android.service.storage.TelegramDriveService
import ai.deepcode.android.service.notion.NotionService
import ai.deepcode.android.service.github.GitHubService
import ai.deepcode.android.service.drive.DriveHandler
import com.google.gson.Gson
import com.google.gson.JsonObject
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.service.TerminalRunner
import ai.deepcode.android.data.remote.WebBridgeAPI
import ai.deepcode.android.data.remote.WebBridgeTask
import ai.deepcode.android.data.remote.WebBridgeAttachment
import ai.deepcode.android.data.remote.WebBridgeConstraints
import ai.deepcode.android.data.remote.WebBridgeResult
import ai.deepcode.android.data.remote.WebBridgeOutputResult
import ai.deepcode.android.data.remote.WebBridgeUserAction
import ai.deepcode.android.data.remote.WebBridgeDiagnostics
import ai.deepcode.android.data.remote.WebBridgeFailoverContext
import ai.deepcode.android.data.remote.WebBridgeChatTurn
import ai.deepcode.android.data.remote.WebBridgeFailoverMetadata
import ai.deepcode.android.data.remote.WebBridgeRecoveryHint
import ai.deepcode.android.data.remote.WebBridgeOutputContent
import ai.deepcode.android.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

data class ReferenceLayoutInfo(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String  // markdown instruction manual for the AI
)

class ToolExecutor(private val context: Context? = null) {
    private val gson = Gson()
    private var orchestratorEngine: ai.deepcode.android.orchestrator.OrchestratorEngine? = null

    companion object {
        private var _cachedReferenceLayouts: List<ReferenceLayoutInfo>? = null

        fun getReferenceLayouts(): List<ReferenceLayoutInfo> {
            _cachedReferenceLayouts?.let { return it }
            val layouts = listOf(
                ReferenceLayoutInfo("field-notes", "Field Notes",
                    "Spiral notebook / logbook style — ideal for study notes, lab reports, and rough drafts.",
                    """## Field Notes — Layout Instructions

**Style:** Spiral notebook / logbook. Warm informal feel with hand-drawn accents.

**Layout to use:** classic (or custom if available).

**Formatting rules:**
- Use `---` horizontal rules to separate sections.
- Section headings: **bold, uppercase** (e.g. **OBSERVATIONS**, **METRICS**, **NOTES**).
- Body text: plain paragraphs. Use `>` blockquote for key highlights.
- Tables: bordered pipe tables with a header row and aligned columns.
- Footer: include a "Field Notes · [title]" line at the very end.

**Color scheme:** Warm earth tones — accent = #C1622D (burnt orange), primary text = #1A1A1A.
"""             ),
                ReferenceLayoutInfo("editorial-journal", "Editorial Journal",
                    "Scholarly magazine / journal format — for articles, essays, and long-form content.",
                    """## Editorial Journal — Layout Instructions

**Style:** Scholarly magazine / journal. Two-column text with pull quotes and bylines.

**Layout to use:** academic-paper (enables two-column body).

**Formatting rules:**
- Title: a bold single-word category label (e.g. **CULTURE**) in uppercase above the main title.
- Main title: large, centered, with line breaks for visual impact.
- Byline: italic, right-aligned below the title (e.g. *By the Editorial Desk · July 2026*).
- Body: justified prose. First letter of first paragraph should be a **drop cap** (large, bold).
- Blockquote: center-aligned italic pull quote, 1–2 sentences, between body paragraphs and separated by blank lines.
- At the end of the article, add a horizontal rule `---` and the line `*Editorial Journal template*`.

**Color scheme:** Black on white. Accent = #000000 (pure black). Minimal color.
"""             ),
                ReferenceLayoutInfo("executive-briefing", "Executive Briefing",
                    "Slide-deck / dashboard style — for business reports, status updates, and proposals.",
                    """## Executive Briefing — Layout Instructions

**Style:** Slide-deck / executive dashboard. Clean, sparse, data-driven.

**Layout to use:** corporate-report.

**Formatting rules:**
- At the top: a bold document title, followed by a metadata line: `PREPARED FOR: ... | DATE: ... | OWNER: ...`.
- Below that: **4 KPI cards** as a pipe table with a single row and 4 columns. Each cell contains a large number and a label underneath. Example: `| 92% | 14 | 3 | $2.1M |` with row below `| Readiness Score | Days to Launch | Open Blockers | Projected Revenue |`.
- Then an `## Executive Summary` heading with 2–3 concise paragraphs.
- Then `## Key Risks` as a numbered list of risks.
- A **Recommendation box**: use a blockquote `>` with bold text for the recommendation statement.
- End with an accent line `---`.

**Color scheme:** Navy blue accent (#1B365D), professional serif headings.
"""             ),
                ReferenceLayoutInfo("blueprint", "Blueprint",
                    "Technical / engineering blueprint style — for specifications, architecture docs, and system designs.",
                    """## Blueprint — Layout Instructions

**Style:** Technical blueprint / engineering spec. Monospace headings, structured sections.

**Layout to use:** classic with custom tweaks.

**Formatting rules:**
- A spec number banner at the top right: `SPEC-XXXX-XXXX` in monospace.
- Below that a draft indicator: `· DRAFT v0.X` in monospace, centered.
- Sections are numbered boxes connected by arrows. Format as markdown headings with numbers: `## 01 API Gateway`.
- Use pipe tables for parameter/config data. Table must have two columns: PARAMETER | VALUE.
- Use - bullet lists for endpoint listings. Each endpoint starts with HTTP method + path: `GET /v2/health`.
- Sub-sections like AUTH, RATE LIMIT, ROUTING should be bold uppercase.
- End with a `---` and `Blueprint template` line.

**Color scheme:** Dark monochrome. Primary = #2D2D2D, accent = #9E9E9E (steel grey). Use monospace font for code-like elements.
"""             ),
                ReferenceLayoutInfo("quickstart-guide", "Quickstart Guide",
                    "Step-by-step tutorial / how-to guide format — for documentation and walkthroughs.",
                    """## Quickstart Guide — Layout Instructions

**Style:** Step-by-step tutorial / how-to guide. Numbered steps with code blocks.

**Layout to use:** classic.

**Formatting rules:**
- Banner: a large bold title **QUICKSTART** at the top.
- Subtitle: the guide title + estimated time (e.g. `5 steps · about 10 minutes`).
- Prerequisites line: italic, before step 1. (e.g. `*Before you start — Docker, Node 20+, and a GitHub account.*`)
- Each step: a numbered heading format: `## 1 Step title`. Brief instruction in plain text. Then a code block with the command.
- Steps should number sequentially: 1, 2, 3, 4, 5...
- End with a tip line: `Stuck? See TROUBLESHOOTING.md · Quickstart template`.

**Color scheme:** Clean and light. Accent = #3498DB (blue), body = #333333.
"""             ),
                ReferenceLayoutInfo("poster", "Poster",
                    "Large-format single-page announcement / event poster — for announcements, meetups, and events.",
                    """## Poster — Layout Instructions

**Style:** Event poster / announcement. Bold, sparse, single A4 page.

**Layout to use:** modern-minimal.

**Formatting rules:**
- At the top: small uppercase label line (e.g. `MONTHLY TALK · AUGUST 2026`).
- Main title: very large, bold, centered, with line breaks for dramatic spacing.
- Below the title: a subtitle/description line in smaller text.
- Three detail fields at the bottom, aligned left: `DATE`, `TIME`, `VENUE` each on its own line with the value next to it.
- An RSVP / CTA line at the very bottom.
- Minimal content — this is a single-page announcement.

**Color scheme:** Bold. Dark background feel primary (#1A1A2E), accent = #E94560 (vibrant red).
"""             ),
                ReferenceLayoutInfo("resume", "Resume",
                    "ATS-friendly professional resume / CV format — for job applications.",
                    """## Resume — Layout Instructions

**Style:** Professional resume / CV. Clean, scannable, ATS-friendly.

**Layout to use:** resume-cv.

**Formatting rules:**
- Name at the top: large bold.
- Contact info line below name: email | phone | location.
- Sections with bold headings: `## SUMMARY`, `## SKILLS`, `## EXPERIENCE`, `## EDUCATION`.
- **SUMMARY**: 2-3 line paragraph with impact metrics (%, $ amounts).
- **SKILLS**: bullet list with skills grouped by category.
- **EXPERIENCE**: each entry: job title, company, date range (right-aligned). Then bullet points of achievements with metrics.
- **EDUCATION**: degree, institution, year.

**Color scheme:** Classic navy (#2C3E50) for headings. Clean white background.
"""             ),
                ReferenceLayoutInfo("dashboard", "Dashboard",
                    "Data-heavy analytics dashboard style — for reports, metrics, and KPIs.",
                    """## Dashboard — Layout Instructions

**Style:** Analytics dashboard / report. Data-heavy with KPI cards and chart descriptions.

**Layout to use:** corporate-report.

**Formatting rules:**
- Title line: large bold title + subtitle period (e.g. `June 2026`).
- Subtitle: small italic context line (e.g. `*Weekly product analytics — all platforms*`).
- **4 KPI cards**: single-row pipe table with 4 columns. Each cell: large number on first line, metric label on second line. Example: `| 48.2K | 3.4 | 62% | 6m 12s |` with second row `| Daily Active Users | Avg Sessions/User | 7-Day Retention | Avg Session Time |`.
- **Feature Usage Table**: pipe table with Feature name | percentage. Sorted descending by percentage.
- **Chart placeholder**: a `> blockquote` describing the chart (e.g. `> Daily Active Users — Last 14 Days` with a trend figure).
- End with `---` and `Dashboard template`.

**Color scheme:** Dark corporate. Primary = #1A1A1A, accent = #2E86C1 (blue/teal).
"""             ),
            )
            _cachedReferenceLayouts = layouts
            return layouts
        }
    }

    private fun getOrchestrator(): ai.deepcode.android.orchestrator.OrchestratorEngine {
        if (orchestratorEngine == null) {
            orchestratorEngine = ai.deepcode.android.orchestrator.OrchestratorEngine(context ?: throw IllegalStateException("Context required for orchestration"))
        }
        return orchestratorEngine!!
    }

    private fun runOrchestration(taskDescription: String): String {
        val engine = getOrchestrator()
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val plan = engine.callOrchestrator(taskDescription)
                if (plan == null) return@runBlocking "I analysed this task but couldn't decompose it. I'll handle it directly."
                if (plan.tasks.size < 2) return@runBlocking "This task is simple enough for me to handle directly — no need to summon sub-agents."

                val results = engine.executePlan(plan) { task, status ->
                    AppLogger.i("ToolExecutor", "Sub-agent ${task.id}: $status")
                }
                val aggregated = engine.callAggregator(plan.goal, results, plan.aggregationStrategy)
                "**Multi-Agent Results:**\n\n$aggregated"
            } catch (e: Exception) {
                AppLogger.e("ToolExecutor", "Orchestration failed", e)
                "I tried summoning sub-agents but encountered an error: ${e.message}. I'll handle this directly instead."
            }
        }
    }
    var telegramDrive: TelegramDriveService? = null
    var notionService: NotionService? = null
    var gitHubService: GitHubService? = null
    var driveHandler: DriveHandler? = null

    fun getOrInitGitHubService(): GitHubService? {
        gitHubService?.let { return it }
        val ctx = context ?: return null
        val token = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx).getSetting("github_token", "").trim()
            .ifEmpty {
                try {
                    kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                        ai.deepcode.android.ui.connections.IntegrationRepository(ctx).getIntegrationByAppId("github")?.accessToken?.trim()
                    } ?: ""
                } catch (_: Exception) { "" }
            }
        if (token.isNotEmpty()) {
            val svc = GitHubService(token)
            gitHubService = svc
            return svc
        }
        return null
    }

    private fun optString(a: JsonObject, k: String): String? =
        try { a.get(k)?.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotEmpty() } } catch (_: Exception) { null }

    private fun optInt(a: JsonObject, k: String, default: Int): Int {
        try {
            val el = a.get(k) ?: return default
            if (el.isJsonNull) return default
            if (el.isJsonPrimitive) {
                val p = el.asJsonPrimitive
                return if (p.isNumber) p.asInt else p.asString.toDoubleOrNull()?.toInt() ?: default
            }
            return default
        } catch (_: Exception) {
            return default
        }
    }

    private fun sanitizeFileName(raw: String?, fallback: String): String {
        if (raw.isNullOrBlank()) return fallback
        var n = raw.trim().substringAfterLast("/").substringAfterLast("\\")
        n = n.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_', '.', '-')
        if (n.isBlank()) return fallback
        return n.take(64)
    }

    fun executeTool(name: String, argumentsJson: String, workingDir: String, useRoot: Boolean): String {
        return try {
            val args = try { gson.fromJson(argumentsJson, JsonObject::class.java) ?: JsonObject() } catch (_: Exception) { JsonObject() }
            if (name == "summon_agents") {
                val taskDesc = args.get("task")?.asString ?: args.get("description")?.asString ?: return "Missing task/description argument"
                return runOrchestration(taskDesc)
            }
            when (name) {
                "read_file", "file_read" -> {
                    val path = args.get("path")?.asString ?: return "Missing path argument"
                    readFile(path, workingDir, useRoot)
                }
                "write_file", "file_write", "edit" -> {
                    val path = args.get("path")?.asString ?: return "Missing path argument"
                    val content = args.get("content")?.asString ?: return "Missing content argument"
                    val storage = args.get("storage")?.asString ?: ""
                    writeFile(path, content, workingDir, useRoot, storage)
                }
                "list_directory", "list", "glob" -> {
                    val path = args.get("path")?.asString ?: "."
                    listDirectory(path, workingDir, useRoot)
                }
                "run_command", "shell", "git_operations", "node_exec", "npm_exec", "curl" -> {
                    val command = args.get("command")?.asString ?: return "Missing command argument"
                    TerminalRunner.runCommand(command, workingDir, useRoot)
                }
                "grep_search", "grep" -> {
                    val query = args.get("query")?.asString ?: return "Missing query argument"
                    val path = args.get("path")?.asString ?: "."
                    grepSearch(query, path, workingDir, useRoot)
                }
                "create_file" -> {
                    val path = args.get("path")?.asString ?: return "Missing path argument"
                    val isDir = args.get("isDirectory")?.asBoolean ?: false
                    createFileOrDirectory(path, isDir, workingDir, useRoot)
                }
                "delete_file" -> {
                    val path = args.get("path")?.asString ?: return "Missing path argument"
                    deleteFileOrDirectory(path, workingDir, useRoot)
                }
                "apply_patch" -> {
                    val path = args.get("path")?.asString ?: return "Missing path argument"
                    val content = args.get("content")?.asString ?: args.get("patch")?.asString ?: return "Missing content/patch argument"
                    val storage = args.get("storage")?.asString ?: ""
                    writeFile(path, content, workingDir, useRoot, storage)
                }
                "web_fetch" -> {
                    val url = args.get("url")?.asString ?: return "Missing url argument"
                    webFetch(url)
                }
                "tinyfish_agent" -> {
                    val url = args.get("url")?.asString ?: return "Missing url argument"
                    val goal = args.get("goal")?.asString ?: return "Missing goal argument"
                    runTinyFishAgent(url, goal)
                }
                "web_search" -> {
                    val query = optString(args, "query") ?: return "Missing query argument"
                    val numResults = optInt(args, "numResults", 8).coerceIn(1, 20)
                    val livecrawl = optString(args, "livecrawl") ?: "fallback"
                    val type = optString(args, "type") ?: "auto"
                    val contextMaxCharacters = optInt(args, "contextMaxCharacters", 10000).coerceIn(1000, 20000)
                    webSearch(query, numResults, livecrawl, type, contextMaxCharacters)
                }
                "todowrite" -> {
                    val title = args.get("title")?.asString ?: args.toString()
                    "TODO recorded: $title"
                }
                "plan_exit" -> {
                    "Plan execution completed."
                }
                "notion_search" -> {
                    try {
                        val svc = notionService ?: return "Notion not connected. Connect your Notion integration token first."
                        val query = args.get("query")?.asString ?: ""
                        val result = svc.search(query)
                        result.fold(
                            onSuccess = { pages ->
                                if (pages.isEmpty()) "No Notion pages found."
                                else pages.joinToString("\n") { "- [${it.objectType}] ${it.title} (${it.url})" }
                            },
                            onFailure = { "Notion search failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "notion_search failed: ${e.message}"
                    }
                }
                "notion_read" -> {
                    try {
                        val svc = notionService ?: return "Notion not connected. Connect your Notion integration token first."
                        val rawPageId = optString(args, "page_id") ?: return "Missing page_id argument"
                        val pageId = rawPageId.trim().replace("-", "").take(64)
                        if (pageId.isBlank() || !pageId.all { it.isLetterOrDigit() }) return "Invalid page_id parameter"
                        val result = svc.getPageContent(pageId)
                        result.fold(
                            onSuccess = { it },
                            onFailure = { "Notion read failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "notion_read failed: ${e.message}"
                    }
                }
                "notion_list_databases" -> {
                    try {
                        val svc = notionService ?: return "Notion not connected. Connect your Notion integration token first."
                        val result = svc.listDatabases()
                        result.fold(
                            onSuccess = { dbs ->
                                if (dbs.isEmpty()) "No Notion databases found."
                                else dbs.joinToString("\n") { "- ${it.title} (${it.url})" }
                            },
                            onFailure = { "Notion list databases failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "notion_list_databases failed: ${e.message}"
                    }
                }
                "github_get_user" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected. Connect your GitHub token in the Connections screen first."
                        val username = optString(args, "username")
                        svc.getUser(username).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get user failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_user failed: ${e.message}"
                    }
                }
                "github_list_repos" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected. Connect your GitHub token in the Connections screen first."
                        val type = optString(args, "type") ?: "all"
                        val perPage = optInt(args, "per_page", 50)
                        svc.listRepos(type, perPage).fold(
                            onSuccess = { repos ->
                                if (repos.isEmpty()) "No repositories found."
                                else repos.joinToString("\n") { r ->
                                    "${if (r.private) "🔒" else "🌍"} ${r.fullName} (${r.defaultBranch})${if (r.fork) " [fork]" else ""}${if (r.description.isNotEmpty()) " — ${r.description}" else ""}"
                                }
                            },
                            onFailure = { "GitHub list repos failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_repos failed: ${e.message}"
                    }
                }
                "github_get_repo" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        svc.getRepo(owner, repo).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get repo failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_repo failed: ${e.message}"
                    }
                }
                "github_create_repo" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val name = optString(args, "name")?.trim() ?: return "Missing name"
                        val description = optString(args, "description") ?: ""
                        val isPrivate = try { args.get("private")?.asBoolean } catch (_: Exception) { true } ?: true
                        val autoInit = try { args.get("auto_init")?.asBoolean } catch (_: Exception) { false } ?: false
                        svc.createRepo(name, description, isPrivate, autoInit).fold(
                            onSuccess = { "Repository created successfully: $it" },
                            onFailure = { "GitHub create repo failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_repo failed: ${e.message}"
                    }
                }
                "github_delete_repo" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        svc.deleteRepo(owner, repo).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub delete repo failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_delete_repo failed: ${e.message}"
                    }
                }
                "github_fork_repo" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val org = optString(args, "organization")
                        svc.forkRepo(owner, repo, org).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub fork repo failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_fork_repo failed: ${e.message}"
                    }
                }
                "github_list_contents" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val path = optString(args, "path") ?: ""
                        val ref = optString(args, "ref")
                        svc.listRepoContents(owner, repo, path, ref).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list contents failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_contents failed: ${e.message}"
                    }
                }
                "github_read_file" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val path = optString(args, "path") ?: return "Missing path"
                        val ref = optString(args, "ref")
                        svc.getFileTextContent(owner, repo, path, ref).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub read file failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_read_file failed: ${e.message}"
                    }
                }
                "github_write_file" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val path = optString(args, "path") ?: return "Missing path"
                        val content = optString(args, "content") ?: return "Missing content"
                        val message = optString(args, "message") ?: "Update $path via DeepCode"
                        val sha = optString(args, "sha")
                        val branch = optString(args, "branch") ?: "main"
                        svc.createOrUpdateFile(owner, repo, path, content, message, sha, branch).fold(
                            onSuccess = { "GitHub file written: $it" },
                            onFailure = { "GitHub write file failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_write_file failed: ${e.message}"
                    }
                }
                "github_delete_file" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val path = optString(args, "path") ?: return "Missing path"
                        val sha = optString(args, "sha") ?: return "Missing sha (required for deleting a file)"
                        val message = optString(args, "message") ?: "Delete $path via DeepCode"
                        val branch = optString(args, "branch") ?: "main"
                        svc.deleteFile(owner, repo, path, message, sha, branch).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub delete file failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_delete_file failed: ${e.message}"
                    }
                }
                "github_list_branches" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        svc.listBranches(owner, repo).fold(
                            onSuccess = { branches ->
                                if (branches.isEmpty()) "No branches found."
                                else branches.joinToString("\n") { "- ${it.name} (${it.sha.take(7)})" }
                            },
                            onFailure = { "GitHub list branches failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_branches failed: ${e.message}"
                    }
                }
                "github_create_branch" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val branch = optString(args, "branch")?.trim() ?: return "Missing branch"
                        val source = optString(args, "source") ?: "main"
                        svc.createBranch(owner, repo, branch, source).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub create branch failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_branch failed: ${e.message}"
                    }
                }
                "github_list_commits" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val sha = optString(args, "sha")
                        val path = optString(args, "path")
                        val perPage = optInt(args, "per_page", 15)
                        svc.listCommits(owner, repo, sha, path, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list commits failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_commits failed: ${e.message}"
                    }
                }
                "github_get_commit" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val ref = optString(args, "ref")?.trim() ?: return "Missing ref/sha"
                        svc.getCommit(owner, repo, ref).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get commit failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_commit failed: ${e.message}"
                    }
                }
                "github_list_prs" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val state = optString(args, "state") ?: "open"
                        val perPage = optInt(args, "per_page", 20)
                        svc.listPullRequests(owner, repo, state, perPage).fold(
                            onSuccess = { prs ->
                                if (prs.isEmpty()) "No pull requests found in $owner/$repo ($state)."
                                else prs.joinToString("\n") { "#${it.number} [${it.state}] ${it.title} (${it.url})" }
                            },
                            onFailure = { "GitHub list PRs failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_prs failed: ${e.message}"
                    }
                }
                "github_get_pr" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid PR number"
                        svc.getPullRequest(owner, repo, number).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get PR failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_pr failed: ${e.message}"
                    }
                }
                "github_get_pr_diff" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid PR number"
                        svc.getPullRequestDiff(owner, repo, number).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get PR diff failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_pr_diff failed: ${e.message}"
                    }
                }
                "github_create_pr" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val title = optString(args, "title")?.trim() ?: return "Missing title"
                        val head = optString(args, "head")?.trim() ?: return "Missing head branch"
                        val base = optString(args, "base") ?: "main"
                        val body = optString(args, "body") ?: ""
                        val draft = try { args.get("draft")?.asBoolean } catch (_: Exception) { false } ?: false
                        svc.createPullRequest(owner, repo, title, head, base, body, draft).fold(
                            onSuccess = { "PR created successfully: $it" },
                            onFailure = { "GitHub create PR failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_pr failed: ${e.message}"
                    }
                }
                "github_update_pr" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid PR number"
                        val title = optString(args, "title")
                        val body = optString(args, "body")
                        val state = optString(args, "state")
                        val base = optString(args, "base")
                        svc.updatePullRequest(owner, repo, number, title, body, state, base).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub update PR failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_update_pr failed: ${e.message}"
                    }
                }
                "github_merge_pr" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid PR number"
                        val commitTitle = optString(args, "commit_title")
                        val commitMessage = optString(args, "commit_message")
                        val mergeMethod = optString(args, "merge_method") ?: "merge"
                        svc.mergePullRequest(owner, repo, number, commitTitle, commitMessage, mergeMethod).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub merge PR failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_merge_pr failed: ${e.message}"
                    }
                }
                "github_list_issues" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val state = optString(args, "state") ?: "open"
                        val perPage = optInt(args, "per_page", 20)
                        svc.listIssues(owner, repo, state, perPage).fold(
                            onSuccess = { issues ->
                                if (issues.isEmpty()) "No issues found in $owner/$repo ($state)."
                                else issues.joinToString("\n") { "#${it.number} [${it.state}] ${it.title} (${it.url})" }
                            },
                            onFailure = { "GitHub list issues failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_issues failed: ${e.message}"
                    }
                }
                "github_get_issue" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid issue number"
                        svc.getIssue(owner, repo, number).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get issue failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_issue failed: ${e.message}"
                    }
                }
                "github_create_issue" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val title = optString(args, "title")?.trim() ?: return "Missing title"
                        val body = optString(args, "body") ?: ""
                        val labelsStr = optString(args, "labels")
                        val labels = labelsStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        svc.createIssue(owner, repo, title, body, labels).fold(
                            onSuccess = { "Issue created successfully: $it" },
                            onFailure = { "GitHub create issue failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_issue failed: ${e.message}"
                    }
                }
                "github_update_issue" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val number = optInt(args, "number", 0)
                        if (number <= 0) return "Invalid issue number"
                        val title = optString(args, "title")
                        val body = optString(args, "body")
                        val state = optString(args, "state")
                        val labelsStr = optString(args, "labels")
                        val labels = labelsStr?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        svc.updateIssue(owner, repo, number, title, body, state, labels).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub update issue failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_update_issue failed: ${e.message}"
                    }
                }
                "github_list_comments" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val issueNumber = optInt(args, "issue_number", 0)
                        if (issueNumber <= 0) return "Invalid issue/PR number"
                        val perPage = optInt(args, "per_page", 20)
                        svc.listIssueComments(owner, repo, issueNumber, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list comments failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_comments failed: ${e.message}"
                    }
                }
                "github_create_comment" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val issueNumber = optInt(args, "issue_number", 0)
                        if (issueNumber <= 0) return "Invalid issue/PR number"
                        val body = optString(args, "body") ?: return "Missing comment body"
                        svc.createIssueComment(owner, repo, issueNumber, body).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub create comment failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_comment failed: ${e.message}"
                    }
                }
                "github_list_releases" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val perPage = optInt(args, "per_page", 10)
                        svc.listReleases(owner, repo, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list releases failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_releases failed: ${e.message}"
                    }
                }
                "github_get_latest_release" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        svc.getLatestRelease(owner, repo).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub get latest release failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_get_latest_release failed: ${e.message}"
                    }
                }
                "github_create_release" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val tagName = optString(args, "tag_name")?.trim() ?: return "Missing tag_name"
                        val name = optString(args, "name") ?: tagName
                        val body = optString(args, "body") ?: ""
                        val target = optString(args, "target_commitish") ?: "main"
                        val draft = try { args.get("draft")?.asBoolean } catch (_: Exception) { false } ?: false
                        val prerelease = try { args.get("prerelease")?.asBoolean } catch (_: Exception) { false } ?: false
                        svc.createRelease(owner, repo, tagName, name, body, target, draft, prerelease).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub create release failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_release failed: ${e.message}"
                    }
                }
                "github_list_workflows" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        svc.listWorkflows(owner, repo).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list workflows failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_workflows failed: ${e.message}"
                    }
                }
                "github_trigger_workflow" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val workflowId = optString(args, "workflow_id")?.trim() ?: return "Missing workflow_id (ID or filename like 'build.yml')"
                        val ref = optString(args, "ref") ?: "main"
                        val inputs = mutableMapOf<String, Any>()
                        args.getAsJsonObject("inputs")?.entrySet()?.forEach { entry ->
                            inputs[entry.key] = if (entry.value.isJsonPrimitive) entry.value.asString else entry.value.toString()
                        }
                        svc.triggerWorkflow(owner, repo, workflowId, ref, inputs).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub trigger workflow failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_trigger_workflow failed: ${e.message}"
                    }
                }
                "github_check_workflow" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val runId = try { args.get("run_id")?.asLong } catch (_: Exception) { null }
                        if (runId != null && runId > 0) {
                            svc.checkWorkflowStatus(owner, repo, runId).fold(
                                onSuccess = { it },
                                onFailure = { "GitHub check workflow failed: ${it.message}" }
                            )
                        } else {
                            val branch = optString(args, "branch") ?: "main"
                            val perPage = optInt(args, "per_page", 10)
                            svc.listWorkflowRuns(owner, repo, branch, perPage).fold(
                                onSuccess = { it },
                                onFailure = { "GitHub check workflow failed: ${it.message}" }
                            )
                        }
                    } catch (e: Exception) {
                        "github_check_workflow failed: ${e.message}"
                    }
                }
                "github_download_artifact" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val owner = optString(args, "owner")?.trim() ?: return "Missing owner"
                        val repo = optString(args, "repo")?.trim() ?: return "Missing repo"
                        val savePath = optString(args, "save_path") ?: return "Missing save_path"
                        val artifactId = try { args.get("artifact_id")?.asLong } catch (_: Exception) { null }
                        if (artifactId != null && artifactId > 0) {
                            svc.downloadArtifact(owner, repo, artifactId, savePath).fold(
                                onSuccess = { it },
                                onFailure = { "GitHub download artifact failed: ${it.message}" }
                            )
                        } else {
                            svc.downloadLatestArtifact(owner, repo, savePath).fold(
                                onSuccess = { it },
                                onFailure = { "GitHub download artifact failed: ${it.message}" }
                            )
                        }
                    } catch (e: Exception) {
                        "github_download_artifact failed: ${e.message}"
                    }
                }
                "github_list_gists" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val perPage = optInt(args, "per_page", 20)
                        svc.listGists(perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub list gists failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_list_gists failed: ${e.message}"
                    }
                }
                "github_create_gist" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val description = optString(args, "description") ?: ""
                        val filename = optString(args, "filename") ?: "snippet.txt"
                        val content = optString(args, "content") ?: return "Missing content"
                        val isPublic = try { args.get("public")?.asBoolean } catch (_: Exception) { false } ?: false
                        svc.createGist(description, mapOf(filename to content), isPublic).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub create gist failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_create_gist failed: ${e.message}"
                    }
                }
                "github_search_code" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val query = optString(args, "query") ?: return "Missing query"
                        val perPage = optInt(args, "per_page", 10)
                        svc.searchCode(query, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub search code failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_search_code failed: ${e.message}"
                    }
                }
                "github_search_repos" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val query = optString(args, "query") ?: return "Missing query"
                        val perPage = optInt(args, "per_page", 10)
                        svc.searchRepositoriesFormatted(query, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub search repos failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_search_repos failed: ${e.message}"
                    }
                }
                "github_search_issues" -> {
                    try {
                        val svc = getOrInitGitHubService() ?: return "GitHub not connected."
                        val query = optString(args, "query") ?: return "Missing query"
                        val perPage = optInt(args, "per_page", 15)
                        svc.searchIssues(query, perPage).fold(
                            onSuccess = { it },
                            onFailure = { "GitHub search issues failed: ${it.message}" }
                        )
                    } catch (e: Exception) {
                        "github_search_issues failed: ${e.message}"
                    }
                }
                "drive_list" -> {
                    try {
                        val handler = driveHandler ?: return "Google Drive not connected. Connect your Google Account or Drive integration first."
                        val query = args.get("query")?.asString ?: ""
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            handler.process("list ${if (query.isNotEmpty()) "search $query" else ""} files")
                        }
                    } catch (e: Exception) {
                        "drive_list failed: ${e.message}"
                    }
                }
                "drive_search" -> {
                    try {
                        val handler = driveHandler ?: return "Google Drive not connected."
                        val query = args.get("query")?.asString ?: return "Missing query"
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            handler.process("search $query files")
                        }
                    } catch (e: Exception) {
                        "drive_search failed: ${e.message}"
                    }
                }
                "drive_read" -> {
                    try {
                        val handler = driveHandler ?: return "Google Drive not connected."
                        val fileId = args.get("file_id")?.asString ?: return "Missing file_id"
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val result = handler.readFile(fileId)
                            result.fold(
                                onSuccess = { it },
                                onFailure = { "Drive read failed: ${it.message}" }
                            )
                        }
                    } catch (e: Exception) {
                        "drive_read failed: ${e.message}"
                    }
                }
                "drive_upload" -> {
                    try {
                        val handler = driveHandler ?: return "Google Drive not connected."
                        val name = optString(args, "name") ?: return "Missing name"
                        val content = optString(args, "content") ?: return "Missing content"
                        val rawMime = optString(args, "mimeType")?.trim() ?: "text/plain"
                        val mime = if (rawMime.matches(Regex("^[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+$"))) rawMime else "text/plain"
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                            val result = handler.uploadFile(name, content, mime)
                            result.fold(
                                onSuccess = { "File uploaded to Drive. File ID: $it" },
                                onFailure = { "Drive upload failed: ${it.message}" }
                            )
                        }
                    } catch (e: Exception) {
                        "drive_upload failed: ${e.message}"
                    }
                }
                "webbridge_agent" -> {
                    try {
                        val taskId = args.get("task_id")?.asString ?: java.util.UUID.randomUUID().toString()
                        val taskType = args.get("task_type")?.asString ?: args.get("capability")?.asString ?: "text_generation"
                        val targetSite = args.get("target_site")?.asString ?: args.get("preferred_site")?.asString ?: "auto"
                        val userPrompt = args.get("user_prompt")?.asString ?: args.get("prompt")?.asString ?: ""
                        if (taskType.lowercase() == "search") {
                            val result = webSearch(userPrompt)
                            return gson.toJson(mapOf(
                                "task_id" to taskId,
                                "status" to "SUCCESS",
                                "mode" to "NATIVE",
                                "site_used" to "web_search",
                                "target_site_used" to "web_search",
                                "capability" to "search",
                                "output_type" to "text",
                                "output" to mapOf<String, Any>(
                                    "text_content" to result,
                                    "image_urls" to emptyList<String>(),
                                    "description" to "Web search results via DuckDuckGo/Wikipedia"
                                ),
                                "error" to null
                            ))
                        }
                        if (taskType.lowercase() == "audio_generation") {
                            val voice = args.get("voice")?.asString ?: ""
                            val rate = args.get("rate")?.asString ?: ""
                            val pitch = args.get("pitch")?.asString ?: ""
                            val audioResult = executeEdgeTts(userPrompt, voice, rate, pitch, workingDir)
                            return gson.toJson(mapOf(
                                "task_id" to taskId,
                                "status" to "SUCCESS",
                                "mode" to "NATIVE",
                                "site_used" to targetSite,
                                "target_site_used" to targetSite,
                                "capability" to "audio_generation",
                                "output_type" to "audio",
                                "output" to mapOf<String, Any>(
                                    "text_content" to audioResult,
                                    "image_urls" to emptyList<String>(),
                                    "description" to "Audio generated from text"
                                ),
                                "error" to null
                            ))
                        }
                        if (taskType.lowercase() in listOf("image_search", "image_fetch")) {
                            val imageUrl = fetchWebImage(userPrompt)
                            val resultUrl = if (imageUrl.isNotBlank()) imageUrl else executeImageGeneration(userPrompt)
                            return gson.toJson(mapOf(
                                "task_id" to taskId,
                                "status" to "SUCCESS",
                                "mode" to "NATIVE",
                                "site_used" to "web_image_search",
                                "target_site_used" to "web_image_search",
                                "capability" to "image_search",
                                "output_type" to "image",
                                "output" to mapOf<String, Any>(
                                    "text_content" to if (imageUrl.isNotBlank()) "Found image for: $userPrompt" else "Generated image for: $userPrompt",
                                    "image_urls" to listOf(resultUrl),
                                    "description" to "Image result from web search or generation"
                                ),
                                "error" to null
                            ))
                        }
                        if (taskType.lowercase() in listOf("image_generation", "image_generate")) {
                            val imageResult = executeImageGeneration(userPrompt)
                            return gson.toJson(mapOf(
                                "task_id" to taskId,
                                "status" to "SUCCESS",
                                "mode" to "NATIVE",
                                "site_used" to "pollinations",
                                "target_site_used" to "pollinations",
                                "capability" to "image_generation",
                                "output_type" to "image",
                                "output" to mapOf<String, Any>(
                                    "text_content" to "Generated image for: $userPrompt",
                                    "image_urls" to listOf(imageResult),
                                    "description" to "AI-generated image"
                                ),
                                "error" to null
                            ))
                        }
                        executeWebBridgeAgent(taskId, taskType, targetSite, userPrompt, args)
                    } catch (e: Exception) {
                        "webbridge_agent failed: ${e.message}"
                    }
                }
                "generate_chatgpt_document" -> {
                    val prompt = args.get("prompt")?.asString ?: return "Missing prompt argument"
                    val format = args.get("format")?.asString ?: "markdown"
                    val ctx = context ?: return "Context unavailable"
                    val bridge = ai.deepcode.android.service.chatgpt.ChatGPTHeadlessBridge.getInstance(ctx)
                    if (!bridge.isConfigured()) return "ChatGPT access token not configured. Please set chatgpt_access_token in DeepCode settings."
                    try {
                        kotlinx.coroutines.runBlocking { bridge.generateDocument(prompt, format) }
                    } catch (e: Exception) {
                        "ChatGPT document generation failed: ${e.message}"
                    }
                }
                "schedule_chatgpt_task" -> {
                    val name = args.get("name")?.asString ?: return "Missing name argument"
                    val taskType = args.get("task_type")?.asString ?: "document_creation"
                    val prompt = args.get("prompt")?.asString ?: return "Missing prompt argument"
                    val cron = args.get("cron")?.asString ?: "0 9 * * *"
                    val ctx = context ?: return "Context unavailable"
                    executeScheduleChatGPTTask(name, taskType, prompt, cron, ctx)
                }
                "cron_add", "cron_list", "cron_remove" -> {
                    "Automation scheduling is handled by the main AI. Please ask the user to set up the automation through the chat interface."
                }
                "search_image" -> {
                    val query = args.get("query")?.asString ?: return "Missing query argument"
                    val imageUrl = fetchWebImage(query)
                    if (imageUrl.isBlank()) return "No images found for: $query"
                    "[image:${imageUrl}]"
                }
                "generate_image" -> {
                    val prompt = args.get("prompt")?.asString ?: return "Missing prompt argument"
                    val model = args.get("model")?.asString ?: args.get("engine")?.asString ?: ""
                    val result = executeImageGeneration(prompt, model)
                    if (result.startsWith("Error:") || result.startsWith("No image")) {
                        result
                    } else if (result.startsWith("[image:")) {
                        result
                    } else {
                        "[image:${result}]"
                    }
                }
                "generate_video" -> {
                    val prompt = args.get("prompt")?.asString ?: return "Missing prompt argument"
                    // executeVideoGeneration() already wraps the result as a [video:...] marker;
                    // return it directly to avoid double-wrapping (e.g. [video:[video:...]]).
                    executeVideoGeneration(prompt)
                }
                "edge_tts" -> {
                    val text = optString(args, "text") ?: return "Missing text argument"
                    val voice = optString(args, "voice") ?: ""
                    val rate = optString(args, "rate") ?: ""
                    val pitch = optString(args, "pitch") ?: ""
                    val verbatim = try { args.get("verbatim")?.asBoolean ?: true } catch (_: Exception) { true }
                    executeEdgeTts(text, voice, rate, pitch, workingDir, verbatim)
                }
                "set_tts_voice" -> {
                    val voice = optString(args, "voice") ?: return "Missing voice argument"
                    context?.let {
                        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(it)
                        // Exact Edge voice (e.g. en-US-AriaNeural) goes to its own slot; locale (en-US) stays in tts_voice.
                        if (voice.endsWith("Neural", ignoreCase = true)) prefs.saveSetting("tts_edge_voice", voice)
                        else prefs.saveSetting("tts_voice", voice)
                    }
                    "TTS voice set to: $voice"
                }
                "set_tts_backend" -> {
                    val backend = args.get("backend")?.asString ?: return "Missing backend argument (edge_tts or kokoro)"
                    val valid = listOf("edge_tts", "kokoro", "android", "google")
                    if (backend !in valid) return "Invalid backend. Options: ${valid.joinToString(", ")}"
                    context?.let {
                        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(it)
                        prefs.saveSetting("tts_backend", backend)
                    }
                    "TTS backend set to: $backend"
                }
                "set_kokoro_url" -> {
                    val url = optString(args, "url") ?: return "Missing url argument"
                    val clean = url.trim().trimEnd('/')
                    if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "Invalid URL: must start with http:// or https://"
                    context?.let {
                        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(it)
                        prefs.saveSetting("kokoro_url", clean)
                    }
                    "Kokoro server URL set to: $clean"
                }
                "set_gotenberg_url" -> {
                    val url = optString(args, "url") ?: return "Missing url argument"
                    val clean = url.trim().trimEnd('/')
                    if (!clean.startsWith("http://") && !clean.startsWith("https://")) return "Invalid URL: must start with http:// or https://"
                    context?.let {
                        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(it)
                        prefs.saveSetting("gotenberg_url", clean)
                    }
                    "Gotenberg server URL set to: $clean"
                }
                "analyze_pdf" -> {
                    val path = optString(args, "path") ?: return "Missing path argument"
                    analyzePdfFile(path)
                }
                "list_pdf_layouts" -> listPdfLayouts()
                "create_pdf_from_reference" -> {
                    val refPath = optString(args, "reference_path") ?: optString(args, "path") ?: return "Missing reference_path argument"
                    val title = optString(args, "title") ?: return "Missing title argument"
                    val content = optString(args, "content") ?: return "Missing content argument"
                    val author = optString(args, "author")
                    val filename = optString(args, "filename")
                    createPdfFromReference(refPath, title, content, author, filename)
                }
                "create_pdf" -> {
                    val title = optString(args, "title") ?: return "Missing title argument"
                    val content = optString(args, "content") ?: return "Missing content argument"
                    val author = optString(args, "author")
                    val filename = optString(args, "filename")
                    val layout = optString(args, "layout")
                    if (!layout.isNullOrBlank()) {
                        val resolved = try { resolveCustomLayout(layout) } catch (_: Exception) { null }
                            ?: PdfLayoutEngine.getLayoutById(layout)
                            ?: PdfLayoutEngine.getLayoutByName(layout)
                            ?: PdfLayoutEngine.findLayout(layout)
                        if (resolved != null) {
                            return renderLocalPdf(title, content, author, filename, resolved)
                        }
                        AppLogger.w("PDF", "Unknown layout '$layout' — falling back to Gotenberg")
                    }
                    executeGotenbergPdf(title, content, author, filename, layout)
                }
                else -> {
                    if (ai.deepcode.android.plugin.PluginRegistry.hasToolName(name)) {
                        val ctx = context ?: return "Error: Context not available for plugin execution"
                        ai.deepcode.android.plugin.PluginRegistry.executeTool(name, args, ctx)
                    } else {
                        "Unknown tool: $name"
                    }
                }
            }
        } catch (e: Exception) {
            "Error executing tool $name: ${e.message}"
        }
    }

    private fun isMetaReferenceText(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.length > 150) return false
        val metaPatterns = listOf(
            "last response", "previous response", "last message", "previous message",
            "last reply", "previous reply", "that response", "your response", "my last response",
            "work last response", "create audio of last response", "audio of last response",
            "read last response", "read the last response", "speak last response", "audio of that",
            "audio of it", "convert that", "convert it", "read that", "read it", "speak that", "speak it",
            "last answer", "previous answer", "your last reply", "your previous message",
            "what you said", "what you wrote", "make audio of last response", "convert last message"
        )
        if (metaPatterns.any { clean.contains(it) }) return true
        val hasMetaTarget = clean.contains("last") || clean.contains("previous") || clean.contains("that") || clean.contains("what you")
        val hasMetaAction = clean.contains("response") || clean.contains("message") || clean.contains("reply") || clean.contains("answer") || clean.contains("audio") || clean.contains("speak") || clean.contains("read") || clean.contains("convert")
        return hasMetaTarget && hasMetaAction
    }

    private fun resolveLastAssistantMessage(ctx: Context): String? {
        return try {
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(ctx)
            val messages = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                db.messageDao().getRecentAssistantMessages()
            }
            val target = messages.firstOrNull { msg ->
                !msg.isToolCall &&
                !msg.content.startsWith("Executing tool") &&
                !msg.content.startsWith("Running tool") &&
                !msg.content.startsWith("I've completed") &&
                !msg.content.startsWith("Tool result:") &&
                !msg.content.startsWith("I will generate") &&
                !msg.content.startsWith("Converting to speech") &&
                !isMetaReferenceText(msg.content) &&
                msg.content.replace(Regex("\\[(audio|file|image|video):[^\\]]+\\]"), "").trim().length > 5
            }
            target?.content
                ?.replace(Regex("\\[audio:[^\\]]+\\]"), "")
                ?.replace(Regex("\\[file:[^\\]]+\\]"), "")
                ?.replace(Regex("\\[image:[^\\]]+\\]"), "")
                ?.replace(Regex("\\[video:[^\\]]+\\]"), "")
                ?.trim()
        } catch (e: Exception) {
            AppLogger.e("TTS", "Failed to resolve last assistant message", e)
            null
        }
    }

    private fun cleanTextForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), " [code snippet] ")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .replace(Regex("\\bhttps?://\\S+"), "")
            .replace(Regex("[#*_`~]+"), " ")
            .replace(Regex("\\n+"), ". ")
            .trim()
    }

    private fun isTopicOrQuestionPrompt(input: String): Boolean {
        val clean = input.trim().lowercase()
        if (clean.length > 150) return false
        if (isMetaReferenceText(input)) return false
        val topicPatterns = listOf(
            "what is", "what are", "who is", "who are", "where is", "when did",
            "why does", "why do", "how to", "how does", "how do", "explain",
            "tell me", "tell a", "give me", "create audio", "generate audio",
            "make audio", "speech about", "audio of", "definition of", "meaning of",
            "history of", "summary of", "poem about", "story about", "joke about",
            "llm", "ai", "python", "quantum"
        )
        if (clean.contains("?")) return true
        return topicPatterns.any { clean.contains(it) }
    }

    private fun generateAnswerForTopic(promptText: String, ctx: Context): String? {
        return try {
            val cleanTopic = promptText
                .replace(Regex("^(create|generate|make|speak|read)\\s+(audio|speech|voice)?\\s*(of|for|about|on)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^audio\\s+(of|for|about|on)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace("?", "")
                .trim()

            AppLogger.i("TTS", "Searching web for topic: '$cleanTopic'...")

            // 1. Web Search on the topic
            val searchResults = try {
                webSearch(cleanTopic, numResults = 4, livecrawl = "fallback", type = "auto", contextMaxCharacters = 3000)
            } catch (e: Exception) {
                AppLogger.w("TTS", "Web search failed for topic: ${e.message}")
                ""
            }

            // 2. Generate clean narration script using active AI provider
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val savedModel = prefs.getSetting("agent_model", "deepseek-v4-flash-free")
            val savedProviderName = prefs.getSetting("agent_provider", "Zen AI")
            val provider = ai.deepcode.android.data.remote.AIProviderFactory.providers.firstOrNull { it.name == savedProviderName }
                ?: ai.deepcode.android.data.remote.AIProviderFactory.providers.firstOrNull { it.name == "Zen AI" }
                ?: ai.deepcode.android.data.remote.AIProviderFactory.providers.firstOrNull() ?: return null

            val modelId = provider.models.firstOrNull { it.id == savedModel }?.id ?: provider.models.firstOrNull()?.id ?: savedModel
            val apiKey = when (provider.name) {
                "Zen AI", "Zen", "Zen (Free)" -> prefs.getApiKey("zen")
                "OpenAI" -> prefs.getSetting("openai_api_key", "")
                "Anthropic" -> prefs.getSetting("anthropic_api_key", "")
                "Google Gemini" -> prefs.getSetting("gemini_api_key", "")
                "Groq" -> prefs.getSetting("groq_api_key", "")
                "DeepSeek" -> prefs.getSetting("deepseek_api_key", "")
                "OpenRouter" -> prefs.getSetting("openrouter_api_key", "")
                else -> ""
            }
            val customUrl = when (provider.name) {
                "Custom OpenAI / Local" -> prefs.getSetting("custom_api_url", "")
                else -> null
            }

            val promptContent = if (!searchResults.isNullOrBlank() && !searchResults.contains("No results found")) {
                "Topic: $cleanTopic\n\nWeb Search Results:\n$searchResults\n\nBased on the search results above, summarize and explain '$cleanTopic' clearly, concisely, and naturally in 2 short paragraphs suitable for text-to-speech audio narration. Do not include markdown formatting or URLs."
            } else {
                "Explain or answer '$cleanTopic' clearly, concisely, and naturally in 2 short paragraphs suitable for text-to-speech audio narration. Do not include markdown formatting."
            }

            val promptMsg = ai.deepcode.android.domain.model.Message(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = "tts_gen",
                role = "user",
                content = promptContent,
                timestamp = System.currentTimeMillis()
            )

            val sb = StringBuilder()
            val latch = java.util.concurrent.CountDownLatch(1)
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                provider.streamCompletion(
                    messages = listOf(promptMsg),
                    model = modelId,
                    tools = null,
                    apiKey = apiKey,
                    customBaseUrl = customUrl,
                    onToken = { sb.append(it) },
                    onToolCall = {},
                    onComplete = { latch.countDown() },
                    onError = { latch.countDown() }
                )
            }
            latch.await(12, java.util.concurrent.TimeUnit.SECONDS)
            val answerText = sb.toString().trim()
            if (answerText.isNotEmpty()) {
                AppLogger.i("TTS", "Generated answer from search results (${answerText.length} chars)")
                answerText
            } else if (!searchResults.isNullOrBlank()) {
                cleanTextForSpeech(searchResults.take(500))
            } else null
        } catch (e: Exception) {
            AppLogger.e("TTS", "Failed to search and generate answer: ${e.message}")
            null
        }
    }

    private fun ttsCacheKey(text: String, locale: String, voice: String, backend: String, tone: String, char: String): String {
        return try {
            val raw = "$backend|$locale|$voice|$tone|$char|${text.trim()}"
            val md = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            md.joinToString("") { "%02x".format(it) }.take(32)
        } catch (_: Exception) { java.util.UUID.randomUUID().toString().take(8) }
    }

    private fun isValidAudioData(data: ByteArray, ext: String): Boolean {
        if (data.size < 2000) return false
        return try {
            if (ext == "wav") {
                data.size > 44 && data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte()
            } else {
                // MP3: ID3 header or frame sync 0xFF 0xE0
                (data[0] == 0x49.toByte() && data[1] == 0x44.toByte()) ||
                    (data[0] == 0xFF.toByte() && (data[1].toInt() and 0xE0) == 0xE0)
            }
        } catch (_: Exception) { data.size >= 2000 }
    }

    private fun trimAudioCache(dir: File, maxBytes: Long = 200L * 1024 * 1024, maxFiles: Int = 200) {
        try {
            val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
            var total = files.sumOf { it.length() }
            var idx = 0
            while ((total > maxBytes || files.size - idx > maxFiles) && idx < files.size) {
                total -= files[idx].length()
                try { files[idx].delete() } catch (_: Exception) {}
                idx++
            }
        } catch (_: Exception) {}
    }

    private fun executeEdgeTts(text: String, voice: String, rate: String, pitch: String, workingDir: String, verbatim: Boolean = false): String {
        val ctx = context ?: return "Audio generation requires an Android context"
        return try {
            var textToSpeak = text
            if (!verbatim) {
                val isMeta = isMetaReferenceText(text)
                if (isMeta) {
                    val resolved = resolveLastAssistantMessage(ctx)
                    if (!resolved.isNullOrBlank()) {
                        AppLogger.i("TTS", "Resolved meta-reference '$text' to actual last assistant message (${resolved.length} chars)")
                        textToSpeak = resolved
                    } else {
                        return "Audio generation error: Could not locate a previous assistant response in the conversation to convert to speech."
                    }
                }
            } else {
                AppLogger.i("TTS", "verbatim=true — speaking literal text without meta/topic rewrite")
            }
            textToSpeak = cleanTextForSpeech(textToSpeak)
            if (textToSpeak.isBlank()) {
                return "Audio generation failed: text to speak is empty"
            }
            if (textToSpeak.length > 5000) {
                AppLogger.w("TTS", "Text too long (${textToSpeak.length} chars), truncating to 5000")
                textToSpeak = textToSpeak.take(5000)
            }

            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)

            // ALWAYS use saved settings — completely ignore the AI's voice/rate/pitch params
            val savedLocale = prefs.getSetting("tts_voice", "en-US")
            val savedExactVoice = prefs.getSetting("tts_edge_voice", "")
            val char = prefs.getSetting("tts_voice_character", "default")
            val tone = prefs.getSetting("tts_voice_tone", "normal")

            val ttsBackend = prefs.getSetting("tts_backend", "edge_tts")
            val voiceName = when {
                savedExactVoice.isNotEmpty() -> {
                    AppLogger.i("EdgeTTS", "Using saved exact voice: $savedExactVoice")
                    savedExactVoice
                }
                else -> {
                    val name = getEdgeVoiceName(savedLocale, char)
                    AppLogger.i("EdgeTTS", "Using voice from locale/char: $name")
                    name
                }
            }
            val styleName = getEdgeStyleAttr(tone)

            // Map locale -> Kokoro voice (Kokoro uses its own voice IDs, not Edge Neural names).
            val kokoroVoiceMap = mapOf(
                "en-US" to "af_bella", "en-GB" to "bf_emma", "hi-IN" to "hf_alpha",
                "es-ES" to "ef_dora", "fr-FR" to "ff_siwis", "de-DE" to "df_dora",
                "ja-JP" to "jf_alpha", "ko-KR" to "kf_alpha", "zh-CN" to "zf_xiaobei",
                "pt-BR" to "pf_dora", "ru-RU" to "rf_sasha", "it-IT" to "if_sara"
            )
            val kokoroVoice = kokoroVoiceMap[savedLocale] ?: kokoroVoiceMap[savedLocale.substringBefore("-") + "-US"]
                ?: kokoroVoiceMap.values.firstOrNull { it.startsWith(savedLocale.substringBefore("-").lowercase()) } ?: "af_bella"

            // Hash cache: identical text+voice+backend reuses file without network.
            try {
                val cacheDir = File(ctx.cacheDir, "audio")
                val ext0 = if (ttsBackend == "android") "wav" else "mp3"
                val key = ttsCacheKey(textToSpeak, savedLocale, voiceName, ttsBackend, tone, char)
                val cached = File(cacheDir, "tts_$key.$ext0")
                if (cached.exists() && cached.length() >= 2000) {
                    AppLogger.i("TTS", "Cache hit — ${cached.length()} bytes")
                    return "[audio:${cached.absolutePath}]"
                }
            } catch (_: Exception) {}

            // Execute based on selected TTS backend
            val audioData = when (ttsBackend) {
                "android" -> {
                    AppLogger.i("TTS", "Using Android built-in TTS backend")
                    androidTtsSynthesize(textToSpeak, savedLocale, voiceName, ctx)
                }
                "google" -> {
                    AppLogger.i("TTS", "Using Google Translate TTS backend")
                    val gResult = try { googleTranslateTts(textToSpeak, savedLocale, ctx) } catch (_: Exception) { "" }
                    // googleTranslateTts returns "[audio:path]" on success or an error string — only return on success so fallbacks can run.
                    if (gResult.startsWith("[audio:")) return gResult
                    AppLogger.w("TTS", "Google TTS failed ($gResult), trying fallbacks")
                    null
                }
                "kokoro" -> {
                    AppLogger.i("TTS", "Using Kokoro backend with voice=$kokoroVoice, URL=${prefs.getSetting("kokoro_url", "http://localhost:8880")}")
                    try {
                        val result = kokoroTts(textToSpeak, kokoroVoice, ctx)
                        AppLogger.i("TTS", "Kokoro result: ${if (result != null) "${result.size} bytes" else "null"}")
                        result
                    } catch (e: Exception) {
                        AppLogger.e("TTS", "Kokoro failed", e)
                        null
                    }
                }
                else -> {
                    try {
                        val ssml = buildEdgeSsml(textToSpeak, voiceName, savedLocale, "", "", styleName)
                        AppLogger.i("EdgeTTS", "SSML: $ssml")
                        val result = edgeWsSynthesize(ssml)
                        AppLogger.i("EdgeTTS", "Edge TTS result: ${if (result != null) "${result.size} bytes" else "null"}")
                        result
                    } catch (e: Exception) {
                        AppLogger.e("EdgeTTS", "Edge TTS WebSocket failed", e)
                        null
                    }
                }
            }

            val primaryExt = if (ttsBackend == "android") "wav" else "mp3"
            if (audioData != null && isValidAudioData(audioData, primaryExt)) {
                val audioDir = File(ctx.cacheDir, "audio")
                audioDir.mkdirs()
                val key = ttsCacheKey(textToSpeak, savedLocale, voiceName, ttsBackend, tone, char)
                val outputFile = File(audioDir, "tts_$key.$primaryExt")
                try {
                    outputFile.writeBytes(audioData)
                    trimAudioCache(audioDir)
                } catch (_: Exception) {}
                if (!outputFile.exists() || outputFile.length() <= 0) {
                    return "Audio generation failed: no audio produced"
                }
                AppLogger.i("TTS", "${ttsBackend} TTS succeeded — ${audioData.size} bytes to ${outputFile.name}")
                return "[audio:${outputFile.absolutePath}]"
            } else if (audioData != null) {
                AppLogger.w("TTS", "Primary $ttsBackend returned ${audioData.size} bytes (likely truncated/corrupt) — trying fallbacks")
            }

            // Fallback 1: Edge TTS (if primary wasn't edge_tts)
            if (ttsBackend != "edge_tts") {
                AppLogger.w("TTS", "Primary $ttsBackend backend failed, trying Edge TTS fallback")
                val edgeAudio = try {
                    val ssml = buildEdgeSsml(textToSpeak, voiceName, savedLocale, "", "", styleName)
                    edgeWsSynthesize(ssml)
                } catch (e: Exception) {
                    AppLogger.e("EdgeTTS", "Edge TTS WebSocket failed", e)
                    null
                }
                if (edgeAudio != null && isValidAudioData(edgeAudio, "mp3")) {
                    val audioDir = File(ctx.cacheDir, "audio")
                    audioDir.mkdirs()
                    val key = ttsCacheKey(textToSpeak, savedLocale, voiceName, "edge_tts", tone, char)
                    val outputFile = File(audioDir, "tts_$key.mp3")
                    try {
                        outputFile.writeBytes(edgeAudio)
                        trimAudioCache(audioDir)
                    } catch (_: Exception) {}
                    if (outputFile.exists() && outputFile.length() > 0) {
                        return "[audio:${outputFile.absolutePath}]"
                    }
                }
            }

            // Fallback 2: Android built-in TTS
            if (ttsBackend != "android") {
                AppLogger.w("TTS", "Primary backend failed, trying Android TTS fallback")
                val androidAudio = androidTtsSynthesize(textToSpeak, savedLocale, voiceName, ctx)
                if (androidAudio != null && isValidAudioData(androidAudio, "wav")) {
                    val audioDir = File(ctx.cacheDir, "audio")
                    audioDir.mkdirs()
                    val key = ttsCacheKey(textToSpeak, savedLocale, voiceName, "android", tone, char)
                    val outputFile = File(audioDir, "tts_$key.wav")
                    try {
                        outputFile.writeBytes(androidAudio)
                        trimAudioCache(audioDir)
                    } catch (_: Exception) {}
                    if (outputFile.exists() && outputFile.length() > 0) {
                        return "[audio:${outputFile.absolutePath}]"
                    }
                }
            }

            // Fallback 3: Google Translate TTS (skip if it was already the primary and failed).
            if (ttsBackend != "google") {
                AppLogger.w("TTS", "Android TTS failed, falling back to Google TTS")
                googleTranslateTts(textToSpeak, savedLocale, ctx)
            } else {
                "Audio generation failed: all TTS backends failed"
            }
        } catch (e: java.net.UnknownHostException) {
            "Audio generation failed: no internet connection"
        } catch (e: Exception) {
            "Audio generation error: ${e.message}"
        }
    }

    private fun googleTranslateTts(text: String, locale: String, ctx: Context): String {
        val audioDir = File(ctx.cacheDir, "audio")
        audioDir.mkdirs()
        val outputFile = File(audioDir, "tts_${java.util.UUID.randomUUID()}.mp3")
        val cleanText = text.replace(Regex("[\\u2600-\\u27BF\\uD83C-\\uDBFF\\uDC00-\\uDFFF]"), "").trim()
        if (cleanText.isBlank()) return "Audio generation failed: no text to synthesize"
        val localeObj = parseTtsLocale(locale)

        // Split text into <=180 character chunks at punctuation or space boundaries (up to 1000 chars)
        val chunks = mutableListOf<String>()
        var remaining = cleanText.take(1000)
        while (remaining.isNotEmpty()) {
            if (remaining.length <= 180) {
                chunks.add(remaining)
                break
            }
            var splitIdx = remaining.take(180).lastIndexOfAny(charArrayOf('.', '!', '?', ';', '\n'))
            if (splitIdx < 60) splitIdx = remaining.take(180).lastIndexOfAny(charArrayOf(',', ' '))
            if (splitIdx < 40) splitIdx = 180
            val chunk = remaining.substring(0, splitIdx + 1).trim()
            if (chunk.isNotEmpty()) chunks.add(chunk)
            remaining = remaining.substring(splitIdx + 1).trim()
        }

        var anySuccess = false
        java.io.FileOutputStream(outputFile).use { fos ->
            for (chunk in chunks) {
                val encoded = java.net.URLEncoder.encode(chunk, "UTF-8")
                val url = java.net.URL("https://translate.google.com/translate_tts?ie=UTF-8&q=$encoded&tl=${localeObj.toLanguageTag()}&client=tw-ob&ttsspeed=1.0")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 30000
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                try {
                    if (conn.responseCode == 200) {
                        conn.inputStream.use { it.copyTo(fos) }
                        anySuccess = true
                    }
                } catch (_: Exception) {
                } finally {
                    try { conn.disconnect() } catch (_: Exception) {}
                }
            }
        }

        if (!anySuccess || !outputFile.exists() || outputFile.length() <= 0) {
            try { outputFile.delete() } catch (_: Exception) {}
            return "Audio generation failed: no audio produced"
        }
        return "[audio:${outputFile.absolutePath}]"
    }

    private fun kokoroTts(text: String, voice: String, ctx: Context): ByteArray? {
        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
        val baseUrl = prefs.getSetting("kokoro_url", "http://localhost:8880")
        val cleanText = text.replace(Regex("[\\u2600-\\u27BF\\uD83C-\\uDBFF\\uDC00-\\uDFFF]"), "")
        if (cleanText.isBlank()) return null
        return try {
            val json = """{"model":"kokoro","input":${gson.toJson(cleanText)},"voice":${gson.toJson(voice)},"response_format":"mp3","speed":1.0}"""
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/v1/audio/speech")
                .post(body)
                .header("User-Agent", "DeepCode-Android/1.0")
                .build()
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code != 200) {
                        AppLogger.w("KokoroTTS", "Server returned ${response.code}: ${response.body?.string()?.take(300)}")
                        try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
                        return null
                    }
                    val ct = response.header("Content-Type") ?: ""
                    val bytes = response.body?.bytes()
                    try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
                    if (bytes == null || bytes.size < 2000) {
                        AppLogger.w("KokoroTTS", "Empty/short body (${bytes?.size ?: 0}b, ct=$ct)")
                        return null
                    }
                    if (!ct.contains("audio", ignoreCase = true) && !ct.contains("octet", ignoreCase = true) && !ct.contains("mpeg", ignoreCase = true)) {
                        AppLogger.w("KokoroTTS", "Unexpected content-type $ct")
                    }
                    bytes
                }
            } finally {
                try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            AppLogger.e("KokoroTTS", "Kokoro TTS failed: ${e.message}", e)
            null
        }
    }

    private val edgeVoiceMap = mapOf(
        "en-US" to listOf(
            "en-US-AriaNeural" to "female", "en-US-JennyNeural" to "female",
            "en-US-MichelleNeural" to "female", "en-US-AnaNeural" to "female",
            "en-US-SaraNeural" to "female", "en-US-GuyNeural" to "male",
            "en-US-DavisNeural" to "male", "en-US-TonyNeural" to "male",
            "en-US-JasonNeural" to "male"
        ),
        "en-GB" to listOf(
            "en-GB-SoniaNeural" to "female", "en-GB-LibbyNeural" to "female",
            "en-GB-MaisieNeural" to "female", "en-GB-RyanNeural" to "male",
            "en-GB-ThomasNeural" to "male"
        ),
        "hi-IN" to listOf("hi-IN-SwaraNeural" to "female", "hi-IN-MadhurNeural" to "male"),
        "es-ES" to listOf("es-ES-ElviraNeural" to "female", "es-ES-AlvaroNeural" to "male"),
        "fr-FR" to listOf("fr-FR-DeniseNeural" to "female", "fr-FR-HenriNeural" to "male"),
        "de-DE" to listOf("de-DE-KatjaNeural" to "female", "de-DE-ConradNeural" to "male"),
        "ja-JP" to listOf("ja-JP-NanamiNeural" to "female", "ja-JP-KeitaNeural" to "male"),
        "ko-KR" to listOf("ko-KR-SunHiNeural" to "female", "ko-KR-InJoonNeural" to "male"),
        "zh-CN" to listOf(
            "zh-CN-XiaoxiaoNeural" to "female", "zh-CN-XiaoyiNeural" to "female",
            "zh-CN-YunxiNeural" to "male", "zh-CN-YunyangNeural" to "male"
        ),
        "ar-SA" to listOf("ar-SA-ZariyahNeural" to "female", "ar-SA-HamedNeural" to "male"),
        "pt-BR" to listOf("pt-BR-FranciscaNeural" to "female", "pt-BR-AntonioNeural" to "male"),
        "ru-RU" to listOf("ru-RU-SvetlanaNeural" to "female", "ru-RU-DmitryNeural" to "male"),
        "it-IT" to listOf("it-IT-ElsaNeural" to "female", "it-IT-DiegoNeural" to "male"),
        "nl-NL" to listOf("nl-NL-FennaNeural" to "female", "nl-NL-MaartenNeural" to "male"),
        "tr-TR" to listOf("tr-TR-EmelNeural" to "female", "tr-TR-AhmetNeural" to "male")
    )

    private fun getEdgeVoiceName(locale: String, character: String): String {
        val localeKey = when {
            locale.length > 5 && locale[2] == '-' -> locale.substring(0, 5)
            edgeVoiceMap.containsKey(locale) -> locale
            else -> "en-US"
        }
        val voices = edgeVoiceMap[localeKey] ?: edgeVoiceMap["en-US"]!!
        val gender = when (character) {
            "male" -> "male"
            "female" -> "female"
            else -> if (voices.any { it.second == "female" }) "female" else "male"
        }
        return voices.firstOrNull { it.second == gender }?.first ?: voices.first().first
    }

    private fun getEdgeStyleAttr(tone: String): String {
        return when (tone) {
            "cheerful" -> "cheerful"
            "calm" -> "calm"
            "energetic" -> "excited"
            else -> ""
        }
    }

    private fun buildEdgeSsml(text: String, voiceName: String, locale: String, rate: String, pitch: String, styleName: String): String {
        val safeText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        val rateVal = if (rate.isNotEmpty()) rate else "+0"
        val pitchVal = if (pitch.isNotEmpty()) pitch else "+0"
        val voiceTag = buildString {
            append("<voice name=\"$voiceName\">")
            if (styleName.isNotEmpty()) {
                append("<mstts:express-as style=\"$styleName\">")
            }
            append("<prosody rate=\"${rateVal}%\" pitch=\"${pitchVal}%\">$safeText</prosody>")
            if (styleName.isNotEmpty()) {
                append("</mstts:express-as>")
            }
            append("</voice>")
        }
        val ns = if (styleName.isNotEmpty()) " xmlns:mstts=\"https://www.w3.org/2001/mstts\"" else ""
        return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\"$ns xml:lang=\"$locale\">$voiceTag</speak>"
    }

    private fun edgeWsSynthesize(ssml: String): ByteArray? {
        val latch = CountDownLatch(1)
        val audioBuf = ByteArrayOutputStream()
        val turnEnd = java.util.concurrent.atomic.AtomicBoolean(false)
        val socketRef = java.util.concurrent.atomic.AtomicReference<WebSocket?>(null)
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val connectionId = java.util.UUID.randomUUID().toString()
        val clientToken = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        val winEpoch = 11644473600L
        val now = System.currentTimeMillis() / 1000L + winEpoch
        val roundedSec = now - (now % 300)
        val strToHash = "${roundedSec}$clientToken"
        val sha256 = java.security.MessageDigest.getInstance("SHA-256").digest(strToHash.toByteArray())
        val secMsGec = sha256.joinToString("") { "%02X".format(it) }
        val secMsGecVersion = "1-143.0.3650.75"
        val muid = java.security.SecureRandom().let { r ->
            ByteArray(16).also { r.nextBytes(it) }.joinToString("") { "%02X".format(it) }
        }

        val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
            "?TrustedClientToken=$clientToken" +
            "&ConnectionId=$connectionId" +
            "&Sec-MS-GEC=$secMsGec" +
            "&Sec-MS-GEC-Version=$secMsGecVersion"

        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Sec-WebSocket-Version", "13")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=$muid;")
            .build()

        val utcDateFormat = java.text.SimpleDateFormat(
            "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
            java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketRef.set(webSocket)
                AppLogger.i("EdgeTTS", "WebSocket opened")
                val dateStr = utcDateFormat.format(java.util.Date())
                val connMsg = "X-Timestamp:$dateStr\r\n" +
                    "Content-Type:application/json; charset=utf-8\r\n" +
                    "Path:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":" +
                    "{\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"}," +
                    "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n"
                webSocket.send(connMsg)

                val requestId = java.util.UUID.randomUUID().toString()
                val ts = utcDateFormat.format(java.util.Date())
                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                    "Content-Type:application/ssml+xml\r\n" +
                    "X-Timestamp:$ts\r\n" +
                    "Path:ssml\r\n\r\n" +
                    ssml
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val headerEnd = text.indexOf("\r\n\r\n")
                if (headerEnd >= 0) {
                    val headers = text.substring(0, headerEnd)
                    if (headers.contains("Path:turn.end")) {
                        turnEnd.set(true)
                        latch.countDown()
                    } else if (headers.contains("Path:turn.start")) {
                        // stream started — no-op
                    }
                } else if (text.contains("Path:turn.end")) {
                    turnEnd.set(true)
                    latch.countDown()
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val raw = bytes.toByteArray()
                if (raw.size < 2) return
                val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                if (headerLen > raw.size) return
                val afterHeader = 2 + headerLen
                val dataStart = if (afterHeader + 1 < raw.size &&
                    raw[afterHeader] == 0x0D.toByte() && raw[afterHeader + 1] == 0x0A.toByte()
                ) afterHeader + 2 else afterHeader
                if (dataStart < raw.size) {
                    audioBuf.write(raw, dataStart, raw.size - dataStart)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e("EdgeTTS", "WebSocket failure: ${t.message}", t)
                latch.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i("EdgeTTS", "WebSocket closed: code=$code reason=$reason")
                latch.countDown()
            }
        })
        latch.await(30, TimeUnit.SECONDS)
        try { socketRef.get()?.close(1000, "done") } catch (_: Exception) {}
        try { socketRef.get()?.cancel() } catch (_: Exception) {}
        try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
        val data = audioBuf.toByteArray()
        // Without turn.end the stream was cut — treat tiny buffers as failure so fallbacks run.
        if (!turnEnd.get() && data.size < 8000) {
            AppLogger.w("EdgeTTS", "No turn.end and only ${data.size} bytes — treating as failure")
            return null
        }
        return if (data.isNotEmpty()) data else null
    }

    private fun androidTtsSynthesize(text: String, locale: String, voiceName: String, ctx: Context): ByteArray? {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            AppLogger.w("EdgeTTS", "androidTtsSynthesize called on main thread — refusing (would deadlock)")
            return null
        }
        var tts: android.speech.tts.TextToSpeech? = null
        val safeText = text.take(3500)
        return try {
            val initLatch = CountDownLatch(1)
            var initOk = false

            // Ensure TextToSpeech is initialized using Handler on Main Looper for Android reliability
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    tts = android.speech.tts.TextToSpeech(ctx.applicationContext) { status ->
                        initOk = (status == android.speech.tts.TextToSpeech.SUCCESS)
                        initLatch.countDown()
                    }
                } catch (e: Exception) {
                    AppLogger.e("EdgeTTS", "Error instantiating TextToSpeech", e)
                    initLatch.countDown()
                }
            }

            if (!initLatch.await(7, TimeUnit.SECONDS) || !initOk) {
                AppLogger.w("EdgeTTS", "Android TextToSpeech initialization failed or timed out")
                return null
            }

            val localeObj = parseTtsLocale(locale)
            val isMale = voiceName.contains("male", ignoreCase = true) ||
                (voiceName.endsWith("Neural", ignoreCase = true) && edgeVoiceMap.values.flatten()
                    .firstOrNull { it.first == voiceName }?.second == "male")

            val wantFemale = voiceName.contains("female", ignoreCase = true) ||
                (voiceName.endsWith("Neural", ignoreCase = true) && edgeVoiceMap.values.flatten()
                    .firstOrNull { it.first == voiceName }?.second == "female")
            val matchedVoice = tts?.voices
                ?.filter { v ->
                    val vl = v.locale
                    vl?.language == localeObj.language &&
                        (vl?.country == localeObj.country || localeObj.country.isNullOrEmpty())
                }
                ?.sortedByDescending { v -> if (v.features.any { f -> f.contains("network", ignoreCase = true) }) 1 else 0 }
                ?.firstOrNull { v ->
                    val feats = v.features.joinToString(" ").lowercase() + " " + (v.name ?: "").lowercase()
                    val looksMale = feats.contains("male") && !feats.contains("female")
                    val looksFemale = feats.contains("female")
                    if (isMale) looksMale else if (wantFemale) looksFemale else true
                } ?: tts?.voices?.firstOrNull { v ->
                    val vl = v.locale
                    vl?.language == localeObj.language
                }
            if (matchedVoice != null) {
                tts?.voice = matchedVoice
            } else {
                tts?.setLanguage(localeObj)
            }

            val uid = java.util.UUID.randomUUID().toString().take(8)
            val audioDir = File(ctx.cacheDir, "audio")
            audioDir.mkdirs()
            val outputFile = File(audioDir, "tts_$uid.wav")

            val doneLatch = CountDownLatch(1)
            var synthOk = false
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onDone(utteranceId: String?) { synthOk = true; doneLatch.countDown() }
                override fun onError(utteranceId: String?) { doneLatch.countDown() }
                override fun onStart(utteranceId: String?) {}
            })
            val result = tts?.synthesizeToFile(safeText, android.os.Bundle.EMPTY, outputFile, "tts_$uid")
            if (result != android.speech.tts.TextToSpeech.SUCCESS) return null
            if (!doneLatch.await(30, TimeUnit.SECONDS) || !synthOk) return null
            if (!outputFile.exists() || outputFile.length() <= 0) return null
            outputFile.readBytes()
        } catch (e: Exception) {
            AppLogger.e("EdgeTTS", "Android TTS failed", e)
            null
        } finally {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (_: Exception) {}
        }
    }

    private fun fetchWebImage(query: String): String {
        return try {
            val url = java.net.URL("https://duckduckgo.com/i.js?q=${java.net.URLEncoder.encode(query, "UTF-8")}&o=json&p=1")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            if (conn.responseCode != 200) {
                conn.disconnect()
                return ""
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val json = gson.fromJson(body, JsonObject::class.java)
            val results = json.getAsJsonArray("results")
            if (results != null && results.size() > 0) {
                for (i in 0 until results.size()) {
                    val item = results.get(i).asJsonObject
                    val imageUrl = item.get("image")?.asString ?: item.get("thumbnail")?.asString ?: ""
                    if (imageUrl.isNotBlank()) {
                        return imageUrl
                    }
                }
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun executeImageGeneration(prompt: String, requestedModel: String = ""): String {
        val ctx = context
        val lowerPrompt = prompt.lowercase()
        val lowerModel = requestedModel.lowercase()

        val specifiedModel = when {
            lowerModel.contains("chatgpt") -> "chatgpt"
            lowerModel.contains("dalle") || lowerModel.contains("dall-e") -> "dalle"
            lowerModel.contains("antigravity") -> "antigravity"
            lowerModel.contains("flux") -> "flux"
            lowerModel.contains("turbo") -> "turbo"
            lowerModel.contains("imagen") || lowerModel.contains("gemini") || lowerModel.contains("google") -> "imagen"
            lowerModel.contains("sdxl") -> "sdxl"
            lowerPrompt.contains("model:chatgpt") || lowerPrompt.contains("using chatgpt") || lowerPrompt.contains("with chatgpt") -> "chatgpt"
            lowerPrompt.contains("model:dalle") || lowerPrompt.contains("model:dall-e") || lowerPrompt.contains("using dalle") || lowerPrompt.contains("with dalle") -> "dalle"
            lowerPrompt.contains("model:antigravity") || lowerPrompt.contains("using antigravity") || lowerPrompt.contains("with antigravity") -> "antigravity"
            lowerPrompt.contains("model:flux") || lowerPrompt.contains("using flux") || lowerPrompt.contains("with flux") -> "flux"
            lowerPrompt.contains("model:turbo") || lowerPrompt.contains("using turbo") || lowerPrompt.contains("with turbo") -> "turbo"
            lowerPrompt.contains("model:imagen") || lowerPrompt.contains("using imagen") || lowerPrompt.contains("with imagen") -> "imagen"
            lowerPrompt.contains("model:google") || lowerPrompt.contains("using google") || lowerPrompt.contains("with google") -> "imagen"
            lowerPrompt.contains("model:gemini") || lowerPrompt.contains("using gemini") || lowerPrompt.contains("with gemini") -> "imagen"
            lowerPrompt.contains("model:sdxl") || lowerPrompt.contains("using sdxl") || lowerPrompt.contains("with sdxl") -> "sdxl"
            lowerPrompt.contains("using pollinations") || lowerPrompt.contains("with pollinations") -> "pollinations"
            else -> ""
        }

        val cleanPrompt = prompt
            .replace(Regex("""(?i)(model:\s*\w+|using\s+(chatgpt|dalle|dall-e|antigravity|flux|turbo|imagen|google|gemini|sdxl|pollinations)|with\s+(chatgpt|dalle|dall-e|antigravity|flux|turbo|imagen|google|gemini|sdxl|pollinations))"""), "")
            .trim()
            .ifEmpty { prompt }

        // 1a. ChatGPT Headless Single-Session Image Generation (Default image engine if connected and no specific model requested)
        if (ctx != null && (specifiedModel == "chatgpt" || (specifiedModel.isBlank() && lowerModel.isBlank()) || (specifiedModel == "dalle" && ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx).getApiKey("openai").isBlank()))) {
            val bridge = ai.deepcode.android.service.chatgpt.ChatGPTHeadlessBridge.getInstance(ctx)
            if (bridge.isConfigured()) {
                AppLogger.i("ToolExecutor", "Using ChatGPT headless image generation for prompt: $cleanPrompt")
                try {
                    val result = kotlinx.coroutines.runBlocking { bridge.generateImage(cleanPrompt) }
                    if (result.isNotBlank() && !result.startsWith("Error:") && !result.startsWith("No image")) {
                        AppLogger.i("ToolExecutor", "ChatGPT image generation success: $result")
                        return result
                    }
                } catch (e: Exception) {
                    AppLogger.e("ToolExecutor", "ChatGPT headless image generation failed: ${e.message}", e)
                    if (specifiedModel == "chatgpt") {
                        return "Error: ${e.message}"
                    }
                }
            }
        }

        // 1b. OpenAI DALL-E 3 / DALL-E 2
        if (ctx != null && (specifiedModel == "dalle" || lowerModel.contains("openai") || lowerModel.contains("dall-e"))) {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val openAiKey = prefs.getApiKey("openai")
            if (openAiKey.isNotBlank()) {
                val dalleResult = executeOpenAIDallE(cleanPrompt, openAiKey, lowerModel)
                if (dalleResult.isNotBlank()) return dalleResult
            }
        }

        // 1a. Antigravity (Google Cloud Vertex AI Imagen via Antigravity OAuth)
        if (ctx != null && (specifiedModel == "antigravity" ||
                (specifiedModel.isBlank() && lowerModel.isBlank() && hasConfiguredAntigravity(ctx)))) {
            val result = executeAntigravityImage(cleanPrompt, ctx)
            if (result.isNotBlank()) return result
        }

        // 1b. User-configured Cloudflare Workers AI (preferred whenever credentials are set up)
        // Only kicks in when the user did not explicitly request another generator model.
        if (ctx != null && specifiedModel.isBlank() && (lowerModel.contains("cloudflare")
                || listOf("dreamshaper", "sdxl", "lightning", "flux-2").any { lowerModel.contains(it) }
                || lowerPrompt.contains("cloudflare")
                || hasConfiguredCloudflare(ctx))) {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val accountId = prefs.getSetting("cloudflare_account_id", "").trim()
            if (accountId.isNotEmpty()) {
                val cfKeys = prefs.getApiKeys("cloudflare").filter { it.isNotBlank() }
                for (key in cfKeys) {
                    val cfResult = executeCloudflareImage(cleanPrompt, accountId, key, ctx, lowerModel)
                    if (cfResult.isNotBlank() && !cfResult.startsWith("http")) {
                        return cfResult
                    }
                }
            }
        }

        // 1. If user specifically requested a model, execute that model directly
        if (specifiedModel == "imagen") {
            if (ctx != null) {
                val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
                val geminiKey = prefs.getApiKey("gemini")
                if (geminiKey.isNotBlank()) {
                    val result = executeGeminiImagen(cleanPrompt, geminiKey, ctx)
                    if (!result.isNullOrBlank()) return result
                }
            }
        } else if (specifiedModel == "flux" || specifiedModel == "turbo" || specifiedModel == "sdxl" || specifiedModel == "pollinations") {
            val modelParam = if (specifiedModel == "turbo") "turbo" else if (specifiedModel == "sdxl") "sdxl" else "flux"
            val encodedPrompt = try { java.net.URLEncoder.encode(cleanPrompt, "UTF-8") } catch (_: Exception) { cleanPrompt }
            val pollUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&seed=${System.currentTimeMillis()}&model=$modelParam&nologo=true"
            val localPath = downloadImageToLocalFile(pollUrl, ctx)
            if (localPath.isNotBlank() && !localPath.startsWith("http")) return localPath
        }

        // 2. Default Image Generator: Cloudflare Worker API
        val workerResult = executeDefaultCloudflareWorkerImage(cleanPrompt, ctx)
        if (workerResult.isNotBlank() && !workerResult.startsWith("http")) {
            return workerResult
        }

        // 3. Fallback: Google Gemini Imagen 3
        if (ctx != null) {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val geminiKey = prefs.getApiKey("gemini")
            if (geminiKey.isNotBlank()) {
                val imagenResult = executeGeminiImagen(cleanPrompt, geminiKey, ctx)
                if (!imagenResult.isNullOrBlank()) return imagenResult
            }
        }

        // 4. Fallback: Pollinations AI FLUX
        val encodedPrompt = try { java.net.URLEncoder.encode(cleanPrompt, "UTF-8") } catch (_: Exception) { cleanPrompt }
        val pollSeed = System.currentTimeMillis()
        val candidateUrls = listOf(
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&seed=$pollSeed&nologo=true&model=flux",
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&seed=$pollSeed&model=turbo"
        )

        for (url in candidateUrls) {
            val localPath = downloadImageToLocalFile(url, ctx)
            if (localPath.isNotBlank() && !localPath.startsWith("http")) {
                return localPath
            }
        }

        // 5. Fallback: Real photo web search
        val realPhotoPath = fetchRealPhotoFromWeb(cleanPrompt, ctx)
        if (realPhotoPath.isNotBlank() && !realPhotoPath.startsWith("http")) {
            return realPhotoPath
        }

        return createLocalFallbackImage(cleanPrompt, ctx)
    }

    private fun executeDefaultCloudflareWorkerImage(prompt: String, ctx: Context?): String {
        if (ctx == null) return ""
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val jsonBody = com.google.gson.JsonObject().apply {
                    addProperty("prompt", prompt)
                }.toString()

                val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = okhttp3.Request.Builder()
                    .url("https://free-image-generation-api.harshdeepdhiman79.workers.dev/")
                    .post(requestBody)
                    .header("Authorization", "Bearer Rv4LcPMTJzHK7dLaAVO-0kFSi5EEFoZ")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                    .build()

                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        val bytes = resp.body!!.bytes()
                        if (bytes.size > 500) {
                            val picsDir = java.io.File(ctx.filesDir, "Pictures")
                            if (!picsDir.exists()) picsDir.mkdirs()
                            val outFile = java.io.File(picsDir, "worker_img_${System.currentTimeMillis()}.png")
                            outFile.writeBytes(bytes)
                            AppLogger.i("ImageGen", "Successfully generated image via Cloudflare Worker API: ${outFile.absolutePath} (${bytes.size} bytes)")
                            return@runBlocking outFile.absolutePath
                        }
                    }
                    ""
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ImageGen", "Default Cloudflare Worker image gen failed: ${e.message}", e)
            ""
        }
    }

    private fun fetchRealPhotoFromWeb(prompt: String, ctx: Context?): String {
        if (ctx == null) return ""
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val encoded = try { java.net.URLEncoder.encode(prompt, "UTF-8") } catch (_: Exception) { prompt }
                val wikiSearchUrl = "https://commons.wikimedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srnamespace=6&format=json"

                val req = okhttp3.Request.Builder()
                    .url(wikiSearchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        val jsonStr = resp.body!!.string()
                        val jsObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                        val searchArr = jsObj.getAsJsonObject("query")?.getAsJsonArray("search")
                        if (searchArr != null && searchArr.size() > 0) {
                            val rawTitle = searchArr.get(0).asJsonObject.get("title")?.asString ?: ""
                            if (rawTitle.isNotEmpty()) {
                                val encTitle = try { java.net.URLEncoder.encode(rawTitle, "UTF-8") } catch (_: Exception) { rawTitle }
                                val fileInfoUrl = "https://commons.wikimedia.org/w/api.php?action=query&titles=$encTitle&prop=imageinfo&iiprop=url&format=json"
                                val infoReq = okhttp3.Request.Builder().url(fileInfoUrl).header("User-Agent", "Mozilla/5.0").build()
                                client.newCall(infoReq).execute().use { infoResp ->
                                    if (infoResp.isSuccessful && infoResp.body != null) {
                                        val infoJson = infoResp.body!!.string()
                                        val infoJs = com.google.gson.JsonParser.parseString(infoJson).asJsonObject
                                        val pagesObj = infoJs.getAsJsonObject("query")?.getAsJsonObject("pages")
                                        if (pagesObj != null) {
                                            for (key in pagesObj.keySet()) {
                                                val page = pagesObj.getAsJsonObject(key)
                                                val imageInfo = page.getAsJsonArray("imageinfo")
                                                if (imageInfo != null && imageInfo.size() > 0) {
                                                    val photoUrl = imageInfo.get(0).asJsonObject.get("url")?.asString ?: ""
                                                    if (photoUrl.isNotEmpty()) {
                                                        val localFile = downloadImageToLocalFile(photoUrl, ctx)
                                                        if (localFile.isNotBlank() && !localFile.startsWith("http")) {
                                                            return@runBlocking localFile
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val picsumFile = downloadImageToLocalFile("https://picsum.photos/1024/1024", ctx)
                if (picsumFile.isNotBlank() && !picsumFile.startsWith("http")) {
                    return@runBlocking picsumFile
                }
                ""
            }
        } catch (e: Exception) {
            AppLogger.e("ImageGen", "Real photo fetch error: ${e.message}", e)
            ""
        }
    }

    private fun downloadImageToLocalFile(imageUrl: String, ctx: Context?): String {
        if (ctx == null) return imageUrl
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val req = okhttp3.Request.Builder()
                    .url(imageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        val bytes = resp.body!!.bytes()
                        if (bytes.size > 100) {
                            val picsDir = java.io.File(ctx.filesDir, "Pictures")
                            if (!picsDir.exists()) picsDir.mkdirs()
                            val outFile = java.io.File(picsDir, "gen_img_${System.currentTimeMillis()}.png")
                            outFile.writeBytes(bytes)
                            AppLogger.i("ImageGen", "Successfully downloaded image to: ${outFile.absolutePath} (${bytes.size} bytes)")
                            return@runBlocking outFile.absolutePath
                        }
                    }
                    imageUrl
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ImageGen", "Failed to download image locally: ${e.message}", e)
            imageUrl
        }
    }

    private fun createLocalFallbackImage(prompt: String, ctx: Context?): String {
        if (ctx == null) return ""
        return try {
            val width = 1024
            val height = 1024
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            
            val gradient = android.graphics.LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            
            paint.shader = null
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 12f
            paint.color = 0xFFF5A623.toInt()
            canvas.drawRoundRect(24f, 24f, width - 24f, height - 24f, 32f, 32f, paint)

            paint.style = android.graphics.Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = 42f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("🎨 Generated AI Art", width / 2f, height / 2f - 40f, paint)
            
            paint.textSize = 28f
            paint.color = 0xFFCCCCCC.toInt()
            val displayPrompt = if (prompt.length > 50) prompt.take(50) + "..." else prompt
            canvas.drawText("\"$displayPrompt\"", width / 2f, height / 2f + 40f, paint)

            val picsDir = java.io.File(ctx.filesDir, "Pictures")
            if (!picsDir.exists()) picsDir.mkdirs()
            val outFile = java.io.File(picsDir, "gen_img_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(outFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            AppLogger.i("ImageGen", "Created local fallback bitmap at: ${outFile.absolutePath}")
            outFile.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun executeGeminiImagen(prompt: String, apiKey: String, ctx: Context): String? {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict?key=$apiKey"
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val payload = JsonObject().apply {
                val instances = com.google.gson.JsonArray().apply {
                    val inst = JsonObject()
                    inst.addProperty("prompt", prompt)
                    add(inst)
                }
                add("instances", instances)
                val params = JsonObject().apply {
                    addProperty("sampleCount", 1)
                    addProperty("aspectRatio", "1:1")
                    addProperty("outputMimeType", "image/jpeg")
                }
                add("parameters", params)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val predictions = json.getAsJsonArray("predictions") ?: return null
                if (predictions.size() == 0) return null
                val bytesB64 = predictions.get(0).asJsonObject.get("bytesBase64Encoded")?.asString ?: return null
                val imageBytes = android.util.Base64.decode(bytesB64, android.util.Base64.DEFAULT)

                val imgFile = java.io.File(ctx.cacheDir, "gemini_img_${System.currentTimeMillis()}.jpg")
                imgFile.writeBytes(imageBytes)
                "file://${imgFile.absolutePath}"
            }
        } catch (_: Exception) { null }
    }

    fun executeOpenAIDallE(prompt: String, apiKey: String, requestedModel: String = "dall-e-3"): String {
        return try {
            val ctx = context
            val cleanKey = apiKey.trim().removePrefix("Bearer ").trim()
            if (cleanKey.isEmpty()) return ""

            val modelName = if (requestedModel.contains("dall-e-2")) "dall-e-2" else "dall-e-3"

            val url = java.net.URL("https://api.openai.com/v1/images/generations")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $cleanKey")
            conn.setRequestProperty("Content-Type", "application/json")

            val jsonBody = com.google.gson.JsonObject().apply {
                addProperty("model", modelName)
                addProperty("prompt", prompt)
                addProperty("n", 1)
                addProperty("size", "1024x1024")
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val respStr = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = com.google.gson.JsonParser.parseString(respStr).asJsonObject
                if (json.has("data") && json.getAsJsonArray("data").size() > 0) {
                    val imgObj = json.getAsJsonArray("data")[0].asJsonObject
                    val imgUrl = if (imgObj.has("url")) imgObj.get("url").asString else ""
                    if (imgUrl.isNotEmpty()) {
                        val localFile = downloadImageToLocalFile(imgUrl, ctx)
                        return if (localFile.isNotBlank()) localFile else imgUrl
                    }
                }
            } else {
                conn.disconnect()
            }
            ""
        } catch (e: Exception) {
            AppLogger.e("ImageGen", "DALL-E image generation error: ${e.message}", e)
            ""
        }
    }

    private fun executeAntigravityImage(prompt: String, ctx: Context): String {
        return try {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val oauthToken = prefs.getSetting("oauth_token_antigravity", "")
            val projectId = prefs.getSetting("oauth_project_antigravity", "")
            if (oauthToken.isBlank() || !oauthToken.startsWith("ya29.")) {
                AppLogger.w("ImageGen", "Antigravity image gen skipped: no valid Antigravity OAuth token")
                return ""
            }
            if (projectId.isBlank()) {
                AppLogger.w("ImageGen", "Antigravity image gen skipped: no Antigravity project ID")
                return ""
            }

            // Try Cloud Code's Gemini/Imagen endpoint first, fall back to Vertex AI Imagen
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val targets = listOf(
                "https://daily-cloudcode-pa.googleapis.com/v1internal:streamGenerateContent?alt=sse",
                "https://us-central1-aiplatform.googleapis.com/v1/projects/$projectId/locations/us-central1/publishers/google/models/imagen-3.0-generate-002:predict"
            )

            for (url in targets) {
                try {
                    val payload: com.google.gson.JsonObject
                    if (url.contains("daily-cloudcode")) {
                        // Match Antigravity's real envelope format (roles lowercase,
                        // request wrapper, model at top level) so the API accepts it.
                        val activeModel = prefs.getSetting("chat_model", "gemini-3-pro-preview")
                            .split("/").lastOrNull() ?: "gemini-3-pro-preview"
                        payload = com.google.gson.JsonObject().apply {
                            addProperty("model", activeModel)
                            addProperty("userAgent", "Antigravity/4.2.0 (X11; Linux x86_64) Chrome/142.0.7444.175 Electron/39.2.3")
                            addProperty("requestType", "agent")
                            addProperty("requestId", java.util.UUID.randomUUID().toString())
                            add("enabledCreditTypes", com.google.gson.JsonArray().apply { add("GOOGLE_ONE_AI") })
                            add("request", com.google.gson.JsonObject().apply {
                                add("contents", com.google.gson.JsonArray().apply {
                                    add(com.google.gson.JsonObject().apply {
                                        addProperty("role", "user")
                                        add("parts", com.google.gson.JsonArray().apply {
                                            add(com.google.gson.JsonObject().apply { addProperty("text", prompt) })
                                        })
                                    })
                                })
                                add("generationConfig", com.google.gson.JsonObject().apply {
                                    addProperty("temperature", 0.7)
                                    addProperty("maxOutputTokens", 16384)
                                    add("responseModalities", com.google.gson.JsonArray().apply { add("TEXT"); add("IMAGE") })
                                })
                            })
                        }
                    } else {
                        payload = com.google.gson.JsonObject().apply {
                            add("instances", com.google.gson.JsonArray().apply {
                                add(com.google.gson.JsonObject().apply { addProperty("prompt", prompt) })
                            })
                            add("parameters", com.google.gson.JsonObject().apply {
                                addProperty("sampleCount", 1)
                                addProperty("aspectRatio", "1:1")
                                addProperty("outputMimeType", "image/png")
                            })
                        }
                    }
                    val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $oauthToken")
                        .header("Content-Type", "application/json")
                        .header("Accept", if (url.contains("daily-cloudcode")) "text/event-stream" else "application/json")
                        .header("User-Agent", "Antigravity/4.2.0 (X11; Linux x86_64) Chrome/142.0.7444.175 Electron/39.2.3")
                        .post(requestBody)
                        .build()

                    client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful && resp.body != null) {
                            val bodyStr = resp.body!!.string()
                            // Vertex Imagen: {"predictions":[{"bytesBase64Encoded":"...","mimeType":"image/png"}]}
                            if (!url.contains("daily-cloudcode")) {
                                val json = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
                                val predictions = json.getAsJsonArray("predictions")
                                val b64 = if (predictions != null && predictions.size() > 0) {
                                    predictions.get(0).asJsonObject.get("bytesBase64Encoded")?.asString
                                } else {
                                    json.get("bytesBase64Encoded")?.asString
                                }
                                if (b64 != null && b64.isNotEmpty()) {
                                    val imageBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    if (imageBytes.size > 500) {
                                        val imgFile = java.io.File(ctx.cacheDir, "antigravity_img_${System.currentTimeMillis()}.png")
                                        imgFile.writeBytes(imageBytes)
                                        AppLogger.i("ImageGen", "Antigravity (Vertex Imagen) generated: ${imgFile.absolutePath}")
                                        return imgFile.absolutePath
                                    }
                                }
                            } else {
                                // Cloud Code stream: SSE with {"response":{"candidates":[{...inlineData{data:"...",mimeType:"image/png"}}]}}
                                val dataRegex = Regex("""data:\s*(\{.*\})""")
                                val matches = dataRegex.findAll(bodyStr)
                                for (match in matches) {
                                    try {
                                        val json = com.google.gson.JsonParser.parseString(match.groupValues[1]).asJsonObject
                                        val candidates = json.getAsJsonObject("response")?.getAsJsonArray("candidates")
                                        if (candidates != null && candidates.size() > 0) {
                                            val content = candidates.get(0).asJsonObject.getAsJsonObject("content")
                                            val parts = content?.getAsJsonArray("parts")
                                            if (parts != null) {
                                                for (i in 0 until parts.size()) {
                                                    val part = parts.get(i).asJsonObject
                                                    if (part.has("inlineData")) {
                                                        val inline = part.getAsJsonObject("inlineData")
                                                        val b64 = inline.get("data")?.asString ?: ""
                                                        if (b64.isNotEmpty()) {
                                                            val imageBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                                            if (imageBytes.size > 500) {
                                                                val imgFile = java.io.File(ctx.cacheDir, "antigravity_img_${System.currentTimeMillis()}.png")
                                                                imgFile.writeBytes(imageBytes)
                                                                AppLogger.i("ImageGen", "Antigravity (Cloud Code) generated: ${imgFile.absolutePath}")
                                                                return imgFile.absolutePath
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasConfiguredCloudflare(ctx: Context): Boolean {
        return try {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            prefs.getSetting("cloudflare_account_id", "").trim().isNotEmpty() &&
                prefs.getApiKeys("cloudflare").any { it.isNotBlank() }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasConfiguredAntigravity(ctx: Context): Boolean {
        return try {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val token = prefs.getSetting("oauth_token_antigravity", "")
            val project = prefs.getSetting("oauth_project_antigravity", "")
            token.startsWith("ya29.") && project.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveCfModel(requestedModel: String): String? {
        val m = requestedModel.lowercase()
        return when {
            m.contains("dreamshaper") -> "@cf/lykon/dreamshaper-8-lcm"
            m.contains("sdxl-lightning") || m.contains("lightning") -> "@cf/bytedance/stable-diffusion-xl-lightning"
            m.contains("sdxl") || m.contains("stable-diffusion") -> "@cf/stabilityai/stable-diffusion-xl-base-1.0"
            m.contains("flux-2-dev") -> "@cf/black-forest-labs/flux-2-dev"
            m.contains("flux-2-klein-9b") -> "@cf/black-forest-labs/flux-2-klein-9b"
            m.contains("flux-2") || m.contains("flux") -> "@cf/black-forest-labs/flux-2-klein-4b"
            m.contains("cloudflare") -> null
            else -> null
        }
    }

    private fun executeCloudflareImage(prompt: String, accountId: String, apiToken: String, ctx: Context, requestedModel: String = ""): String {
        return try {
            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val configuredModel = prefs.getSetting("cloudflare_image_model", "@cf/lykon/dreamshaper-8-lcm")
            val model = resolveCfModel(requestedModel) ?: configuredModel
            val isFlux = model.contains("flux-2")
            val url = java.net.URL("https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$model")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer $apiToken")

            if (isFlux) {
                val boundary = "----Boundary${System.currentTimeMillis()}"
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                val body = buildString {
                    append("--${boundary}\r\n")
                    append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
                    append("$prompt\r\n")
                    append("--${boundary}\r\n")
                    append("Content-Disposition: form-data; name=\"height\"\r\n\r\n")
                    append("1024\r\n")
                    append("--${boundary}\r\n")
                    append("Content-Disposition: form-data; name=\"width\"\r\n\r\n")
                    append("1024\r\n")
                    append("--${boundary}--\r\n")
                }
                conn.outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }
            } else {
                conn.setRequestProperty("Content-Type", "application/json")
                val params = when {
                    model.contains("dreamshaper") -> """{"prompt":${gson.toJson(prompt)},"num_inference_steps":8,"guidance_scale":2.0}"""
                    model.contains("sdxl") || model.contains("stable-diffusion") -> """{"prompt":${gson.toJson(prompt)},"num_steps":20}"""
                    model.contains("lightning") -> """{"prompt":${gson.toJson(prompt)},"num_steps":4}"""
                    else -> """{"prompt":${gson.toJson(prompt)}}"""
                }
                conn.outputStream.use { os -> os.write(params.toByteArray(Charsets.UTF_8)) }
            }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() ?: "unknown" } catch (_: Exception) { "unknown" }
                conn.disconnect()
                AppLogger.w("ImageGen", "Cloudflare image gen failed (HTTP $responseCode): $errorBody")
                return ""
            }

            val imageDir = File(ctx.cacheDir, "images")
            imageDir.mkdirs()
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmssSSS", java.util.Locale.US).format(java.util.Date())

            val responseBytes = conn.inputStream.readBytes()
            conn.disconnect()

            val outputFile: File
            val responseStr = responseBytes.toString(Charsets.UTF_8)

            if (responseStr.trimStart().startsWith("{")) {
                val json = gson.fromJson(responseStr, JsonObject::class.java)
                val imgB64 = json?.getAsJsonObject("result")?.get("image")?.asString
                if (imgB64 != null && imgB64.isNotEmpty()) {
                    val imgData = java.util.Base64.getDecoder().decode(imgB64)
                    outputFile = File(imageDir, "cf_${timestamp}.jpg")
                    java.io.FileOutputStream(outputFile).use { fos -> fos.write(imgData) }
                } else {
                    AppLogger.w("ImageGen", "Cloudflare image gen returned no image in JSON response")
                    return ""
                }
            } else {
                outputFile = File(imageDir, "cf_${timestamp}.png")
                java.io.FileOutputStream(outputFile).use { fos -> fos.write(responseBytes) }
            }

            if (!outputFile.exists() || outputFile.length() <= 0) {
                AppLogger.w("ImageGen", "Cloudflare image gen produced empty file")
                return ""
            }

            outputFile.absolutePath
        } catch (e: Exception) {
            AppLogger.w("ImageGen", "Cloudflare image gen failed: ${e.message}")
            ""
        }
    }

    private fun wrapVideoUrl(url: String): String = if (url.startsWith("[")) url else "[video:$url]"

    private fun executeVideoGeneration(prompt: String): String {
        val ctx = context
        return try {
            val apiKey = if (ctx != null) {
                val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
                prefs.getApiKey("veo")
            } else {
                System.getenv("VEO_API_KEY") ?: System.getProperty("VEO_API_KEY") ?: ""
            }
            val endpointUrl = if (ctx != null) {
                val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
                prefs.getSetting("veo_endpoint_url", "https://us-central1-aiplatform.googleapis.com/v1/projects/placeholder/locations/us-central1/publishers/google/models/veo-2.0-generate-preview:predict")
            } else {
                "https://us-central1-aiplatform.googleapis.com/v1/projects/placeholder/locations/us-central1/publishers/google/models/veo-2.0-generate-preview:predict"
            }

            if (apiKey.isBlank()) {
                logW("Veo", "No Veo API key configured, falling back")
                return wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
            }

            val isGeminiApi = endpointUrl.contains("generativelanguage.googleapis.com") || endpointUrl.contains("placeholder") || apiKey.startsWith("AIzaSy") || apiKey.startsWith("AQ.")
            val targetUrl = if (isGeminiApi) {
                "https://generativelanguage.googleapis.com/v1beta/models/veo-3.1-generate-preview:predictLongRunning"
            } else {
                endpointUrl
            }

            val json = """{"instances":[{"prompt":${gson.toJson(prompt)}}],"parameters":{"sampleCount":1}}"""
            val body = json.toRequestBody("application/json".toMediaType())
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(body)
                .header("User-Agent", "DeepCode-Android/1.0")

            if (isGeminiApi) {
                requestBuilder.header("x-goog-api-key", apiKey)
            } else {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build()

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            response.close()

            if (response.code != 200) {
                logW("Veo", "API returned ${response.code}: $responseBody")
                return wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
            }

            val jsonResp = gson.fromJson(responseBody, JsonObject::class.java)

            if (isGeminiApi && jsonResp.has("name")) {
                val operationName = jsonResp.get("name").asString
                var done = false
                var videoUrl: String? = null
                var attempts = 0
                while (!done && attempts < 30) {
                    for (step in 0 until 12) {
                        if (Thread.currentThread().isInterrupted) {
                            logW("Veo", "Polling interrupted — returning fallback")
                            return wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
                        }
                        try {
                            Thread.sleep(500)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            logW("Veo", "Polling interrupted — returning fallback")
                            return wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
                        }
                    }
                    attempts++
                    val pollUrl = "https://generativelanguage.googleapis.com/v1beta/$operationName"
                    val pollRequest = Request.Builder()
                        .url(pollUrl)
                        .header("x-goog-api-key", apiKey)
                        .get()
                        .build()
                    client.newCall(pollRequest).execute().use { pollResp ->
                        if (pollResp.isSuccessful) {
                            val pollBody = pollResp.body?.string() ?: ""
                            val pollJson = gson.fromJson(pollBody, JsonObject::class.java)
                            if (pollJson.get("done")?.asBoolean == true) {
                                done = true
                                if (pollJson.has("error")) {
                                    val err = pollJson.getAsJsonObject("error")
                                    val msg = err.get("message")?.asString ?: "Veo generation operation failed"
                                    throw RuntimeException(msg)
                                }
                                val responseObj = pollJson.getAsJsonObject("response")
                                val generatedVideos = responseObj?.getAsJsonArray("generatedVideos")
                                if (generatedVideos != null && generatedVideos.size() > 0) {
                                    val videoObj = generatedVideos.get(0).asJsonObject?.getAsJsonObject("video")
                                    val uri = videoObj?.get("uri")?.asString
                                    if (!uri.isNullOrEmpty()) {
                                        videoUrl = uri
                                        if (videoUrl.contains("generativelanguage.googleapis.com") && !videoUrl.contains("key=")) {
                                            videoUrl = videoUrl + (if (videoUrl.contains("?")) "&key=$apiKey" else "?key=$apiKey")
                                        }
                                    }
                                }
                            }
                        } else {
                            logW("Veo", "Polling failed with code: ${pollResp.code}")
                        }
                    }
                }
                if (videoUrl != null) {
                    try {
                        val dlRequest = Request.Builder().url(videoUrl!!).build()
                        client.newCall(dlRequest).execute().use { dlResp ->
                            if (dlResp.isSuccessful) {
                                val videoDir = if (ctx != null) File(ctx.cacheDir, "videos") else File(System.getProperty("java.io.tmpdir"), "videos")
                                videoDir.mkdirs()
                                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmssSSS", java.util.Locale.US).format(java.util.Date())
                                val outputFile = File(videoDir, "veo_${timestamp}.mp4")
                                dlResp.body?.byteStream()?.use { input ->
                                    outputFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (outputFile.exists() && outputFile.length() > 0) {
                                    return wrapVideoUrl(outputFile.absolutePath)
                                }
                            }
                        }
                    } catch (de: Exception) {
                        logE("Veo", "Failed to download video locally, returning URL", de)
                    }
                    return wrapVideoUrl(videoUrl!!)
                }
            }

            // Fallback parsing for Vertex AI direct response
            val predictions = jsonResp?.getAsJsonArray("predictions")
            if (predictions != null && predictions.size() > 0) {
                val first = predictions.get(0).asJsonObject
                val b64 = first.get("bytesBase64Encoded")?.asString
                if (b64 != null && b64.isNotEmpty()) {
                    val videoDir = if (ctx != null) File(ctx.cacheDir, "videos") else File(System.getProperty("java.io.tmpdir"), "videos")
                    videoDir.mkdirs()
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmssSSS", java.util.Locale.US).format(java.util.Date())
                    val outputFile = File(videoDir, "veo_${timestamp}.mp4")
                    val videoData = java.util.Base64.getDecoder().decode(b64)
                    java.io.FileOutputStream(outputFile).use { fos -> fos.write(videoData) }
                    if (outputFile.exists() && outputFile.length() > 0) {
                        return wrapVideoUrl(outputFile.absolutePath)
                    }
                }
                val videoUrl = first.get("videoUrl")?.asString ?: first.get("video_url")?.asString ?: first.get("videoUri")?.asString
                if (videoUrl != null && videoUrl.isNotEmpty()) {
                    return wrapVideoUrl(videoUrl)
                }
                val gcsUri = first.get("gcsUri")?.asString ?: first.get("gcs_uri")?.asString
                if (gcsUri != null && gcsUri.isNotEmpty()) {
                    return wrapVideoUrl(gcsUri)
                }
            }

            logW("Veo", "No video found in API response: $responseBody")
            wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
        } catch (e: Exception) {
            logE("Veo", "Video generation failed", e)
            wrapVideoUrl("https://storage.googleapis.com/veo-samples/sample.mp4")
        }
    }

    private fun parseTtsLocale(voice: String): java.util.Locale {
        if (voice.isBlank()) return java.util.Locale.US
        val parts = voice.split("-")
        val langTag = if (parts.size >= 2) "${parts[0]}-${parts[1]}" else voice
        return try {
            java.util.Locale.forLanguageTag(langTag)
        } catch (_: Exception) {
            java.util.Locale.US
        }
    }

    private fun resolvePath(path: String, workingDir: String): File {
        val file = File(path)
        val base = if (file.isAbsolute) file else File(workingDir, path)
        // Contain `..` escapes inside workingDir when a project dir is set — absolute paths outside are still allowed.
        return try {
            if (workingDir.isNotBlank() && !file.isAbsolute) {
                val root = File(workingDir).canonicalFile
                val canon = base.canonicalFile
                if (!canon.path.startsWith(root.path)) File(root, base.name) else canon
            } else base.canonicalFile
        } catch (_: Exception) { base }
    }

    private fun readFile(path: String, workingDir: String, useRoot: Boolean): String {
        val file = resolvePath(path, workingDir)
        val maxChars = 256 * 1024
        val maxBytes = 256 * 1024L
        if (useRoot) {
            val escapedPath = escapeShellArg(file.absolutePath)
            val result = TerminalRunner.runCommand("cat $escapedPath", workingDir, true)
            if (!result.startsWith("Error running command:") && !result.contains("No such file") && !result.contains("Permission denied")) {
                return if (result.length > maxChars) {
                    result.take(maxChars) + "\n\n... [TRUNCATED: Content exceeds 256KB preview limit]"
                } else {
                    result
                }
            }
        }
        if (file.exists() && file.isFile) {
            // For binary files (PDF, images, etc.), return the path with a file marker instead of raw bytes
            val name = file.name.lowercase()
            if (name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".docx") || name.endsWith(".xlsx") || name.endsWith(".pptx")) {
                return "[file:${file.absolutePath}] (${file.length()} bytes)"
            }
            if (file.length() > maxBytes) {
                val bytes = ByteArray(maxBytes.toInt())
                file.inputStream().use { it.read(bytes) }
                return String(bytes, Charsets.UTF_8) + "\n\n... [TRUNCATED: File size (${file.length()} bytes) exceeds 256KB preview limit]"
            }
            return file.readText()
        }
        val drive = telegramDrive
        if (drive != null && drive.isConfigured()) {
            val fileName = File(path).name
            val result = drive.retrieveTextFile(fileName)
            if (result.isSuccess) {
                val text = result.getOrThrow()
                return if (text.length > maxChars) {
                    text.take(maxChars) + "\n\n... [TRUNCATED: File size exceeds 256KB preview limit]"
                } else {
                    text
                }
            }
        }
        return "File does not exist or is a directory: ${file.absolutePath}"
    }

    private fun writeFile(path: String, content: String, workingDir: String, useRoot: Boolean, storage: String = ""): String {
        val lowerPath = path.lowercase()
        if (lowerPath.endsWith(".pdf")) {
            val fileName = File(path).nameWithoutExtension
            return executeGotenbergPdf(fileName, content, null, "$fileName.pdf")
        }
        if (lowerPath.endsWith(".docx")) {
            return "DOCX files are not supported. Use create_pdf for PDFs instead."
        }
        val drive = telegramDrive
        if (storage != "local" && drive != null && drive.isConfigured()) {
            val fileName = File(path).name
            val result = drive.storeTextFile(fileName, content)
            if (result.isSuccess) {
                // Also save a local copy so [file:/path] can deliver it
                val localFile = resolvePath(path, workingDir)
                try {
                    val parent = localFile.parentFile
                    if (parent != null && !parent.exists()) parent.mkdirs()
                    localFile.writeText(content)
                } catch (_: Exception) {}
                return "Successfully wrote file: ${localFile.absolutePath}"
            }
        }
        val file = resolvePath(path, workingDir)
        if (useRoot) {
            val escapedPath = escapeShellArg(file.absolutePath)
            val base64Content = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
            val parentDir = file.parentFile
            if (parentDir != null) {
                TerminalRunner.runCommand("mkdir -p ${escapeShellArg(parentDir.absolutePath)}", workingDir, true)
            }
            val cmd = "echo '$base64Content' | base64 -d > $escapedPath"
            val result = TerminalRunner.runCommand(cmd, workingDir, true)
            if (result.trim().isEmpty() || result.contains("success")) {
                return "Successfully wrote file (root): ${file.absolutePath}"
            }
        }
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            file.writeText(content)
            "Successfully wrote file: ${file.absolutePath}"
        } catch (e: Exception) {
            "Failed to write file: ${e.message}"
        }
    }

    private fun listDirectory(path: String, workingDir: String, useRoot: Boolean): String {
        val normalized = path.trim().trimEnd('/')
        if (normalized == ".tgdrive" || normalized.endsWith("/.tgdrive")) {
            val drive = telegramDrive
            if (drive != null && drive.isConfigured()) {
                val files = drive.listFiles()
                if (files.isEmpty()) return "Telegram Drive is empty"
                return files.joinToString("\n") { "f | $it | telegram" }
            }
            return "Telegram Drive not configured"
        }
        val dir = resolvePath(path, workingDir)
        if (useRoot) {
            val escapedPath = escapeShellArg(dir.absolutePath)
            val result = TerminalRunner.runCommand("ls -la $escapedPath", workingDir, true)
            if (!result.startsWith("Error running command:") && !result.contains("Permission denied")) {
                return result
            }
        }
        return if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files.isNullOrEmpty()) {
                "Empty directory"
            } else {
                files.joinToString("\n") { f ->
                    "${if (f.isDirectory) "d" else "f"} | ${f.name} | ${f.length()} bytes"
                }
            }
        } else {
            "Directory does not exist or is a file: ${dir.absolutePath}"
        }
    }

    private fun grepSearch(query: String, path: String, workingDir: String, useRoot: Boolean): String {
        if (query.isBlank()) return "Missing query argument"
        if (query.length > 500) return "Grep failed: query too long (max 500 chars)"
        val target = resolvePath(path, workingDir)
        if (useRoot) {
            val escapedQuery = escapeShellArg(query)
            val escapedPath = escapeShellArg(target.absolutePath)
            return TerminalRunner.runCommand("grep -rnw $escapedPath -e $escapedQuery | head -n 100", workingDir, true)
        }
        return try {
            val results = mutableListOf<String>()
            val skipDirs = setOf(".git", ".gradle", "build", "node_modules", ".idea", ".kotlin")
            val skipExt = setOf("png", "jpg", "jpeg", "gif", "mp3", "mp4", "zip", "pdf", "apk", "aab", "so", "dex")
            target.walkTopDown().maxDepth(12).onEnter { dir -> dir.name !in skipDirs && !dir.name.startsWith(".") || dir == target }.forEach { file ->
                if (results.size >= 100) return@forEach
                if (!file.isFile) return@forEach
                try {
                    if (file.length() > 1024 * 1024) return@forEach
                    if (file.extension.lowercase() in skipExt) return@forEach
                    file.useLines { lines ->
                        lines.forEachIndexed { idx, line ->
                            if (results.size >= 100) return@forEachIndexed
                            if (line.contains(query)) {
                                results.add("${file.absolutePath}:${idx + 1}: ${line.take(500)}")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            if (results.isEmpty()) "No matches found" else results.joinToString("\n")
        } catch (e: Exception) {
            "Grep failed: ${e.message}"
        }
    }

    private fun createFileOrDirectory(path: String, isDir: Boolean, workingDir: String, useRoot: Boolean): String {
        val file = resolvePath(path, workingDir)
        if (useRoot) {
            val escapedPath = escapeShellArg(file.absolutePath)
            val cmd = if (isDir) "mkdir -p $escapedPath" else "touch $escapedPath"
            val result = TerminalRunner.runCommand(cmd, workingDir, true)
            return "File/Directory creation command executed. Output: $result"
        }
        return try {
            if (isDir) {
                if (file.mkdirs()) "Directory created: ${file.absolutePath}" else "Failed to create directory"
            } else {
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                if (file.createNewFile()) "File created: ${file.absolutePath}" else "File already exists or failed to create"
            }
        } catch (e: Exception) {
            "Creation failed: ${e.message}"
        }
    }

    private fun deleteFileOrDirectory(path: String, workingDir: String, useRoot: Boolean): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/" || trimmed == "." || trimmed == "./") return "Delete failed: refusing to delete root/empty path"
        val file = resolvePath(trimmed, workingDir)
        try {
            val canon = file.canonicalPath
            if (canon == "/" || canon == "/storage" || canon == "/storage/emulated" || canon == "/storage/emulated/0") {
                return "Delete failed: refusing to delete system path $canon"
            }
        } catch (_: Exception) {}
        if (useRoot) {
            val escapedPath = escapeShellArg(file.absolutePath)
            val result = TerminalRunner.runCommand("rm -rf $escapedPath", workingDir, true)
            return "Delete command executed. Output: $result"
        }
        return try {
            if (file.deleteRecursively()) "Deleted successfully: ${file.absolutePath}" else "Delete failed"
        } catch (e: Exception) {
            "Delete failed: ${e.message}"
        }
    }

    private fun htmlToText(html: String): String {
        var text = html
        text = text.replace(Regex("""<(script|style)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        text = text.replace(Regex("""(?i)</?(h[1-6]|p|div|tr|br|li|ul|ol|table)[^>]*>"""), "\n")
        text = text.replace(Regex("""<[^>]+>"""), "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun getTinyFishApiKeys(): List<String> {
        val ctx = context
        val saved = if (ctx != null) ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx).getSetting("api_key_tinyfish", "") else ""
        val keys = mutableListOf<String>()
        if (saved.isNotBlank()) keys.add(saved.trim())
        keys.addAll(listOf(
            "sk-tinyfish-aK8cMadRILADH2TCnLBYET-qX-XnH4QN",
            "sk-tinyfish-DjC09TqxENUb1E4iwuQOGGhsWgs9bfQc",
            "sk-tinyfish-5rUK4e-6nnSHkFmvR3cCmAYaiIFvbXox"
        ))
        return keys.distinct()
    }

    private fun getTinyFishApiKey(): String = getTinyFishApiKeys().firstOrNull() ?: ""

    /**
     * 1st Priority Web Search Engine — TinyFish Search API
     */
    private fun fetchTinyFishSearch(query: String, numResults: Int = 8): String? {
        val keys = getTinyFishApiKeys()
        if (keys.isEmpty()) return null
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://api.search.tinyfish.ai?query=$encodedQuery"
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        for (apiKey in keys) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("X-API-Key", apiKey)
                    .header("User-Agent", "DeepCode-Android/1.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                    val results = json.getAsJsonArray("results") ?: return@use
                    if (results.size() == 0) return@use

                    val sb = StringBuilder()
                    sb.appendLine("Search results for '$query' (via TinyFish):")
                    sb.appendLine()
                    val limit = minOf(results.size(), numResults)
                    for (i in 0 until limit) {
                        val item = results.get(i).asJsonObject
                        val pos = item.get("position")?.asInt ?: (i + 1)
                        val title = item.get("title")?.asString ?: "No title"
                        val link = item.get("url")?.asString ?: ""
                        val snippet = item.get("snippet")?.asString ?: ""
                        val siteName = item.get("site_name")?.asString ?: ""

                        sb.appendLine("$pos. $title ${if (siteName.isNotEmpty()) "[$siteName]" else ""}")
                        if (link.isNotEmpty()) sb.appendLine("   URL: $link")
                        if (snippet.isNotEmpty()) sb.appendLine("   $snippet")
                        sb.appendLine()
                    }
                    val resultStr = sb.toString().trim()
                    if (resultStr.isNotEmpty()) return resultStr
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 1st Priority Web Fetch Engine — TinyFish Fetch API
     */
    private fun fetchTinyFishFetch(targetUrl: String, raw: Boolean = false): String? {
        val keys = getTinyFishApiKeys()
        if (keys.isEmpty()) return null
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val payload = JsonObject().apply {
            val urls = com.google.gson.JsonArray()
            urls.add(targetUrl)
            add("urls", urls)
        }

        for (apiKey in keys) {
            try {
                val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("https://api.fetch.tinyfish.ai")
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                    val results = json.getAsJsonArray("results") ?: return@use
                    if (results.size() == 0) return@use

                    val first = results.get(0).asJsonObject
                    val text = first.get("text")?.asString ?: return@use
                    val title = first.get("title")?.asString
                    val finalUrl = first.get("final_url")?.asString ?: targetUrl

                    val sb = StringBuilder()
                    if (!title.isNullOrBlank()) sb.appendLine("# $title\nURL: $finalUrl\n")
                    sb.append(text)
                    val resultText = sb.toString().trim()
                    if (resultText.isNotEmpty()) {
                        return if (raw) resultText else resultText.take(40000)
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * TinyFish Web Automation Agent API
     */
    private fun runTinyFishAgent(targetUrl: String, goal: String): String {
        val apiKey = getTinyFishApiKey()
        if (apiKey.isBlank()) return "Error: TinyFish API key not configured"
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val payload = JsonObject().apply {
                addProperty("url", targetUrl)
                addProperty("goal", goal)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("https://agent.tinyfish.ai/v1/automation/run")
                .header("X-API-Key", apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val body = resp.body?.string() ?: return "Error: Empty response from TinyFish Agent"
                if (!resp.isSuccessful) return "TinyFish Agent HTTP ${resp.code}: $body"

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val status = json.get("status")?.asString ?: "UNKNOWN"
                val resultObj = json.getAsJsonObject("result")
                val resultText = resultObj?.get("result")?.asString ?: body

                "TinyFish Agent [$status]:\n$resultText"
            }
        } catch (e: Exception) {
            "TinyFish Agent failed: ${e.message}"
        }
    }

    private fun webFetch(url: String, throwOnError: Boolean = false, raw: Boolean = false): String {
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            val msg = "Web fetch failed: only http(s) URLs allowed"
            if (throwOnError) throw RuntimeException(msg) else return msg
        }
        // Block cloud-metadata + loopback SSRF targets; LAN hosts still allowed for local dev servers.
        try {
            val host = java.net.URL(url).host.lowercase()
            if (host == "169.254.169.254" || host == "metadata.google.internal" || host == "[::1]") {
                val msg = "Web fetch failed: blocked host $host"
                if (throwOnError) throw RuntimeException(msg) else return msg
            }
        } catch (_: Exception) {}
        // 1st Priority: TinyFish Fetch API
        val tinyFishResult = fetchTinyFishFetch(url, raw = raw)
        if (!tinyFishResult.isNullOrBlank()) {
            return tinyFishResult
        }

        // Standard HTTP / JSoup Fallback
        return try {
            val clientBuilder = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            val activeProxy = ai.deepcode.android.util.VpnManager.getActiveProxy()
            if (activeProxy != null) {
                clientBuilder.proxy(activeProxy)
            }
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            val response = clientBuilder.build().newCall(request).execute()
            response.use {
                val body = it.body?.string()
                if (body == null) return if (throwOnError) throw RuntimeException("Empty response body") else "Empty response"
                if (!it.isSuccessful) {
                    val msg = "HTTP ${it.code} fetching $url"
                    if (throwOnError) throw RuntimeException(msg) else return msg
                }
                val contentType = response.header("Content-Type") ?: ""
                val isHtml = contentType.contains("html", ignoreCase = true) || body.trim().startsWith("<")
                val cleanText = if (raw) body else if (isHtml) htmlToText(body) else body
                if (raw) cleanText else cleanText.take(40000)
            }
        } catch (e: Exception) {
            if (throwOnError) throw e
            "Web fetch failed: ${e.message}"
        }
    }


    private data class GeoResult(val lat: Double, val lon: Double, val name: String)

    private fun fetchGeo(query: String): GeoResult? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val result = webFetch(url)
            val json = com.google.gson.JsonParser.parseString(result).asJsonObject
            val results = json.getAsJsonArray("results")
            if (results != null && results.size() > 0) {
                val first = results.get(0).asJsonObject
                val lat = first.get("latitude").asDouble
                val lon = first.get("longitude").asDouble
                val name = first.get("name").asString
                GeoResult(lat, lon, name)
            } else null
        } catch (e: Exception) { null }
    }

    private fun fetchWeather(lat: Double, lon: Double, name: String): String {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto"
            val result = webFetch(url)
            val json = com.google.gson.JsonParser.parseString(result).asJsonObject
            val current = json.getAsJsonObject("current") ?: return "Weather data unavailable"
            val temp = current.get("temperature_2m")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val feels = current.get("apparent_temperature")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val humidity = current.get("relative_humidity_2m")?.asDouble?.let { String.format("%.0f", it) } ?: "?"
            val wind = current.get("wind_speed_10m")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val precip = current.get("precipitation")?.asDouble?.let { String.format("%.1f", it) } ?: "0.0"
            val weatherCode = current.get("weather_code")?.asInt ?: 0
            val condition = weatherCodeToCondition(weatherCode)
            buildString {
                appendLine("Current weather in $name:")
                appendLine("Condition: $condition")
                appendLine("Temperature: $temp°C (feels like $feels°C)")
                appendLine("Humidity: $humidity%")
                appendLine("Wind Speed: $wind km/h")
                appendLine("Precipitation: $precip mm")
            }
        } catch (e: Exception) { "Weather fetch failed: ${e.message}" }
    }

    private fun weatherCodeToCondition(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2, 3 -> "Mainly clear, partly cloudy, or overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown ($code)"
    }

    /**
     * PRIMARY search: DuckDuckGo Lite — proven clean HTML (~22KB), no JS, no CAPTCHA.
     * Uses table-based result layout with nofollow links pointing to uddg= encoded URLs.
     */
    internal fun fetchDDGLite(query: String, maxResults: Int = 8): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val html = webFetch("https://lite.duckduckgo.com/lite/?q=$encoded", false, raw = true)
            if (html.startsWith("Web fetch failed") || html.length < 500) return null

            val sb = StringBuilder()
            val crawlUrls = mutableListOf<Pair<String, String>>()

            // DDG Lite uses <a rel="nofollow" href="//duckduckgo.com/l/?uddg=ENCODED_URL&rut=..." class='result-link'>
            val linkPat = Regex("""<a\s+rel="nofollow"\s+href="(//duckduckgo\.com/l/\?[^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            // Snippets are in <span class='result-snippet'>...</span>
            val snippetPat = Regex("""class='result-snippet'[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

            val linkMatches = linkPat.findAll(html).toList()
            val snippetMatches = snippetPat.findAll(html).toList()

            var count = 0
            for (i in 0 until minOf(linkMatches.size, maxResults)) {
                val rawHref = linkMatches[i].groupValues[1]
                val title = linkMatches[i].groupValues[2]
                    .replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
                if (title.isEmpty()) continue

                // Decode uddg= param to get real URL
                val url = try {
                    val hrefDecoded = rawHref.replace("&amp;", "&")
                    val uddgMatch = Regex("uddg=([^&]+)").find(hrefDecoded)
                    if (uddgMatch != null) java.net.URLDecoder.decode(uddgMatch.groupValues[1], "UTF-8")
                    else "https:$rawHref"
                } catch (_: Exception) { "https:$rawHref" }

                val snippet = if (i < snippetMatches.size) {
                    snippetMatches[i].groupValues[1]
                        .replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()
                } else ""

                sb.appendLine("${count + 1}. $title")
                if (snippet.isNotEmpty()) sb.appendLine("   $snippet")
                if (url.startsWith("http")) {
                    sb.appendLine("   $url")
                    crawlUrls.add(Pair(url, title))
                }
                count++
            }

            val text = sb.toString().trim()
            if (text.isEmpty()) return null

            val crawled = crawlTopResults(crawlUrls.take(2))
            if (crawled.isNotEmpty()) "Search results for '$query':\n$text\n\n$crawled"
            else "Search results for '$query':\n$text"
        } catch (e: Exception) { null }
    }

    /**
     * FALLBACK search: Yahoo Search — robust results with smaller page (~80KB with compression).
     * Uses Accept-Encoding gzip + reads only first 120KB to avoid OOM on Android.
     */
    internal fun fetchYahoo(query: String, maxResults: Int = 6): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            // Build request with compression to keep response small
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder()
                .url("https://search.yahoo.com/search?p=$encoded&ei=UTF-8")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val html = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                // Read with 120KB limit to avoid OOM on large responses
                val stream = resp.body?.byteStream() ?: return null
                val bytes = java.io.BufferedInputStream(stream, 8192).let { bis ->
                    val buf = ByteArray(120_000)
                    var total = 0
                    while (total < buf.size) {
                        val n = bis.read(buf, total, buf.size - total)
                        if (n == -1) break
                        total += n
                    }
                    buf.copyOf(total)
                }
                String(bytes, Charsets.UTF_8)
            }
            if (html.length < 500) return null

            val sb = StringBuilder()
            val crawlUrls = mutableListOf<Pair<String, String>>()
            // Match title+href in compTitle, then snippet in compText 
            val pat = Regex("""<div class="compTitle[^"]*"><a[^>]+href="([^"]+)"[^>]*>.*?<h3[^>]*>(.*?)</h3></a></div>\s*<div class="compText[^"]*"><p[^>]*>(.*?)</p></div>""", RegexOption.DOT_MATCHES_ALL)
            val matches = pat.findAll(html).toList()

            var count = 0
            for (m in matches.take(maxResults)) {
                val redirectUrl = m.groupValues[1]
                val title = m.groupValues[2].replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
                val snippet = m.groupValues[3].replace(Regex("<[^>]+>"), "").replace("&amp;", "&").trim()
                if (title.isEmpty()) continue

                val url = try {
                    if (redirectUrl.contains("RU=")) {
                        java.net.URLDecoder.decode(redirectUrl.substringAfter("RU=").substringBefore("/RK="), "UTF-8")
                    } else redirectUrl
                } catch (_: Exception) { redirectUrl }

                sb.appendLine("${count + 1}. $title")
                if (snippet.isNotEmpty()) sb.appendLine("   $snippet")
                if (url.startsWith("http")) {
                    sb.appendLine("   $url")
                    crawlUrls.add(Pair(url, title))
                }
                count++
            }

            val text = sb.toString().trim()
            if (text.isEmpty()) return null

            val crawled = crawlTopResults(crawlUrls.take(2))
            if (crawled.isNotEmpty()) "Search results for '$query':\n$text\n\n$crawled"
            else "Search results for '$query':\n$text"
        } catch (e: Exception) { null }
    }

    /**
     * DuckDuckGo Instant Answer API — good for factual/definition queries, fast.
     */
    private fun fetchDDGInstant(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val result = webFetch(url, false, raw = false)
            if (result.startsWith("Web fetch failed") || result.startsWith("HTTP ")) return null
            val json = com.google.gson.JsonParser.parseString(result).asJsonObject
            val answer = json.get("Answer")?.asString?.trim() ?: ""
            val abstractText = json.get("AbstractText")?.asString?.trim() ?: ""
            val definition = json.get("Definition")?.asString?.trim() ?: ""
            val abstractSource = json.get("AbstractSource")?.asString?.trim() ?: ""
            val abstractUrl = json.get("AbstractURL")?.asString?.trim() ?: ""
            val sb = StringBuilder()
            if (answer.isNotEmpty()) sb.appendLine("Answer: $answer")
            if (abstractText.isNotEmpty()) {
                if (abstractSource.isNotEmpty()) sb.appendLine("Source: $abstractSource")
                sb.appendLine(abstractText)
                if (abstractUrl.isNotEmpty()) sb.appendLine("More: $abstractUrl")
            }
            if (definition.isNotEmpty()) sb.appendLine("Definition: $definition")
            val related = json.getAsJsonArray("RelatedTopics") ?: com.google.gson.JsonArray()
            var topicCount = 0
            for (i in 0 until related.size()) {
                if (topicCount >= 4) break
                try {
                    val topic = related.get(i).asJsonObject
                    val text = topic.get("Text")?.asString ?: continue
                    val firstUrl = topic.get("FirstURL")?.asString ?: ""
                    if (text.isNotEmpty()) {
                        sb.appendLine("- $text${if (firstUrl.isNotEmpty()) " ($firstUrl)" else ""}")
                        topicCount++
                    }
                } catch (_: Exception) { continue }
            }
            sb.toString().trim().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    /**
     * Wikipedia extracts API — full introductory paragraphs.
     */
    private fun fetchWikipedia(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&redirects=1&titles=$encoded&format=json"
            val result = webFetch(url, false, raw = false)
            if (result.startsWith("Web fetch failed") || result.startsWith("HTTP ")) return null
            val json = com.google.gson.JsonParser.parseString(result).asJsonObject
            val pages = json.getAsJsonObject("query")?.getAsJsonObject("pages") ?: return null
            val sb = StringBuilder()
            for ((_, page) in pages.entrySet()) {
                val pageObj = page.asJsonObject
                if (pageObj.get("missing") != null) continue
                val title = pageObj.get("title")?.asString ?: ""
                val extract = pageObj.get("extract")?.asString ?: ""
                if (extract.isBlank()) continue
                sb.appendLine("## Wikipedia: $title")
                sb.appendLine(extract.take(3000))
                sb.appendLine("Source: https://en.wikipedia.org/wiki/${java.net.URLEncoder.encode(title, "UTF-8").replace("+", "_")}")
            }
            sb.toString().ifEmpty { null }
        } catch (e: Exception) { null }
    }

    /**
     * Fetches and cleans content from top result URLs for richer answers.
     */
    private fun crawlTopResults(urls: List<Pair<String, String>>): String {
        if (urls.isEmpty()) return ""
        val sb = StringBuilder()
        val skip = listOf("youtube.com", "youtu.be", "twitter.com", "x.com",
            "facebook.com", "instagram.com", "tiktok.com", "reddit.com")
        for ((url, title) in urls) {
            try {
                if (skip.any { url.contains(it) }) continue
                val content = webFetch(url, false, false)
                if (content.startsWith("Web fetch failed") || content.startsWith("HTTP ")) continue
                val trimmed = content.take(3000).trim()
                if (trimmed.length < 100) continue
                sb.appendLine("\n## From: $title")
                sb.appendLine(trimmed)
            } catch (_: Exception) { continue }
        }
        return sb.toString().trim()
    }

    private fun webSearch(query: String, numResults: Int = 8, livecrawl: String = "fallback", type: String = "auto", contextMaxCharacters: Int = 10000): String {
        val lower = query.lowercase()

        // 1. TinyFish Web Search (1st Priority)
        val tinyFishResult = fetchTinyFishSearch(query, numResults)
        if (!tinyFishResult.isNullOrBlank()) return tinyFishResult

        // 2. MCP-based providers (Exa, Parallel)
        val mcpResult = mcpWebSearch(query, numResults, livecrawl, type, contextMaxCharacters)
        if (mcpResult != null) return mcpResult

        // 2. Weather queries → Open-Meteo (dedicated, accurate)
        if (lower.contains("weather") || lower.contains("temperature") || lower.contains("forecast")) {
            var city = query
                .replace(Regex("(?i)\\b(whats|what's|what is|tell me|give me|show me|current|latest|the|today's|todays)\\b"), "")
                .replace(Regex("(?i)\\b(weather|temperature|forecast|conditions|report)\\b"), "")
                .replace(Regex("(?i)\\b(in|at|for|of)\\b"), "")
                .replace("?", "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (city.length in 2..50) {
                var geo = fetchGeo(city)
                if (geo == null && city.contains(",")) {
                    val firstPart = city.substringBefore(",").trim()
                    if (firstPart.length >= 2) geo = fetchGeo(firstPart)
                }
                if (geo != null) {
                    val weather = fetchWeather(geo.lat, geo.lon, geo.name)
                    if (weather.isNotEmpty()) return weather
                }
            }
        }

        // 3. DuckDuckGo Lite — proven clean 22KB HTML, no CAPTCHA, primary engine
        val ddgLite = fetchDDGLite(query, numResults)
        if (ddgLite != null) return ddgLite

        // 4. Yahoo Search — robust fallback, 120KB byte-limited read
        val yahoo = fetchYahoo(query, numResults)
        if (yahoo != null) return yahoo

        // 5. DuckDuckGo Instant Answer — good for factual/definition queries
        val ddgInstant = fetchDDGInstant(query)
        if (ddgInstant != null) return ddgInstant

        // 6. Wikipedia — encyclopedic content fallback
        val wiki = fetchWikipedia(query)
        if (wiki != null) return wiki

        return "No search results found for: $query. Please try rephrasing or use a more specific query."
    }



    private fun mcpWebSearch(query: String, numResults: Int, livecrawl: String, type: String, contextMaxCharacters: Int): String? {
        // Try MCP providers: Exa first, then Parallel
        val exaResult = try {
            callExaMcp(query, numResults, livecrawl, type, contextMaxCharacters)
        } catch (e: Exception) { null as String? }
        if (exaResult != null) return "Search results for '$query':\n$exaResult"

        val parallelResult = try {
            callParallelMcp(query)
        } catch (e: Exception) { null as String? }
        if (parallelResult != null) return "Search results for '$query':\n$parallelResult"

        return null
    }

    private fun callExaMcp(query: String, numResults: Int, livecrawl: String, type: String, contextMaxCharacters: Int): String? {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val payload = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "tools/call")
                val params = JsonObject()
                params.addProperty("name", "web_search_exa")
                val arguments = JsonObject()
                arguments.addProperty("query", query)
                arguments.addProperty("type", type)
                arguments.addProperty("numResults", numResults)
                arguments.addProperty("livecrawl", livecrawl)
                arguments.addProperty("contextMaxCharacters", contextMaxCharacters)
                params.add("arguments", arguments)
                add("params", params)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("https://mcp.exa.ai/mcp")
                .post(requestBody)
                .header("User-Agent", "DeepCode-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            response.use {
                val body = it.body?.string() ?: return null
                if (!it.isSuccessful) return null

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return null
                val sb = StringBuilder()
                for (i in 0 until minOf(content.size(), 8)) {
                    val item = content.get(i).asJsonObject
                    val text = item.get("text")?.asString ?: continue
                    val source = item.get("source")?.asString ?: ""
                    if (source.isNotEmpty()) sb.appendLine("Source: $source")
                    sb.appendLine(text.take(contextMaxCharacters))
                    sb.appendLine()
                }
                sb.toString().ifEmpty { null }
            }
        } catch (e: Exception) { null }
    }

    private fun callParallelMcp(query: String): String? {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val payload = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", 1)
                addProperty("method", "tools/call")
                val params = JsonObject()
                params.addProperty("name", "web_search")
                val arguments = JsonObject()
                arguments.addProperty("objective", query)
                val queries = com.google.gson.JsonArray()
                queries.add(query)
                arguments.add("search_queries", queries)
                arguments.addProperty("session_id", java.util.UUID.randomUUID().toString())
                params.add("arguments", arguments)
                add("params", params)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = okhttp3.Request.Builder()
                .url("https://search.parallel.ai/mcp")
                .post(requestBody)
                .header("User-Agent", "DeepCode-Android/1.0")
                .build()

            val response = client.newCall(request).execute()
            response.use {
                val body = it.body?.string() ?: return null
                if (!it.isSuccessful) return null

                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return null
                val sb = StringBuilder()
                for (i in 0 until minOf(content.size(), 8)) {
                    val item = content.get(i).asJsonObject
                    val text = item.get("text")?.asString ?: continue
                    sb.appendLine(text)
                    sb.appendLine()
                }
                sb.toString().ifEmpty { null }
            }
        } catch (e: Exception) { null }
    }

    private fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    fun getDeclaredTools(): List<Tool> {
        val coreTools = listOf(
            Tool("read_file", "Read the entire content of a file. Checks Telegram Drive if not found locally.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Path to the file to read (relative to project or absolute)")
                ),
                "required" to listOf("path")
            )),
            Tool("write_file", "Write text content (code, data, config files, markdown, etc.) to a file. NOT for PDFs — use create_pdf for PDF documents. The file will be delivered via [file:/path]. Set storage='local' to save on device only, otherwise stored to cloud + local.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Path to write (relative or absolute)"),
                    "content" to mapOf("type" to "string", "description" to "The full text content to write"),
                    "storage" to mapOf("type" to "string", "description" to "Storage backend: 'local' for device, omit for Telegram Drive")
                ),
                "required" to listOf("path", "content")
            )),
            Tool("list_directory", "List contents of a directory", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Directory path to list (relative or absolute, default is current directory)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("run_command", "Run a shell command locally on the device", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "command" to mapOf("type" to "string", "description" to "The shell command line to run")
                ),
                "required" to listOf("command")
            )),
            Tool("grep_search", "Grep search file contents for a pattern", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search pattern or query"),
                    "path" to mapOf("type" to "string", "description" to "Directory path to search in (relative or absolute, default is current directory)")
                ),
                "required" to listOf("query")
            )),
            Tool("create_file", "Create a new file or directory", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "File or directory path"),
                    "isDirectory" to mapOf("type" to "boolean", "description" to "True if creating a directory, false for file")
                ),
                "required" to listOf("path")
            )),
            Tool("delete_file", "Delete a file or directory recursively", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "File or directory path to delete")
                ),
                "required" to listOf("path")
            )),
            Tool("notion_search", "Search Notion pages and databases by query", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search query (leave empty to list all pages)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("notion_read", "Read the content of a Notion page by its ID", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "page_id" to mapOf("type" to "string", "description" to "The Notion page ID (UUID format)")
                ),
                "required" to listOf("page_id")
            )),
            Tool("notion_list_databases", "List all Notion databases in the workspace", mapOf<String, Any>(
                "type" to "object",
                "properties" to mapOf<String, Any>(),
                "required" to emptyList<String>()
            )),
            Tool("github_get_user", "Get authenticated GitHub user profile, bio, repo counts, and token permissions/scopes, or inspect any GitHub user by username", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "username" to mapOf("type" to "string", "description" to "Optional GitHub username (omit to get authenticated profile and token scopes)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("github_list_repos", "List repositories accessible to the GitHub token", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "type" to mapOf("type" to "string", "description" to "Repository type: 'all', 'owner', 'public', 'private' (default: all)"),
                    "per_page" to mapOf("type" to "number", "description" to "Results per page (default: 50)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("github_get_repo", "Get comprehensive details of a GitHub repository (description, stars, forks, issues, branch, language, URLs)", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner (username or org)"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_create_repo", "Create a new repository on GitHub under the authenticated user or organization", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Repository name"),
                    "description" to mapOf("type" to "string", "description" to "Optional repository description"),
                    "private" to mapOf("type" to "boolean", "description" to "Whether the repository should be private (default: true)"),
                    "auto_init" to mapOf("type" to "boolean", "description" to "Whether to initialize with a README (default: false)")
                ),
                "required" to listOf("name")
            )),
            Tool("github_delete_repo", "Delete a repository on GitHub (requires delete_repo token scope)", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_fork_repo", "Fork an existing repository to the authenticated user or organization", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "organization" to mapOf("type" to "string", "description" to "Optional organization name to fork into")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_list_contents", "List files and directories in a GitHub repo path", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner (user or org)"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "path" to mapOf("type" to "string", "description" to "Directory path (default: root)"),
                    "ref" to mapOf("type" to "string", "description" to "Branch, tag, or commit SHA (optional)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_read_file", "Read text content of a file from a public or private GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "path" to mapOf("type" to "string", "description" to "File path in the repo"),
                    "ref" to mapOf("type" to "string", "description" to "Branch, tag, or commit SHA (optional)")
                ),
                "required" to listOf("owner", "repo", "path")
            )),
            Tool("github_write_file", "Create or update a file in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "path" to mapOf("type" to "string", "description" to "File path to create/update"),
                    "content" to mapOf("type" to "string", "description" to "Full file content"),
                    "message" to mapOf("type" to "string", "description" to "Commit message (optional)"),
                    "sha" to mapOf("type" to "string", "description" to "File SHA (required for updating existing files)"),
                    "branch" to mapOf("type" to "string", "description" to "Target branch (default: main)")
                ),
                "required" to listOf("owner", "repo", "path", "content")
            )),
            Tool("github_delete_file", "Delete a file from a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "path" to mapOf("type" to "string", "description" to "File path to delete"),
                    "sha" to mapOf("type" to "string", "description" to "Current file blob SHA (required)"),
                    "message" to mapOf("type" to "string", "description" to "Commit message (optional)"),
                    "branch" to mapOf("type" to "string", "description" to "Target branch (default: main)")
                ),
                "required" to listOf("owner", "repo", "path", "sha")
            )),
            Tool("github_list_branches", "List branches in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_create_branch", "Create a new branch in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "branch" to mapOf("type" to "string", "description" to "New branch name"),
                    "source" to mapOf("type" to "string", "description" to "Source branch (default: main)")
                ),
                "required" to listOf("owner", "repo", "branch")
            )),
            Tool("github_list_commits", "List commit history for a repository, branch, or file path", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "sha" to mapOf("type" to "string", "description" to "Branch name or commit SHA to start from (optional)"),
                    "path" to mapOf("type" to "string", "description" to "Only commits containing this file path (optional)"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of commits (default: 15)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_get_commit", "Get detailed information about a commit including message, stats, and changed files", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "ref" to mapOf("type" to "string", "description" to "Commit SHA or ref")
                ),
                "required" to listOf("owner", "repo", "ref")
            )),
            Tool("github_list_prs", "List pull requests in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "state" to mapOf("type" to "string", "description" to "PR state: 'open', 'closed', 'all' (default: open)"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of PRs (default: 20)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_get_pr", "Get full details of a specific pull request including branches, stats, and mergeability", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Pull request number")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_get_pr_diff", "Get the unified git diff of a pull request", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Pull request number")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_create_pr", "Create a pull request on a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "title" to mapOf("type" to "string", "description" to "Pull request title"),
                    "head" to mapOf("type" to "string", "description" to "Source branch (head)"),
                    "base" to mapOf("type" to "string", "description" to "Target branch (default: main)"),
                    "body" to mapOf("type" to "string", "description" to "PR description (optional)"),
                    "draft" to mapOf("type" to "boolean", "description" to "Create as draft PR (default: false)")
                ),
                "required" to listOf("owner", "repo", "title", "head")
            )),
            Tool("github_update_pr", "Update a pull request title, body, state (open/closed), or base branch", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Pull request number"),
                    "title" to mapOf("type" to "string", "description" to "New title (optional)"),
                    "body" to mapOf("type" to "string", "description" to "New description (optional)"),
                    "state" to mapOf("type" to "string", "description" to "New state: 'open' or 'closed' (optional)"),
                    "base" to mapOf("type" to "string", "description" to "New base branch (optional)")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_merge_pr", "Merge a pull request using merge, squash, or rebase", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Pull request number"),
                    "commit_title" to mapOf("type" to "string", "description" to "Title for the merge commit (optional)"),
                    "commit_message" to mapOf("type" to "string", "description" to "Extra detail for the merge commit (optional)"),
                    "merge_method" to mapOf("type" to "string", "description" to "Merge method: 'merge', 'squash', or 'rebase' (default: merge)")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_list_issues", "List issues in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "state" to mapOf("type" to "string", "description" to "Issue state: 'open', 'closed', 'all' (default: open)"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of issues (default: 20)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_get_issue", "Get full details of a specific issue including body, labels, and author", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Issue number")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_create_issue", "Create an issue in a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "title" to mapOf("type" to "string", "description" to "Issue title"),
                    "body" to mapOf("type" to "string", "description" to "Issue body/description (optional)"),
                    "labels" to mapOf("type" to "string", "description" to "Comma-separated list of labels (optional)")
                ),
                "required" to listOf("owner", "repo", "title")
            )),
            Tool("github_update_issue", "Update an issue: close, reopen, change title, edit body, or update labels", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "number" to mapOf("type" to "number", "description" to "Issue number"),
                    "title" to mapOf("type" to "string", "description" to "New title (optional)"),
                    "body" to mapOf("type" to "string", "description" to "New body (optional)"),
                    "state" to mapOf("type" to "string", "description" to "New state: 'open' or 'closed' (optional)"),
                    "labels" to mapOf("type" to "string", "description" to "Comma-separated list of labels (optional)")
                ),
                "required" to listOf("owner", "repo", "number")
            )),
            Tool("github_list_comments", "List discussion comments on an issue or pull request", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "issue_number" to mapOf("type" to "number", "description" to "Issue or PR number"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of comments (default: 20)")
                ),
                "required" to listOf("owner", "repo", "issue_number")
            )),
            Tool("github_create_comment", "Add a comment to an issue or pull request", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "issue_number" to mapOf("type" to "number", "description" to "Issue or PR number"),
                    "body" to mapOf("type" to "string", "description" to "Comment body in markdown")
                ),
                "required" to listOf("owner", "repo", "issue_number", "body")
            )),
            Tool("github_list_releases", "List releases for a GitHub repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of releases (default: 10)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_get_latest_release", "Get the latest published release and asset download links", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_create_release", "Create a new release or tag on GitHub", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "tag_name" to mapOf("type" to "string", "description" to "The git tag name (e.g. 'v1.0.0')"),
                    "name" to mapOf("type" to "string", "description" to "Release title (default: tag_name)"),
                    "body" to mapOf("type" to "string", "description" to "Release notes/changelog"),
                    "target_commitish" to mapOf("type" to "string", "description" to "Branch or commit (default: main)"),
                    "draft" to mapOf("type" to "boolean", "description" to "Whether to save as draft (default: false)"),
                    "prerelease" to mapOf("type" to "boolean", "description" to "Whether to mark as pre-release (default: false)")
                ),
                "required" to listOf("owner", "repo", "tag_name")
            )),
            Tool("github_list_workflows", "List GitHub Actions workflows in a repository", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_trigger_workflow", "Trigger a GitHub Actions workflow dispatch run", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "workflow_id" to mapOf("type" to "string", "description" to "Workflow ID or filename (e.g. 'build.yml')"),
                    "ref" to mapOf("type" to "string", "description" to "Git ref/branch to run against (default: main)"),
                    "inputs" to mapOf("type" to "object", "description" to "Optional workflow input parameters")
                ),
                "required" to listOf("owner", "repo", "workflow_id")
            )),
            Tool("github_check_workflow", "Check workflow run status or list recent workflow runs", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "run_id" to mapOf("type" to "number", "description" to "Specific workflow run ID to check (optional)"),
                    "branch" to mapOf("type" to "string", "description" to "Branch to filter runs (default: main)"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of runs (default: 10)")
                ),
                "required" to listOf("owner", "repo")
            )),
            Tool("github_download_artifact", "Download a GitHub Actions build artifact zip file", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "owner" to mapOf("type" to "string", "description" to "Repository owner"),
                    "repo" to mapOf("type" to "string", "description" to "Repository name"),
                    "save_path" to mapOf("type" to "string", "description" to "Local file path to save the artifact zip"),
                    "artifact_id" to mapOf("type" to "number", "description" to "Specific artifact ID (optional, default: latest)")
                ),
                "required" to listOf("owner", "repo", "save_path")
            )),
            Tool("github_list_gists", "List GitHub Gists owned by the authenticated user", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "per_page" to mapOf("type" to "number", "description" to "Number of gists to list (default: 20)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("github_create_gist", "Create a new public or secret GitHub Gist", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "description" to mapOf("type" to "string", "description" to "Gist description"),
                    "filename" to mapOf("type" to "string", "description" to "File name (e.g. 'snippet.py')"),
                    "content" to mapOf("type" to "string", "description" to "Text content of the file"),
                    "public" to mapOf("type" to "boolean", "description" to "Whether the gist is public (default: false)")
                ),
                "required" to listOf("content")
            )),
            Tool("github_search_code", "Search code across GitHub repositories", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search query (supports qualifiers like repo:, language:, etc.)"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of results (default: 10)")
                ),
                "required" to listOf("query")
            )),
            Tool("github_search_repos", "Search repositories on GitHub by keywords, language, stars, etc.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search query (e.g. 'android assistant stars:>500 language:kotlin')"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of results (default: 10)")
                ),
                "required" to listOf("query")
            )),
            Tool("github_search_issues", "Search issues and pull requests across GitHub or within repositories", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search query (e.g. 'repo:owner/name is:issue is:open bug')"),
                    "per_page" to mapOf("type" to "number", "description" to "Number of results (default: 15)")
                ),
                "required" to listOf("query")
            )),
            Tool("drive_list", "List files and folders in Google Drive root or search by name", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Optional search query to filter files by name")
                ),
                "required" to emptyList<String>()
            )),
            Tool("drive_search", "Search for files in Google Drive by name or content", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Search query to find files")
                ),
                "required" to listOf("query")
            )),
            Tool("drive_read", "Read the content of a file from Google Drive by its file ID", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file_id" to mapOf("type" to "string", "description" to "The Google Drive file ID")
                ),
                "required" to listOf("file_id")
            )),
            Tool("drive_upload", "Upload a file to Google Drive", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Name of the file to create"),
                    "content" to mapOf("type" to "string", "description" to "Text content of the file"),
                    "mimeType" to mapOf("type" to "string", "description" to "MIME type (default: text/plain)")
                ),
                "required" to listOf("name", "content")
            )),
            Tool("web_search", "Search the web for current information. Use this for news, weather, research, or any real-time data query. Uses free APIs (no key needed). Supports multiple search providers with automatic fallback. IMPORTANT: Do NOT call this tool before calling create_pdf — when the user asks to create a PDF document, call create_pdf directly using your training knowledge. Only use web_search for PDFs if the user explicitly asks you to search first.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "The search query (e.g. 'weather in London', 'latest AI news', 'Python tutorial')"),
                    "numResults" to mapOf("type" to "number", "description" to "Number of search results to return (default: 8)"),
                    "livecrawl" to mapOf("type" to "string", "enum" to listOf("fallback", "preferred"), "description" to "Whether to attempt live crawling of search results: 'fallback' (use cached if available, crawl otherwise) or 'preferred' (always crawl live)"),
                    "type" to mapOf("type" to "string", "enum" to listOf("auto", "fast", "deep"), "description" to "Search type: 'auto' (balanced), 'fast' (quick results), 'deep' (comprehensive search)"),
                    "contextMaxCharacters" to mapOf("type" to "number", "description" to "Maximum characters for each result's context string (default: 10000)")
                ),
                "required" to listOf("query")
            )),
            Tool("web_fetch", "Fetch the text content of a specific URL. Use this when you have a direct URL to read.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "url" to mapOf("type" to "string", "description" to "The complete URL to fetch")
                ),
                "required" to listOf("url")
            )),
            Tool("tinyfish_agent", "Execute AI web automation on a target URL using TinyFish Agent API. Use this for complex multi-step browser interactions, structured web extraction, or goal-based web task execution.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "url" to mapOf("type" to "string", "description" to "The target website URL"),
                    "goal" to mapOf("type" to "string", "description" to "Natural language instruction/goal to perform on the website")
                ),
                "required" to listOf("url", "goal")
            )),
            Tool("search_image",
                "Search for real existing images from the web. Use this when the user asks to get, find, show, or search for an image/picture of something. Returns the URL of a found image.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "What to search for (e.g. 'cat', 'sunset', 'cyberpunk city')")
                    ),
                    "required" to listOf("query")
                )
            ),
            Tool("generate_image",
                "Generate an image from a text prompt using AI. Automatically uses the connected ChatGPT engine by default when connected. Supported model values: chatgpt, dalle, antigravity, cloudflare, flux, turbo, sdxl, imagen, pollinations. ALWAYS call this tool whenever the user asks for an image, drawing, or photo.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "The text description of the image to generate. Be detailed for best results."),
                        "model" to mapOf("type" to "string", "description" to "Optional image model to use: chatgpt, dalle, antigravity, cloudflare, flux, turbo, sdxl, imagen, or pollinations. Omit to use the default generator.")
                    ),
                    "required" to listOf("prompt")
                )
            ),
            Tool("generate_video",
                "Generate a video from a text description using Veo AI. Returns a playable video URL or file path. Use this when the user asks to create, generate, or make a video, animation, or clip.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "The text description of the video to generate. Describe scenes, motion, style, and duration.")
                    ),
                    "required" to listOf("prompt")
                )
            ),
            Tool("edge_tts",
                "Generate speech/audio from text using neural TTS. Use this when the user asks for audio, voice, speech, TTS, or 'read aloud'. Returns a playable audio file path. IMPORTANT: Do NOT set the 'voice' parameter — the voice/language is controlled by the user's /voice and /language settings. Set verbatim=true to speak the exact text (skips last-response lookup and topic narration).",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "text" to mapOf("type" to "string", "description" to "The text to convert to speech"),
                        "voice" to mapOf("type" to "string", "description" to "DO NOT USE. Omit this parameter. The user's saved voice settings will be used automatically."),
                        "rate" to mapOf("type" to "string", "description" to "DO NOT USE. Omit this parameter. The user's saved tone settings will be used automatically."),
                        "pitch" to mapOf("type" to "string", "description" to "DO NOT USE. Omit this parameter."),
                        "verbatim" to mapOf("type" to "boolean", "description" to "If true, speak text literally without resolving last-response or generating topic narration")
                    ),
                    "required" to listOf("text")
                )
            ),
            Tool("summon_agents",
                """Summon multiple specialized sub-agents to work on a complex task in parallel.
Use this when the user asks for generating substantial content, research, or multi-step work (e.g. creating study materials, reports, projects).
For simple chat, greetings, or single-step requests, respond directly — do NOT use this tool.
Describe the full task clearly — sub-agents won't see the original message.""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "task" to mapOf("type" to "string", "description" to "The full task description that sub-agents should work on")
                    ),
                    "required" to listOf("task")
                )
            ),
            Tool("webbridge_agent",
                """Executes tasks on web-based AI platforms that have no public API by automating a real browser session.

USE THIS TOOL WHEN the user asks for:
- Image search / get/find existing web images → task_type='image_search', target_site='auto'
- Image generation → task_type='image_generation', target_site='auto'
- Image analysis / vision tasks → task_type='image_analysis', target_site='auto'
- Audio or voice synthesis → task_type='audio_generation', target_site='edge_tts'
- Web search or research (Perplexity) → task_type='search', target_site='perplexity'
- Text generation on a specific platform the user names (e.g. "ask ChatGPT...") → task_type='text_generation'
- Any capability your native AI cannot do natively

For audio/speech tasks, prefer the edge_tts tool directly instead.
For image tasks, prefer generate_image or search_image tools directly instead.
Always set target_site='auto' unless the user specifically names a platform.
Always set triggered_by='primary_ai' for normal calls.
Always pass the user's exact request as user_prompt.""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "task_id" to mapOf("type" to "string", "description" to "Unique task ID (auto-generated if omitted)"),
                        "triggered_by" to mapOf("type" to "string", "description" to "Who triggered this: 'primary_ai' (default), 'app_watchdog', 'user_direct', 'recovery_system'"),
                        "task_type" to mapOf("type" to "string", "description" to "Task type: 'image_generation', 'image_analysis', 'search', 'audio_generation', 'text_generation', 'document_analysis'"),
                        "target_site" to mapOf("type" to "string", "description" to "Target site: 'auto' (recommended), 'gemini', 'claude', 'chatgpt', 'perplexity', 'midjourney', 'firefly', 'leonardo', 'elevenlabs', 'suno'"),
                        "user_prompt" to mapOf("type" to "string", "description" to "The exact prompt or content to submit to the target site"),
                        "credential_key" to mapOf("type" to "string", "description" to "Credential vault key for the target site. Leave empty to use default."),
                        "output_format" to mapOf("type" to "string", "description" to "Output format: 'text', 'image_url', 'base64_image', 'audio_url', 'markdown'"),
                        "timeout_seconds" to mapOf("type" to "integer", "description" to "Max wait in seconds. Default 120 for normal tasks, 20 for failover."),
                        "attachments" to mapOf(
                            "type" to "array",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "type" to mapOf("type" to "string", "description" to "Attachment type: 'image', 'file', 'url'"),
                                    "ref" to mapOf("type" to "string", "description" to "Base64 data or URL reference")
                                )
                            )
                        ),
                        "additional_instructions" to mapOf("type" to "string", "description" to "Extra instructions for the agent on how to handle this task"),
                        "new_conversation" to mapOf("type" to "boolean", "description" to "Start fresh conversation on the target site (default: true)")
                    ),
                    "required" to listOf("task_type", "user_prompt")
                )
            ),
            Tool("create_pdf",
                """Create a high-precision PDF document using Gotenberg API (falls back to offline layout engine if server unreachable). USE THIS IMMEDIATELY when the user asks to create any PDF — including study notes, exam papers, reports, resumes, or any document. Generates clean vector PDF and returns [file:/path/to/doc.pdf].""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "title" to mapOf("type" to "string", "description" to "The title of the document"),
                        "content" to mapOf("type" to "string", "description" to "The full text content. Supports HTML/Markdown formatting (e.g. <h2>, <p>, <ul>, <li>, <table>)."),
                        "author" to mapOf("type" to "string", "description" to "Optional author name"),
                        "filename" to mapOf("type" to "string", "description" to "Optional custom filename (without .pdf extension)"),
                        "layout" to mapOf("type" to "string", "description" to "Optional offline layout id (classic, modern-minimal, corporate-report, academic-paper, invoice-receipt, resume-cv). Uses offline engine directly.")
                    ),
                    "required" to listOf("title", "content")
                )
            ),
            Tool("set_gotenberg_url",
                """Configure the Gotenberg API server URL (default: http://192.168.1.71:3000). Gotenberg converts HTML, Markdown, and Office documents to PDF.""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "Base URL of Gotenberg server e.g. http://192.168.1.71:3000")
                    ),
                    "required" to listOf("url")
                )
            ),
            Tool("set_tts_backend",
                "Switch the TTS (text-to-speech) backend. Options: edge_tts (default, Microsoft Edge neural voices), kokoro (self-hosted Kokoro-FastAPI server), android (built-in Android TTS), google (Google Translate TTS). Requires running 'docker run -p 8880:8880 remsky/kokoro-fastapi' if using kokoro backend.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "backend" to mapOf("type" to "string", "enum" to listOf("edge_tts", "kokoro", "android", "google"), "description" to "TTS backend to use")
                    ),
                    "required" to listOf("backend")
                )
            ),
            Tool("set_kokoro_url",
                "Set the Kokoro-FastAPI server URL (for the kokoro TTS backend). Default is http://localhost:8880. On Android emulator use http://10.0.2.2:8880, on device use your PC LAN IP (e.g. http://192.168.1.100:8880). The server runs via: docker run -p 8880:8880 remsky/kokoro-fastapi",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "Full URL of the Kokoro-FastAPI server, e.g. http://192.168.1.100:8880")
                    ),
                    "required" to listOf("url")
                )
            ),
            Tool("set_tts_voice",
                "Set the TTS voice. Pass a locale like en-US / hi-IN, or an exact Edge voice like en-US-AriaNeural (saved as exact voice).",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "voice" to mapOf("type" to "string", "description" to "Locale (en-US) or exact Edge voice (en-US-AriaNeural)")
                    ),
                    "required" to listOf("voice")
                )
            ),
            Tool("analyze_pdf",
                "Analyze a PDF file and describe its layout (columns, margins, styles) so a matching layout can be recreated.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "Absolute path to the PDF file")
                    ),
                    "required" to listOf("path")
                )
            ),
            Tool("list_pdf_layouts",
                "List available built-in and custom PDF layouts for create_pdf.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf<String, Any>(),
                    "required" to listOf<String>()
                )
            ),
            Tool("create_pdf_from_reference",
                "Create a PDF whose layout matches a reference PDF file.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "reference_path" to mapOf("type" to "string", "description" to "Path to the reference PDF"),
                        "title" to mapOf("type" to "string", "description" to "Document title"),
                        "content" to mapOf("type" to "string", "description" to "Document content"),
                        "author" to mapOf("type" to "string", "description" to "Optional author"),
                        "filename" to mapOf("type" to "string", "description" to "Optional filename")
                    ),
                    "required" to listOf("reference_path", "title", "content")
                )
            ),
            Tool("generate_chatgpt_document",
                """Generate a high-precision structured document (Markdown, specification, report, table) using the headless ChatGPT single-session engine.
Suppresses all conversational filler and returns clean formatted document content and local file path.""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "Document requirements, topic, sections, or details"),
                        "format" to mapOf("type" to "string", "description" to "Format style: 'markdown', 'report', 'guide', 'specs'. Default is 'markdown'")
                    ),
                    "required" to listOf("prompt")
                )
            ),
            Tool("schedule_chatgpt_task",
                """Schedule a recurring or delayed task on ChatGPT directly from chat. The task will be managed in DeepCode's Automations page.
Can be used for:
- Recurring image generations (e.g. daily wallpapers)
- Scheduled document creations (e.g. daily/weekly briefs, summaries, reports)
- Automated periodic ChatGPT tasks

The task strictly runs within DeepCode's single persistent ChatGPT conversation session and outputs results into a dedicated chat session.""",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Descriptive name for the scheduled task (e.g. 'Daily Tech Brief')"),
                        "task_type" to mapOf("type" to "string", "description" to "Type of task: 'image_generation', 'document_creation', or 'task'"),
                        "prompt" to mapOf("type" to "string", "description" to "The exact prompt to run on ChatGPT"),
                        "cron" to mapOf("type" to "string", "description" to "Standard cron expression (e.g. '0 9 * * *' for daily at 9am, '0 8 * * 1' for weekly Mondays)")
                    ),
                    "required" to listOf("name", "task_type", "prompt", "cron")
                )
            )
        )
        return try {
            coreTools + ai.deepcode.android.plugin.PluginRegistry.getEnabledTools()
        } catch (e: Exception) {
            coreTools
        }
    }

    private fun executeWebBridgeAgent(
        taskId: String,
        taskType: String,
        targetSite: String,
        userPrompt: String,
        args: JsonObject
    ): String {
        val ctx = context ?: return gson.toJson(generateMockWebBridgeResponse(taskId, taskType, targetSite, userPrompt, "primary_ai", "No context"))

        val outputFormat = args.get("output_format")?.asString ?: "text"
        val timeout = args.get("timeout_seconds")?.asInt ?: 120
        val credentialKey = args.get("credential_key")?.asString ?: ""
        val triggeredBy = args.get("triggered_by")?.asString ?: "primary_ai"

        val resolvedSite = if (targetSite == "auto") WebViewAutomator.getTargetSite(taskType, targetSite) else targetSite

        // Route ChatGPT tasks directly to ChatGPTHeadlessBridge if configured
        if (resolvedSite.equals("chatgpt", ignoreCase = true)) {
            val bridge = ai.deepcode.android.service.chatgpt.ChatGPTHeadlessBridge.getInstance(ctx)
            if (bridge.isConfigured()) {
                return try {
                    val result = kotlinx.coroutines.runBlocking {
                        when {
                            taskType.lowercase().contains("image") -> bridge.generateImage(userPrompt)
                            taskType.lowercase().contains("document") -> bridge.generateDocument(userPrompt)
                            else -> bridge.executeTask(userPrompt)
                        }
                    }
                    val isImage = taskType.lowercase().contains("image")
                    gson.toJson(mapOf(
                        "task_id" to taskId,
                        "status" to "SUCCESS",
                        "mode" to "HEADLESS",
                        "site_used" to "chatgpt",
                        "target_site_used" to "chatgpt",
                        "capability" to taskType,
                        "output_type" to if (isImage) "image" else "text",
                        "output" to mapOf<String, Any>(
                            "text_content" to result,
                            "image_urls" to if (isImage) listOf(result.removePrefix("[image:").removeSuffix("]")) else emptyList<String>(),
                            "description" to "ChatGPT Headless Single-Session delivery"
                        ),
                        "error" to null
                    ))
                } catch (e: Exception) {
                    AppLogger.e("ToolExecutor", "ChatGPTHeadlessBridge failed: ${e.message}", e)
                    gson.toJson(generateMockWebBridgeResponse(taskId, taskType, resolvedSite, userPrompt, triggeredBy, e.message ?: "ChatGPT headless error"))
                }
            }
        }

        return try {
            val automator = WebViewAutomator(ctx)

            val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
            val plat = resolvedSite.lowercase()
            val credEmail = prefs.getSetting("${plat}_email", "")
            val credPass = prefs.getSetting("${plat}_password", "")
            if (credEmail.isNotBlank() && credPass.isNotBlank()) {
                automator.setCredentials(plat, credEmail, credPass)
            }

            val result = automator.execute(
                platformKey = resolvedSite,
                userPrompt = userPrompt,
                taskId = taskId,
                taskType = taskType,
                outputFormat = outputFormat,
                timeoutSeconds = timeout
            )
            result
        } catch (e: Exception) {
            ai.deepcode.android.util.AppLogger.e("ToolExecutor", "WebView automation failed: ${e.message}. Falling back to mock.")
            gson.toJson(generateMockWebBridgeResponse(taskId, taskType, resolvedSite, userPrompt, triggeredBy, e.message ?: "WebView error"))
        }
    }

    private fun generateMockWebBridgeResponse(
        taskId: String,
        taskType: String,
        targetSite: String,
        userPrompt: String,
        triggeredBy: String,
        errorMsg: String
    ): WebBridgeResult {
        val siteUsed = if (targetSite == "auto") {
            when {
                taskType.contains("image") -> "pollinations"
                taskType.contains("audio") -> "elevenlabs"
                taskType.contains("search") -> "perplexity"
                else -> "chatgpt"
            }
        } else targetSite

        val isFailover = triggeredBy != "primary_ai"

        val userErrorNote = "\n\n⚠️ **WebBridge service unavailable.** The remote browser automation server could not be reached. Error: $errorMsg\n\nPlease check your `webbridge_url` setting and network connectivity. You can also try again later."

        val outputContent = when {
            taskType == "image_generation" || taskType == "image_generate" -> {
                val encodedPrompt = java.net.URLEncoder.encode(userPrompt, "UTF-8")
                val pollinationsUrl = "https://image.pollinations.ai/prompt/$encodedPrompt"
                WebBridgeOutputContent(
                    text_content = "Generated image for: '$userPrompt'.\n\n⚠️ WebBridge server unavailable. Used free public fallback image service.",
                    image_urls = listOf(pollinationsUrl),
                    description = "AI-generated image from pollinations.ai (WebBridge unavailable)"
                )
            }
            taskType == "image_analysis" || taskType == "image_understand" -> WebBridgeOutputContent(
                text_content = "Image analysis is unavailable.$userErrorNote",
                description = "Image analysis failed (WebBridge unavailable)"
            )
            taskType.contains("search") -> WebBridgeOutputContent(
                text_content = "Web search is unavailable.$userErrorNote",
                description = "Search failed (WebBridge unavailable)"
            )
            taskType.contains("audio") -> WebBridgeOutputContent(
                text_content = "Audio generation is unavailable.$userErrorNote",
                description = "Audio generation failed (WebBridge unavailable)"
            )
            else -> WebBridgeOutputContent(
                text_content = "Task failed.$userErrorNote"
            )
        }

        return WebBridgeResult(
            task_id = taskId,
            status = "FAILOVER",
            mode = "FAILOVER",
            target_site_used = siteUsed,
            site_used = siteUsed,
            output_type = taskType,
            output = outputContent,
            result = WebBridgeOutputResult(
                text = outputContent.text_content,
                image_urls = outputContent.image_urls,
                file_refs = null,
                description = outputContent.description
            ),
            failover_metadata = if (isFailover) {
                WebBridgeFailoverMetadata(
                    primary_ai_status = "timeout",
                    silence_duration_seconds = 8.0,
                    fallback_site_selected = siteUsed,
                    failover_triggered_at = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()),
                    context_passed_to_site = true,
                    user_notified = false
                )
            } else null,
            recovery_hint = if (isFailover) {
                WebBridgeRecoveryHint(
                    primary_ai_should_resume = true,
                    buffered_exchange = listOf(
                        WebBridgeChatTurn(role = "user", content = userPrompt),
                        WebBridgeChatTurn(role = "assistant", content = outputContent.text_content ?: "")
                    )
                )
            } else null,
            needs_user_action = null,
            diagnostics = WebBridgeDiagnostics(
                attempts = 1,
                wait_seconds = if (isFailover) 2 else 3,
                notes = "Simulated local fallback due to WebBridge server error: $errorMsg"
            )
        )
    }

    // ════════════════════════════════════════════════
    // PDF Creation — Layout-Aware
    // ════════════════════════════════════════════════

    private fun htmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun executeGotenbergPdf(title: String, content: String, author: String?, filename: String?, layout: String? = null): String {
        val ctx = context ?: return "Error: Context not available for PDF generation"
        val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(ctx)
        val gotenbergUrl = prefs.getSetting("gotenberg_url", "").ifBlank { "http://192.168.1.71:3000" }.trim().trimEnd('/')
        if (content.isBlank()) return "Missing content argument"

        val formattedBody = if (content.contains("<p>") || content.contains("<h") || content.contains("<div>") || content.contains("<!DOCTYPE")) {
            content
        } else {
            content.split("\n\n").joinToString("") { block ->
                if (block.isBlank()) ""
                else "<p style='margin-bottom:12px;white-space:pre-wrap;'>" + htmlEscape(block.trim()).replace("\n", "<br>") + "</p>"
            }
        }

        val safeTitle = htmlEscape(title.ifBlank { "Document" }.take(200))
        val safeAuthor = if (!author.isNullOrBlank()) "• Author: ${htmlEscape(author.take(100))}" else ""
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Noto Sans', 'Segoe UI', Helvetica, Arial, sans-serif;
                        padding: 40px 50px;
                        color: #1F2937;
                        line-height: 1.6;
                        word-break: break-word;
                    }
                    h1 {
                        color: #4F46E5;
                        border-bottom: 2px solid #E5E7EB;
                        padding-bottom: 10px;
                        font-size: 26px;
                    }
                    h2 { color: #374151; margin-top: 24px; font-size: 18px; }
                    p { font-size: 14px; margin-bottom: 12px; }
                    ul, ol { margin-left: 20px; font-size: 14px; }
                    li { margin-bottom: 6px; }
                    table { width: 100%; border-collapse: collapse; margin: 12px 0; }
                    th, td { border: 1px solid #E5E7EB; padding: 6px 8px; font-size: 12px; word-break: break-word; }
                    th { background: #F9FAFB; }
                    img { max-width: 100%; height: auto; }
                    pre, code { white-space: pre-wrap; word-break: break-word; font-size: 12px; }
                    .footer {
                        margin-top: 40px;
                        font-size: 11px;
                        color: #9CA3AF;
                        border-top: 1px solid #F3F4F6;
                        padding-top: 10px;
                        text-align: right;
                    }
                </style>
            </head>
            <body>
                <h1>$safeTitle</h1>
                <div>$formattedBody</div>
                <div class="footer">Generated by DeepCode Gotenberg API $safeAuthor</div>
            </body>
            </html>
        """.trimIndent()

        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val boundary = "----WebKitFormBoundaryGotenbergPdf"
            val requestBodyBuilder = okhttp3.MultipartBody.Builder(boundary).setType(okhttp3.MultipartBody.FORM)
            requestBodyBuilder.addFormDataPart(
                "files",
                "index.html",
                okhttp3.RequestBody.create("text/html; charset=utf-8".toMediaType(), htmlContent)
            )

            val req = okhttp3.Request.Builder()
                .url("$gotenbergUrl/forms/chromium/convert/html")
                .post(requestBodyBuilder.build())
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful && resp.body != null) {
                    val contentType = resp.header("Content-Type") ?: ""
                    val docsDir = File(ctx.filesDir, "Documents")
                    if (!docsDir.exists()) docsDir.mkdirs()
                    val baseName = sanitizeFileName(filename?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(".pdf")) it.dropLast(4) else it }, "doc_${System.currentTimeMillis()}")
                    val outName = "$baseName.pdf"
                    val outFile = File(docsDir, outName)
                    // Stream to disk instead of loading whole PDF into heap.
                    try {
                        resp.body!!.byteStream().use { input ->
                            java.io.FileOutputStream(outFile).use { fos -> input.copyTo(fos) }
                        }
                    } finally {
                        try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
                    }
                    if (!outFile.exists() || outFile.length() == 0L) return fallbackLocalPdf(title, content, author, filename, "Gotenberg returned empty PDF", layout)
                    if (!contentType.contains("pdf", ignoreCase = true) && outFile.length() < 1000) {
                        val preview = try { outFile.readBytes().take(300).toByteArray().let { String(it) } } catch (_: Exception) { "" }
                        try { outFile.delete() } catch (_: Exception) {}
                        return "Gotenberg API Error: unexpected response (${contentType.take(60)}): ${preview.take(300)}"
                    }

                    // Also save copy to public Downloads folder if accessible (best-effort, internal path stays canonical)
                    try {
                        val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        if (publicDownloads != null && publicDownloads.exists()) {
                            outFile.copyTo(File(publicDownloads, outName), overwrite = true)
                        }
                    } catch (_: Exception) {}

                    AppLogger.i("Gotenberg", "Successfully created PDF via Gotenberg API: ${outFile.absolutePath} (${outFile.length()} bytes)")
                    "[file:${outFile.absolutePath}]"
                } else {
                    val errBody = try { resp.body?.string()?.take(300) } catch (_: Exception) { resp.message }
                    try { client.dispatcher.executorService.shutdown() } catch (_: Exception) {}
                    fallbackLocalPdf(title, content, author, filename, "Gotenberg API Error (${resp.code}): $errBody", layout)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Gotenberg", "Gotenberg API PDF generation failed: ${e.message}", e)
            fallbackLocalPdf(title, content, author, filename, "Gotenberg PDF generation failed: ${e.message} (Server: $gotenbergUrl)", layout)
        }
    }

    private fun renderLocalPdf(title: String, content: String, author: String?, filename: String?, layout: PdfLayout): String {
        val ctx = context ?: return "Error: Context not available for PDF generation"
        return try {
            val docContent = DocumentContent(
                title = title.ifBlank { "Document" },
                author = author,
                date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US).format(java.util.Date()),
                rawContent = content
            )
            val safeName = sanitizeFileName(filename?.let { if (it.endsWith(".pdf")) it.dropLast(4) else it }, "doc_${System.currentTimeMillis()}")
            PdfLayoutEngine().renderDocument(ctx, layout, docContent, "$safeName.pdf")
        } catch (e: Exception) {
            "Local PDF generation failed: ${e.message}"
        }
    }

    private fun fallbackLocalPdf(title: String, content: String, author: String?, filename: String?, reason: String, layoutName: String? = null): String {
        AppLogger.w("Gotenberg", "Falling back to local PDF engine. Reason: $reason")
        val targetLayout = (layoutName?.let {
            try { resolveCustomLayout(it) } catch (_: Exception) { null }
                ?: PdfLayoutEngine.getLayoutById(it)
                ?: PdfLayoutEngine.getLayoutByName(it)
                ?: PdfLayoutEngine.findLayout(it)
        }) ?: PdfLayoutEngine.getLayoutById("classic") ?: PdfLayoutEngine.findLayout("classic")!!

        val local = try {
            renderLocalPdf(title, content, author, filename, targetLayout)
        } catch (_: Exception) { "" }
        return if (local.startsWith("[file:")) "$local\n\n(Note: Gotenberg unavailable — used offline layout. $reason)"
        else "$reason\n\nLocal fallback also failed: $local"
    }

    private fun resolveCustomLayout(idOrName: String): PdfLayout? {
        val ctx = context ?: return null
        return try {
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(ctx)
            val entity = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                db.pdfLayoutDao().getLayoutById(idOrName)
                    ?: db.pdfLayoutDao().getLayoutByName(idOrName)
            }
            entity?.let { PdfLayoutEngine().layoutFromJson(it.layoutJson) }
        } catch (e: Exception) { null }
    }

    private fun analyzePdfFile(path: String): String {
        val ctx = context ?: return "Error: Context not available for PDF analysis"
        return try {
            val analyzer = PdfAnalyzer(ctx)
            val result = analyzer.analyzePdf(path)
            val json = analyzer.getAnalysisAsJson(result)
            "PDF Analysis Complete:\n${result.summary}\n\nRaw data: $json"
        } catch (e: Exception) {
            "Error analyzing PDF: ${e.message}"
        }
    }

    private fun listPdfLayouts(): String {
        val ctx = context
        val builtin = PdfLayoutEngine.getAllLayoutDescriptions()

        val custom = if (ctx != null) {
            try {
                val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(ctx)
                val layouts = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.pdfLayoutDao().getAllLayoutsList() }
                if (layouts.isEmpty()) ""
                else "\n\nCustom Layouts:\n" + layouts.joinToString("\n") {
                    "• ${it.name} (id: ${it.id}) — ${it.description}"
                }
            } catch (_: Exception) { "" }
        } else ""

        return "Available PDF Layouts:\n\nBuilt-in Layouts:\n$builtin$custom"
    }

    private fun savePdfLayout(name: String, description: String, layoutJson: String): String {
        val ctx = context ?: return "Error: Context not available"
        return try {
            // Validate JSON
            val engine = PdfLayoutEngine()
            engine.layoutFromJson(layoutJson) ?: return "Error: Invalid layout JSON"

            val id = name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(30)
            val entity = ai.deepcode.android.data.local.PdfLayoutEntity(
                id = id,
                name = name,
                description = description,
                layoutJson = layoutJson,
                isBuiltin = false
            )
            val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(ctx)
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { db.pdfLayoutDao().insertLayout(entity) }
            "Custom layout '$name' saved successfully (id: $id). You can now use layout='$id' in create_pdf."
        } catch (e: Exception) {
            "Error saving layout: ${e.message}"
        }
    }

    private fun createPdfFromReference(refPath: String, title: String, content: String, author: String?, filename: String?): String {
        val ctx = context ?: return "Error: Context not available"
        return try {
            val analyzer = PdfAnalyzer(ctx)
            val analysis = analyzer.analyzePdf(refPath)
            val matchingLayout = analyzer.generateMatchingLayout(analysis)

            val docContent = DocumentContent(
                title = title,
                author = author,
                date = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US).format(java.util.Date()),
                rawContent = content
            )

            val result = PdfLayoutEngine().renderDocument(ctx, matchingLayout, docContent, filename)
            "$result\n\n(Layout derived from reference: ${analysis.suggestedLayoutId ?: "custom"} — ${analysis.estimatedColumnCount} column(s), margins: ${analysis.detectedMargins.left.toInt()}/${analysis.detectedMargins.top.toInt()}/${analysis.detectedMargins.right.toInt()}/${analysis.detectedMargins.bottom.toInt()} pts)"
        } catch (e: Exception) {
            "Error creating PDF from reference: ${e.message}"
        }
    }

    private fun executeScheduleChatGPTTask(
        name: String,
        taskType: String,
        prompt: String,
        cron: String,
        ctx: Context
    ): String {
        return try {
            kotlinx.coroutines.runBlocking {
                val db = ai.deepcode.android.data.local.AppDatabase.getDatabase(ctx)
                val sessionId = java.util.UUID.randomUUID().toString()
                val session = ai.deepcode.android.data.local.SessionEntity(
                    id = sessionId,
                    title = "🤖 $name (ChatGPT)",
                    createdAt = System.currentTimeMillis()
                )
                db.sessionDao().insertSession(session)

                val configMap = mutableMapOf(
                    "target" to "chatgpt",
                    "task_type" to taskType,
                    "action_prompt" to prompt,
                    "chat_session_id" to sessionId
                )
                val configJson = gson.toJson(configMap)
                val nextRun = ai.deepcode.android.ui.automations.AutomationScheduler.computeNextRunAt(cron)

                val entity = ai.deepcode.android.ui.automations.AutomationEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    description = "ChatGPT automated task: $prompt",
                    category = "CHATGPT",
                    isEnabled = true,
                    cronExpression = cron,
                    lastRunAt = 0L,
                    nextRunAt = nextRun,
                    templateId = "chatgpt_task",
                    configJson = configJson,
                    chatSessionId = sessionId
                )

                val repo = ai.deepcode.android.ui.automations.AutomationRepository(ctx)
                repo.insertAutomation(entity)
                ai.deepcode.android.ui.automations.AutomationScheduler(ctx).schedule(entity, forceRecalculate = true)

                "✅ Scheduled ChatGPT task **$name** created!\n- **Type**: $taskType\n- **Schedule**: `$cron`\n- **Dedicated Chat**: 🤖 $name (ChatGPT)\n\nYou can view and manage it anytime from the **Automations** page."
            }
        } catch (e: Exception) {
            "Failed to schedule ChatGPT task: ${e.message}"
        }
    }

    private fun logW(tag: String, msg: String) {
        if (context != null) {
            AppLogger.w(tag, msg)
        } else {
            println("[$tag] WARN: $msg")
        }
    }

    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        if (context != null) {
            if (tr != null) AppLogger.e(tag, msg, tr) else AppLogger.e(tag, msg)
        } else {
            System.err.println("[$tag] ERROR: $msg")
            tr?.printStackTrace()
        }
    }
}

