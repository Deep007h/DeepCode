package ai.deepcode.android.service.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import ai.deepcode.android.util.AppLogger
import androidx.core.app.NotificationCompat
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.ModelCatalog
import ai.deepcode.android.data.remote.providerStorageId
import ai.deepcode.android.domain.model.AIModel
import ai.deepcode.android.ui.settings.Persona
import ai.deepcode.android.ui.settings.builtInPersonas
import ai.deepcode.android.agent.AgentEngine
import ai.deepcode.android.orchestrator.OrchestratorEngine
import ai.deepcode.android.orchestrator.OrchestratorDecision
import ai.deepcode.android.orchestrator.TaskType
import ai.deepcode.android.service.music.MusicDetectionHandler
import ai.deepcode.android.service.schedule.AutomationHandler
import ai.deepcode.android.service.gmail.GmailHandler
import ai.deepcode.android.service.github.GitHubHandler
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TelegramBridgeService : Service() {

    companion object {
        private const val CHANNEL_ID = "telegram_bridge_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "TelegramBridge"
        private const val API_BASE = "https://api.telegram.org/bot"

        private const val PREF_BRIDGE_RUNNING = "bridge_running"

        fun isRunning(context: Context): Boolean {
            return context.getSharedPreferences("telegram_bridge", Context.MODE_PRIVATE)
                .getBoolean(PREF_BRIDGE_RUNNING, false)
        }

        fun start(context: Context) {
            val intent = Intent(context, TelegramBridgeService::class.java)
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                notificationManager.areNotificationsEnabled()
            } else {
                true
            }

            if (hasNotificationPermission && areNotificationsEnabled) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TelegramBridgeService::class.java))
        }
    }

    private lateinit var repository: DeepCodeRepository
    private lateinit var botStore: BotConfigStore
    private lateinit var agentEngine: AgentEngine

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pollingJobs = ConcurrentHashMap<String, Job>()
    private val chatMutexes = ConcurrentHashMap<Long, Mutex>()
    private var configCheckJob: Job? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val offsets = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        repository = DeepCodeRepository(this)
        botStore = BotConfigStore(this)
        agentEngine = AgentEngine(this)
        setRunningFlag(true)
        startPolling()
        AppLogger.d(TAG, "TelegramBridgeService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val areNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }

        if (hasNotificationPermission && areNotificationsEnabled) {
            try {
                createNotificationChannel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification("Telegram Bridge is running"),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification("Telegram Bridge is running"))
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to call startForeground", e)
            }
        } else {
            AppLogger.w(TAG, "Notification permission not granted or disabled. Running in background.")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
        configCheckJob?.cancel()
        serviceScope.cancel()
        setRunningFlag(false)
        AppLogger.d(TAG, "TelegramBridgeService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setRunningFlag(running: Boolean) {
        getSharedPreferences("telegram_bridge", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_BRIDGE_RUNNING, running).apply()
    }

    private fun startPolling() {
        configCheckJob = serviceScope.launch {
            while (isActive) {
                val bots = botStore.getTokens()

                pollingJobs.keys.removeIf { token ->
                    if (token !in bots) {
                        pollingJobs[token]?.cancel()
                        true
                    } else {
                        false
                    }
                }

                for (token in bots) {
                    if (!pollingJobs.containsKey(token)) {
                        pollingJobs[token] = launch {
                            pollBot(token)
                        }
                    }
                }

                delay(5000)
            }
        }
    }

    private suspend fun pollBot(token: String) {
        AppLogger.d(TAG, "Started polling bot: ...${token.takeLast(6)}")
        var offset = offsets.getOrDefault(token, 0L)

        while (currentCoroutineContext().isActive) {
            try {
                val url = "${API_BASE}${token}/getUpdates"
                val payload = JsonObject().apply {
                    addProperty("offset", offset)
                    addProperty("timeout", 30)
                    add("allowed_updates", JsonArray())
                }
                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                    .build()
                val call = client.newCall(request)
                val disposable = currentCoroutineContext()[Job]?.invokeOnCompletion {
                    call.cancel()
                }
                try {
                    call.execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLogger.e(TAG, "Poll HTTP ${response.code} for bot ...${token.takeLast(6)}")
                        delay(5000)
                        return@use
                    }

                    val bodyStr = response.body?.string() ?: return@use
                    val json = gson.fromJson(bodyStr, JsonObject::class.java)

                    if (json.get("ok")?.asBoolean != true) {
                        AppLogger.e(TAG, "Telegram API returned ok=false: ${json.get("description")}")
                        delay(5000)
                        return@use
                    }

                    val result = try {
                        json.getAsJsonArray("result")
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to parse 'result' as JSON array", e)
                        return@use
                    } ?: return@use

                    for (updateElem in result) {
                        if (updateElem.isJsonNull || !updateElem.isJsonObject) continue
                        try {
                            val update = updateElem.asJsonObject
                            val updateId = update.get("update_id")?.asLong ?: continue
                            offset = updateId + 1
                            offsets[token] = offset

                            AppLogger.d(TAG, "Received Telegram update: ${gson.toJson(update)}")
                            val callbackQuery = try {
                                update.getAsJsonObject("callback_query")
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "Failed to get callback_query object", e)
                                null
                            }
                            if (callbackQuery != null) {
                                AppLogger.d(TAG, "Parsing callback query: ${gson.toJson(callbackQuery)}")
                                val msg = try { callbackQuery.getAsJsonObject("message") } catch (e: Exception) {
                                    AppLogger.e(TAG, "Failed to get message from callbackQuery", e)
                                    null
                                }
                                val chat = try { msg?.getAsJsonObject("chat") } catch (e: Exception) {
                                    AppLogger.e(TAG, "Failed to get chat from message", e)
                                    null
                                }
                                val chatId = chat?.get("id")?.asLong
                                val messageId = msg?.get("message_id")?.asLong
                                val cbData = callbackQuery.get("data")?.asString
                                val cbId = callbackQuery.get("id")?.asString

                                AppLogger.d(TAG, "Parsed Callback query - chatId: $chatId, messageId: $messageId, cbData: $cbData, cbId: $cbId")

                                if (chatId == null || messageId == null || cbData == null || cbId == null) {
                                    AppLogger.w(TAG, "Skipping callback query due to missing parameters (chatId, messageId, cbData, or cbId)")
                                    continue
                                }

                                AppLogger.d(TAG, "Launching handleCallbackQuery coroutine...")
                                serviceScope.launch {
                                    handleCallbackQuery(token, chatId, messageId, cbId, cbData)
                                }
                                continue
                            }

                            val message = try { update.getAsJsonObject("message") } catch (_: Exception) { null } ?: continue
                            val text = message.get("text")?.asString ?: ""
                            val chat = try { message.getAsJsonObject("chat") } catch (_: Exception) { null } ?: continue
                            val chatId = chat.get("id")?.asLong ?: continue

                            if (text.isNotEmpty()) {
                                AppLogger.d(TAG, "Message from chat $chatId: ${text.take(100)}")
                                serviceScope.launch {
                                    handleMessage(token, chatId, text)
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to process update in bot ...${token.takeLast(6)}", e)
                        }
                    }
                }
                } finally {
                    disposable?.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Poll error for bot ...${token.takeLast(6)}", e)
                delay(5000)
            }

            delay(100)
        }
    }

    private fun hasProviderCredentials(storageKey: String, isFree: Boolean): Boolean {
        val prefs = repository.securePrefs
        return prefs.getApiKeys(storageKey).isNotEmpty() ||
            prefs.getSetting("oauth_token_$storageKey", "").isNotEmpty() ||
            prefs.getSetting("cookie_$storageKey", "").isNotEmpty() ||
            isFree
    }

    private fun getAvailableModels(): List<AIModel> {
        val seenIds = mutableSetOf<String>()
        val result = mutableListOf<AIModel>()

        AIProviderFactory.providers.forEach { provider ->
            val storageKey = providerStorageId(provider.name)
            if (hasProviderCredentials(storageKey, provider.isFree)) {
                provider.models.forEach { model ->
                    if (seenIds.add(model.id)) {
                        result.add(model)
                    }
                }
                val dynamicModels = ModelCatalog.models.value[provider.name]
                if (dynamicModels != null) {
                    dynamicModels.forEach { model ->
                        if (seenIds.add(model.id)) {
                            result.add(model)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun getSavedModelForChat(chatId: Long): String? {
        return repository.securePrefs.getSetting("tg_model_$chatId", "")
            .ifEmpty { null }
    }

    private fun getSavedProviderForChat(chatId: Long): String? {
        return repository.securePrefs.getSetting("tg_provider_$chatId", "")
            .ifEmpty { null }
    }

    private fun saveModelForChat(chatId: Long, modelId: String, providerName: String? = null) {
        repository.securePrefs.saveSetting("tg_model_$chatId", modelId)
        if (providerName != null) {
            repository.securePrefs.saveSetting("tg_provider_$chatId", providerName)
        }
    }

    private suspend fun handleMessage(token: String, chatId: Long, text: String) {
        // Save this chat as the default for UI-created automation delivery
        repository.securePrefs.saveSetting("telegram_default_chat_id", chatId.toString())

        if (text.startsWith("/")) {
            handleCommand(token, chatId, text)
            return
        }

        // Serialize per-chat: if a message is already being processed for this chat,
        // tell the user and skip instead of launching a concurrent agent run.
        val mutex = chatMutexes.getOrPut(chatId) { Mutex() }
        if (!mutex.tryLock()) {
            sendMessage(token, chatId, "⏳ I'm already processing your previous message. Please wait a moment.")
            return
        }

        try {
            _handleMessageLocked(token, chatId, text)
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun _handleMessageLocked(token: String, chatId: Long, text: String) {
        val savedModelId = getSavedModelForChat(chatId)
        val savedProviderName = getSavedProviderForChat(chatId)
        val modelInfo = if (savedModelId != null) {
            AIProviderFactory.providers.flatMap { it.models }.firstOrNull {
                it.id == savedModelId && (savedProviderName == null || it.provider == savedProviderName)
            }
        } else null

        val processingText = detectProcessingMessage(text)
        val processingMsgId = sendMessage(token, chatId, processingText)

        try {
            val sessionId = "telegram_$chatId"

            // Music playback bypass — no AI needed
            val musicHandler = MusicDetectionHandler(this)
            val musicMsg = musicHandler.play(text)
            if (musicMsg.isNotEmpty()) {
                if (processingMsgId != null) {
                    editMessage(token, chatId, processingMsgId, musicMsg)
                } else {
                    sendMessage(token, chatId, musicMsg)
                }
                return
            }

            // Automation setup bypass — no AI needed
            val automationHandler = AutomationHandler(this)
            val automationMsg = automationHandler.create(text, chatId.toString())
            if (automationMsg.isNotEmpty()) {
                if (processingMsgId != null) {
                    editMessage(token, chatId, processingMsgId, automationMsg)
                } else {
                    sendMessage(token, chatId, automationMsg)
                }
                return
            }

            // Gmail bypass — no AI needed
            val gmailHandler = GmailHandler(this)
            val gmailResponse = gmailHandler.fetch(text)
            if (gmailResponse.isNotEmpty()) {
                if (processingMsgId != null) {
                    editMessage(token, chatId, processingMsgId, gmailResponse)
                } else {
                    sendMessage(token, chatId, gmailResponse)
                }
                return
            }

            // GitHub bypass — no AI needed
            val githubHandler = GitHubHandler(this)
            val githubResponse = githubHandler.fetch(text)
            if (githubResponse.isNotEmpty()) {
                if (processingMsgId != null) {
                    editMessage(token, chatId, processingMsgId, githubResponse)
                } else {
                    sendMessage(token, chatId, githubResponse)
                }
                return
            }

            var finalResponse = ""

            // Try DirectTool routing first (image search, audio, etc.) — no AI needed
            val orchestrator = OrchestratorEngine(applicationContext)
            val decision = orchestrator.classifyIntent(text)
            if (decision is OrchestratorDecision.DirectTool) {
                val toolJob = decision.toolJob
                val workingDir = repository.securePrefs.getSetting("default_project", "/storage/emulated/0")
                val toolResult = when (toolJob.taskType) {
                    TaskType.IMAGE_SEARCH -> {
                        val searchArgs = """{"query":${gson.toJson(toolJob.userPrompt)}}"""
                        ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                            .executeTool("search_image", searchArgs, workingDir, false)
                    }
                    TaskType.IMAGE_GENERATION -> {
                        val genArgs = """{"prompt":${gson.toJson(toolJob.userPrompt)}}"""
                        ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                            .executeTool("generate_image", genArgs, workingDir, false)
                    }
                    TaskType.AUDIO_GENERATION -> {
                        val audioArgs = """{"text":${gson.toJson(toolJob.userPrompt)},"voice":"","rate":"","pitch":""}"""
                        ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                            .executeTool("edge_tts", audioArgs, workingDir, false)
                    }
                    TaskType.VIDEO_GENERATION -> {
                        val videoArgs = """{"prompt":${gson.toJson(toolJob.userPrompt)}}"""
                        ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                            .executeTool("generate_video", videoArgs, workingDir, false)
                    }
                    else -> null
                }
                if (toolResult != null) {
                    finalResponse = toolResult
                }
            }

            // Fall back to the main AI agent
            if (finalResponse.isEmpty()) {
                withTimeout(300_000L) {
                    agentEngine.run(sessionId, text, modelInfo?.provider, modelInfo?.id, noFallback = modelInfo != null).collect { statusOrText ->
                        if (!statusOrText.startsWith("Thinking...\n") && !statusOrText.startsWith("Running tool: ")) {
                            finalResponse = statusOrText
                        }
                    }
                }

                // Reply review loop (runs 1 time before giving the telegram answer ONLY when raw tool/XML tags leak)
                val needsReview = finalResponse.isNotEmpty() &&
                        decision !is OrchestratorDecision.DirectTool &&
                        (finalResponse.contains("<tool_calls") || 
                         finalResponse.contains("<invoke") || 
                         finalResponse.contains("|DSML|") || 
                         finalResponse.contains("<parameter")) &&
                        !finalResponse.contains("[file:") &&
                        !finalResponse.contains("[audio:") &&
                        !finalResponse.contains("[image:") &&
                        !finalResponse.startsWith("I'm currently unable to reach the AI service") &&
                        !finalResponse.startsWith("⏱️") &&
                        !finalResponse.startsWith("⚠️")

                if (needsReview) {
                    val reviewPrompt = """
                        [CRITICAL REVIEW TASK]
                        Please review your draft response above. 
                        1. Ensure it does NOT contain any raw XML, DSML, or tool tags (like < | DSML |, <invoke>, <parameter>, <tool_calls>). If any are present, clean them up and show only clean text.
                        2. Check that formatting is natural and friendly.
                        3. Output only the polished final response directly. Do not include any intro, explanation, or tags.
                    """.trimIndent()

                    val beforeReviewMessages = repository.getMessagesListForSession(sessionId)

                    try {
                        withTimeout(60_000L) {
                            var reviewedResponse = ""
                            agentEngine.run(sessionId, reviewPrompt, modelInfo?.provider, modelInfo?.id, noFallback = modelInfo != null).collect { statusOrText ->
                                if (!statusOrText.startsWith("Thinking...\n") && !statusOrText.startsWith("Running tool: ")) {
                                    reviewedResponse = statusOrText
                                }
                            }
                            if (reviewedResponse.isNotEmpty() && !reviewedResponse.contains("CRITICAL REVIEW TASK")) {
                                // Preserve media markers that the review might have stripped
                                val mediaMarkersRegex = Regex("""\[(?:file|audio|image):[^\]]+\]""")
                                val originalMarkers = mediaMarkersRegex.findAll(finalResponse).map { it.value }.toList()
                                var reviewedText = reviewedResponse
                                for (marker in originalMarkers) {
                                    if (!reviewedText.contains(marker)) {
                                        reviewedText = reviewedText.trimEnd() + "\n\n$marker"
                                    }
                                }
                                finalResponse = reviewedText

                                val afterReviewMessages = repository.getMessagesListForSession(sessionId)
                                val addedMessages = afterReviewMessages.filter { msg -> beforeReviewMessages.none { it.id == msg.id } }
                                for (msg in addedMessages) {
                                    repository.deleteMessage(msg.id)
                                }

                                val draftMsg = beforeReviewMessages.lastOrNull { it.role == "assistant" }
                                if (draftMsg != null) {
                                    repository.deleteMessage(draftMsg.id)
                                }

                                repository.insertMessage(ai.deepcode.android.domain.model.Message(
                                    id = java.util.UUID.randomUUID().toString(),
                                    sessionId = sessionId,
                                    role = "assistant",
                                    content = finalResponse,
                                    timestamp = System.currentTimeMillis()
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Review loop failed", e)
                    }
                }
            }

            if (finalResponse.trim().isEmpty()) {
                finalResponse = "Sorry, I couldn't generate a response."
            }

            // Final safety net: strip any residual XML tool-call markup from the response
            val asciiResp = StringBuilder(finalResponse.length)
            for (ch in finalResponse) {
                val c = ch.code
                asciiResp.append(when {
                    c in 0xFF01..0xFF5E -> (c - 0xFEE0).toChar()
                    c == 0xFF3F -> '_'
                    c == 0x3000 -> ' '
                    else -> ch
                })
            }
            finalResponse = Regex(
                """<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?tool_calls?\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*tool_calls?\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?>|<invoke[^>]*>.*?</invoke>|<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?parameter[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*parameter\s*>|<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?invoke[^>]*>.*?<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?/\s*invoke\s*>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).replace(asciiResp.toString(), "").trim()
            if (finalResponse.isEmpty()) finalResponse = "I've completed the requested actions."

            val audioMatch = Regex("""\[audio:([^\]]+)\]""").find(finalResponse)
            val imageMatch = Regex("""\[image:([^\]]+)\]""").find(finalResponse)
            val mdImageMatch = Regex("""!\[image\]\(([^)]+)\)""").find(finalResponse)
            val videoMatch = Regex("""\[video:([^\]]+)\]""").find(finalResponse)
            val fileMatch = Regex("""\[file:([^\]]+)\]""").find(finalResponse)
            val hasMedia = audioMatch != null || imageMatch != null || mdImageMatch != null || videoMatch != null || fileMatch != null

            val cleanResponse = finalResponse
                .replace(Regex("""\[audio:[^\]]+\]"""), "").trim()
                .replace(Regex("""\[image:[^\]]+\]"""), "").trim()
                .replace(Regex("""\[video:[^\]]+\]"""), "").trim()
                .replace(Regex("""\[file:[^\]]+\]"""), "").trim()

            // Send media files first
            if (audioMatch != null) {
                val audioPath = audioMatch.groupValues[1]
                try {
                    val audioFile = File(audioPath)
                    if (audioFile.exists()) {
                        if (processingMsgId != null) deleteMessage(token, chatId, processingMsgId)
                        sendAudio(token, chatId, audioFile, cleanResponse.ifEmpty { null })
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send audio file", e)
                }
                return
            }

            if (imageMatch != null || mdImageMatch != null) {
                val imgUrl = (imageMatch?.groupValues?.getOrNull(1) ?: mdImageMatch?.groupValues?.getOrNull(1)) ?: ""
                if (imgUrl.isNotEmpty()) {
                    if (cleanResponse.isNotEmpty()) {
                        sendMessage(token, chatId, cleanResponse)
                    }
                    sendPhoto(token, chatId, imgUrl)
                    if (processingMsgId != null) deleteMessage(token, chatId, processingMsgId)
                    return
                }
            }

            if (videoMatch != null) {
                val videoUrl = videoMatch.groupValues[1]
                if (videoUrl.isNotEmpty()) {
                    if (cleanResponse.isNotEmpty()) {
                        sendMessage(token, chatId, cleanResponse)
                    }
                    sendVideo(token, chatId, videoUrl)
                    if (processingMsgId != null) deleteMessage(token, chatId, processingMsgId)
                    return
                }
            }

            if (fileMatch != null) {
                val filePath = fileMatch.groupValues[1]
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        if (processingMsgId != null) deleteMessage(token, chatId, processingMsgId)
                        sendDocument(token, chatId, file, cleanResponse.ifEmpty { null })
                    } else {
                        AppLogger.w(TAG, "File not found for sending: $filePath")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send document", e)
                }
                return
            }

            // Orchestration summary omitted for cleaner reply

            val maxLen = 4000
            if (finalResponse.length <= maxLen) {
                if (processingMsgId != null) {
                    editMessage(token, chatId, processingMsgId, finalResponse)
                } else {
                    sendMessage(token, chatId, finalResponse)
                }
            } else {
                if (processingMsgId != null) {
                    deleteMessage(token, chatId, processingMsgId)
                }
                var remaining = finalResponse
                while (remaining.isNotEmpty()) {
                    val chunk = remaining.take(maxLen)
                    sendMessage(token, chatId, chunk)
                    remaining = remaining.drop(maxLen)
                    if (remaining.isNotEmpty()) delay(500)
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (processingMsgId != null) {
                editMessage(token, chatId, processingMsgId, "⏱️ Request timed out after 5 minutes. Please try again.")
            } else {
                sendMessage(token, chatId, "⏱️ Request timed out after 5 minutes. Please try again.")
            }
            AppLogger.e(TAG, "Agent run timed out", e)
        } catch (e: Exception) {
            if (processingMsgId != null) {
                editMessage(token, chatId, processingMsgId, "⚠️ An error occurred: ${e.message}")
            } else {
                sendMessage(token, chatId, "⚠️ An error occurred: ${e.message}")
            }
            AppLogger.e(TAG, "Agent run failed", e)
        }
    }

    private suspend fun handleCommand(token: String, chatId: Long, command: String) {
        when {
            command.startsWith("/clear") || command.startsWith("/new") -> {
                val sessionId = "telegram_$chatId"
                repository.deleteSession(sessionId)
                sendMessage(token, chatId, "🧹 Conversation history cleared! Starting fresh. 🚀")
            }
        command.startsWith("/change") || command.startsWith("/model") -> {
            sendProviderSelection(token, chatId)
        }
            command.startsWith("/voice") -> {
                showVoicesSelection(token, chatId)
            }
            command.startsWith("/language") || command.startsWith("/lang") -> {
                showLanguageSelection(token, chatId)
            }
            command.startsWith("/persona") -> {
                showPersonasSelection(token, chatId)
            }
            command.startsWith("/start") -> {
                val msg = repository.securePrefs.getSetting("tg_start_msg", "")
                    .ifEmpty {
                        "👋 Welcome to DeepCode Bot!\n\n" +
                        "Send me a message and I'll respond using AI.\n\n" +
                        "Commands:\n" +
                        "/change - Switch AI model\n" +
                        "/model - Same as /change\n" +
                        "/voice - Change voice character & tone\n" +
                        "/language - Set AI & TTS language\n" +
                        "/persona - List available personas\n" +
                        "/start - Show this message"
                    }
                sendMessage(token, chatId, msg)
            }
            command.startsWith("/help") -> {
                val msg = repository.securePrefs.getSetting("tg_help_msg", "")
                    .ifEmpty {
                        "Available commands:\n" +
                        "/change - Switch AI model\n" +
                        "/model - Same as /change\n" +
                        "/voice - Change voice character & tone\n" +
                        "/language - Set AI & TTS language\n" +
                        "/persona - List available personas\n" +
                        "/clear - Start fresh session / clear history\n" +
                        "/start - Welcome message\n" +
                        "/help - This message\n\n" +
                        "Just send any text to chat with the AI!"
                    }
                sendMessage(token, chatId, msg)
            }
            else -> {
                sendMessage(token, chatId, "Unknown command. Try /change, /voice, /language, /persona, /start, or /help")
            }
        }
    }

    private suspend fun sendProviderSelection(token: String, chatId: Long) {
        val (header, rows) = buildProviderSelection(chatId)
        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        sendMessageWithKeyboard(token, chatId, header, replyMarkup)
    }

    private suspend fun showProviderSelection(token: String, chatId: Long, messageId: Long) {
        val (header, rows) = buildProviderSelection(chatId)
        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        editMessageWithKeyboard(token, chatId, messageId, header, replyMarkup)
    }

    private fun buildProviderSelection(chatId: Long): Pair<String, JsonArray> {
        val availableModels = getAvailableModels()
        val savedId = getSavedModelForChat(chatId)
        val savedProvider = getSavedProviderForChat(chatId)
        val currentModel = availableModels.firstOrNull { it.id == savedId && it.provider == savedProvider }

        val providers = availableModels.map { it.provider }.distinct().sorted()
        val header = buildString {
            append("🧠 *Select Provider*\n\n")
            if (currentModel != null) {
                append("Current: ${currentModel.name} (${currentModel.provider})\n\n")
            } else {
                append("No model selected. Default will be used.\n\n")
            }
            append("Tap a provider to see its models:")
        }

        val rows = JsonArray()
        for (provider in providers) {
            val row = JsonArray()
            val btn = JsonObject()
            val isActiveProvider = currentModel?.provider == provider
            val label = if (isActiveProvider) "✓ $provider" else provider
            btn.addProperty("text", label)
            btn.addProperty("callback_data", "select_provider:$provider")
            row.add(btn)
            rows.add(row)
        }
        return header to rows
    }

    private suspend fun showModelsForProvider(token: String, chatId: Long, messageId: Long, providerName: String) {
        val availableModels = getAvailableModels().filter { it.provider == providerName }
        val savedId = getSavedModelForChat(chatId)
        val savedProvider = getSavedProviderForChat(chatId)
        val currentModel = availableModels.firstOrNull { it.id == savedId && savedProvider == providerName }

        val header = buildString {
            append("🤖 *${providerName} Models*\n\n")
            if (currentModel != null) {
                append("Current: ${currentModel.name}\n\n")
            } else {
                append("No model selected from this provider.\n\n")
            }
            append("Tap a model to switch:")
        }

        val rows = JsonArray()
        for (model in availableModels) {
            val row = JsonArray()
            val btn = JsonObject()
            val isSelected = model.id == savedId && savedProvider == providerName
            val label = if (isSelected) "✓ ${model.name}" else model.name
            btn.addProperty("text", label)
            btn.addProperty("callback_data", "select_model:${providerName}:${model.id}")
            row.add(btn)
            rows.add(row)
        }

        // Back button
        val backRow = JsonArray()
        val backBtn = JsonObject()
        backBtn.addProperty("text", "« Back to providers")
        backBtn.addProperty("callback_data", "back_to_providers")
        backRow.add(backBtn)
        rows.add(backRow)

        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        editMessageWithKeyboard(token, chatId, messageId, header, replyMarkup)
    }

    private suspend fun showVoicesSelection(token: String, chatId: Long) {
        val savedVoice = repository.securePrefs.getSetting("tts_edge_voice", "")
        val voices = listOf(
            "en-US-AriaNeural" to "🇺🇸 Aria (US Female)",
            "en-US-JennyNeural" to "🇺🇸 Jenny (US Female)",
            "en-US-MichelleNeural" to "🇺🇸 Michelle (US Female)",
            "en-US-AnaNeural" to "🇺🇸 Ana (US Female)",
            "en-US-SaraNeural" to "🇺🇸 Sara (US Female)",
            "en-US-GuyNeural" to "🇺🇸 Guy (US Male)",
            "en-US-DavisNeural" to "🇺🇸 Davis (US Male)",
            "en-US-TonyNeural" to "🇺🇸 Tony (US Male)",
            "en-US-JasonNeural" to "🇺🇸 Jason (US Male)",
            "en-GB-SoniaNeural" to "🇬🇧 Sonia (UK Female)",
            "en-GB-LibbyNeural" to "🇬🇧 Libby (UK Female)",
            "en-GB-MaisieNeural" to "🇬🇧 Maisie (UK Female)",
            "en-GB-RyanNeural" to "🇬🇧 Ryan (UK Male)",
            "en-GB-ThomasNeural" to "🇬🇧 Thomas (UK Male)",
            "hi-IN-SwaraNeural" to "🇮🇳 Swara (Hindi Female)",
            "hi-IN-MadhurNeural" to "🇮🇳 Madhur (Hindi Male)",
            "es-ES-ElviraNeural" to "🇪🇸 Elvira (Spanish Female)",
            "es-ES-AlvaroNeural" to "🇪🇸 Alvaro (Spanish Male)",
            "fr-FR-DeniseNeural" to "🇫🇷 Denise (French Female)",
            "fr-FR-HenriNeural" to "🇫🇷 Henri (French Male)",
            "de-DE-KatjaNeural" to "🇩🇪 Katja (German Female)",
            "de-DE-ConradNeural" to "🇩🇪 Conrad (German Male)",
            "ja-JP-NanamiNeural" to "🇯🇵 Nanami (Japanese Female)",
            "ja-JP-KeitaNeural" to "🇯🇵 Keita (Japanese Male)",
            "ko-KR-SunHiNeural" to "🇰🇷 SunHi (Korean Female)",
            "ko-KR-InJoonNeural" to "🇰🇷 InJoon (Korean Male)",
            "zh-CN-XiaoxiaoNeural" to "🇨🇳 Xiaoxiao (Chinese Female)",
            "zh-CN-XiaoyiNeural" to "🇨🇳 Xiaoyi (Chinese Female)",
            "zh-CN-YunxiNeural" to "🇨🇳 Yunxi (Chinese Male)",
            "zh-CN-YunyangNeural" to "🇨🇳 Yunyang (Chinese Male)",
            "ar-SA-ZariyahNeural" to "🇸🇦 Zariyah (Arabic Female)",
            "ar-SA-HamedNeural" to "🇸🇦 Hamed (Arabic Male)",
            "pt-BR-FranciscaNeural" to "🇧🇷 Francisca (Portuguese Female)",
            "pt-BR-AntonioNeural" to "🇧🇷 Antonio (Portuguese Male)",
            "ru-RU-SvetlanaNeural" to "🇷🇺 Svetlana (Russian Female)",
            "ru-RU-DmitryNeural" to "🇷🇺 Dmitry (Russian Male)",
            "it-IT-ElsaNeural" to "🇮🇹 Elsa (Italian Female)",
            "it-IT-DiegoNeural" to "🇮🇹 Diego (Italian Male)",
            "nl-NL-FennaNeural" to "🇳🇱 Fenna (Dutch Female)",
            "nl-NL-MaartenNeural" to "🇳🇱 Maarten (Dutch Male)",
            "tr-TR-EmelNeural" to "🇹🇷 Emel (Turkish Female)",
            "tr-TR-AhmetNeural" to "🇹🇷 Ahmet (Turkish Male)"
        )

        val header = buildString {
            append("🗣 *Select Voice*\n\n")
            if (savedVoice.isNotEmpty()) {
                val current = voices.firstOrNull { it.first == savedVoice }
                if (current != null) {
                    append("Current: ${current.second}\n\n")
                } else {
                    append("Current: $savedVoice\n\n")
                }
            } else {
                append("No voice selected. Default will be used.\n\n")
            }
            append("Tap a voice below:")
        }

        val rows = JsonArray()
        for ((code, label) in voices) {
            val row = JsonArray()
            val btn = JsonObject()
            val display = if (code == savedVoice) "✓ $label" else label
            btn.addProperty("text", display)
            btn.addProperty("callback_data", "select_voice:$code")
            row.add(btn)
            rows.add(row)
        }

        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        sendMessageWithKeyboard(token, chatId, header, replyMarkup)
    }

    private suspend fun showLanguageSelection(token: String, chatId: Long) {
        val savedLang = repository.securePrefs.getSetting("tts_voice", "en-US")
        val languages = listOf(
            "en-US" to "🇺🇸 English",
            "hi-IN" to "🇮🇳 Hindi",
            "es-ES" to "🇪🇸 Spanish",
            "fr-FR" to "🇫🇷 French",
            "de-DE" to "🇩🇪 German",
            "ja-JP" to "🇯🇵 Japanese",
            "ko-KR" to "🇰🇷 Korean",
            "zh-CN" to "🇨🇳 Chinese (Simplified)",
            "ar-SA" to "🇸🇦 Arabic",
            "pt-BR" to "🇧🇷 Portuguese (Brazil)",
            "ru-RU" to "🇷🇺 Russian",
            "it-IT" to "🇮🇹 Italian",
            "nl-NL" to "🇳🇱 Dutch",
            "tr-TR" to "🇹🇷 Turkish"
        )

        val header = buildString {
            append("🌐 *Select AI Language*\n\n")
            append("Current: $savedLang\n\n")
            append("This changes both TTS voice language and AI response language.\n\n")
            append("Tap a language below:")
        }

        val rows = JsonArray()
        for ((code, label) in languages) {
            val row = JsonArray()
            val btn = JsonObject()
            val display = if (code == savedLang) "✓ $label" else label
            btn.addProperty("text", display)
            btn.addProperty("callback_data", "select_language:$code")
            row.add(btn)
            rows.add(row)
        }

        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        sendMessageWithKeyboard(token, chatId, header, replyMarkup)
    }

    private suspend fun showPersonasSelection(token: String, chatId: Long) {
        val allPersonas = builtInPersonas + loadCustomPersonas()
        if (allPersonas.isEmpty()) {
            sendMessage(token, chatId, "No personas available.")
            return
        }

        val activePersonaId = getSavedPersonaForChat(chatId)

        val header = buildString {
            append("🎭 *Select Persona*\n\n")
            if (activePersonaId != null) {
                val current = allPersonas.firstOrNull { it.id == activePersonaId }
                if (current != null) {
                    append("Current: ${current.name}\n\n")
                } else {
                    append("Current persona no longer available.\n\n")
                }
            } else {
                append("No persona active. Default behavior will be used.\n\n")
            }
            append("Tap a persona below to switch:")
        }

        val rows = JsonArray()
        for (persona in allPersonas) {
            val row = JsonArray()
            val btn = JsonObject()
            val label = if (persona.id == activePersonaId) "✓ ${persona.name}" else persona.name
            btn.addProperty("text", label)
            btn.addProperty("callback_data", "select_persona:${persona.id}")
            row.add(btn)
            rows.add(row)
        }

        // Add a "None" option to disable persona
        val noneRow = JsonArray()
        val noneBtn = JsonObject()
        noneBtn.addProperty("text", if (activePersonaId == null) "✓ None (Default)" else "None (Default)")
        noneBtn.addProperty("callback_data", "select_persona:")
        noneRow.add(noneBtn)
        rows.add(noneRow)

        val replyMarkup = JsonObject()
        replyMarkup.add("inline_keyboard", rows)
        sendMessageWithKeyboard(token, chatId, header, replyMarkup)
    }

    private fun loadCustomPersonas(): List<Persona> {
        val raw = repository.securePrefs.getSetting("saved_personas", "[]")
        return try {
            val json = org.json.JSONArray(raw)
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                Persona(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    content = obj.getString("content"),
                    isSystem = obj.optBoolean("isSystem", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getSavedPersonaForChat(chatId: Long): String? {
        return repository.securePrefs.getSetting("tg_persona_$chatId", "")
            .ifEmpty { null }
    }

    private fun savePersonaForChat(chatId: Long, personaId: String?) {
        if (personaId != null) {
            repository.securePrefs.saveSetting("tg_persona_$chatId", personaId)
            // Also update global persona settings
            val allPersonas = builtInPersonas + loadCustomPersonas()
            val persona = allPersonas.firstOrNull { it.id == personaId }
            if (persona != null) {
                repository.securePrefs.saveSetting("custom_persona", persona.content)
                repository.securePrefs.saveSetting("persona_enabled", "true")
            }
        } else {
            repository.securePrefs.saveSetting("tg_persona_$chatId", "")
            repository.securePrefs.saveSetting("persona_enabled", "false")
        }
    }

    private suspend fun handleCallbackQuery(token: String, chatId: Long, messageId: Long, callbackId: String, callbackData: String) {
        AppLogger.d(TAG, "handleCallbackQuery entered: callbackId=$callbackId, callbackData=$callbackData")
        when {
            callbackData == "back_to_providers" -> {
                showProviderSelection(token, chatId, messageId)
                answerCallbackQuery(token, callbackId, "")
                AppLogger.d(TAG, "handleCallbackQuery finish (back).")
            }
            callbackData.startsWith("select_provider:") -> {
                val providerName = callbackData.removePrefix("select_provider:")
                showModelsForProvider(token, chatId, messageId, providerName)
                answerCallbackQuery(token, callbackId, "")
                AppLogger.d(TAG, "handleCallbackQuery finish (provider).")
            }
            callbackData.startsWith("select_model:") -> {
                val parts = callbackData.removePrefix("select_model:").split(":", limit = 2)
                val modelId = if (parts.size == 2) parts[1] else parts[0]
                val providerName = if (parts.size == 2) parts[0] else null
                AppLogger.d(TAG, "Selected model ID: $modelId from provider: $providerName")
                saveModelForChat(chatId, modelId, providerName)
                val model = AIProviderFactory.providers.flatMap { it.models }
                    .firstOrNull { it.id == modelId && (providerName == null || it.provider == providerName) }
                val name = model?.name ?: modelId
                AppLogger.d(TAG, "Editing bot selection message to model name: $name")
                editMessage(token, chatId, messageId, "✅ Switched to *$name*!\n\nSend a message to chat with this model.", "Markdown")
                AppLogger.d(TAG, "Sending answerCallbackQuery response...")
                answerCallbackQuery(token, callbackId, "Model switched to $name")
                AppLogger.d(TAG, "handleCallbackQuery finish.")
            }
            callbackData.startsWith("select_voice:") -> {
                val voice = callbackData.removePrefix("select_voice:")
                AppLogger.d(TAG, "Selected voice: $voice")
                repository.securePrefs.saveSetting("tts_edge_voice", voice)
                val shortName = voice.removePrefix("en-US-").removePrefix("en-GB-").removePrefix("hi-IN-")
                    .removePrefix("es-ES-").removePrefix("fr-FR-").removePrefix("de-DE-")
                    .removePrefix("ja-JP-").removePrefix("ko-KR-").removePrefix("zh-CN-")
                    .removePrefix("ar-SA-").removePrefix("pt-BR-").removePrefix("ru-RU-")
                    .removePrefix("it-IT-").removePrefix("nl-NL-").removePrefix("tr-TR-")
                    .removeSuffix("Neural")
                editMessage(token, chatId, messageId, "✅ Voice set to *$shortName*! 🎵 Generating sample...", "Markdown")
                answerCallbackQuery(token, callbackId, "Voice: $shortName")
                // Generate and send a sample in background
                serviceScope.launch {
                    try {
                        val gson = com.google.gson.Gson()
                        val sampleText = "Hello, this is $shortName, your AI voice assistant."
                        val executor = ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                        val result = executor.executeTool("edge_tts", """{"text":${gson.toJson(sampleText)}}""", "", false)
                        val audioMatch = Regex("""\[audio:([^\]]+)\]""").find(result)
                        if (audioMatch != null) {
                            val audioFile = java.io.File(audioMatch.groupValues[1])
                            if (audioFile.exists()) {
                                sendAudio(token, chatId, audioFile, "🎵 $shortName")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to generate voice sample", e)
                    }
                    editMessage(token, chatId, messageId, "✅ Voice set to *$shortName*! 🎵 Sample above ☝️", "Markdown")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish.")
            }
            callbackData.startsWith("select_language:") -> {
                val lang = callbackData.removePrefix("select_language:")
                AppLogger.d(TAG, "Selected language: $lang")
                repository.securePrefs.saveSetting("tts_voice", lang)
                val langNames = mapOf("en-US" to "English", "hi-IN" to "Hindi", "es-ES" to "Spanish", "fr-FR" to "French", "de-DE" to "German", "ja-JP" to "Japanese", "ko-KR" to "Korean", "zh-CN" to "Chinese", "ar-SA" to "Arabic", "pt-BR" to "Portuguese", "ru-RU" to "Russian", "it-IT" to "Italian", "nl-NL" to "Dutch", "tr-TR" to "Turkish")
                val name = langNames[lang] ?: lang
                editMessage(token, chatId, messageId, "✅ Language set to *$name*!\n\nAI will now respond in $name. Use /language to change it anytime.", "Markdown")
                answerCallbackQuery(token, callbackId, "Language: $name")
                AppLogger.d(TAG, "handleCallbackQuery finish.")
            }
            callbackData.startsWith("select_persona:") -> {
                val personaId = callbackData.removePrefix("select_persona:")
                AppLogger.d(TAG, "Selected persona ID: '$personaId'")
                if (personaId.isNotEmpty()) {
                    savePersonaForChat(chatId, personaId)
                    val allPersonas = builtInPersonas + loadCustomPersonas()
                    val persona = allPersonas.firstOrNull { it.id == personaId }
                    val name = persona?.name ?: personaId
                    editMessage(token, chatId, messageId, "✅ Switched to *$name* persona!\n\nSend a message to chat with this persona.", "Markdown")
                    answerCallbackQuery(token, callbackId, "Persona switched to $name")
                } else {
                    savePersonaForChat(chatId, null)
                    editMessage(token, chatId, messageId, "✅ Persona disabled. Default behavior restored.\n\nSend a message to chat with the AI.", "Markdown")
                    answerCallbackQuery(token, callbackId, "Persona disabled")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish.")
            }
        }
    }

    private fun stripThoughts(text: String): String {
        var cleaned = text.replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<thought>[\\s\\S]*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</thought>", RegexOption.IGNORE_CASE), "")
        val trimmed = cleaned.trim()
        return if (trimmed.isEmpty() && text.isNotEmpty()) {
            "Thinking completed."
        } else {
            trimmed
        }
    }

    private fun formatMarkdownTablesForTelegram(text: String): String {
        val lines = text.lines()
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("|")) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|")) {
                    val raw = lines[j].trim()
                    val isSeparator = raw.matches(Regex("^\\|[-:| ]+\\|$"))
                    if (!isSeparator) {
                        tableLines.add(raw)
                    }
                    j++
                }
                if (tableLines.isNotEmpty()) {
                    val rows = tableLines.map { row ->
                        row.split("|").map { it.trim() }.drop(1).dropLastWhile { it.isEmpty() }
                    }.filter { it.isNotEmpty() }
                    
                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOfOrNull { it.size } ?: 0
                        val colWidths = IntArray(maxCols)
                        for (row in rows) {
                            for (colIndex in 0 until minOf(row.size, maxCols)) {
                                colWidths[colIndex] = maxOf(colWidths[colIndex], row[colIndex].length)
                            }
                        }
                        val formattedTable = StringBuilder()
                        formattedTable.appendLine("```")
                        for (row in rows) {
                            val rowStr = StringBuilder()
                            for (colIndex in 0 until maxCols) {
                                val cell = row.getOrElse(colIndex) { "" }
                                val width = colWidths[colIndex]
                                val paddedCell = cell.padEnd(width)
                                rowStr.append(paddedCell).append("   ")
                            }
                            formattedTable.appendLine(rowStr.toString().trimEnd())
                        }
                        formattedTable.append("```")
                        result.append(formattedTable.toString())
                    } else {
                        for (k in i until j) {
                            result.append(lines[k])
                            if (k < j - 1) result.append("\n")
                        }
                    }
                }
                i = j
            } else {
                result.append(line)
                i++
            }
            if (i < lines.size) {
                result.append("\n")
            }
        }
        return result.toString()
    }

    private fun preprocessMarkdown(text: String): String {
        val tableFormatted = formatMarkdownTablesForTelegram(text)
        val headerFormatted = tableFormatted.lines().joinToString("\n") { line ->
            when {
                line.matches(Regex("^#{1,6}\\s+.*")) -> {
                    val content = line.replaceFirst(Regex("^#{1,6}\\s+"), "")
                    "**$content**"
                }
                else -> line
            }
        }
        return headerFormatted.replace(Regex("([a-zA-Z0-9])_([a-zA-Z0-9])"), "$1＿$2")
    }

    private fun detectProcessingMessage(text: String): String {
        val lower = text.lowercase()
        val audioShort = Regex("""\b(voiceover|tts|sing|song|music|melody|speak|narrate)\b""")
        val imageShort = Regex("""\b(draw|illustrate|logo|poster|thumbnail|banner|paint|artwork)\b""")
        val videoShort = Regex("""\b(video|animation|clip|film|movie|generate video|create video|make video)\b""")
        val audioPhrases = listOf(
            "generate audio", "generate speech", "create audio", "make audio",
            "elevenlabs", "audio of", "text to speech", "read aloud"
        )
        val imagePhrases = listOf(
            "generate image", "create image", "make image", "make an image",
            "generate an image", "create an image", "image of", "picture of"
        )
        val videoPhrases = listOf(
            "generate video", "create video", "make video", "generate a video",
            "create a video", "make a video", "video of", "text to video", "veo"
        )
        val isAudio = audioShort.containsMatchIn(lower) || audioPhrases.any { lower.contains(it) }
        val isImage = imageShort.containsMatchIn(lower) || imagePhrases.any { lower.contains(it) }
        val isVideo = videoShort.containsMatchIn(lower) || videoPhrases.any { lower.contains(it) }
        return when {
            isAudio -> "🎵 Composing audio..."
            isImage -> "🎨 Creating image..."
            isVideo -> "🎬 Generating video..."
            else -> "💭 Thinking..."
        }
    }

    private fun sendMessage(token: String, chatId: Long, text: String): Long? {
        val cleanText = stripThoughts(text)
        val processed = preprocessMarkdown(cleanText)
        return try {
            val url = "${API_BASE}${token}/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", processed)
                addProperty("parse_mode", "Markdown")
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            var messageId: Long? = null
            var shouldRetry = false

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    messageId = json.getAsJsonObject("result")?.get("message_id")?.asLong
                } else {
                    AppLogger.e(TAG, "sendMessage failed: $body")
                    if (body != null) {
                        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
                        val desc = json?.get("description")?.asString ?: ""
                        if (desc.contains("can't parse entities", ignoreCase = true) || desc.contains("parse_mode", ignoreCase = true)) {
                            shouldRetry = true
                        }
                    }
                }
            }

            if (shouldRetry) {
                AppLogger.d(TAG, "Retrying sendMessage without Markdown for chat $chatId")
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("text", cleanText)
                }
                val retryRequest = Request.Builder()
                    .url(url)
                    .post(gson.toJson(retryPayload).toRequestBody(jsonMediaType))
                    .build()
                client.newCall(retryRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = gson.fromJson(body, JsonObject::class.java)
                            messageId = json.getAsJsonObject("result")?.get("message_id")?.asLong
                        }
                    } else {
                        AppLogger.e(TAG, "sendMessage retry failed: ${response.body?.string()}")
                    }
                }
            }
            messageId
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendMessage error", e)
            null
        }
    }

    private fun editMessage(token: String, chatId: Long, messageId: Long, text: String, parseMode: String = "Markdown") {
        AppLogger.d(TAG, "editMessage entry: messageId=$messageId, text.length=${text.length}, parseMode=$parseMode")
        val cleanText = stripThoughts(text)
        val processed = if (parseMode == "Markdown") preprocessMarkdown(cleanText) else cleanText
        try {
            val url = "${API_BASE}${token}/editMessageText"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
                addProperty("text", processed)
                if (parseMode.isNotEmpty()) {
                    addProperty("parse_mode", parseMode)
                }
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            var shouldRetry = false
            AppLogger.d(TAG, "Executing editMessage HTTP POST request...")
            client.newCall(request).execute().use { response ->
                val code = response.code
                AppLogger.d(TAG, "editMessage HTTP response received: code=$code")
                if (!response.isSuccessful) {
                    val body = response.body?.string()
                    AppLogger.e(TAG, "editMessage failed (code $code): $body")
                    if (body != null) {
                        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
                        val desc = json?.get("description")?.asString ?: ""
                        if (desc.contains("can't parse entities", ignoreCase = true) || desc.contains("parse_mode", ignoreCase = true)) {
                            shouldRetry = true
                        }
                    }
                } else {
                    AppLogger.d(TAG, "editMessage succeeded.")
                }
            }

            if (shouldRetry && parseMode.isNotEmpty()) {
                AppLogger.d(TAG, "Retrying editMessage without parse_mode for chat $chatId, message $messageId")
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("message_id", messageId)
                    addProperty("text", cleanText)
                }
                val retryRequest = Request.Builder()
                    .url(url)
                    .post(gson.toJson(retryPayload).toRequestBody(jsonMediaType))
                    .build()
                client.newCall(retryRequest).execute().use { response ->
                    AppLogger.d(TAG, "editMessage retry response: code=${response.code}")
                    if (!response.isSuccessful) {
                        AppLogger.e(TAG, "editMessage retry failed: ${response.body?.string()}")
                    } else {
                        AppLogger.d(TAG, "editMessage retry succeeded.")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "editMessage error", e)
        }
    }

    private fun editMessageWithKeyboard(token: String, chatId: Long, messageId: Long, text: String, replyMarkup: JsonObject) {
        val cleanText = stripThoughts(text)
        val processed = preprocessMarkdown(cleanText)
        try {
            val url = "${API_BASE}${token}/editMessageText"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
                addProperty("text", processed)
                addProperty("parse_mode", "Markdown")
                add("reply_markup", Gson().toJsonTree(replyMarkup))
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "editMessageWithKeyboard failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "editMessageWithKeyboard error", e)
        }
    }

    private fun deleteMessage(token: String, chatId: Long, messageId: Long) {
        try {
            val url = "${API_BASE}${token}/deleteMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "deleteMessage failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteMessage error", e)
        }
    }

    private fun sendAudio(token: String, chatId: Long, audioFile: File, caption: String? = null) {
        try {
            val url = "${API_BASE}${token}/sendAudio"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody(null))
                .apply { caption?.let { addFormDataPart("caption", it) } }
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "sendAudio failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendAudio error", e)
        }
    }

    private fun sendPhoto(token: String, chatId: Long, photoUrl: String, caption: String? = null) {
        try {
            val url = "${API_BASE}${token}/sendPhoto"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("photo", photoUrl)
                caption?.let { addProperty("caption", it) }
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "sendPhoto failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendPhoto error", e)
        }
    }

    private fun sendVideo(token: String, chatId: Long, videoUrl: String, caption: String? = null) {
        try {
            val url = "${API_BASE}${token}/sendVideo"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("video", videoUrl)
                caption?.let { addProperty("caption", it) }
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "sendVideo failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendVideo error", e)
        }
    }

    private fun sendDocument(token: String, chatId: Long, file: File, caption: String? = null) {
        try {
            val url = "${API_BASE}${token}/sendDocument"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("document", file.name, file.asRequestBody(null))
                .apply { caption?.let { addFormDataPart("caption", it) } }
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "sendDocument failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendDocument error", e)
        }
    }

    private fun sendMessageWithKeyboard(token: String, chatId: Long, text: String, replyMarkup: JsonObject): Long? {
        val cleanText = stripThoughts(text)
        val processed = preprocessMarkdown(cleanText)
        return try {
            val url = "${API_BASE}${token}/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", processed)
                addProperty("parse_mode", "Markdown")
                add("reply_markup", Gson().toJsonTree(replyMarkup))
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            var messageId: Long? = null
            var shouldRetry = false

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    messageId = json.getAsJsonObject("result")?.get("message_id")?.asLong
                } else {
                    AppLogger.e(TAG, "sendMessageWithKeyboard failed: $body")
                    if (body != null) {
                        val json = try { gson.fromJson(body, JsonObject::class.java) } catch (_: Exception) { null }
                        val desc = json?.get("description")?.asString ?: ""
                        if (desc.contains("can't parse entities", ignoreCase = true) || desc.contains("parse_mode", ignoreCase = true)) {
                            shouldRetry = true
                        }
                    }
                }
            }

            if (shouldRetry) {
                AppLogger.d(TAG, "Retrying sendMessageWithKeyboard without Markdown for chat $chatId")
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("text", cleanText)
                    add("reply_markup", Gson().toJsonTree(replyMarkup))
                }
                val retryRequest = Request.Builder()
                    .url(url)
                    .post(gson.toJson(retryPayload).toRequestBody(jsonMediaType))
                    .build()
                client.newCall(retryRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = gson.fromJson(body, JsonObject::class.java)
                            messageId = json.getAsJsonObject("result")?.get("message_id")?.asLong
                        }
                    } else {
                        AppLogger.e(TAG, "sendMessageWithKeyboard retry failed: ${response.body?.string()}")
                    }
                }
            }
            messageId
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendMessageWithKeyboard error", e)
            null
        }
    }

    private fun answerCallbackQuery(token: String, callbackQueryId: String, text: String) {
        AppLogger.d(TAG, "answerCallbackQuery entry: callbackQueryId=$callbackQueryId, text=$text")
        try {
            val url = "${API_BASE}${token}/answerCallbackQuery"
            val payload = JsonObject().apply {
                addProperty("callback_query_id", callbackQueryId)
                addProperty("text", text)
                addProperty("show_alert", false)
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            AppLogger.d(TAG, "Executing answerCallbackQuery HTTP POST request...")
            client.newCall(request).execute().use { response ->
                val code = response.code
                AppLogger.d(TAG, "answerCallbackQuery response: code=$code")
                if (!response.isSuccessful) {
                    AppLogger.e(TAG, "answerCallbackQuery failed (code $code): ${response.body?.string()}")
                } else {
                    AppLogger.d(TAG, "answerCallbackQuery succeeded.")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "answerCallbackQuery error", e)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Bot Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telegram Bot Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
