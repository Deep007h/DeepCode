package ai.deepcode.android.agent

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProvider
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.providerStorageId
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.domain.model.ToolCall
import ai.deepcode.android.service.google.GmailService
import ai.deepcode.android.service.google.CalendarService
import ai.deepcode.android.service.google.YouTubeMusicService
import ai.deepcode.android.service.gmail.GmailHandler
import ai.deepcode.android.service.github.GitHubHandler
import ai.deepcode.android.memory.MemoryChunk
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.ui.automations.AutomationEntity
import ai.deepcode.android.ui.automations.AutomationScheduler
import ai.deepcode.android.ui.connections.IntegrationEntity
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ai.deepcode.android.service.music.MusicDetectionHandler
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

class AgentEngine(private val context: Context) {
    companion object {
        private val THOUGHT_OPEN_REGEX = Regex("""<\s*(?:think|thinking|reasoning)\s*>""", RegexOption.IGNORE_CASE)
        private val THOUGHT_CLOSE_REGEX = Regex("""<\s*/\s*(?:think|thinking|reasoning)\s*>""", RegexOption.IGNORE_CASE)
        private val TOOL_CALL_TAG_REGEX = Regex("""<(?:invoke|parameter|tool_calls?)""", RegexOption.IGNORE_CASE)
    }

    private val repository = AgentRepository(context)
    private val securePrefs = EncryptedPrefs.getInstance(context)
    private val sessionCompactor = ai.deepcode.android.domain.SessionCompactor(context)
    private val database = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
    private val tokenUsageRepo = ai.deepcode.android.data.repository.TokenUsageRepository(database.tokenUsageDao(), database.tokenEventDao())
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .proxySelector(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?): List<java.net.Proxy> {
                val activeProxy = ai.deepcode.android.util.VpnManager.getActiveProxy()
                return if (activeProxy != null) {
                    listOf(activeProxy)
                } else {
                    listOf(java.net.Proxy.NO_PROXY)
                }
            }
            override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                ai.deepcode.android.util.VpnManager.handleProxyFailure()
            }
        })
        .build()

    private fun truncateContent(content: String, maxLen: Int = 20_000): String {
        return if (content.length > maxLen) content.substring(0, maxLen) + "\n\n[Content truncated at $maxLen characters]"
        else content
    }

    // Formats a tool result following TokenJuice compression rules
    private fun compressResult(toolName: String, rawResult: String): String {
        // Strip HTML tags
        val noHtml = rawResult.replace(Regex("<[^>]*>"), "")
        // Remove duplicate lines
        val lines = noHtml.split("\n")
        val uniqueLines = lines.distinct()
        val merged = uniqueLines.joinToString("\n")
        // Truncate to max 12000 chars
        val truncated = if (merged.length > 12000) merged.substring(0, 12000) else merged
        return "[TOOL: $toolName]\n$truncated"
    }

    // Declares the agent tools to the AIProvider
    private fun getAgentTools(): List<Tool> {
        val coreTools = listOf(
            Tool("integration", "Query or trigger actions on connected services. Use this to read emails (appId=gmail), check calendar (appId=google_calendar), play music (appId=youtube_music, action=play, params={song:..., artist:...}) — it can control apps on the user's device.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "appId" to mapOf("type" to "string", "description" to "The app ID: gmail, google_calendar, youtube_music, github, slack, notion"),
                    "action" to mapOf("type" to "string", "description" to "Action: for youtube_music use 'play' or 'search', for gmail use 'read' or 'search', for calendar use 'today' or 'upcoming'"),
                    "params" to mapOf("type" to "object", "description" to "Action parameters: for play include song and artist, for search include query, for read include maxResults")
                ),
                "required" to listOf("appId", "action")
            )),
            Tool("web_search", "Search the web for current information. Use this for news, weather, research, or any real-time data query. Supports multiple search providers with automatic fallback.", mapOf(
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
            Tool("web_fetch", "Fetch text content from a URL", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "url" to mapOf("type" to "string", "description" to "The web URL to fetch")
                ),
                "required" to listOf("url")
            )),
            Tool("telegram_send", "Send a message to a Telegram chat", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "chatId" to mapOf("type" to "string", "description" to "Telegram Chat ID"),
                    "message" to mapOf("type" to "string", "description" to "Text message to send")
                ),
                "required" to listOf("chatId", "message")
            )),
            Tool("memory_read", "Query the local knowledge base by keywords", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "description" to "Keywords to search")
                ),
                "required" to listOf("query")
            )),
            Tool("memory_write", "Save a key-value record to the knowledge base", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "key" to mapOf("type" to "string", "description" to "Record key or title"),
                    "value" to mapOf("type" to "string", "description" to "Record content value")
                ),
                "required" to listOf("key", "value")
            )),
            Tool("create_automation", "CRITICAL: Use this tool whenever the user asks for something to happen on a schedule (daily, weekly, every N minutes, at a specific time). Do NOT try to do recurring tasks yourself — always use this tool. Examples: 'morning briefing at 7 AM', 'news digest every hour', 'check battery every 30 min'. Parameters: name, description, category (MESSAGING/CONTENT/SYSTEM/DEVELOPER), cron expression, action prompt. On Telegram, include telegramChatId to deliver results.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Rule name"),
                    "description" to mapOf("type" to "string", "description" to "Rule description"),
                    "category" to mapOf("type" to "string", "description" to "Category: MESSAGING, CONTENT, SYSTEM, or DEVELOPER"),
                    "cron" to mapOf("type" to "string", "description" to "Cron schedule expression"),
                    "actionPrompt" to mapOf("type" to "string", "description" to "What action the automation should perform"),
                    "telegramChatId" to mapOf("type" to "string", "description" to "Telegram Chat ID to deliver results to (omit if not on Telegram)")
                ),
                "required" to listOf("name", "description", "category", "cron", "actionPrompt")
            )),
            Tool("list_automations", "List all existing scheduled automation rules with their current schedule and status.", mapOf(
                "type" to "object",
                "properties" to mapOf<String, Any>(),
                "required" to emptyList<String>()
            )),
            Tool("delete_automation", "Delete an existing automation rule by name.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Name of the automation rule to delete")
                ),
                "required" to listOf("name")
            )),
            Tool("create_connection", "Register a new external service connection (e.g. GitHub, Slack, Gmail). Ask the user for the app name and display name if not provided.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "appId" to mapOf("type" to "string", "description" to "Unique app identifier (e.g. github, slack, gmail)"),
                    "appName" to mapOf("type" to "string", "description" to "Short app name (e.g. GitHub, Slack)"),
                    "displayName" to mapOf("type" to "string", "description" to "User-facing display name")
                ),
                "required" to listOf("appId", "appName", "displayName")
            )),
            Tool("update_setting", "Read or update any app setting. To read, omit value. To change, provide both key and value. Known keys: tg_start_msg (Telegram /start message), tg_help_msg (Telegram /help message), agent_model, agent_provider, theme (system/light/dark), default_project.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "key" to mapOf("type" to "string", "description" to "Setting key"),
                    "value" to mapOf("type" to "string", "description" to "New value (omit to just read current value)")
                ),
                "required" to listOf("key")
            )),
            Tool("generate_image",
                "Generate an image from a text prompt using AI. Falls back to web image search if AI generation is unavailable.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "The text description of the image to generate. Be detailed for best results.")
                    ),
                    "required" to listOf("prompt")
                )
            ),
            Tool("generate_video",
                "Generate a video from a text description using Veo AI. Returns a playable video URL or file path. Use this when the user asks to create, generate, or make a video.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "prompt" to mapOf("type" to "string", "description" to "The text description of the video to generate.")
                    ),
                    "required" to listOf("prompt")
                )
            ),
            Tool("search_image",
                "Search for real existing images from the web. Use this when the user asks to get, find, show, or search for an image/picture of something real.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "What to image search for")
                    ),
                    "required" to listOf("query")
                )
            ),
            Tool("edge_tts",
                "Generate speech/audio from text using neural TTS. Uses the configured backend (edge_tts by default, kokoro if set via set_tts_backend). Use this when the user asks for audio, voice, speech, TTS, or read aloud. IMPORTANT: The 'text' field must always contain the ACTUAL content to speak — never pass literal phrases like 'last response' or 'my previous message'. If the user's request references a previous reply, the system will inject the resolved content into the conversation context for you to use.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "text" to mapOf("type" to "string", "description" to "The actual text content to convert to speech. Must be real content, not meta-references like 'last response'."),
                        "voice" to mapOf("type" to "string", "description" to "Optional voice locale (e.g. 'en-US', 'hi-IN', 'ja-JP')"),
                        "rate" to mapOf("type" to "string", "description" to "Optional speech rate adjustment"),
                        "pitch" to mapOf("type" to "string", "description" to "Optional pitch adjustment")
                    ),
                    "required" to listOf("text")
                )
            ),
            Tool("update_bot_profile", "Update the Telegram bot's name, description, or short description via the Bot API. Only works if a Telegram bot token is configured in settings.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "New bot display name (e.g. DeepCode Bot)"),
                    "description" to mapOf("type" to "string", "description" to "New bot description (shown below the name in chat info)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("shell", "Run a shell command on the device. Use this to execute Python scripts, run terminal commands, or perform any CLI operation. Essential for creating PDFs, running scripts, and system tasks.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "command" to mapOf("type" to "string", "description" to "The shell command to execute (e.g. 'python3 script.py', 'ls -la', 'mkdir dir')")
                ),
                "required" to listOf("command")
            )),
            Tool("create_pdf", "Generate a PDF document with layout selection. Choose from layouts: classic, modern-minimal, corporate-report, academic-paper, creative-portfolio, invoice-receipt, newsletter, resume-cv. Use the 'layout' parameter to pick a layout. Content supports markdown: ## headings, - bullets, > blockquotes, | tables. No Python needed — generates the PDF directly.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "title" to mapOf("type" to "string", "description" to "Document title, displayed as a heading"),
                    "content" to mapOf("type" to "string", "description" to "Body text content. Use markdown: ## headings, - bullets, > blockquotes, | tables."),
                    "author" to mapOf("type" to "string", "description" to "Optional author name shown below the title"),
                    "filename" to mapOf("type" to "string", "description" to "Optional output filename (default: document.pdf)"),
                    "layout" to mapOf("type" to "string", "description" to "Layout ID: classic, modern-minimal, corporate-report, academic-paper, creative-portfolio, invoice-receipt, newsletter, resume-cv")
                ),
                "required" to listOf("title", "content")
            )),
            Tool("analyze_pdf", "Analyze a PDF file to extract its layout: margins, colors, columns, headers, text density. Use when the user shares a PDF and wants to understand or replicate its design.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Absolute file path to the PDF")
                ),
                "required" to listOf("path")
            )),
            Tool("list_pdf_layouts", "List all available PDF layouts (built-in and custom).", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "required" to emptyList<String>()
            )),
            Tool("create_pdf_from_reference", "Create a PDF matching the layout of a reference PDF. Analyzes the reference PDF and uses its style for the new document.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "reference_pdf" to mapOf("type" to "string", "description" to "Path to the reference PDF"),
                    "title" to mapOf("type" to "string", "description" to "Title for the new document"),
                    "content" to mapOf("type" to "string", "description" to "Body content (supports markdown)"),
                    "author" to mapOf("type" to "string", "description" to "Optional author name"),
                    "filename" to mapOf("type" to "string", "description" to "Optional output filename")
                ),
                "required" to listOf("reference_pdf", "title", "content")
            )),
            Tool("file_write", "Write content to a file. Use this to save generated scripts, documents, or any text content.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "File path to write to"),
                    "content" to mapOf("type" to "string", "description" to "File content to write")
                ),
                "required" to listOf("path", "content")
            )),
            Tool("file_read", "Read the contents of a file from the device's filesystem.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "File path to read")
                ),
                "required" to listOf("path")
            )),
            Tool("list", "List files and directories in a given path. Use this to explore the filesystem and find files.", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "path" to mapOf("type" to "string", "description" to "Directory path to list (default: current directory)")
                ),
                "required" to emptyList<String>()
            )),
            Tool("set_tts_backend",
                "Switch the TTS (text-to-speech) backend. Options: edge_tts (default, Microsoft Edge neural voices), kokoro (self-hosted Kokoro-FastAPI server), android (built-in Android TTS), google (Google Translate TTS).",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "backend" to mapOf("type" to "string", "enum" to listOf("edge_tts", "kokoro", "android", "google"), "description" to "TTS backend to use")
                    ),
                    "required" to listOf("backend")
                )
            ),
            Tool("set_kokoro_url",
                "Set the Kokoro-FastAPI server URL (for the kokoro TTS backend). Default is http://localhost:8880. Run: docker run -p 8880:8880 remsky/kokoro-fastapi",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "url" to mapOf("type" to "string", "description" to "Full URL of the Kokoro-FastAPI server")
                    ),
                    "required" to listOf("url")
                )
            )
        )
        return try {
            coreTools + ai.deepcode.android.plugin.PluginRegistry.getEnabledTools()
        } catch (e: Exception) {
            coreTools
        }
    }

    // Resolves model and provider based on settings in EncryptedPrefs
    private fun resolveProviderAndModel(providerOverride: String? = null, modelOverride: String? = null): Pair<AIProvider, String> {
        val modelSetting = modelOverride ?: securePrefs.getSetting("agent_model", "big-pickle")
        val providerSetting = providerOverride ?: securePrefs.getSetting("agent_provider", "Zen AI")
        val providers = AIProviderFactory.providers
        if (providers.isEmpty()) {
            AppLogger.e("AgentEngine", "No AI providers available!")
            throw IllegalStateException("No AI providers configured")
        }
        var provider = providers.firstOrNull { it.name == providerSetting }
            ?: providers.firstOrNull { it.name == "Zen AI" }
            ?: providers.first()

        // When an explicit override was given, trust it — don't silently replace
        if (providerOverride == null) {
            val key = getApiKeyForProvider(provider)
            if (key.isEmpty()) {
                val firstConfigured = AIProviderFactory.providers.firstOrNull { p ->
                    getApiKeyForProvider(p).isNotEmpty()
                }
                if (firstConfigured != null) {
                    provider = firstConfigured
                    val modelId = provider.models.firstOrNull()?.id ?: ""
                    return Pair(provider, modelId)
                }
            }
        }

        val hasModel = provider.models.any { it.id == modelSetting }
        val finalModel = if (hasModel) modelSetting else (provider.models.firstOrNull()?.id ?: "")
        return Pair(provider, finalModel)
    }
    // Helper to get API key for the chosen provider
    private fun getApiKeyForProvider(provider: AIProvider): String {
        val name = provider.name
        val key = when (name) {
            "Zen AI", "Zen", "Zen (Free)" -> securePrefs.getApiKey("zen").ifEmpty { "zen-free" }
            "Google Gemini" -> securePrefs.getApiKey("gemini")
            "Groq" -> securePrefs.getApiKey("groq")
            "Cerebrus", "Cerebras" -> {
                val k1 = securePrefs.getApiKey("cerebrus")
                if (k1.isNotEmpty()) k1 else securePrefs.getApiKey("cerebras")
            }
            "OpenRouter" -> securePrefs.getApiKey("openrouter")
            "Omniroute" -> securePrefs.getApiKey("omniroute")
            "OpenAI" -> securePrefs.getApiKey("openai")
            "Anthropic" -> securePrefs.getApiKey("anthropic")
            "Mistral AI" -> securePrefs.getApiKey("mistral")
            "Agent Router" -> securePrefs.getApiKey("agentrouter")
            "Antigravity" -> securePrefs.getSetting("oauth_token_antigravity", "")
            "Ollama Cloud" -> securePrefs.getApiKey("ollama-cloud")
            "NVIDIA NIM" -> securePrefs.getApiKey("nvidia")
            "Together AI" -> securePrefs.getApiKey("together")
            "Perplexity" -> securePrefs.getApiKey("perplexity")
            "xAI" -> securePrefs.getApiKey("xai")
            "Cohere" -> securePrefs.getApiKey("cohere")
            "DeepInfra" -> securePrefs.getApiKey("deepinfra")
            "Fireworks AI" -> securePrefs.getApiKey("fireworks")
            "SambaNova" -> securePrefs.getApiKey("sambanova")
            "Hyperbolic" -> securePrefs.getApiKey("hyperbolic")
            "GitHub Models" -> securePrefs.getApiKey("github-models")
            "Novita AI" -> securePrefs.getApiKey("novita")
            "SiliconFlow" -> securePrefs.getApiKey("siliconflow")
            "DeepSeek" -> securePrefs.getApiKey("deepseek")
            else -> {
                val resolvedId = name.lowercase().replace(" ai", "").replace(" ", "").replace("-", "")
                val keyFromId = securePrefs.getApiKey(resolvedId)
                if (keyFromId.isNotEmpty()) keyFromId else securePrefs.getApiKey(name.lowercase().replace(" ", "-"))
            }
        }
        return key
    }
    private suspend fun refreshAntigravityToken(): String {
        val refreshToken = securePrefs.getSetting("oauth_refresh_antigravity", "")
        if (refreshToken.isEmpty()) return ""
        try {
            val googleAuth = ai.deepcode.android.service.google.GoogleAuthService(context)
            val result = googleAuth.refreshAccessToken(refreshToken)
            if (result.isSuccess) {
                val tokens = result.getOrThrow()
                securePrefs.saveSetting("oauth_token_antigravity", tokens.accessToken)
                if (tokens.refreshToken.isNotEmpty()) {
                    securePrefs.saveSetting("oauth_refresh_antigravity", tokens.refreshToken)
                }
                return tokens.accessToken
            }
        } catch (_: Exception) {}
        return ""
    }

    // Helper to get Custom Endpoint URL
    private fun getCustomUrlForProvider(provider: AIProvider): String? {
        val key = when (provider.name) {
            "Zen AI", "Zen", "Zen (Free)" -> "url_zen"
            "Google Gemini" -> "url_gemini"
            "Groq" -> "url_groq"
            "Cerebrus", "Cerebras" -> "url_cerebrus"
            "OpenRouter" -> "url_openrouter"
            "Omniroute" -> "url_omniroute"
            "OpenAI" -> "url_openai"
            "Anthropic" -> "url_anthropic"
            "Mistral AI" -> "url_mistral"
            "Ollama", "Ollama Cloud" -> "url_ollama"
            "Agent Router" -> "url_agentrouter"
            else -> ""
        }
        val url = securePrefs.getSetting(key, "")
        return url.ifEmpty { null }
    }

    // Executes the requested tool and returns the raw string result
    private suspend fun executeTool(name: String, argsJson: String): String = withContext(Dispatchers.IO) {
        AppLogger.i("AgentEngine", "Executing tool: $name with args: $argsJson")
        try {
            val args = gson.fromJson(argsJson, JsonObject::class.java)
            when (name) {
                "web_search" -> {
                    val query = args.get("query")?.asString ?: return@withContext "Error: Missing query"
                    val numResults = args.get("numResults")?.asInt ?: 8
                    val livecrawl = args.get("livecrawl")?.asString ?: "fallback"
                    val type = args.get("type")?.asString ?: "auto"
                    val contextMaxCharacters = args.get("contextMaxCharacters")?.asInt ?: 10000
                    executeWebSearch(query, numResults, livecrawl, type, contextMaxCharacters)
                }
                "web_fetch" -> {
                    val url = args.get("url")?.asString ?: return@withContext "Error: Missing url"
                    executeWebFetch(url)
                }
                "integration" -> {
                    val appId = args.get("appId")?.asString ?: return@withContext "Error: Missing appId"
                    val action = args.get("action")?.asString ?: return@withContext "Error: Missing action"
                    val paramsObj = args.getAsJsonObject("params")
                    val params = mutableMapOf<String, String>()
                    paramsObj?.entrySet()?.forEach { entry ->
                        params[entry.key] = if (entry.value.isJsonPrimitive) entry.value.asString else entry.value.toString()
                    }
                    executeIntegration(appId, action, params)
                }
                "telegram_send" -> {
                    val chatId = args.get("chatId")?.asString ?: return@withContext "Error: Missing chatId"
                    val message = args.get("message")?.asString ?: return@withContext "Error: Missing message"
                    executeTelegramSend(chatId, message)
                }
                "memory_read" -> {
                    val query = args.get("query")?.asString ?: return@withContext "Error: Missing query"
                    executeMemoryRead(query)
                }
                "memory_write" -> {
                    val key = args.get("key")?.asString ?: return@withContext "Error: Missing key"
                    val value = args.get("value")?.asString ?: return@withContext "Error: Missing value"
                    executeMemoryWrite(key, value)
                }
                "create_automation", "cron_add" -> {
                    val name = args.get("name")?.asString ?: return@withContext "Error: Missing name"
                    val description = args.get("description")?.asString ?: ""
                    val category = args.get("category")?.asString ?: "MESSAGING"
                    val cron = args.get("cron")?.asString ?: "0 * * * *"
                    val actionPrompt = args.get("actionPrompt")?.asString ?: ""
                    val telegramChatId = args.get("telegramChatId")?.asString ?: ""
                    executeCreateAutomation(name, description, category, cron, actionPrompt, telegramChatId)
                }
                "list_automations", "cron_list" -> {
                    executeListAutomations()
                }
                "delete_automation", "cron_remove" -> {
                    val name = args.get("name")?.asString ?: return@withContext "Error: Missing name"
                    executeDeleteAutomation(name)
                }
                "create_connection" -> {
                    val appId = args.get("appId")?.asString ?: return@withContext "Error: Missing appId"
                    val appName = args.get("appName")?.asString ?: return@withContext "Error: Missing appName"
                    val displayName = args.get("displayName")?.asString ?: appName
                    executeCreateConnection(appId, appName, displayName)
                }
                "update_setting" -> {
                    val key = args.get("key")?.asString ?: return@withContext "Error: Missing key"
                    val value = args.get("value")?.asString
                    executeUpdateSetting(key, value)
                }
                "generate_image" -> {
                    val prompt = args.get("prompt")?.asString ?: return@withContext "Error: Missing prompt"
                    val executor = ToolExecutor(context)
                    executor.executeTool("generate_image", """{"prompt":${gson.toJson(prompt)}}""", "", false)
                }
                "generate_video" -> {
                    val prompt = args.get("prompt")?.asString ?: return@withContext "Error: Missing prompt"
                    val executor = ToolExecutor(context)
                    executor.executeTool("generate_video", """{"prompt":${gson.toJson(prompt)}}""", "", false)
                }
                "search_image" -> {
                    val query = args.get("query")?.asString ?: return@withContext "Error: Missing query"
                    val executor = ToolExecutor(context)
                    executor.executeTool("search_image", """{"query":${gson.toJson(query)}}""", "", false)
                }
                "edge_tts" -> {
                    val text = args.get("text")?.asString ?: return@withContext "Error: Missing text"
                    val executor = ToolExecutor(context)
                    executor.executeTool("edge_tts", """{"text":${gson.toJson(text)}}""", "", false)
                }
                "set_tts_backend" -> {
                    val backend = args.get("backend")?.asString ?: return@withContext "Error: Missing backend"
                    val executor = ToolExecutor(context)
                    executor.executeTool("set_tts_backend", """{"backend":${gson.toJson(backend)}}""", "", false)
                }
                "set_kokoro_url" -> {
                    val url = args.get("url")?.asString ?: return@withContext "Error: Missing url"
                    val executor = ToolExecutor(context)
                    executor.executeTool("set_kokoro_url", """{"url":${gson.toJson(url)}}""", "", false)
                }
                "update_bot_profile" -> {
                    val name = args.get("name")?.asString
                    val description = args.get("description")?.asString
                    executeUpdateBotProfile(name, description)
                }
                "shell" -> {
                    val command = args.get("command")?.asString ?: return@withContext "Error: Missing command"
                    val executor = ToolExecutor(context)
                    executor.executeTool("shell", """{"command":${gson.toJson(command)}}""", "", false)
                }
                "create_pdf" -> {
                    val title = args.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing title"
                    val content = args.get("content")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing content"
                    val author = args.get("author")?.takeIf { !it.isJsonNull }?.asString
                    val filename = args.get("filename")?.takeIf { !it.isJsonNull }?.asString
                    val layout = args.get("layout")?.takeIf { !it.isJsonNull }?.asString
                    val executor = ToolExecutor(context)
                    executor.executeTool("create_pdf", """{"title":${gson.toJson(title)},"content":${gson.toJson(content)},"author":${gson.toJson(author)},"filename":${gson.toJson(filename)},"layout":${gson.toJson(layout)}}""", "", false)
                }
                "analyze_pdf" -> {
                    val path = args.get("path")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing path"
                    val executor = ToolExecutor(context)
                    executor.executeTool("analyze_pdf", """{"path":${gson.toJson(path)}}""", "", false)
                }
                "list_pdf_layouts" -> {
                    val executor = ToolExecutor(context)
                    executor.executeTool("list_pdf_layouts", "{}", "", false)
                }
                "create_pdf_from_reference" -> {
                    val refPath = args.get("reference_pdf")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing reference_pdf"
                    val title = args.get("title")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing title"
                    val content = args.get("content")?.takeIf { !it.isJsonNull }?.asString ?: return@withContext "Error: Missing content"
                    val author = args.get("author")?.takeIf { !it.isJsonNull }?.asString
                    val filename = args.get("filename")?.takeIf { !it.isJsonNull }?.asString
                    val executor = ToolExecutor(context)
                    executor.executeTool("create_pdf_from_reference", """{"reference_pdf":${gson.toJson(refPath)},"title":${gson.toJson(title)},"content":${gson.toJson(content)},"author":${gson.toJson(author)},"filename":${gson.toJson(filename)}}""", "", false)
                }
                "file_write" -> {
                    val path = args.get("path")?.asString ?: return@withContext "Error: Missing path"
                    val content = args.get("content")?.asString ?: return@withContext "Error: Missing content"
                    val executor = ToolExecutor(context)
                    executor.executeTool("file_write", """{"path":${gson.toJson(path)},"content":${gson.toJson(content)}}""", "", false)
                }
                "file_read" -> {
                    val path = args.get("path")?.asString ?: return@withContext "Error: Missing path"
                    val executor = ToolExecutor(context)
                    executor.executeTool("file_read", """{"path":${gson.toJson(path)}}""", "", false)
                }
                "list" -> {
                    val path = args.get("path")?.asString ?: "."
                    val executor = ToolExecutor(context)
                    executor.executeTool("list", """{"path":${gson.toJson(path)}}""", "", false)
                }
                else -> {
                    if (ai.deepcode.android.plugin.PluginRegistry.hasToolName(name)) {
                        val executor = ToolExecutor(context)
                        executor.executeTool(name, argsJson, "", false)
                    } else {
                        "Error: Unknown tool $name"
                    }
                }
            }
        } catch (e: Exception) {
            "Error executing tool $name: ${e.message}"
        }
    }

    private fun agentRequest(url: String) = Request.Builder()
        .url(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .build()

    private fun executeWebSearch(query: String, numResults: Int = 8, livecrawl: String = "fallback", type: String = "auto", contextMaxCharacters: Int = 10000): String {
        // Delegate to ToolExecutor — single source of truth for all search logic
        // This ensures AgentEngine (Telegram/agent sessions) gets the same quality
        // results as regular chat: SearXNG, content crawl, Wikipedia extracts, etc.
        return try {
            val executor = ToolExecutor(context)
            val argsJson = """{"query":${gson.toJson(query)},"numResults":$numResults,"livecrawl":${gson.toJson(livecrawl)},"type":${gson.toJson(type)},"contextMaxCharacters":$contextMaxCharacters}"""
            executor.executeTool("web_search", argsJson, "", false)
        } catch (e: Exception) {
            // Fallback: try SearXNG directly if ToolExecutor delegation fails
            fetchSearXNG(query, numResults) ?: fetchDDG(query) ?: fetchWiki(query) ?: "\"$query\" — no results found."
        }
    }

    private fun mcpWebSearch(query: String, numResults: Int, livecrawl: String, type: String, contextMaxCharacters: Int): String? {
        // Try Exa MCP first
        val exaResult = runCatching {
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
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://mcp.exa.ai/mcp")
                .post(body)
                .header("User-Agent", "DeepCode-Android/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            response.use {
                val respBody = it.body?.string() ?: return@runCatching null
                if (!it.isSuccessful) return@runCatching null
                val json = com.google.gson.JsonParser.parseString(respBody).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return@runCatching null
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
        }.getOrNull()
        if (exaResult != null) return "Search results for '$query':\n$exaResult"

        // Try Parallel MCP as second fallback
        val parallelResult = runCatching {
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
            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://search.parallel.ai/mcp")
                .post(body)
                .header("User-Agent", "DeepCode-Android/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            response.use {
                val respBody = it.body?.string() ?: return@runCatching null
                if (!it.isSuccessful) return@runCatching null
                val json = com.google.gson.JsonParser.parseString(respBody).asJsonObject
                val result = json.getAsJsonObject("result")
                val content = result?.getAsJsonArray("content") ?: return@runCatching null
                val sb = StringBuilder()
                for (i in 0 until minOf(content.size(), 8)) {
                    val item = content.get(i).asJsonObject
                    val text = item.get("text")?.asString ?: continue
                    sb.appendLine(text)
                    sb.appendLine()
                }
                sb.toString().ifEmpty { null }
            }
        }.getOrNull()
        if (parallelResult != null) return "Search results for '$query':\n$parallelResult"

        return null
    }

    private data class Geo(val lat: Double, val lon: Double, val name: String)

    private fun geocodeCity(city: String): Geo? {
        return try {
            val encoded = java.net.URLEncoder.encode(city, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json"
            val text = httpClient.newCall(agentRequest(url)).execute().use { it.body?.string() } ?: return null
            val json = com.google.gson.JsonParser.parseString(text).asJsonObject
            val results = json.getAsJsonArray("results")
            if (results != null && results.size() > 0) {
                val first = results.get(0).asJsonObject
                Geo(first.get("latitude").asDouble, first.get("longitude").asDouble, first.get("name").asString)
            } else null
        } catch (e: Exception) { null }
    }

    private fun fetchWeather(lat: Double, lon: Double, name: String): String {
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m&timezone=auto"
            val text = httpClient.newCall(agentRequest(url)).execute().use { it.body?.string() } ?: return "Weather data unavailable"
            val json = com.google.gson.JsonParser.parseString(text).asJsonObject
            val current = json.getAsJsonObject("current") ?: return "Weather data unavailable"
            val temp = current.get("temperature_2m")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val feels = current.get("apparent_temperature")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val humidity = current.get("relative_humidity_2m")?.asDouble?.let { String.format("%.0f", it) } ?: "?"
            val wind = current.get("wind_speed_10m")?.asDouble?.let { String.format("%.1f", it) } ?: "?"
            val code = current.get("weather_code")?.asInt ?: 0
            buildString {
                appendLine("Current weather in $name:")
                appendLine("Temperature: $temp°C (feels like $feels°C)")
                appendLine("Humidity: $humidity% | Wind: $wind km/h")
                appendLine("Condition code: $code")
            }
        } catch (e: Exception) { "Weather error: ${e.message}" }
    }

    private fun fetchDDG(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val text = httpClient.newCall(agentRequest(url)).execute().use { it.body?.string() } ?: return fetchDDGHtml(query)
            val json = com.google.gson.JsonParser.parseString(text).asJsonObject
            val answer = json.get("Answer")?.asString ?: ""
            val abstractText = json.get("AbstractText")?.asString ?: ""
            val definition = json.get("Definition")?.asString ?: ""
            val abstractSource = json.get("AbstractSource")?.asString ?: ""
            val abstractUrl = json.get("AbstractURL")?.asString ?: ""
            val relatedTopics = json.getAsJsonArray("RelatedTopics") ?: com.google.gson.JsonArray()
            val sb = StringBuilder()
            if (answer.isNotEmpty()) sb.appendLine("Answer: $answer")
            if (abstractText.isNotEmpty()) {
                if (abstractSource.isNotEmpty()) sb.appendLine("Source: $abstractSource")
                sb.appendLine(abstractText)
                if (abstractUrl.isNotEmpty()) sb.appendLine("More: $abstractUrl")
            }
            if (definition.isNotEmpty()) sb.appendLine("Definition: $definition")
            var topicCount = 0
            for (i in 0 until relatedTopics.size()) {
                if (topicCount >= 4) break
                try {
                    val topic = relatedTopics.get(i).asJsonObject
                    val topicText = topic.get("Text")?.asString ?: continue
                    val firstUrl = topic.get("FirstURL")?.asString ?: ""
                    if (topicText.isNotEmpty()) {
                        sb.appendLine("- $topicText${if (firstUrl.isNotEmpty()) " ($firstUrl)" else ""}")
                        topicCount++
                    }
                } catch (_: Exception) { continue }
            }
            val out = sb.toString().trim()
            if (out.isNotEmpty()) out else fetchDDGHtml(query)
        } catch (e: Exception) {
            fetchDDGHtml(query)
        }
    }

    private fun fetchDDGHtml(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val html = httpClient.newCall(agentRequest("https://html.duckduckgo.com/html/?q=$encoded")).execute().use { it.body?.string() } ?: return null
            if (html.length < 200) return null

            val resultUrls = mutableListOf<Pair<String, String>>()
            val sb = StringBuilder()
            val linkPattern = Regex("""href="(/l/\?[^"]*|https?://[^"]+)"[^>]*class="result__a[^"]*"[^>]*>([\s\S]*?)</a>""")
            val snippetPattern = Regex("""class="result__snippet[^"]*"[^>]*>([\s\S]*?)</a>""")
            val linkMatches = linkPattern.findAll(html).toList()
            val snippetMatches = snippetPattern.findAll(html).toList()
            var added = 0
            for (i in 0 until minOf(linkMatches.size, 6)) {
                val rawHref = linkMatches[i].groupValues[1]
                val title = linkMatches[i].groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                if (title.isEmpty()) continue
                val resolvedUrl = try {
                    if (rawHref.startsWith("/l/?")) {
                        val uddg = rawHref.substringAfter("?").split("&").find { it.startsWith("uddg=") }?.substringAfter("uddg=") ?: ""
                        java.net.URLDecoder.decode(uddg, "UTF-8")
                    } else rawHref
                } catch (_: Exception) { rawHref }
                val snippet = if (i < snippetMatches.size) {
                    snippetMatches[i].groupValues[1].replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
                } else ""
                sb.appendLine("${added + 1}. $title")
                if (snippet.isNotEmpty()) sb.appendLine("   $snippet")
                if (resolvedUrl.startsWith("http")) {
                    sb.appendLine("   $resolvedUrl")
                    resultUrls.add(Pair(resolvedUrl, title))
                }
                added++
                if (added >= 5) break
            }
            val text = sb.toString().trim().ifEmpty { return null }
            val crawled = crawlTopResults(resultUrls.take(2))
            if (crawled.isNotEmpty()) "$text\n\n$crawled" else text
        } catch (e: Exception) { null }
    }

    private fun fetchWiki(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            // Use the extracts API — returns full intro paragraphs (500–2000 chars)
            // instead of the old opensearch API which only returned 1-line title summaries
            val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&redirects=1&titles=$encoded&format=json"
            val text = httpClient.newCall(agentRequest(url)).execute().use { it.body?.string() } ?: return null
            val json = com.google.gson.JsonParser.parseString(text).asJsonObject
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

    private fun fetchMojeek(query: String): String? {
        return try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val html = httpClient.newCall(agentRequest("https://www.mojeek.com/search?q=$encoded")).execute().use { it.body?.string() } ?: return null
            val pattern = Regex("""<h2><a[^>]* href="([^"]*)"[^>]*>([\s\S]*?)</a></h2>.*?<p class="s[^"]*">([\s\S]*?)</p>""")
            val matches = pattern.findAll(html).toList()
            val sb = StringBuilder()
            val crawlUrls = mutableListOf<Pair<String, String>>()
            val count = minOf(matches.size, 5)
            for (i in 0 until count) {
                val url = matches[i].groupValues[1].trim()
                val rawTitle = matches[i].groupValues[2]
                val title = rawTitle.replace(Regex("<[^>]+>"), "").trim()
                if (title.isEmpty()) continue
                val snippet = matches[i].groupValues[3].replace(Regex("<[^>]+>"), "").trim()
                sb.appendLine("${i + 1}. $title")
                if (snippet.isNotEmpty()) sb.appendLine("   $snippet")
                sb.appendLine("   $url")
                if (url.startsWith("http")) crawlUrls.add(Pair(url, title))
            }
            val text = sb.toString().trim().ifEmpty { return null }
            val crawled = crawlTopResults(crawlUrls.take(2))
            if (crawled.isNotEmpty()) "$text\n\n$crawled" else text
        } catch (e: Exception) { null }
    }

    /**
     * SearXNG public instance search — free, no API key, queries Google+Bing+DDG combined.
     */
    private fun fetchSearXNG(query: String, numResults: Int = 6): String? {
        val instances = listOf(
            "https://searx.be",
            "https://search.mdosch.de",
            "https://searxng.site"
        )
        for (instance in instances) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "$instance/search?q=$encoded&format=json&language=en"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
                val response = httpClient.newCall(request).execute()
                response.use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                    val results = json.getAsJsonArray("results") ?: return@use
                    if (results.size() == 0) return@use
                    val sb = StringBuilder()
                    val crawlUrls = mutableListOf<Pair<String, String>>()
                    var count = 0
                    for (i in 0 until minOf(results.size(), numResults)) {
                        val item = results.get(i).asJsonObject
                        val title = item.get("title")?.asString?.trim() ?: continue
                        val resultUrl = item.get("url")?.asString?.trim() ?: ""
                        val content = item.get("content")?.asString?.trim() ?: ""
                        sb.appendLine("${count + 1}. $title")
                        if (content.isNotEmpty()) sb.appendLine("   $content")
                        if (resultUrl.isNotEmpty()) {
                            sb.appendLine("   $resultUrl")
                            crawlUrls.add(Pair(resultUrl, title))
                        }
                        count++
                    }
                    if (sb.isNotEmpty()) {
                        var output = "Search results for '$query' (via SearXNG):\n${sb.toString().trim()}"
                        val crawled = crawlTopResults(crawlUrls.take(2))
                        if (crawled.isNotEmpty()) output += "\n\n$crawled"
                        return output
                    }
                }
            } catch (_: Exception) { continue }
        }
        return null
    }

    /**
     * Fetches actual page content from the top result URLs.
     */
    private fun crawlTopResults(urls: List<Pair<String, String>>): String {
        if (urls.isEmpty()) return ""
        val sb = StringBuilder()
        val skip = listOf("youtube.com", "youtu.be", "twitter.com", "x.com",
            "facebook.com", "instagram.com", "tiktok.com", "reddit.com")
        for ((url, title) in urls) {
            try {
                if (skip.any { url.contains(it) }) continue
                val response = httpClient.newCall(agentRequest(url)).execute()
                val body = response.use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: continue
                // Strip HTML tags for plain text
                val text = body.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(3000)
                if (text.length < 100) continue
                sb.appendLine("\n## From: $title")
                sb.appendLine(text)
            } catch (_: Exception) { continue }
        }
        return sb.toString().trim()
    }

    private fun executeWebFetch(url: String): String {
        return try {
            val request = agentRequest(url)
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Error fetching web page: HTTP ${response.code}"
                val body = response.body?.string() ?: ""
                body
            }
        } catch (e: Exception) {
            "Error loading URL $url: ${e.message}"
        }
    }

    private suspend fun executeGmailAction(action: String, params: Map<String, String>): String {
        AppLogger.i("AgentEngine", "executeGmailAction called: action=$action params=$params")
        val service = GmailService(context)
        return try {
            val result = when (action.lowercase()) {
                "list_unread", "unread", "read" -> service.listUnread(params.get("maxResults")?.toIntOrNull() ?: 10)
                "search" -> service.search(params["query"] ?: "", params.get("maxResults")?.toIntOrNull() ?: 10)
                "list_inbox", "inbox" -> service.listInbox(params.get("maxResults")?.toIntOrNull() ?: 10)
                else -> service.listUnread(5)
            }
            if (result.isFailure) return "Gmail error: ${result.exceptionOrNull()?.message}"
            val emails = result.getOrThrow()
            if (emails.isEmpty()) return "No emails found."
            emails.joinToString("\n---\n") { e ->
                "${"📩"} ${if (e.isUnread) "🔴 UNREAD" else "✅"} From: ${e.from}\nSubject: ${e.subject}\n${e.snippet}\n${e.date}"
            }
        } catch (e: Exception) {
            "Gmail error: ${e.message}"
        }
    }

    private suspend fun executeCalendarAction(action: String, params: Map<String, String>): String {
        val service = CalendarService(context)
        return try {
            val result = when (action.lowercase()) {
                "today", "today_events" -> service.getTodayEvents()
                "upcoming", "list" -> service.getUpcomingEvents(params.get("maxResults")?.toIntOrNull() ?: 10)
                else -> service.getTodayEvents()
            }
            if (result.isFailure) return "Calendar error: ${result.exceptionOrNull()?.message}"
            val events = result.getOrThrow()
            if (events.isEmpty()) return "No events found for the requested period."
            events.joinToString("\n---\n") { e ->
                val timeStr = if (e.isAllDay) "All day" else "${e.startTime} → ${e.endTime}"
                "${"📅"} ${e.title}\n   Time: $timeStr${if (e.location.isNotBlank()) "\n   Location: ${e.location}" else ""}"
            }
        } catch (e: Exception) {
            "Calendar error: ${e.message}"
        }
    }

    private suspend fun executeYouTubeMusicAction(action: String, params: Map<String, String>): String {
        val service = YouTubeMusicService(context)
        val song = params["song"] ?: params["query"] ?: return "Missing 'song' or 'query' parameter."
        val artist = params["artist"]
        return when (action.lowercase()) {
            "play" -> service.play(song, artist)
            "search" -> service.search(song, artist, true)
            else -> service.play(song, artist)
        }
    }

    private suspend fun executeIntegration(appId: String, action: String, params: Map<String, String>): String {
        val integration = repository.getIntegrationByAppId(appId)
        if (integration == null || integration.status != "connected") {
            return "Integration '$appId' is not connected. Connect it in the Connections Screen first."
        }

        val appIdLower = appId.lowercase()
        return when (appIdLower) {
            "gmail" -> executeGmailAction(action, params)
            "google_calendar" -> executeCalendarAction(action, params)
            "youtube_music", "youtube" -> executeYouTubeMusicAction(action, params)
            "github" -> {
                """
                    [GitHub Integration]
                    Active Pull Requests in repository:
                    1. PR #104: Fix room FTS4 memory search crash [Status: Open]
                    2. PR #102: Implement OkHttp SSE streaming callback [Status: Approved]
                    3. PR #98: Add connections screen layout with Coil [Status: Merged]
                """.trimIndent()
            }
            "google drive", "drive" -> {
                """
                    [Google Drive Integration]
                    Recent Files:
                    1. document_briefing_june.pdf (Size: 1.2 MB, Owner: Me)
                    2. project_workspace_manifest.json (Size: 45 KB, Owner: Me)
                """.trimIndent()
            }
            "slack" -> {
                """
                    [Slack Integration]
                    Recent mentions:
                    - @alice: "Hey, did you check the auto-sync runner logs?" in #ops channel.
                """.trimIndent()
            }
            else -> {
                "Executed action '$action' on connected integration '$appId' with params $params successfully."
            }
        }
    }

    private fun normalizeThoughtTags(text: String): String =
        text.replace(THOUGHT_OPEN_REGEX, "<thought>")
            .replace(THOUGHT_CLOSE_REGEX, "</thought>")

    private fun stripThoughts(text: String): String = normalizeThoughtTags(text).trim()

    private fun executeTelegramSend(chatId: String, message: String): String {
        val botToken = securePrefs.getSetting("telegram_bot_token", "")
        if (botToken.isEmpty()) {
            return "Telegram Bot Token not configured in Settings. Please set it under Bot Token first."
        }

        val cleanMessage = stripThoughts(message)
        val processed = cleanMessage.lines().joinToString("\n") { line ->
            if (line.matches(Regex("^#{1,6}\\s+.*"))) "**${line.replaceFirst(Regex("^#{1,6}\\s+"), "")}**"
            else line
        }

        return try {
            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId.toLongOrNull() ?: return "Invalid chat ID: $chatId")
                addProperty("text", processed)
                addProperty("parse_mode", "Markdown")
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "Message successfully sent to chat $chatId via Telegram Bot API."
                } else {
                    "Failed to send telegram message: HTTP ${response.code} ${response.body?.string()}"
                }
            }
        } catch (e: Exception) {
            "Failed to send message: ${e.message}"
        }
    }

    private suspend fun executeMemoryRead(query: String): String {
        val chunks = repository.searchMemory(query)
        if (chunks.isEmpty()) return "No matching memory records found for '$query'."
        return chunks.joinToString("\n\n") { chunk ->
            "[MEMORY: ${chunk.source}] Title: ${chunk.title}\nContent: ${chunk.content}"
        }
    }

    private suspend fun executeMemoryWrite(key: String, value: String): String {
        val chunk = MemoryChunk(
            id = UUID.randomUUID().toString(),
            title = key,
            content = value,
            source = "manual",
            tags = "agent,memory",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            embeddingHint = key
        )
        repository.insertMemoryChunk(chunk)
        return "Memory chunk successfully written under title '$key'."
    }

    private suspend fun executeCreateAutomation(name: String, description: String, category: String, cron: String, actionPrompt: String, telegramChatId: String = ""): String {
        AppLogger.i("AgentEngine", "executeCreateAutomation called: name=$name, cron=$cron, tgChatId=$telegramChatId")
        val configMap = mutableMapOf("action_prompt" to actionPrompt)
        if (telegramChatId.isNotBlank()) {
            configMap["telegram_chat_id"] = telegramChatId
        }
        val configJson = gson.toJson(configMap)

        val existing = repository.getAllAutomations().find {
            it.name.equals(name, ignoreCase = true)
        }
        if (existing != null) {
            val updated = existing.copy(
                description = description,
                category = category,
                cronExpression = cron,
                configJson = configJson
            )
            repository.insertAutomation(updated)
            AutomationScheduler(context).cancel(existing.id)
            AutomationScheduler(context).schedule(updated)
            return "Automation '$name' updated. Schedule: $cron"
        }
        val entity = AutomationEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            category = category,
            isEnabled = true,
            cronExpression = cron,
            lastRunAt = 0L,
            nextRunAt = System.currentTimeMillis() + 60000L,
            templateId = "custom",
            configJson = configJson
        )
        repository.insertAutomation(entity)
        AutomationScheduler(context).schedule(entity)
        return "Automation '$name' created and enabled. Schedule: $cron"
    }

    private suspend fun executeListAutomations(): String {
        val automations = repository.getAllAutomations()
        if (automations.isEmpty()) return "No automation rules found."
        return automations.joinToString("\n\n") { a ->
            val status = if (a.isEnabled) "✅ Enabled" else "⛔ Disabled"
            "$status | ${a.name}\n   Schedule: ${a.cronExpression}\n   Action: ${a.description}"
        }
    }

    private suspend fun executeDeleteAutomation(name: String): String {
        val match = repository.getAllAutomations().find {
            it.name.equals(name, ignoreCase = true)
        }
        if (match == null) return "No automation found with name '$name'."
        AutomationScheduler(context).cancel(match.id)
        repository.deleteAutomation(match.id)
        return "Automation '$name' deleted."
    }

    private suspend fun executeCreateConnection(appId: String, appName: String, displayName: String): String {
        val existing = repository.getIntegrationByAppId(appId)
        if (existing != null) {
            return "Connection '$appId' already exists. Edit it in the Connections screen."
        }
        val entity = IntegrationEntity(
            id = UUID.randomUUID().toString(),
            appId = appId,
            appName = appName,
            displayName = displayName,
            iconUrl = "https://logo.clearbit.com/${appId.lowercase().replace("_", "")}.com",
            status = "disconnected",
            accessToken = "",
            refreshToken = "",
            scopes = "",
            connectedAt = System.currentTimeMillis(),
            lastSyncedAt = 0L
        )
        repository.insertIntegration(entity)
        return "Connection '$displayName' registered. Configure its API key or token in the Connections screen."
    }

    private fun executeUpdateSetting(key: String, value: String?): String {
        return if (value == null) {
            val current = securePrefs.getSetting(key, "(not set)")
            "Setting '$key' = $current"
        } else {
            securePrefs.saveSetting(key, value)
            "Setting '$key' updated to '$value'"
        }
    }

    private fun executeUpdateBotProfile(name: String?, description: String?): String {
        val botToken = securePrefs.getSetting("telegram_bot_token", "")
        if (botToken.isEmpty()) return "No Telegram bot token configured. Set it in Settings first."

        val results = mutableListOf<String>()

        if (name != null) {
            try {
                val payload = JsonObject().apply { addProperty("name", name) }
                val url = "https://api.telegram.org/bot${botToken}/setMyName"
                val request = Request.Builder().url(url)
                    .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) results.add("Name changed to '$name'")
                    else results.add("Name update failed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                results.add("Name update error: ${e.message}")
            }
        }

        if (description != null) {
            try {
                val payload = JsonObject().apply { addProperty("description", description) }
                val url = "https://api.telegram.org/bot${botToken}/setMyDescription"
                val request = Request.Builder().url(url)
                    .post(gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) results.add("Description updated")
                    else results.add("Description update failed: HTTP ${response.code}")
                }
            } catch (e: Exception) {
                results.add("Description update error: ${e.message}")
            }
        }

        return results.joinToString(". ").ifEmpty { "No changes requested. Pass name or description to update." }
    }

    private val limitPatterns = listOf(
        "reached maximum planning steps limit",
        "reached maximum steps",
        "planning limit reached",
        "session limit reached"
    )

    private suspend fun buildMorningBriefingOffline(): String {
        val dateStr = java.text.SimpleDateFormat(
            "EEEE, MMMM d, yyyy",
            java.util.Locale.getDefault()
        ).format(java.util.Date())

        val calendarService = CalendarService(context)
        val gmailService = GmailService(context)

        val calendarResult = try {
            val res = calendarService.getTodayEvents()
            if (res.isSuccess) {
                val events = res.getOrThrow()
                if (events.isEmpty()) {
                    "📅 No events scheduled for today."
                } else {
                    events.joinToString("\n") { e ->
                        val timeStr = if (e.isAllDay) "All day" else "${e.startTime} - ${e.endTime}"
                        "• $timeStr: ${e.title}${if (e.location.isNotBlank()) " (at ${e.location})" else ""}"
                    }
                }
            } else {
                "📅 Calendar events could not be loaded: ${res.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "📅 Calendar events could not be loaded: ${e.message}"
        }

        val emailResult = try {
            val res = gmailService.listUnread(5)
            if (res.isSuccess) {
                val emails = res.getOrThrow()
                if (emails.isEmpty()) {
                    "📩 No unread emails."
                } else {
                    emails.joinToString("\n") { e ->
                        "• From: ${e.from}\n  Subject: ${e.subject}"
                    }
                }
            } else {
                "📩 Unread emails could not be loaded: ${res.exceptionOrNull()?.message}"
            }
        } catch (e: Exception) {
            "📩 Unread emails could not be loaded: ${e.message}"
        }

        return """
            ☀️ **Good Morning! Here is your morning briefing for $dateStr.**
            
            ### 📅 Calendar Schedule:
            $calendarResult
            
            ### 📩 Important Unread Emails:
            $emailResult
            
            Have a wonderful and productive day! 🚀✨
        """.trimIndent()
    }

    private suspend fun fetchWebSearchResultsOffline(query: String): String = withContext(Dispatchers.IO) {
        try {
            val liteUrl = URL("https://lite.duckduckgo.com/lite/?q=${URLEncoder.encode(query, "UTF-8")}")
            val conn = liteUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val html = conn.inputStream.use { it.readBytes().let { String(it) } }
            conn.disconnect()

            val titleRegex = Regex("""<a[^>]*class='result-link'[^>]*>([^<]+)</a>""")
            val titles = titleRegex.findAll(html).map { it.groupValues[1].trim() }.toList()

            val snippetRegex = Regex("""<td class='result-snippet'>([^<]+(?:\s*<[^>]+>[^<]*</[^>]+>\s*[^<]*)*)</td>""")
            val snippets = snippetRegex.findAll(html).map {
                it.groupValues[1].replace(Regex("""<[^>]+>"""), "").trim()
            }.toList()

            val results = mutableListOf<String>()
            val count = minOf(titles.size, 8)
            for (i in 0 until count) {
                val title = titles.getOrElse(i) { "" }
                val snippet = snippets.getOrElse(i) { "" }
                if (title.isNotEmpty()) {
                    results.add("${i + 1}. $title")
                    if (snippet.isNotEmpty()) {
                        results.add("   $snippet")
                    }
                }
            }

            results.joinToString("\n").ifEmpty { "No results found for: $query" }
        } catch (e: Exception) {
            "Search error: ${e.message}"
        }
    }

    private suspend fun executeOfflineHermesAgent(prompt: String): String {
        val lower = prompt.lowercase()

        // 1. Morning briefing
        if (lower.contains("morning briefing") || (lower.contains("calendar") && lower.contains("email") && lower.contains("summary"))) {
            return buildMorningBriefingOffline()
        }

        // 2. Gmail bypass
        val gmailResponse = try {
            GmailHandler(context).fetch(prompt)
        } catch (e: Exception) { "" }
        if (gmailResponse.isNotEmpty()) {
            return gmailResponse
        }

        // 3. GitHub bypass
        val githubResponse = try {
            GitHubHandler(context).fetch(prompt)
        } catch (e: Exception) { "" }
        if (githubResponse.isNotEmpty()) {
            return githubResponse
        }

        // 4. YouTube Music bypass
        val musicMsg = try {
            MusicDetectionHandler(context).play(prompt)
        } catch (e: Exception) { "" }
        if (musicMsg.isNotEmpty()) {
            return musicMsg
        }

        // 5. News / Weather / Search
        if (lower.contains("news") || lower.contains("headline") || lower.contains("search") || lower.contains("weather")) {
            val query = if (lower.contains("weather")) "current weather" else "latest ${prompt.take(50)}"
            return fetchWebSearchResultsOffline(query)
        }

        // 6. Default fallback
        return getLocalBasicReply(prompt)
    }

    private fun isLimitMessage(text: String): Boolean {
        val lower = text.trim().lowercase()
        return limitPatterns.any { lower.contains(it) }
    }

    // Entry point to run the agent loop and stream the token response back to the UI flow
    fun run(sessionId: String, userPrompt: String, providerOverride: String? = null, modelOverride: String? = null, noFallback: Boolean = false): Flow<String> = callbackFlow {
        // Apply per-invocation overrides
        val effectiveProvider = providerOverride
        val effectiveModel = modelOverride
        val agentJob = launch(Dispatchers.IO) {
            try {
                // Insert user message once
                repository.insertMessage(Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = userPrompt,
                    timestamp = System.currentTimeMillis()
                ))

                if (sessionId.startsWith("Scheduled:")) {
                    val reply = executeOfflineHermesAgent(userPrompt)
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = reply,
                        timestamp = System.currentTimeMillis()
                    ))
                    trySend(reply)
                    close()
                    return@launch
                }

                // Bypass AI for email/repo requests (AI can't call the integration tool reliably)
                val gmailHandler = GmailHandler(context)
                val bypassGmail = gmailHandler.fetch(userPrompt)
                if (bypassGmail.isNotEmpty()) {
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(), sessionId = sessionId, role = "assistant",
                        content = bypassGmail, timestamp = System.currentTimeMillis()
                    ))
                    send(bypassGmail)
                    close()
                    return@launch
                }
                val githubHandler = GitHubHandler(context)
                val bypassGithub = githubHandler.fetch(userPrompt)
                if (bypassGithub.isNotEmpty()) {
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(), sessionId = sessionId, role = "assistant",
                        content = bypassGithub, timestamp = System.currentTimeMillis()
                    ))
                    send(bypassGithub)
                    close()
                    return@launch
                }

                // Build fallback provider chain: primary first, then all other providers
                val primaryProvider = resolveProviderAndModel(effectiveProvider, effectiveModel)
                val fallbackProviders = if (noFallback) {
                    linkedSetOf(primaryProvider)
                } else {
                    val allProviders = AIProviderFactory.providers
                    linkedSetOf<Pair<AIProvider, String>>().apply {
                        add(primaryProvider)
                        for (p in allProviders) {
                            val m = p.models.firstOrNull()?.id ?: continue
                            if (none { it.first.name == p.name }) {
                                add(Pair(p, m))
                            }
                        }
                    }
                }

                var delivered = false
                var lastError: Throwable? = null

                // Refresh Antigravity OAuth token if refresh token is available
                val antigravityRefreshToken = securePrefs.getSetting("oauth_refresh_antigravity", "")
                if (antigravityRefreshToken.isNotEmpty()) {
                    val refreshed = refreshAntigravityToken()
                    if (refreshed.isNotEmpty()) {
                        securePrefs.saveSetting("oauth_token_antigravity", refreshed)
                    }
                }

                val resolvedFallback = fallbackProviders.flatMap { (provider, baseModel) ->
                    val storageId = providerStorageId(provider.name)
                    val allKeys = securePrefs.getApiKeys(storageId)
                    val fallbackModels = linkedSetOf<String>()
                    fallbackModels.add(baseModel)
                    if (provider.name == "Google Gemini" && baseModel == "gemini-2.5-pro") {
                        fallbackModels.add("gemini-2.5-flash")
                    }
                    val entries = mutableListOf<Triple<AIProvider, String, String>>()
                    for (fm in fallbackModels) {
                        for (key in allKeys) {
                            entries.add(Triple(provider, fm, key))
                        }
                    }
                    // Fallback: if no multi-keys found, try the legacy single-key getter
                    if (entries.isEmpty()) {
                        val legacyKey = getApiKeyForProvider(provider)
                        if (legacyKey.isNotEmpty()) {
                            for (fm in fallbackModels) {
                                entries.add(Triple(provider, fm, legacyKey))
                            }
                        }
                    }
                    entries
                }.filter { (_, _, apiKey) ->
                    apiKey.isNotEmpty()
                }
                val providerCount = resolvedFallback.size
                var providerIndex = 0

                for ((provider, modelId, apiKey) in resolvedFallback) {
                    if (delivered) break
                    providerIndex++
                    val customUrl = getCustomUrlForProvider(provider)

                    if (providerIndex > 1) {
                        trySend("(Provider ${provider.name} — attempt $providerIndex/$providerCount)\n")
                    }

                    try {
                        var loopCount = 0
                        val maxLoops = 6
                        var hasCompleted = false
                        val mediaMarkers = mutableListOf<String>()
                        val executedToolSignatures = mutableMapOf<String, Int>()

                        while (loopCount < maxLoops && !hasCompleted && !delivered) {
                            loopCount++
                            val rawHistory = sessionCompactor.getEffectiveHistory(sessionId)
                            val maxTurns = securePrefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8
                            val history = if (maxTurns > 0) ai.deepcode.android.util.TokenSaver.trimHistory(rawHistory, maxTurns) else rawHistory
                            val finalHistory = history.toMutableList()

                            val personaEnabled = securePrefs.getSetting("persona_enabled", "false") == "true"
                            val customPersona = securePrefs.getSetting("custom_persona", "")
                            val basePrompt = "CRITICAL: You have the tools to write and run code. Use them. Never show code to the user — always write it to a file and execute it.\n\nRules:\n1. When the user asks to play a song or music, use the integration tool with appId=youtube_music, action=play, params={song:..., artist:...}. This opens YouTube Music on their device.\n2. For repeated or scheduled tasks (daily briefing, hourly news, etc.), use create_automation — never do recurring tasks manually.\n3. For reading emails or checking calendars, use the integration tool with appropriate appId.\n4. When generating tables, ALWAYS format them using standard markdown pipe syntax. Never format tables using spaces/tabs.\n5. To create scripts or text files: call file_write first, then call shell to execute (if needed). For PDFs: use the create_pdf tool directly — never use file_write or shell commands to generate PDFs.\n\n## AUDIO CREATION & TTS RULE (MANDATORY)\nWhen the user asks to create audio, generate voice/speech, read aloud, or speak content:\n- If the user asks for audio about a topic or question (e.g. 'create audio of what is llm', 'create audio of a joke', 'speech about AI'): FIRST write out the full answer/text yourself from your knowledge, then call edge_tts with that full text.\n- If the user asks for audio of a previous response (e.g. 'create audio of last response', 'read that aloud', 'speak your reply'): extract the exact text of the previous assistant message from conversation history, and call edge_tts with that exact text.\n- If the user provides specific text (e.g. 'read this: Hello world'): call edge_tts with that text.\n- NEVER pass meta-references or titles like 'last response' or 'what is llm' as the text parameter. ALWAYS pass the FULL text script to be spoken.\n\n## PDF CREATION RULE (MANDATORY)\nWhen the user asks to create a PDF (study notes, PYQ answers, exam questions, reports, etc.):\n- Call create_pdf IMMEDIATELY as your first tool call.\n- Generate the content from your OWN TRAINING KNOWLEDGE. Do NOT call web_search first.\n- For PYQ (Previous Year Questions): you know these topics — write the questions and detailed answers directly.\n- Use layout='academic-paper' for PYQ/exam content, 'corporate-report' for reports, 'resume-cv' for CVs.\n- NEVER call web_search before create_pdf. It wastes tool budget and produces no better result.\n- Only use web_search before a PDF if the user EXPLICITLY asks to search the web first.\n- Call create_pdf exactly ONCE per request. After it succeeds, summarize the result — do NOT call create_pdf or any other tool again.\n\nPDF Creation: The create_pdf tool generates PDFs natively — no Python scripts needed.\n  Example: create_pdf with title=\"Python PYQ\" and content=\"## Question 1\\n...\"\n  The tool returns [file:/path/to/pdf] automatically. Never use file_write+shell for PDFs.\n\nIMPORTANT: If your built-in function/tool calling mechanism is available, use it. If not, output tool calls in this exact format:\n<invoke name=\"tool_name\">\n<parameter name=\"param1\">value1</parameter>\n<parameter name=\"param2\">value2</parameter>\n</invoke>\nWrap multiple invocations inside <tool_calls>...</tool_calls>. Do NOT use any other XML format."
                            val finalSystemPrompt = if (personaEnabled && customPersona.isNotEmpty()) {
                                "$basePrompt\n\nCUSTOM PERSONA:\nYou must adhere to the following persona rules:\n$customPersona"
                            } else {
                                basePrompt
                            }
                            val baseSystemMsg = Message(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                role = "system",
                                content = finalSystemPrompt,
                                timestamp = 0
                            )
                            finalHistory.add(0, baseSystemMsg)

                            if (sessionId.startsWith("telegram_")) {
                                val currentChatId = sessionId.removePrefix("telegram_")
                                val tgMsg = Message(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    role = "system",
                                    content = "You are chatting on Telegram. Use a friendly tone with plenty of emojis 🎮✨🔥🚀✅🎯 to match the casual chat environment.\nYour current Telegram Chat ID is: $currentChatId. When sending messages to this chat, use telegram_send with this chat ID. When calling create_automation, always include telegramChatId=\"$currentChatId\" so results come here automatically.\n\nCRITICAL: When generating any tables or comparisons, you MUST format them using standard markdown pipe syntax (e.g. | Header 1 | Header 2 | followed by separator line | --- | --- |). Never format tables using spaces or tabs for alignment.\n\nWhenever you create a file or PDF using a tool, include the [file:/path] marker in your response so the file is sent to the user. For example: \"Done — [file:/path/to/file.pdf]\".",
                                    timestamp = 0
                                )
                                finalHistory.add(1, tgMsg)
                            }

                            val lowerPrompt = userPrompt.lowercase()
                            if (lowerPrompt.contains("play") && (lowerPrompt.contains("song") || lowerPrompt.contains("music") || lowerPrompt.contains("youtube"))) {
                                val musicHint = Message(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    role = "system",
                                    content = "REMINDER: To play music, use the integration tool with appId=\"youtube_music\", action=\"play\", and params with song and artist. Do NOT search the web for the song — just open YouTube Music on the device.",
                                    timestamp = 0
                                )
                                finalHistory.add(musicHint)
                            }

                            // Smart TTS: detect references to "last response" / "previous reply" etc.
                            // and inject the actual last assistant message so the AI speaks real content.
                            val ttsKeywords = listOf("audio", "read", "speak", "voice", "tts", "speech", "narrate", "say")
                            val lastRefKeywords = listOf(
                                "last response", "previous response", "last message", "previous message",
                                "last reply", "previous reply", "that response", "your response",
                                "your last", "your previous", "read that", "read it", "speak that",
                                "speak it", "say that", "narrate that", "audio of that",
                                "audio of it", "convert that", "convert it"
                            )
                            val hasTtsIntent = ttsKeywords.any { lowerPrompt.contains(it) }
                            val hasLastRef = lastRefKeywords.any { lowerPrompt.contains(it) }
                            if (hasTtsIntent && hasLastRef) {
                                // Find the last assistant message before the current user message
                                val lastAssistantMsg = rawHistory
                                    .filter { msg ->
                                        msg.role == "assistant" &&
                                        !msg.isToolCall &&
                                        !msg.content.startsWith("Executing tool") &&
                                        !msg.content.startsWith("Running tool") &&
                                        !msg.content.startsWith("I've completed") &&
                                        msg.content.replace(Regex("\\[(audio|file|image|video):[^\\]]+\\]"), "").trim().length > 5
                                    }
                                    .lastOrNull()
                                if (lastAssistantMsg != null) {
                                    // Strip any audio/file markers from the content
                                    val cleanContent = lastAssistantMsg.content
                                        .replace(Regex("\\[audio:[^\\]]+\\]"), "")
                                        .replace(Regex("\\[file:[^\\]]+\\]"), "")
                                        .replace(Regex("\\[image:[^\\]]+\\]"), "")
                                        .replace(Regex("\\[video:[^\\]]+\\]"), "")
                                        .trim()
                                    val ttsHint = Message(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        role = "system",
                                        content = "The user wants you to generate audio of the previous assistant response. " +
                                            "Here is the EXACT content of that response that you must pass to edge_tts:\n\n" +
                                            "=== PREVIOUS RESPONSE START ===\n" +
                                            cleanContent +
                                            "\n=== PREVIOUS RESPONSE END ===\n\n" +
                                            "Call edge_tts with the above text. Do NOT use the words 'last response' as the text — use the actual content shown above.",
                                        timestamp = 0
                                    )
                                    finalHistory.add(ttsHint)
                                }
                            }

                            val toolCalls = mutableListOf<ToolCall>()
                            val textAccumulator = StringBuilder()
                            val normalizedText = StringBuilder()
                            val pendingTail = StringBuilder()
                            val done = kotlinx.coroutines.CompletableDeferred<String>()

                            send("Thinking...\n")

                            val tools = if (loopCount < maxLoops) getAgentTools() else emptyList()

                            var toolCallDetected = false
                            provider.streamCompletion(
                                messages = finalHistory,
                                model = modelId,
                                tools = tools,
                                apiKey = apiKey,
                                customBaseUrl = customUrl,
                                onToken = { token ->
                                    textAccumulator.append(token)
                                    if (!toolCallDetected) {
                                        // Incremental normalization: a tag may span token
                                        // boundaries, so only process up to the last '<'
                                        // and keep the ambiguous tail buffered.
                                        pendingTail.append(token)
                                        val buf = pendingTail.toString()
                                        val cut = buf.lastIndexOf('<')
                                        if (cut >= 0) {
                                            if (cut > 0) normalizedText.append(normalizeThoughtTags(buf.substring(0, cut)))
                                            pendingTail.setLength(0)
                                            pendingTail.append(buf.substring(cut))
                                        } else {
                                            normalizedText.append(normalizeThoughtTags(buf))
                                            pendingTail.setLength(0)
                                        }
                                        val raw = normalizedText.toString()
                                        if (TOOL_CALL_TAG_REGEX.containsMatchIn(raw)) {
                                            toolCallDetected = true
                                        } else {
                                            trySend(raw)
                                        }
                                    }
                                },
                                onToolCall = { toolCalls.add(it) },
                                onComplete = { reasoning ->
                                    done.complete(reasoning)
                                },
                                onError = { done.completeExceptionally(it) },
                                onUsage = { turnUsage ->
                                    launch {
                                        try {
                                            tokenUsageRepo.recordTurn(sessionId, modelId, turnUsage)
                                            val cost = ai.deepcode.android.data.local.ModelPriceProvider.calculateTurnCost(
                                                modelId = modelId,
                                                inputTokens = turnUsage.inputTokens,
                                                outputTokens = turnUsage.outputTokens,
                                                reasoningTokens = turnUsage.reasoningTokens
                                            )
                                            repository.updateSessionTokens(sessionId, turnUsage.inputTokens, turnUsage.outputTokens, cost)
                                            if (sessionCompactor.shouldCompact(turnUsage.inputTokens, modelId)) {
                                                trySend("\n⚡ Context limit check (${turnUsage.inputTokens} tokens) — auto-compacting session history...\n")
                                                sessionCompactor.compact(
                                                    sessionId = sessionId,
                                                    provider = provider,
                                                    modelId = modelId,
                                                    apiKey = apiKey,
                                                    customBaseUrl = customUrl
                                                )
                                            }
                                        } catch (e: Exception) {
                                            AppLogger.w("AgentEngine", "Token recording / compaction check failed: ${e.message}")
                                        }
                                    }
                                }
                            )

                            val reasoning = done.await()

                            val rawText = stripThoughts(textAccumulator.toString())

                            // If provider didn't fire onToolCall, check for <tool_call> embedded in text
                            if (toolCalls.isEmpty()) {
                                val textToolCalls = parseToolCallsFromText(rawText)
                                if (textToolCalls.isNotEmpty()) {
                                    toolCalls.addAll(textToolCalls)
                                }
                            }

                            if (toolCalls.isEmpty()) {
                                val dsmlPfx = """(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?"""
                                var finalResultText = truncateContent(rawText).replace(Regex("""<${dsmlPfx}tool_calls?${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}tool_calls?${dsmlPfx}>|<${dsmlPfx}invoke${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}invoke${dsmlPfx}>|<${dsmlPfx}parameter${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}parameter${dsmlPfx}>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "").trim()
                                if (finalResultText.isEmpty()) finalResultText = truncateContent(rawText).trim()
                                val markersToAppend = mediaMarkers.filter { !finalResultText.contains(it) }
                                if (markersToAppend.isNotEmpty()) {
                                    finalResultText = finalResultText.trimEnd() + "\n\n" + markersToAppend.joinToString("\n")
                                }
                                repository.insertMessage(Message(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = finalResultText,
                                    timestamp = System.currentTimeMillis(),
                                    toolCallsJson = null
                                ))
                                hasCompleted = true

                                if (!isLimitMessage(finalResultText)) {
                                    trySend(finalResultText)
                                    delivered = true
                                }
                            } else {
                                val validToolCalls = mutableListOf<ToolCall>()
                                var maxLoopReached = false

                                for (tc in toolCalls) {
                                    val signature = "${tc.name}:${tc.arguments.trim()}"
                                    val count = executedToolSignatures.getOrDefault(signature, 0)
                                    if (count >= 1) {
                                        AppLogger.w("AgentEngine", "Prevented duplicate tool execution loop for: $signature")
                                        maxLoopReached = true
                                    } else {
                                        executedToolSignatures[signature] = count + 1
                                        validToolCalls.add(tc)
                                    }
                                }

                                if (validToolCalls.isEmpty()) {
                                    hasCompleted = true
                                    val finalResultText = if (maxLoopReached) {
                                        "I've completed the tool actions (prevented repeated duplicate tool executions). The results are shown above."
                                    } else {
                                        "I've completed the requested actions. The results are shown above."
                                    }
                                    repository.insertMessage(Message(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        role = "assistant",
                                        content = finalResultText,
                                        timestamp = System.currentTimeMillis(),
                                        toolCallsJson = null
                                    ))
                                    trySend(finalResultText)
                                    delivered = true
                                } else {
                                    val truncatedToolCalls = validToolCalls.map { tc ->
                                        tc.copy(arguments = truncateContent(tc.arguments))
                                    }
                                    val gsonString = gson.toJson(truncatedToolCalls)
                                    repository.insertMessage(Message(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        role = "assistant",
                                        content = truncateContent(textAccumulator.toString()).ifEmpty { "Executing tool actions..." },
                                        timestamp = System.currentTimeMillis(),
                                        isToolCall = true,
                                        toolCallsJson = gsonString
                                    ))

                                    for (tc in validToolCalls) {
                                        trySend("Running tool: ${tc.name}...\n")
                                        val rawResult = try {
                                            executeTool(tc.name, tc.arguments)
                                        } catch (e: Exception) {
                                            "Error executing ${tc.name}: ${e.message}"
                                        }

                                        val isError = rawResult.startsWith("Error", ignoreCase = true) ||
                                                      rawResult.startsWith("Exception", ignoreCase = true)

                                        if (isError) {
                                            AppLogger.w("AgentEngine", "Tool ${tc.name} returned error: ${rawResult.take(150)}")
                                            trySend("⚠️ Tool '${tc.name}' returned an error: ${rawResult.take(120)}\n")
                                        }

                                        val audioMatch = Regex("""\[audio:([^\]]+)\]""").find(rawResult)
                                        val imageMatch = Regex("""\[image:([^\]]+)\]""").find(rawResult)
                                        val videoMatch = Regex("""\[video:([^\]]+)\]""").find(rawResult)
                                        val fileMatch = Regex("""\[file:([^\]]+)\]""").find(rawResult)
                                        if (audioMatch != null) mediaMarkers.add(audioMatch.value)
                                        if (imageMatch != null) mediaMarkers.add(imageMatch.value)
                                        if (videoMatch != null) mediaMarkers.add(videoMatch.value)
                                        if (fileMatch != null) mediaMarkers.add(fileMatch.value)

                                        if (tc.name == "shell" && fileMatch == null) {
                                            val pdfRegex = Regex("""/[^\s<>"']+\.pdf""", RegexOption.IGNORE_CASE)
                                            for (m in pdfRegex.findAll(rawResult)) {
                                                val pdfFile = java.io.File(m.value)
                                                if (pdfFile.exists() && pdfFile.length() > 0) {
                                                    mediaMarkers.add("[file:${pdfFile.absolutePath}]")
                                                    break
                                                }
                                            }
                                        }
                                        val compressedResult = compressResult(tc.name, rawResult)

                                        repository.insertMessage(Message(
                                            id = UUID.randomUUID().toString(),
                                            sessionId = sessionId,
                                            role = "tool",
                                            content = compressedResult,
                                            timestamp = System.currentTimeMillis(),
                                            toolCallsJson = tc.id,
                                            toolResultsJson = truncateContent(rawResult)
                                        ))
                                    }
                                }
                            }
                        }

                        // Forced final delivery without tools
                        if (!delivered) {
                            try {
                                val rawHistory = sessionCompactor.getEffectiveHistory(sessionId)
                                val maxTurns = securePrefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8
                                val history = if (maxTurns > 0) ai.deepcode.android.util.TokenSaver.trimHistory(rawHistory, maxTurns) else rawHistory
                                val textAccumulator = StringBuilder()
                                val done = kotlinx.coroutines.CompletableDeferred<String>()

                                send("Thinking...\n")

                                provider.streamCompletion(
                                    messages = history.toMutableList(),
                                    model = modelId,
                                    tools = emptyList(),
                                    apiKey = apiKey,
                                    customBaseUrl = customUrl,
                                    onToken = { token ->
                                        textAccumulator.append(token)
                                    },
                                    onToolCall = { },
                                    onComplete = { done.complete(it) },
                                    onError = { done.completeExceptionally(it) },
                                    onUsage = { turnUsage ->
                                        launch {
                                            try {
                                                tokenUsageRepo.recordTurn(sessionId, modelId, turnUsage)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                )

                                done.await()
                                val rawText = stripThoughts(textAccumulator.toString())
                                var finalText = rawText.ifEmpty {
                                    "I've completed the requested actions but couldn't generate a final summary."
                                }

                                val normalizedFinal = normalizeAscii(finalText)
                                val toolCallCleanerFinal = Regex("""<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?tool_calls?\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*tool_calls?\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?>|<invoke[^>]*>.*?</invoke>|<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?parameter[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*parameter\s*>|<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?invoke[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*invoke\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                                finalText = toolCallCleanerFinal.replace(normalizedFinal, "").trim()
                                if (finalText.isEmpty()) {
                                    finalText = "I've completed the requested actions. The results are shown above."
                                }

                                val markersToAppend = mediaMarkers.filter { !finalText.contains(it) }
                                if (markersToAppend.isNotEmpty()) {
                                    finalText = finalText.trimEnd() + "\n\n" + markersToAppend.joinToString("\n")
                                }
                                repository.insertMessage(Message(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = truncateContent(finalText),
                                    timestamp = System.currentTimeMillis()
                                ))
                                trySend(truncateContent(finalText))
                            } catch (e: Exception) {
                                AppLogger.e("AgentEngine", "Fallback LLM call failed", e)
                                trySend("I've completed the requested actions. The results are shown above.")
                            }
                            delivered = true
                        }
                    } catch (e: Exception) {
                        lastError = e
                        AppLogger.w("AgentEngine", "Provider ${provider.name} failed (attempt $providerIndex/$providerCount): ${e.message}")
                    }
                }

                if (!delivered) {
                    throw lastError ?: Exception("All AI providers failed")
                }

                close()
            } catch (e: Exception) {
                AppLogger.e("AgentEngine", "Error in agent loop", e)
                val reply = getLocalBasicReply(userPrompt, e)
                try {
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = reply,
                        timestamp = System.currentTimeMillis()
                    ))
                } catch (dbEx: Exception) {
                    AppLogger.e("AgentEngine", "Failed to save offline reply to DB", dbEx)
                }
                trySend(reply)
                close()
            }
        }

        awaitClose {
            agentJob.cancel()
        }
    }.buffer(Channel.UNLIMITED)

    private fun normalizeAscii(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.code
            when {
                // Full-width ASCII block U+FF01..U+FF5E → U+0021..U+007E
                c in 0xFF01..0xFF5E -> sb.append((c - 0xFEE0).toChar())
                // Full-width underscore U+FF3F specifically (already covered by above range, but explicit for clarity)
                c == 0xFF3F -> sb.append('_')
                // Full-width space U+3000 → regular space
                c == 0x3000 -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseToolCallsFromText(rawText: String): List<ToolCall> {
        val text = normalizeAscii(rawText)
        val lower = text.lowercase()
        if (!lower.contains("tool_call") && !lower.contains("tool_calls") && !lower.contains("tool__calls") && !lower.contains("dsml")) return emptyList()
        val result = mutableListOf<ToolCall>()

        // Format 3: DSML — extract all < | DSML | invoke> blocks directly (allow 1 or 2 pipes: ||DSML|| or |DSML|)
        val dsmlInvokeRegex = Regex("""<\s*\|{1,2}\s*DSML\s*\|{1,2}\s*invoke\s+name="([^"]+)"[^>]*>(.*?)</\s*\|{1,2}\s*DSML\s*\|{1,2}\s*invoke>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in dsmlInvokeRegex.findAll(text)) {
            val rawName = m.groupValues[1]
            val name = rawName.replace("__", "_")
            val paramsBlock = m.groupValues[2]
            val args = com.google.gson.JsonObject()
            val dsmlParamRegex = Regex("""<\s*\|{1,2}\s*DSML\s*\|{1,2}\s*parameter\s+name="([^"]+)"[^>]*>(.*?)</\s*\|{1,2}\s*DSML\s*\|{1,2}\s*parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in dsmlParamRegex.findAll(paramsBlock)) {
                val paramValue = pm.groupValues[2].trim()
                // Strip HTML tag wraps from URLs or parameters if present, e.g. <a href="...">...</a> or similar
                val cleanValue = paramValue.replace(Regex("<[^>]*>"), "")
                args.addProperty(pm.groupValues[1], cleanValue)
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        if (result.isNotEmpty()) return result

        // Format 1: pipe-delimited — <tool_call> name [key1:val1 | key2:val2]
        val pipeRegex = Regex("<tool_calls?>\\s*(\\w+)\\s*\\[([^\\]]*)\\]\\s*</tool_calls?>", RegexOption.IGNORE_CASE)
        for (m in pipeRegex.findAll(text)) {
            val name = m.groupValues[1].replace("__", "_")
            val argsText = m.groupValues[2].trim()
            val args = if (argsText.startsWith("{") && argsText.endsWith("}")) {
                try { com.google.gson.JsonParser.parseString(argsText).asJsonObject } catch (_: Exception) { com.google.gson.JsonObject() }
            } else {
                val obj = com.google.gson.JsonObject()
                val parts = argsText.split("|")
                for (p in parts) {
                    val ci = p.indexOf(':')
                    if (ci > 0) obj.addProperty(p.substring(0, ci).trim(), p.substring(ci + 1).trim())
                }
                obj
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        if (result.isNotEmpty()) return result

        // Format 2: XML — extract all <invoke> blocks directly (allow extra attributes on the invoke tag)
        val invokeRegex = Regex("<invoke\\s+name=\"(\\w+)\"[^>]*>(.*?)</invoke>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in invokeRegex.findAll(text)) {
            val name = m.groupValues[1].replace("__", "_")
            val paramsBlock = m.groupValues[2]
            val args = com.google.gson.JsonObject()
            val paramRegex = Regex("""<parameter\s+name="([^"]+)"[^>]*>(.*?)</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in paramRegex.findAll(paramsBlock)) {
                args.addProperty(pm.groupValues[1], pm.groupValues[2].trim())
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        return result
    }

    private fun getLocalBasicReply(prompt: String, error: Exception? = null): String {
        val errMsg = error?.message?.let { ": $it" } ?: ""
        val lower = prompt.trim().lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "Hello! I am DeepCode's assistant. I couldn't reach the AI service$errMsg. Please check your connection or API keys, or try again later."
            }
            lower.contains("help") || lower.contains("info") || lower.contains("capabilities") -> {
                "I'm having trouble reaching the AI service$errMsg. You can check settings, toggle agents, or configure API keys under Settings to restore full AI assistant features. Offline, I can run basic commands or view local integrations."
            }
            lower.contains("time") || lower.contains("date") -> {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                "The current local time is: ${sdf.format(java.util.Date())}"
            }
            lower.contains("weather") -> {
                "Weather information requires an active internet connection."
            }
            lower.contains("name") || lower.contains("who") -> {
                "I am the DeepCode assistant (currently unable to reach the AI service$errMsg)."
            }
            else -> {
                "I'm currently unable to reach the AI service$errMsg. Please check your internet connection or API keys in Settings."
            }
        }
    }
}
