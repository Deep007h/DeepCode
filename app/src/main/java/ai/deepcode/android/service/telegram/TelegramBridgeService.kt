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
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProvider
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.OPENAI_PROVIDERS
import ai.deepcode.android.data.remote.GenericOpenAIProvider
import ai.deepcode.android.data.remote.ModelCatalog
import ai.deepcode.android.data.remote.providerStorageId
import ai.deepcode.android.data.remote.providerDefaultBaseUrl
import ai.deepcode.android.data.remote.fetchModels
import ai.deepcode.android.data.remote.fetchAntigravityModels
import ai.deepcode.android.data.remote.formatModelTitle
import ai.deepcode.android.data.remote.ApiKeyRotator
import ai.deepcode.android.domain.model.AIModel
import ai.deepcode.android.domain.model.Message
import java.util.UUID
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
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.ui.automations.AutomationEntity
import ai.deepcode.android.ui.automations.AutomationRepository
import ai.deepcode.android.ui.automations.AutomationScheduler
import ai.deepcode.android.ui.automations.AutomationRunner
import ai.deepcode.android.ui.components.cleanControlAndCitationTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        fun getRateLimitReply(providerName: String = "AI"): String {
            return "<b>✦ 𝗜’𝗺 𝗵𝗮𝘃𝗶𝗻𝗴 𝗮 𝗹𝗶𝘁𝘁𝗹𝗲 𝘁𝗿𝗼𝘂𝗯𝗹𝗲 𝗿𝗲𝗮𝗰𝗵𝗶𝗻𝗴 ${providerName}</b>\n\n" +
                "The service is currently busy or has reached its request limit.\n" +
                "Please wait a moment before trying again, or use /models to switch.\n\n" +
                "<b>𝟰𝟮𝟵 · 𝗥𝗮𝘁𝗲 𝗹𝗶𝗺𝗶𝘁 𝗲𝘅𝗰𝗲𝗲𝗱𝗲𝗱 ($providerName)</b>"
        }

        fun getProviderErrorReply(providerName: String, errorText: String): String {
            val lower = errorText.lowercase()
            return when {
                lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key") || lower.contains("invalid_api_key") -> {
                    "⚠️ <b>Authentication Failed ($providerName)</b>\n\n" +
                    "The API key configured for <b>$providerName</b> is invalid or expired.\n\n" +
                    "👉 Please update your key in the DeepCode app: <b>Settings → API Keys → $providerName</b>, or switch models with /models.\n\n" +
                    "<b>𝟰𝟬𝟭 · 𝗨𝗻𝗮𝘂𝘁𝗵𝗼𝗿𝗶𝘇𝗲𝗱</b>"
                }
                lower.contains("402") || lower.contains("quota") || lower.contains("insufficient_quota") || lower.contains("credit balance") || lower.contains("credits") || lower.contains("payment") || lower.contains("billing") -> {
                    "⚠️ <b>Credits / Quota Exhausted ($providerName)</b>\n\n" +
                    "Your account for <b>$providerName</b> has exhausted its balance or monthly quota.\n\n" +
                    "👉 Please add credits to your $providerName account or switch to another provider using /models.\n\n" +
                    "<b>𝟰𝟬𝟮 · 𝗣𝗮𝘆𝗺𝗲𝗻𝘁 𝗥𝗲𝗾𝘂𝗶𝗿𝗲𝗱</b>"
                }
                lower.contains("429") || lower.contains("rate limit") || lower.contains("too many requests") || lower.contains("resource_exhausted") -> {
                    getRateLimitReply(providerName)
                }
                lower.contains("403") || lower.contains("forbidden") -> {
                    "⚠️ <b>Access Denied ($providerName)</b>\n\n" +
                    "Access to this model or provider was rejected ($providerName).\n\n" +
                    "<b>𝟰𝟬𝟯 · 𝗙𝗼𝗿𝗯𝗶𝗱𝗱𝗲𝗻</b>"
                }
                lower.contains("api key configured") || lower.contains("api key required") -> {
                    "⚠️ <b>$providerName API Key Required</b>\n\n" +
                    "No API key is configured for <b>$providerName</b>.\n\n" +
                    "👉 Please add your key in DeepCode app: <b>Settings → API Keys → $providerName</b>, or use /models to switch."
                }
                else -> {
                    "⚠️ <b>Error contacting $providerName</b>\n\n" +
                    errorText.take(400) + "\n\n" +
                    "👉 Try again shortly or switch providers with /models."
                }
            }
        }
        const val TELEGRAM_RATE_LIMIT_REPLY = "<b>✦ 𝗜’𝗺 𝗵𝗮𝘃𝗶𝗻𝗴 𝗮 𝗹𝗶𝘁𝘁𝗹𝗲 𝘁𝗿𝗼𝘂𝗯𝗹𝗲 𝗿𝗲𝗮𝗰𝗵𝗶𝗻𝗴 𝘁𝗵𝗲 𝗔𝗜</b>\n\n" +
                "The service is currently busy and has reached its request limit.\n" +
                "Please wait a moment before trying again.\n\n" +
                "<b>𝟰𝟮𝟵 · 𝗥𝗮𝘁𝗲 𝗹𝗶𝗺𝗶𝘁 𝗲𝘅𝗰𝗲𝗲𝗱𝗲𝗱</b>"

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

        fun registerBotCommands(token: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .readTimeout(12, TimeUnit.SECONDS)
                        .build()
                    val gson = Gson()
                    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                    val commands = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("command", "start")
                            addProperty("description", "Start bot & welcome message")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "tasks")
                            addProperty("description", "View scheduled tasks & outputs")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "task_output")
                            addProperty("description", "Show task execution output")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "models")
                            addProperty("description", "Switch AI provider & model")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "voice")
                            addProperty("description", "Change voice character & tone")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "language")
                            addProperty("description", "Set AI & TTS language")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "persona")
                            addProperty("description", "List available personas")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "clear")
                            addProperty("description", "Clear conversation history")
                        })
                        add(JsonObject().apply {
                            addProperty("command", "help")
                            addProperty("description", "Show available commands & guide")
                        })
                    }

                    val scopes = listOf(
                        JsonObject().apply { addProperty("type", "default") },
                        JsonObject().apply { addProperty("type", "all_private_chats") }
                    )
                    for (scope in scopes) {
                        val payload = JsonObject().apply {
                            add("commands", commands)
                            add("scope", scope)
                        }
                        val url = "${API_BASE}${token}/setMyCommands"
                        val request = Request.Builder()
                            .url(url)
                            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(request).execute().use { resp ->
                            AppLogger.d(TAG, "setMyCommands for scope ${scope.get("type").asString} response: ${resp.code}")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to register bot commands: ${e.message}")
                }
            }
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
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val offsets = ConcurrentHashMap<String, Long>()
    private val modelCallbackRegistry = ConcurrentHashMap<String, Pair<String, String>>()

    private fun getShortModelKey(providerName: String, modelId: String): String {
        val raw = "$providerName:$modelId"
        val md5 = java.security.MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return md5.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun resolveModelFromHash(hash: String): Pair<String, String>? {
        modelCallbackRegistry["sm:$hash"]?.let { return it }
        for (p in AIProviderFactory.providers) {
            val models = ModelCatalog.getModelsForProvider(p.name, repository.securePrefs).ifEmpty { p.models }
            for (m in models) {
                if (getShortModelKey(p.name, m.id) == hash) {
                    val pair = p.name to m.id
                    modelCallbackRegistry["sm:$hash"] = pair
                    return pair
                }
            }
        }
        return null
    }

    private fun isRateLimitError(text: String): Boolean {
        return ai.deepcode.android.data.remote.ApiKeyRotator.isRotatableError(null, null, text)
    }

    private fun isRateLimitException(e: Throwable): Boolean {
        val msg = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return ai.deepcode.android.data.remote.ApiKeyRotator.isRotatableError(e, null, msg)
    }

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
        // Load in-app cached models (GMI Cloud, etc.)
        ModelCatalog.loadFromPrefs(repository.securePrefs)

        // Prewarm Zen AI models live from server
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val zenKey = repository.securePrefs.getApiKey("zen")
                if (zenKey.isNotEmpty() && zenKey != "zen-free") {
                    val zenModels = fetchModels(zenKey, providerDefaultBaseUrl("Zen AI"), "Zen AI")
                    if (zenModels.isNotEmpty()) {
                        ModelCatalog.setModels("Zen AI", zenModels)
                        AppLogger.i(TAG, "Prewarmed ${zenModels.size} live Zen AI models")
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to prewarm Zen AI live models: ${e.message}")
            }
        }

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

    private fun registerBotCommands(token: String) {
        Companion.registerBotCommands(token)
    }

    private suspend fun downloadTelegramFile(token: String, fileId: String, customName: String? = null): File? = withContext(Dispatchers.IO) {
        try {
            val getFileUrl = "${API_BASE}${token}/getFile?file_id=${fileId}"
            val req = Request.Builder().url(getFileUrl).get().build()
            val remotePath = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = gson.fromJson(body, JsonObject::class.java)
                    json.getAsJsonObject("result")?.get("file_path")?.asString
                } else null
            } ?: return@withContext null

            val downloadUrl = "https://api.telegram.org/file/bot${token}/${remotePath}"
            val fileName = customName?.takeIf { it.isNotBlank() } ?: File(remotePath).name
            val targetDir = File(repository.getDefaultProjectPath()).apply { mkdirs() }
            val targetFile = File(targetDir, "tg_${System.currentTimeMillis()}_${fileName}")
            val dlReq = Request.Builder().url(downloadUrl).get().build()
            client.newCall(dlReq).execute().use { resp ->
                if (resp.isSuccessful && resp.body != null) {
                    targetFile.outputStream().use { out ->
                        resp.body!!.byteStream().copyTo(out)
                    }
                    targetFile
                } else null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download Telegram file $fileId", e)
            null
        }
    }

    private suspend fun pollBot(token: String) {
        AppLogger.d(TAG, "Started polling bot: ...${token.takeLast(6)}")
        serviceScope.launch { registerBotCommands(token) }
        var offset = offsets.getOrPut(token) {
            repository.securePrefs.getSetting("tg_offset_$token", "0").toLongOrNull() ?: 0L
        }

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
                            repository.securePrefs.saveSetting("tg_offset_$token", offset.toString())

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
                            val rawText = message.get("text")?.asString ?: message.get("caption")?.asString ?: ""
                            val chat = try { message.getAsJsonObject("chat") } catch (_: Exception) { null } ?: continue
                            val chatId = chat.get("id")?.asLong ?: continue
                            val chatType = chat.get("type")?.asString ?: "private"
                            val isPrivateChat = chatType == "private"

                            val photoArray = try { message.getAsJsonArray("photo") } catch (_: Exception) { null }
                            val document = try { message.getAsJsonObject("document") } catch (_: Exception) { null }
                            val voice = try { message.getAsJsonObject("voice") } catch (_: Exception) { null }

                            val largestPhoto = photoArray?.lastOrNull()?.asJsonObject
                            val photoFileId = largestPhoto?.get("file_id")?.asString
                            val docFileId = document?.get("file_id")?.asString
                            val docName = document?.get("file_name")?.asString
                            val voiceFileId = voice?.get("file_id")?.asString

                            val hasMedia = photoFileId != null || docFileId != null || voiceFileId != null

                            if (rawText.isNotEmpty() || hasMedia) {
                                // Group spam protection: Ignore non-command group messages unless replying to the bot
                                if (!isPrivateChat && !rawText.startsWith("/")) {
                                    val isReplyToBot = try {
                                        val replyTo = message.getAsJsonObject("reply_to_message")
                                        val from = replyTo?.getAsJsonObject("from")
                                        from?.get("is_bot")?.asBoolean == true
                                    } catch (_: Exception) { false }
                                    if (!isReplyToBot) {
                                        AppLogger.d(TAG, "Ignoring non-command group message in chat $chatId: ${rawText.take(40)}")
                                        continue
                                    }
                                }

                                AppLogger.d(TAG, "Message from chat $chatId ($chatType): ${rawText.take(100)}")
                                serviceScope.launch {
                                    var effectiveText = rawText
                                    if (photoFileId != null) {
                                        sendChatAction(token, chatId, "upload_photo")
                                        val downloadedPhoto = downloadTelegramFile(token, photoFileId)
                                        if (downloadedPhoto != null) {
                                            effectiveText = if (rawText.isNotBlank()) {
                                                "[image:${downloadedPhoto.absolutePath}]\n\n$rawText"
                                            } else {
                                                "[image:${downloadedPhoto.absolutePath}] Describe this image in detail."
                                            }
                                        }
                                    } else if (docFileId != null) {
                                        sendChatAction(token, chatId, "upload_document")
                                        val downloadedDoc = downloadTelegramFile(token, docFileId, customName = docName)
                                        if (downloadedDoc != null) {
                                            effectiveText = if (rawText.isNotBlank()) {
                                                "📎 ${downloadedDoc.name}\n[File: ${downloadedDoc.absolutePath}]\n\n$rawText"
                                            } else {
                                                "📎 ${downloadedDoc.name}\n[File: ${downloadedDoc.absolutePath}] Please inspect and analyze this document."
                                            }
                                        }
                                    } else if (voiceFileId != null) {
                                        sendChatAction(token, chatId, "record_voice")
                                        val downloadedVoice = downloadTelegramFile(token, voiceFileId, customName = "voice.ogg")
                                        if (downloadedVoice != null) {
                                            effectiveText = if (rawText.isNotBlank()) {
                                                "🎤 [Voice Note: ${downloadedVoice.absolutePath}]\n\n$rawText"
                                            } else {
                                                "🎤 [Voice Note: ${downloadedVoice.absolutePath}] Please listen to this voice note."
                                            }
                                        }
                                    }

                                    if (effectiveText.isNotEmpty()) {
                                        handleMessage(token, chatId, effectiveText, isPrivateChat)
                                    }
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
        if (storageKey == "ollama") return true
        val prefs = repository.securePrefs
        val keysToCheck = mutableListOf(storageKey)
        if (storageKey.contains("-")) {
            keysToCheck.add(storageKey.replace("-", ""))
        }
        if (storageKey == "cerebras") keysToCheck.add("cerebrus")
        if (storageKey == "cerebrus") keysToCheck.add("cerebras")
        if (storageKey == "gemini") keysToCheck.add("google gemini")
        if (storageKey == "gmi") keysToCheck.add("gmi-cloud")
        if (storageKey == "gmi-cloud") keysToCheck.add("gmi")
        if (storageKey == "aimlapi") keysToCheck.add("aiml-api")
        if (storageKey == "nebius") keysToCheck.add("nebius-ai")
        if (storageKey == "friendliai") keysToCheck.add("friendli-ai")
        if (storageKey == "together") keysToCheck.add("together-ai")
        if (storageKey == "fireworks") keysToCheck.add("fireworks-ai")
        if (storageKey == "nvidia") keysToCheck.add("nvidia-nim")
        if (storageKey == "tokenharbor") keysToCheck.add("token-harbor")
        if (storageKey == "token-harbor") keysToCheck.add("tokenharbor")
        if (storageKey == "zen") {
            keysToCheck.add("opencode-zen")
            keysToCheck.add("opencode")
        }
        if (storageKey == "opencode-zen" || storageKey == "opencode") {
            keysToCheck.add("zen")
        }

        for (k in keysToCheck) {
            val hasKey = prefs.getApiKeys(k).isNotEmpty() ||
                prefs.getApiKey(k).isNotEmpty() ||
                (ai.deepcode.android.data.remote.ApiKeyRotator.getNextAvailableKey(prefs, k)?.first?.isNotEmpty() == true)
            if (hasKey) return true
            if (prefs.getSetting("oauth_token_$k", "").isNotEmpty()) return true
            if (prefs.getSetting("cookie_$k", "").isNotEmpty()) return true
            if (prefs.getSetting("web_cookie_$k", "").isNotEmpty()) return true
            if (prefs.getSetting("api_key_$k", "").isNotEmpty()) return true
            for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                if (prefs.getApiKeySlot(k, slot).isNotEmpty()) return true
            }
        }
        return false
    }

    private fun getFirstConfiguredProvider(): String? {
        val candidates = listOf(
            "Google Gemini", "Groq", "Cerebras", "Ollama", "OllamaCloud",
            "OpenAI", "Anthropic", "DeepSeek", "Mistral AI", "Together AI",
            "Fireworks AI", "NVIDIA NIM", "OpenRouter", "Zen AI"
        )
        for (cand in candidates) {
            val storageKey = providerStorageId(cand)
            val isFree = cand.contains("Free", ignoreCase = true) || cand == "Google Gemini" || cand == "Groq" || cand == "Cerebras" || cand == "Ollama"
            if (hasProviderCredentials(storageKey, isFree)) {
                return cand
            }
        }
        return null
    }

    private fun inferProviderForModel(modelId: String): String? {
        if (modelId.isBlank()) return null

        // 1. Check ModelCatalog excluding Zen AI
        for ((provName, models) in ModelCatalog.models.value) {
            if (!provName.contains("Zen", ignoreCase = true) && models.any { it.id.equals(modelId, ignoreCase = true) }) {
                return provName
            }
        }

        // 2. Check static and generic OpenAI providers in AIProviderFactory excluding Zen AI
        for (prov in AIProviderFactory.providers) {
            if (!prov.name.contains("Zen", ignoreCase = true) && prov.models.any { it.id.equals(modelId, ignoreCase = true) }) {
                return prov.name
            }
        }

        // 3. Check OPENAI_PROVIDERS
        for (openAiProv in OPENAI_PROVIDERS) {
            if (openAiProv.models.any { it.id.equals(modelId, ignoreCase = true) }) {
                return openAiProv.name
            }
        }

        // 4. Prefix and naming heuristics for models that may have been fetched or typed
        val lowerId = modelId.lowercase()
        when {
            lowerId.startsWith("claude-") -> return "Anthropic"
            lowerId.startsWith("gpt-") || lowerId.startsWith("o1-") || lowerId.startsWith("o3-") || lowerId.startsWith("chatgpt-") -> return "OpenAI"
            lowerId.startsWith("gemini-") -> return "Google Gemini"
            lowerId.startsWith("grok-") -> return "xAI"
            lowerId.startsWith("mistral-") || lowerId.startsWith("codestral-") || lowerId.startsWith("pixtral-") -> return "Mistral AI"
            lowerId.startsWith("moonshot-") || lowerId.startsWith("kimi-") -> return "Moonshot"
            lowerId.startsWith("minimax-") -> return "MiniMax"
            lowerId.startsWith("qwen-") || lowerId.startsWith("qwq-") -> return "Qwen"
            lowerId.startsWith("command-") -> return "Cohere"
            lowerId.startsWith("deepseek-") && !lowerId.contains("free") -> return "DeepSeek"
        }

        // 5. Check Zen AI last
        val zenProv = AIProviderFactory.providers.firstOrNull { it.name.contains("Zen", ignoreCase = true) }
        if (zenProv?.models?.any { it.id.equals(modelId, ignoreCase = true) } == true) {
            return zenProv.name
        }

        return null
    }

    private fun getAvailableModels(): List<AIModel> {
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<AIModel>()

        AIProviderFactory.providers.forEach { provider ->
            val storageKey = providerStorageId(provider.name)
            if (hasProviderCredentials(storageKey, provider.isFree)) {
                val selectedIdsStr = repository.securePrefs.getSetting("selected_models_$storageKey", "")
                val selectedIdSet = if (selectedIdsStr.isNotBlank()) {
                    selectedIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                } else null

                val candidateModels = mutableListOf<AIModel>()
                val dynamicModels = ModelCatalog.getModelsForProvider(provider.name, repository.securePrefs)
                candidateModels.addAll(dynamicModels)
                provider.models.forEach { sm ->
                    if (candidateModels.none { it.id == sm.id }) {
                        candidateModels.add(sm)
                    }
                }

                val filtered = if (selectedIdSet != null && selectedIdSet.isNotEmpty()) {
                    candidateModels.filter { it.id in selectedIdSet }
                } else {
                    candidateModels
                }

                filtered.forEach { model ->
                    if (seenKeys.add("${provider.name}:${model.id}")) {
                        result.add(model)
                    }
                }
            }
        }
        return result
    }

    private fun getSavedModelForChat(chatId: Long): String? {
        val tgModel = repository.securePrefs.getSetting("tg_model_$chatId", "")
            .ifEmpty { null }
        if (tgModel != null) return tgModel
        val agentModel = repository.securePrefs.getSetting("agent_model", "")
            .ifEmpty { null }
        if (agentModel != null) return agentModel
        return repository.securePrefs.getSetting("chat_model", "")
            .ifEmpty { null }
    }

    private fun getSavedProviderForChat(chatId: Long): String? {
        val prov = repository.securePrefs.getSetting("tg_provider_$chatId", "")
            .ifEmpty { null }
        if (prov != null) return prov
        val agentProv = repository.securePrefs.getSetting("agent_provider", "")
            .ifEmpty { null }
        if (agentProv != null) return agentProv
        val chatProv = repository.securePrefs.getSetting("chat_provider", "")
            .ifEmpty { null }
        if (chatProv != null) return chatProv
        // Automatically infer provider from saved model ID if provider was not explicitly stored
        val modelId = getSavedModelForChat(chatId) ?: return null
        return inferProviderForModel(modelId)
    }

    private fun saveModelForChat(chatId: Long, modelId: String, providerName: String? = null) {
        repository.securePrefs.saveSetting("tg_model_$chatId", modelId)
        val prov = providerName
            ?: inferProviderForModel(modelId)
            ?: getFirstConfiguredProvider()
            ?: "Google Gemini"
        repository.securePrefs.saveSetting("tg_provider_$chatId", prov)
        if (repository.securePrefs.getSetting("agent_provider", "").isEmpty()) {
            repository.securePrefs.saveSetting("agent_provider", prov)
            repository.securePrefs.saveSetting("agent_model", modelId)
        }
    }

    private suspend fun handleMessage(token: String, chatId: Long, text: String, isPrivateChat: Boolean = true) {
        // Only save this chat as default from private interactions with the user, never from group/spam chats
        if (isPrivateChat) {
            repository.securePrefs.saveSetting("telegram_default_chat_id", chatId.toString())
        }

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
        val appConfiguredProvider = repository.securePrefs.getSetting("agent_provider", "")
            .ifEmpty { repository.securePrefs.getSetting("chat_provider", "") }
            .takeIf { it.isNotBlank() }
        val effectiveProvider = savedProviderName
            ?: inferProviderForModel(savedModelId ?: "")
            ?: appConfiguredProvider
            ?: getFirstConfiguredProvider()
            ?: "Google Gemini"

        val providerObj: AIProvider? = AIProviderFactory.providers.firstOrNull { it.name.equals(effectiveProvider, ignoreCase = true) }
            ?: (OPENAI_PROVIDERS.find { it.name.equals(effectiveProvider, ignoreCase = true) }?.let { GenericOpenAIProvider(it) })
        val dynamicModels = ModelCatalog.getModelsForProvider(effectiveProvider, repository.securePrefs)
        val allProviderModels = (dynamicModels + (providerObj?.models ?: emptyList())).associateBy { it.id }.values.toList()

        var effectiveModelId = savedModelId ?: ""
        if (effectiveProvider.contains("Zen", ignoreCase = true)) {
            effectiveModelId = ai.deepcode.android.data.remote.ZenModels.sanitize(effectiveModelId, allProviderModels)
            if (effectiveModelId != savedModelId) {
                saveModelForChat(chatId, effectiveModelId, effectiveProvider)
            }
        }

        if (effectiveModelId.isEmpty() || (allProviderModels.isNotEmpty() && allProviderModels.none { it.id == effectiveModelId })) {
            val storageId = providerStorageId(effectiveProvider)
            val defaultModelSetting = repository.securePrefs.getSetting("default_model_$storageId", "")
            effectiveModelId = if (defaultModelSetting.isNotEmpty() && allProviderModels.any { it.id == defaultModelSetting }) {
                defaultModelSetting
            } else {
                if (effectiveProvider.contains("Zen", ignoreCase = true)) {
                    allProviderModels.firstOrNull { it.id == ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE }?.id
                        ?: allProviderModels.firstOrNull { it.id == "ling-3.0-flash-fin-free" }?.id
                        ?: allProviderModels.firstOrNull { it.isFree }?.id
                        ?: ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE
                } else {
                    allProviderModels.firstOrNull { it.isFree }?.id
                        ?: allProviderModels.firstOrNull()?.id
                        ?: effectiveModelId
                }
            }
            if (effectiveModelId.isNotEmpty()) {
                saveModelForChat(chatId, effectiveModelId, effectiveProvider)
            }
        }

        val modelInfo = allProviderModels.firstOrNull { it.id == effectiveModelId }
            ?: AIModel(
                id = effectiveModelId,
                name = formatModelTitle(effectiveModelId),
                provider = effectiveProvider,
                isFree = effectiveProvider.contains("Zen", ignoreCase = true),
                contextWindow = "128k",
                badge = if (effectiveProvider.contains("Zen", ignoreCase = true)) "Free" else "Paid"
            )

        val storageKey = providerStorageId(effectiveProvider)
        val isProviderFree = storageKey == "ollama"

        val processingText = detectProcessingMessage(text)
        val processingMsgId = sendMessage(token, chatId, processingText)

        try {
            val sessionId = "telegram_$chatId"
            if (repository.getSessionById(sessionId) == null) {
                repository.createSessionWithId(sessionId, "Telegram Chat $chatId")
            }

            // Helper to record an exchange in repository so subsequent turns have full conversational context
            suspend fun recordBypassExchange(userPrompt: String, assistantReply: String) {
                try {
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "user",
                        content = userPrompt,
                        timestamp = System.currentTimeMillis()
                    ))
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = assistantReply,
                        timestamp = System.currentTimeMillis() + 1
                    ))
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to persist bypass exchange", e)
                }
            }

            // Music playback bypass — ONLY for simple, exact song playback (e.g. "play Shape of You by Ed Sheeran").
            // If the request requires reasoning/search (e.g. "play new karan aujla song in youtube music"), allow the AI agent to reason, search for the latest release, and play it!
            val musicHandler = MusicDetectionHandler(this)
            if (!musicHandler.requiresReasoning(text)) {
                val musicMsg = musicHandler.play(text)
                if (musicMsg.isNotEmpty()) {
                    recordBypassExchange(text, musicMsg)
                    if (processingMsgId != null) {
                        if (!editMessage(token, chatId, processingMsgId, musicMsg)) {
                            sendMessage(token, chatId, musicMsg)
                        }
                    } else {
                        sendMessage(token, chatId, musicMsg)
                    }
                    return
                }
            }

            // Automation setup bypass — no AI needed
            val automationHandler = AutomationHandler(this)
            val automationMsg = automationHandler.create(text, chatId.toString())
            if (automationMsg.isNotEmpty()) {
                recordBypassExchange(text, automationMsg)
                if (processingMsgId != null) {
                    if (!editMessage(token, chatId, processingMsgId, automationMsg)) {
                        sendMessage(token, chatId, automationMsg)
                    }
                } else {
                    sendMessage(token, chatId, automationMsg)
                }
                return
            }

            // Gmail bypass — no AI needed
            val gmailHandler = GmailHandler(this)
            val gmailResponse = gmailHandler.fetch(text)
            if (gmailResponse.isNotEmpty()) {
                recordBypassExchange(text, gmailResponse)
                if (processingMsgId != null) {
                    if (!editMessage(token, chatId, processingMsgId, gmailResponse)) {
                        sendMessage(token, chatId, gmailResponse)
                    }
                } else {
                    sendMessage(token, chatId, gmailResponse)
                }
                return
            }

            // GitHub bypass — no AI needed
            val githubHandler = GitHubHandler(this)
            val githubResponse = githubHandler.fetch(text)
            if (githubResponse.isNotEmpty()) {
                recordBypassExchange(text, githubResponse)
                if (processingMsgId != null) {
                    if (!editMessage(token, chatId, processingMsgId, githubResponse)) {
                        sendMessage(token, chatId, githubResponse)
                    }
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
                        val userText = toolJob.userPrompt
                        val model = when {
                            userText.contains("chatgpt", ignoreCase = true) -> "chatgpt"
                            userText.contains("dalle", ignoreCase = true) || userText.contains("dall-e", ignoreCase = true) -> "dalle"
                            userText.contains("imagen", ignoreCase = true) || userText.contains("gemini", ignoreCase = true) -> "imagen"
                            userText.contains("flux", ignoreCase = true) -> "flux"
                            userText.contains("turbo", ignoreCase = true) -> "turbo"
                            userText.contains("sdxl", ignoreCase = true) -> "sdxl"
                            userText.contains("antigravity", ignoreCase = true) -> "antigravity"
                            else -> ""
                        }
                        val genPayload = JsonObject().apply {
                            addProperty("prompt", userText)
                            if (model.isNotEmpty()) addProperty("model", model)
                        }
                        ai.deepcode.android.service.tools.ToolExecutor(applicationContext)
                            .executeTool("generate_image", gson.toJson(genPayload), workingDir, false)
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
                    recordBypassExchange(text, toolResult)
                }
            }

            // Fall back to the main AI agent with live streaming to Telegram
            if (finalResponse.isEmpty()) {
                if (!isProviderFree && !hasProviderCredentials(storageKey, false)) {
                    val modelTitle = modelInfo?.name ?: formatModelTitle(effectiveModelId)
                    val missingKeyMsg = "⚠️ <b>$effectiveProvider API Key Required</b>\n\n" +
                        "Your chat is currently set to use <b>$modelTitle</b> ($effectiveProvider), but no API key is configured.\n\n" +
                        "👉 Please add your key in DeepCode app (<b>Settings → API Keys → $effectiveProvider</b>), or switch to a free model with /models."
                    if (processingMsgId != null) {
                        if (!editMessage(token, chatId, processingMsgId, missingKeyMsg, parseMode = "HTML")) {
                            sendMessage(token, chatId, missingKeyMsg, parseMode = "HTML")
                        }
                    } else {
                        sendMessage(token, chatId, missingKeyMsg, parseMode = "HTML")
                    }
                    return
                }

                val typingJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    while (isActive) {
                        sendChatAction(token, chatId, "typing")
                        kotlinx.coroutines.delay(4000)
                    }
                }

                try {
                    var lastEditTime = System.currentTimeMillis()
                    var lastEditedText = ""

                    withTimeout(180_000L) {
                        agentEngine.run(sessionId, text, effectiveProvider, effectiveModelId, noFallback = true).collect { statusOrText ->
                            if (!statusOrText.startsWith("Thinking...\n") && !statusOrText.startsWith("Running tool: ")) {
                                finalResponse = statusOrText

                                // Live progressive edit to Telegram message:
                                // First chunk is delivered immediately (0s delay); subsequent chunks throttled to 1.2s.
                                if (processingMsgId != null) {
                                    val now = System.currentTimeMillis()
                                    val cleanSoFar = stripThoughts(statusOrText).trim()
                                    val isFirstChunk = lastEditedText.isEmpty() && cleanSoFar.isNotEmpty()
                                    val isSubsequentChunk = cleanSoFar.length > lastEditedText.length + 6 && (now - lastEditTime > 1200)
                                    if (isFirstChunk || isSubsequentChunk) {
                                        lastEditTime = now
                                        lastEditedText = cleanSoFar
                                        val displayChunk = if (cleanSoFar.length > 3900) cleanSoFar.take(3900) + "..." else "$cleanSoFar ▌"
                                        editMessage(token, chatId, processingMsgId, displayChunk, parseMode = "")
                                    }
                                }
                            } else if (statusOrText.startsWith("Running tool: ") && processingMsgId != null) {
                                val toolName = statusOrText.removePrefix("Running tool: ").substringBefore("...").trim()
                                editMessage(token, chatId, processingMsgId, "🔧 Executing: $toolName...", parseMode = "")
                            }
                        }
                    }
                } finally {
                    typingJob.cancel()
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

            // Asynchronously extract and save important memory from this Telegram exchange
            try {
                CoroutineScope(Dispatchers.IO).launch {
                    ai.deepcode.android.memory.MemoryExtractor(applicationContext)
                        .extractAndSave(text, cleanResponse.ifEmpty { finalResponse }, source = "telegram")
                }
            } catch (_: Exception) {}

            // Send media files first
            if (audioMatch != null) {
                val audioPath = audioMatch.groupValues[1]
                try {
                    val audioFile = File(audioPath)
                    if (audioFile.exists()) {
                        val sent = sendAudio(token, chatId, audioFile, cleanResponse.ifEmpty { null })
                        if (processingMsgId != null) {
                            if (sent) deleteMessage(token, chatId, processingMsgId)
                            else editMessage(token, chatId, processingMsgId, "⚠️ Audio was generated, but failed to deliver to Telegram.\nPath: $audioPath")
                        }
                    } else {
                        if (processingMsgId != null) editMessage(token, chatId, processingMsgId, "⚠️ Audio file not found at: $audioPath")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send audio file", e)
                    if (processingMsgId != null) editMessage(token, chatId, processingMsgId, "⚠️ Failed to send audio: ${e.message}")
                }
                return
            }

            if (imageMatch != null || mdImageMatch != null) {
                val imgUrl = (imageMatch?.groupValues?.getOrNull(1) ?: mdImageMatch?.groupValues?.getOrNull(1)) ?: ""
                if (imgUrl.isNotEmpty()) {
                    if (cleanResponse.isNotEmpty()) {
                        sendMessage(token, chatId, cleanResponse)
                    }
                    val sent = sendPhoto(token, chatId, imgUrl)
                    if (processingMsgId != null) {
                        if (sent) {
                            deleteMessage(token, chatId, processingMsgId)
                        } else {
                            editMessage(token, chatId, processingMsgId, "⚠️ Image was generated, but Telegram could not display it.\nLocal path: $imgUrl")
                        }
                    }
                    return
                }
            }

            if (videoMatch != null) {
                val videoUrl = videoMatch.groupValues[1]
                if (videoUrl.isNotEmpty()) {
                    if (cleanResponse.isNotEmpty()) {
                        sendMessage(token, chatId, cleanResponse)
                    }
                    val sent = sendVideo(token, chatId, videoUrl)
                    if (processingMsgId != null) {
                        if (sent) {
                            deleteMessage(token, chatId, processingMsgId)
                        } else {
                            editMessage(token, chatId, processingMsgId, "⚠️ Video was generated, but Telegram could not display it.\nPath: $videoUrl")
                        }
                    }
                    return
                }
            }

            if (fileMatch != null) {
                val filePath = fileMatch.groupValues[1]
                try {
                    val file = File(filePath)
                    if (file.exists()) {
                        val sent = sendDocument(token, chatId, file, cleanResponse.ifEmpty { null })
                        if (processingMsgId != null) {
                            if (sent) deleteMessage(token, chatId, processingMsgId)
                            else editMessage(token, chatId, processingMsgId, "⚠️ Document could not be sent to Telegram: $filePath")
                        }
                    } else {
                        AppLogger.w(TAG, "File not found for sending: $filePath")
                        if (processingMsgId != null) editMessage(token, chatId, processingMsgId, "⚠️ File not found: $filePath")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send document", e)
                    if (processingMsgId != null) editMessage(token, chatId, processingMsgId, "⚠️ Error sending file: ${e.message}")
                }
                return
            }

            // Orchestration summary omitted for cleaner reply

            val maxLen = 4000
            if (finalResponse.length <= maxLen) {
                if (processingMsgId != null) {
                    val edited = editMessage(token, chatId, processingMsgId, finalResponse)
                    if (!edited) {
                        sendMessage(token, chatId, finalResponse)
                    }
                } else {
                    sendMessage(token, chatId, finalResponse)
                }
            } else {
                if (processingMsgId != null) {
                    deleteMessage(token, chatId, processingMsgId)
                }
                val formattedFull = TelegramFormatter.formatMarkdownToTelegramHtml(finalResponse)
                val chunks = TelegramFormatter.chunkTelegramHtml(formattedFull, maxLen = 3900)
                for (chunk in chunks) {
                    sendMessage(token, chatId, chunk, parseMode = "HTML")
                    delay(300)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val timeoutMsg = "⏱️ Request timed out after 5 minutes. Please try again."
            try {
                repository.insertMessage(Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = "telegram_$chatId",
                    role = "assistant",
                    content = timeoutMsg,
                    timestamp = System.currentTimeMillis()
                ))
            } catch (_: Exception) {}
            if (processingMsgId != null) {
                val edited = editMessage(token, chatId, processingMsgId, timeoutMsg)
                if (!edited) {
                    sendMessage(token, chatId, timeoutMsg)
                }
            } else {
                sendMessage(token, chatId, timeoutMsg)
            }
            AppLogger.e(TAG, "Agent run timed out", e)
        } catch (e: Exception) {
            val rawError = e.message?.ifBlank { null } ?: e.cause?.message?.ifBlank { null } ?: e.toString()
            val errorMsg = if (rawError.isNotBlank()) {
                // Route through the friendly formatter so provider/auth/rate-limit errors
                // reach the user as readable messages instead of raw exception text.
                try {
                    if (isRateLimitException(e)) getRateLimitReply(effectiveProvider)
                    else getProviderErrorReply(effectiveProvider, rawError)
                } catch (_: Exception) {
                    rawError
                }
            } else {
                "Error: ${e.javaClass.simpleName}"
            }
            try {
                repository.insertMessage(Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = "telegram_$chatId",
                    role = "assistant",
                    content = errorMsg,
                    timestamp = System.currentTimeMillis()
                ))
            } catch (_: Exception) {}
            if (processingMsgId != null) {
                val edited = editMessage(token, chatId, processingMsgId, errorMsg, parseMode = "HTML")
                if (!edited) {
                    sendMessage(token, chatId, errorMsg, parseMode = "HTML")
                }
            } else {
                sendMessage(token, chatId, errorMsg, parseMode = "HTML")
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
            command.startsWith("/tasks") || command.startsWith("/scheduled") || command.startsWith("/automations") -> {
                showTasksList(token, chatId)
            }
            command.startsWith("/task_output") || command.startsWith("/output") -> {
                val parts = command.split("\\s+".toRegex(), limit = 2)
                val arg = parts.getOrNull(1)?.trim() ?: ""
                showTaskOutput(token, chatId, arg)
            }
            command.startsWith("/models") || command.startsWith("/model") || command.startsWith("/change") ||
            command.startsWith("/provider") || command.startsWith("/providers") -> {
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
                val defaultStartMsg = "👋 <b>Welcome to DeepCode Bot!</b> 🤖\n" +
                    "────────── ✦ ──────────\n" +
                    "💬 Send me a message and I’ll respond using AI.\n\n" +
                    ">_ <b>Commands:</b>\n\n" +
                    "🤖 /models - Switch provider & models\n" +
                    "↔️ /change - Same as /models\n" +
                    "📋 /tasks - View scheduled tasks & outputs\n" +
                    "📄 /task_output - Show latest output of a task\n" +
                    "🔊 /voice - Change voice character & tone\n" +
                    "🌐 /language - Set AI & TTS language\n" +
                    "👤 /persona - List available personas\n" +
                    "🚀 /start - Show this message"
                var msg = repository.securePrefs.getSetting("tg_start_msg", "")
                    .ifEmpty { defaultStartMsg }
                if (msg.contains("*Welcome to DeepCode Bot!*")) {
                    msg = defaultStartMsg
                }
                sendMessage(token, chatId, msg, parseMode = "HTML")
            }
            command.startsWith("/help") -> {
                val defaultHelpMsg = "💡 <b>Available Commands:</b>\n" +
                    "────────── ✦ ──────────\n" +
                    "🤖 /models - Switch provider & models\n" +
                    "↔️ /change - Same as /models\n" +
                    "📋 /tasks - View scheduled tasks & outputs (In-App & ChatGPT)\n" +
                    "📄 /task_output - Show latest output of a task\n" +
                    "🔊 /voice - Change voice character & tone\n" +
                    "🌐 /language - Set AI & TTS language\n" +
                    "👤 /persona - List available personas\n" +
                    "🧹 /clear - Start fresh session / clear history\n" +
                    "🚀 /start - Show welcome message\n" +
                    "❓ /help - Show this guide\n\n" +
                    "💬 <i>Just send any message to chat with AI!</i>"
                var msg = repository.securePrefs.getSetting("tg_help_msg", "")
                    .ifEmpty { defaultHelpMsg }
                if (msg.contains("*Available Commands:*")) {
                    msg = defaultHelpMsg
                }
                sendMessage(token, chatId, msg, parseMode = "HTML")
            }
            else -> {
                sendMessage(token, chatId, "Unknown command. Try /tasks, /task_output, /models, /voice, /language, /persona, /start, or /help")
            }
        }
    }

    private fun isTaskChatGPT(task: AutomationEntity): Boolean {
        return task.category.equals("CHATGPT", ignoreCase = true) ||
            task.templateId.contains("chatgpt", ignoreCase = true) ||
            task.name.contains("chatgpt", ignoreCase = true) ||
            task.configJson.contains("\"target\":\"chatgpt\"") ||
            task.configJson.contains("\"target\": \"chatgpt\"")
    }

    private fun isTaskTelegramForwarded(task: AutomationEntity, chatId: Long? = null): Boolean {
        return try {
            val obj = com.google.gson.JsonParser.parseString(task.configJson).asJsonObject
            val tgChatId = obj.get("telegram_chat_id")?.asString
            if (chatId != null) {
                tgChatId == chatId.toString()
            } else {
                !tgChatId.isNullOrBlank()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun describeCron(cron: String): String {
        val trimmed = cron.trim()
        return when {
            trimmed == "@daily" || trimmed == "@midnight" || trimmed == "0 0 * * *" -> "Daily at midnight"
            trimmed == "@hourly" || trimmed == "0 * * * *" -> "Hourly"
            trimmed == "@weekly" || trimmed == "0 0 * * 0" -> "Weekly on Sunday"
            trimmed == "0 7 * * 1" -> "Weekly on Monday at 07:00 AM"
            trimmed.matches(Regex("""^0\s+(\d{1,2})\s+\*\s+\*\s+\*$""")) -> {
                val match = Regex("""^0\s+(\d{1,2})\s+\*\s+\*\s+\*$""").find(trimmed)!!
                val hour = match.groupValues[1].toInt()
                val ampm = if (hour >= 12) "PM" else "AM"
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "Daily at %02d:00 %s".format(h12, ampm)
            }
            trimmed.matches(Regex("""^(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+\*$""")) -> {
                val match = Regex("""^(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+\*$""").find(trimmed)!!
                val min = match.groupValues[1].toInt()
                val hour = match.groupValues[2].toInt()
                val ampm = if (hour >= 12) "PM" else "AM"
                val h12 = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "Daily at %02d:%02d %s".format(h12, min, ampm)
            }
            trimmed.matches(Regex("""^\*/(\d+)\s+\*\s+\*\s+\*\s+\*$""")) -> {
                val sec = Regex("""^\*/(\d+)\s+\*\s+\*\s+\*\s+\*$""").find(trimmed)!!.groupValues[1]
                "Every $sec seconds"
            }
            trimmed.matches(Regex("""^\*/(\d+)\s+\*\s+\*\s+\*$""")) -> {
                val min = Regex("""^\*/(\d+)\s+\*\s+\*\s+\*$""").find(trimmed)!!.groupValues[1]
                "Every $min minutes"
            }
            trimmed.matches(Regex("""^0\s+\*/(\d+)\s+\*\s+\*\s+\*$""")) -> {
                val hrs = Regex("""^0\s+\*/(\d+)\s+\*\s+\*\s+\*$""").find(trimmed)!!.groupValues[1]
                "Every $hrs hours"
            }
            else -> trimmed
        }
    }

    private fun formatTaskTimestamp(timestamp: Long, isNext: Boolean = false): String {
        if (timestamp <= 0L) return if (isNext) "Pending" else "Never"
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        val dateStr = sdf.format(Date(timestamp))
        val now = System.currentTimeMillis()
        return if (isNext && timestamp > now) {
            val diffMs = timestamp - now
            val diffMin = diffMs / 60000
            val relative = when {
                diffMin < 1 -> "in <1 min"
                diffMin < 60 -> "in ${diffMin}m"
                diffMin < 1440 -> "in ${diffMin / 60}h ${diffMin % 60}m"
                else -> "in ${diffMin / 1440}d ${(diffMin % 1440) / 60}h"
            }
            "$dateStr ($relative)"
        } else if (!isNext && timestamp > 0L && now > timestamp) {
            val diffMin = (now - timestamp) / 60000
            val relative = when {
                diffMin < 1 -> "just now"
                diffMin < 60 -> "${diffMin}m ago"
                diffMin < 1440 -> "${diffMin / 60}h ago"
                else -> "${diffMin / 1440}d ago"
            }
            "$dateStr ($relative)"
        } else {
            dateStr
        }
    }

    private suspend fun getLatestTaskOutput(task: AutomationEntity): Pair<String?, Long?> {
        val sessionId = task.getEffectiveChatSessionId() ?: task.chatSessionId
        if (sessionId.isNullOrBlank()) return null to null

        val db = AppDatabase.getDatabase(applicationContext)
        val messages = db.messageDao().getMessagesListForSession(sessionId)
        if (messages.isEmpty()) return null to null

        val assistantMsg = messages.filter {
            it.role.equals("assistant", ignoreCase = true) && !it.isToolCall && it.content.isNotBlank()
        }.lastOrNull() ?: messages.filter {
            it.role.equals("assistant", ignoreCase = true) && it.content.isNotBlank()
        }.lastOrNull() ?: messages.lastOrNull { it.content.isNotBlank() }

        if (assistantMsg != null) {
            val clean = cleanControlAndCitationTokens(assistantMsg.content)
            val withoutThoughts = stripThoughts(clean)
            return withoutThoughts to assistantMsg.timestamp
        }
        return null to null
    }

    private suspend fun buildTasksListMessage(chatId: Long): Pair<String, JsonArray> {
        val repo = AutomationRepository(applicationContext)
        val tasks = repo.getAllAutomations().sortedBy { it.name.lowercase() }

        if (tasks.isEmpty()) {
            val text = "📋 <b>Scheduled Tasks & Automations</b>\n" +
                "────────── ✦ ──────────\n" +
                "<i>No scheduled tasks or automations found in the app.</i>\n\n" +
                "💡 <b>You can create one anytime:</b>\n" +
                "• In the DeepCode app under <b>Automations</b> (In-App or ChatGPT)\n" +
                "• Or tell me here, e.g.:\n" +
                "  <i>\"Schedule daily morning news at 8:00 AM\"</i>\n" +
                "  <i>\"Setup ChatGPT research every 4 hours\"</i>"
            val rows = JsonArray()
            val row = JsonArray()
            val refreshBtn = JsonObject().apply {
                addProperty("text", "🔄 Refresh")
                addProperty("callback_data", "tasks_list")
            }
            row.add(refreshBtn)
            rows.add(row)
            return text to rows
        }

        val sb = StringBuilder()
        sb.appendLine("📋 <b>Scheduled Tasks & Automations (${tasks.size})</b>")
        sb.appendLine("────────── ✦ ──────────")

        tasks.forEachIndexed { index, task ->
            val num = index + 1
            val isGpt = isTaskChatGPT(task)
            val typeTag = if (isGpt) "🌐 <b>ChatGPT</b>" else "🤖 <b>In-App</b>"
            val statusTag = if (task.isEnabled) "🟢 Active" else "⏸️ Paused"
            val tgForwarded = isTaskTelegramForwarded(task, chatId)
            val tgTag = if (tgForwarded) "🔔 On" else "🔕 Off"
            val scheduleDesc = describeCron(task.cronExpression)

            sb.appendLine()
            sb.appendLine("<b>$num. ${task.name}</b> [$statusTag]")
            sb.appendLine("• <b>Type:</b> $typeTag")
            sb.appendLine("• <b>Schedule:</b> $scheduleDesc (<code>${task.cronExpression}</code>)")
            if (task.isEnabled) {
                sb.appendLine("• <b>Next Run:</b> ${formatTaskTimestamp(task.nextRunAt, isNext = true)}")
            }
            if (task.lastRunAt > 0L) {
                sb.appendLine("• <b>Last Run:</b> ${formatTaskTimestamp(task.lastRunAt, isNext = false)}")
            }
            sb.appendLine("• <b>Telegram Forward:</b> $tgTag")
        }

        sb.appendLine()
        sb.appendLine("👇 <i>Tap below to view output or manage a task:</i>")

        val rows = JsonArray()

        // 1. Buttons to View Output for each task
        for (chunk in tasks.chunked(2)) {
            val row = JsonArray()
            for (task in chunk) {
                val idx = tasks.indexOf(task) + 1
                val btn = JsonObject().apply {
                    val label = if (chunk.size == 1) "📄 Output: ${task.name.take(18)}" else "📄 Output #$idx"
                    addProperty("text", label)
                    addProperty("callback_data", "task_output:${task.id}")
                }
                row.add(btn)
            }
            rows.add(row)
        }

        // 2. Buttons to Manage/Toggle each task
        for (chunk in tasks.chunked(2)) {
            val row = JsonArray()
            for (task in chunk) {
                val idx = tasks.indexOf(task) + 1
                val btn = JsonObject().apply {
                    val label = if (chunk.size == 1) "⚙️ Manage: ${task.name.take(18)}" else "⚙️ Manage #$idx"
                    addProperty("text", label)
                    addProperty("callback_data", "task_manage:${task.id}")
                }
                row.add(btn)
            }
            rows.add(row)
        }

        // 3. Bottom controls
        val bottomRow = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("text", "🔄 Refresh")
                addProperty("callback_data", "tasks_list")
            })
        }
        rows.add(bottomRow)

        return sb.toString() to rows
    }

    private suspend fun showTasksList(token: String, chatId: Long, messageId: Long? = null) {
        val (text, rows) = buildTasksListMessage(chatId)
        val replyMarkup = JsonObject().apply {
            add("inline_keyboard", rows)
        }
        if (messageId != null) {
            editMessageWithKeyboard(token, chatId, messageId, text, replyMarkup, parseMode = "HTML")
        } else {
            sendMessageWithKeyboard(token, chatId, text, replyMarkup, parseMode = "HTML")
        }
    }

    private suspend fun showTaskOutput(token: String, chatId: Long, taskIdOrQuery: String, messageId: Long? = null) {
        val repo = AutomationRepository(applicationContext)
        val allTasks = repo.getAllAutomations()
        if (allTasks.isEmpty()) {
            sendMessage(token, chatId, "No scheduled tasks found.")
            return
        }

        // Resolve by 1-based index or ID or name
        val index = taskIdOrQuery.toIntOrNull()
        val task = when {
            index != null && index in 1..allTasks.size -> {
                allTasks.sortedBy { it.name.lowercase() }[index - 1]
            }
            taskIdOrQuery.isNotBlank() -> {
                allTasks.firstOrNull { it.id == taskIdOrQuery || it.id.startsWith(taskIdOrQuery) }
                    ?: allTasks.firstOrNull { it.name.contains(taskIdOrQuery, ignoreCase = true) }
            }
            else -> allTasks.firstOrNull()
        }

        if (task == null) {
            sendMessage(token, chatId, "Task not found: '$taskIdOrQuery'. Use /tasks to see available tasks.")
            return
        }

        val isGpt = isTaskChatGPT(task)
        val typeTag = if (isGpt) "🌐 ChatGPT" else "🤖 In-App"
        val (output, ts) = getLatestTaskOutput(task)
        val isTgOn = isTaskTelegramForwarded(task, chatId)

        val header = buildString {
            appendLine("📄 <b>Task Output: ${task.name}</b>")
            appendLine("• <b>Type:</b> $typeTag")
            appendLine("• <b>Schedule:</b> ${describeCron(task.cronExpression)}")
            if (ts != null && ts > 0L) {
                appendLine("• <b>Executed:</b> ${formatTaskTimestamp(ts, isNext = false)}")
            }
            appendLine("────────── ✦ ──────────")
            appendLine()
        }

        val actionsMarkup = JsonObject().apply {
            val rows = JsonArray()
            val row1 = JsonArray()
            row1.add(JsonObject().apply {
                addProperty("text", "⚡ Run Now")
                addProperty("callback_data", "task_run:${task.id}")
            })
            row1.add(JsonObject().apply {
                val label = if (isTgOn) "🔕 Forward: ON" else "🔔 Forward: OFF"
                addProperty("text", label)
                addProperty("callback_data", "task_toggle_tg:${task.id}")
            })
            rows.add(row1)

            val row2 = JsonArray()
            row2.add(JsonObject().apply {
                addProperty("text", "⚙️ Manage Task")
                addProperty("callback_data", "task_manage:${task.id}")
            })
            row2.add(JsonObject().apply {
                addProperty("text", "🔙 Back to Tasks")
                addProperty("callback_data", "tasks_list")
            })
            rows.add(row2)
            add("inline_keyboard", rows)
        }

        if (output.isNullOrBlank()) {
            val emptyMsg = header + "<i>No execution output available yet. This task may not have run yet.</i>\n\nTap <b>⚡ Run Now</b> below to execute it and get output immediately!"
            if (messageId != null) {
                editMessageWithKeyboard(token, chatId, messageId, emptyMsg, actionsMarkup, parseMode = "HTML")
            } else {
                sendMessageWithKeyboard(token, chatId, emptyMsg, actionsMarkup, parseMode = "HTML")
            }
            return
        }

        val formattedOutput = TelegramFormatter.formatMarkdownToTelegramHtml(output)
        val fullHtml = header + formattedOutput
        val chunks = TelegramFormatter.chunkTelegramHtml(fullHtml, maxLen = 3900)
        if (chunks.size == 1) {
            if (messageId != null) {
                editMessageWithKeyboard(token, chatId, messageId, chunks[0], actionsMarkup, parseMode = "HTML")
            } else {
                sendMessageWithKeyboard(token, chatId, chunks[0], actionsMarkup, parseMode = "HTML")
            }
        } else {
            for (i in 0 until chunks.size - 1) {
                sendMessage(token, chatId, chunks[i], parseMode = "HTML")
                delay(300)
            }
            sendMessageWithKeyboard(token, chatId, chunks.last(), actionsMarkup, parseMode = "HTML")
        }
    }

    private suspend fun showTaskManage(token: String, chatId: Long, taskId: String, messageId: Long? = null) {
        val repo = AutomationRepository(applicationContext)
        val task = repo.getAutomationById(taskId)
        if (task == null) {
            sendMessage(token, chatId, "Task not found.")
            return
        }

        val isGpt = isTaskChatGPT(task)
        val typeTag = if (isGpt) "🌐 ChatGPT" else "🤖 In-App"
        val statusTag = if (task.isEnabled) "🟢 Active" else "⏸️ Paused"
        val isTgOn = isTaskTelegramForwarded(task, chatId)
        val promptText = task.getEffectiveActionPrompt() ?: "Default task execution"

        val text = buildString {
            appendLine("⚙️ <b>Manage Task: ${task.name}</b>")
            appendLine("────────── ✦ ──────────")
            appendLine("• <b>Type:</b> $typeTag")
            appendLine("• <b>Status:</b> $statusTag")
            appendLine("• <b>Schedule:</b> ${describeCron(task.cronExpression)} (<code>${task.cronExpression}</code>)")
            appendLine("• <b>Next Run:</b> ${formatTaskTimestamp(task.nextRunAt, isNext = true)}")
            appendLine("• <b>Last Run:</b> ${formatTaskTimestamp(task.lastRunAt, isNext = false)}")
            appendLine("• <b>Telegram Auto-Forward:</b> ${if (isTgOn) "🔔 Enabled" else "🔕 Disabled"}")
            appendLine("• <b>Prompt:</b> <code>${promptText.take(100)}</code>")
        }

        val markup = JsonObject().apply {
            val rows = JsonArray()

            val row1 = JsonArray()
            row1.add(JsonObject().apply {
                addProperty("text", "📄 View Output")
                addProperty("callback_data", "task_output:${task.id}")
            })
            row1.add(JsonObject().apply {
                addProperty("text", "⚡ Run Now")
                addProperty("callback_data", "task_run:${task.id}")
            })
            rows.add(row1)

            val row2 = JsonArray()
            row2.add(JsonObject().apply {
                val statusBtn = if (task.isEnabled) "⏸️ Pause" else "▶️ Resume"
                addProperty("text", statusBtn)
                addProperty("callback_data", "task_toggle_status:${task.id}")
            })
            row2.add(JsonObject().apply {
                val tgBtn = if (isTgOn) "🔕 TG Forward: OFF" else "🔔 TG Forward: ON"
                addProperty("text", tgBtn)
                addProperty("callback_data", "task_toggle_tg:${task.id}")
            })
            rows.add(row2)

            val row3 = JsonArray()
            row3.add(JsonObject().apply {
                addProperty("text", "🔙 Back to Tasks")
                addProperty("callback_data", "tasks_list")
            })
            rows.add(row3)

            add("inline_keyboard", rows)
        }

        if (messageId != null) {
            editMessageWithKeyboard(token, chatId, messageId, text, markup, parseMode = "HTML")
        } else {
            sendMessageWithKeyboard(token, chatId, text, markup, parseMode = "HTML")
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
        val currentModel = availableModels.firstOrNull { it.id == savedId && it.provider.equals(savedProvider, ignoreCase = true) }

        val allProviders = AIProviderFactory.providers.map { it.name }.distinct()
        val activeProviders = availableModels.map { it.provider }.distinct()
        val sortedProviders = (listOf("Zen AI") + activeProviders + allProviders).distinct()

        val activeProviderName = savedProvider ?: currentModel?.provider ?: "Zen AI"

        val header = buildString {
            append("🧠 *Select AI Provider*\n\n")
            if (currentModel != null) {
                append("Current: *${currentModel.name}* (${currentModel.provider})\n\n")
            } else if (savedProvider != null) {
                val modelName = savedId?.let { formatModelTitle(it) } ?: "Default Model"
                append("Current: *$modelName* ($savedProvider)\n\n")
            } else {
                append("Current: *Mimo V2.5* (Zen AI - Free)\n\n")
            }
            append("Tap a provider below to choose a model:")
        }

        val rows = JsonArray()
        for (chunk in sortedProviders.chunked(2)) {
            val row = JsonArray()
            for (provider in chunk) {
                val btn = JsonObject()
                val isActiveProvider = provider.equals(activeProviderName, ignoreCase = true)
                val isFreeOrConfigured = provider.contains("Zen", ignoreCase = true) || hasProviderCredentials(providerStorageId(provider), provider.contains("Free"))
                val prefix = if (isActiveProvider) "✓ " else if (isFreeOrConfigured) "⚡ " else ""
                btn.addProperty("text", "$prefix$provider")
                btn.addProperty("callback_data", "select_provider:$provider")
                row.add(btn)
            }
            rows.add(row)
        }
        return header to rows
    }

    private suspend fun showModelsForProvider(
        token: String,
        chatId: Long,
        messageId: Long,
        providerName: String,
        page: Int = 0
    ) {
        val storageId = providerStorageId(providerName)
        val aliasKeys = mutableListOf(storageId)
        if (storageId.contains("-")) aliasKeys.add(storageId.replace("-", ""))
        if (storageId == "cerebras") aliasKeys.add("cerebrus")
        if (storageId == "cerebrus") aliasKeys.add("cerebras")
        if (storageId == "gemini") aliasKeys.add("google gemini")
        if (storageId == "gmi") aliasKeys.add("gmi-cloud")
        if (storageId == "gmi-cloud") aliasKeys.add("gmi")
        if (storageId == "aimlapi") aliasKeys.add("aiml-api")
        if (storageId == "nebius") aliasKeys.add("nebius-ai")
        if (storageId == "friendliai") aliasKeys.add("friendli-ai")
        if (storageId == "together") aliasKeys.add("together-ai")
        if (storageId == "fireworks") aliasKeys.add("fireworks-ai")
        if (storageId == "nvidia") aliasKeys.add("nvidia-nim")
        if (storageId == "tokenharbor") aliasKeys.add("token-harbor")
        if (storageId == "token-harbor") aliasKeys.add("tokenharbor")

        var apiKey = ""
        for (k in aliasKeys) {
            val candidate = ApiKeyRotator.getNextAvailableKey(repository.securePrefs, k)?.first
                ?: repository.securePrefs.getApiKey(k)
            if (candidate.isNotEmpty()) {
                apiKey = candidate
                break
            }
        }
        if (apiKey.isEmpty() && providerName.contains("Zen", ignoreCase = true)) {
            apiKey = repository.securePrefs.getApiKey("zen")
        }
        val baseUrl = providerDefaultBaseUrl(providerName)

        // Live Model Fetching — check cached models first, then fetch live if key is present
        var dynamicModels = ModelCatalog.getModelsForProvider(providerName, repository.securePrefs)
        if (dynamicModels.isEmpty() && (apiKey.isNotEmpty() || providerName.contains("Zen", ignoreCase = true))) {
            val fetched: List<AIModel> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (providerName == "Antigravity") {
                    val oauthToken = repository.securePrefs.getSetting("oauth_token_antigravity", "").split("||")[0]
                    if (oauthToken.isNotEmpty()) fetchAntigravityModels(oauthToken) else emptyList<AIModel>()
                } else {
                    fetchModels(apiKey, baseUrl, providerName)
                }
            }
            if (fetched.isNotEmpty()) {
                ModelCatalog.setModels(providerName, fetched, repository.securePrefs)
                dynamicModels = fetched
            }
        }

        val providerObj: AIProvider? = AIProviderFactory.providers.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
            ?: (OPENAI_PROVIDERS.find { it.name.equals(providerName, ignoreCase = true) }?.let { GenericOpenAIProvider(it) })
        val isFree = providerObj?.isFree == true || providerName.contains("Zen", ignoreCase = true)
        val hasCreds = hasProviderCredentials(storageId, isFree)

        if (!hasCreds && !isFree) {
            val msg = "⚠️ <b>${providerName} requires an API key</b>\n\n" +
                "You haven't configured an API key for <b>${providerName}</b> yet.\n\n" +
                "👉 Open DeepCode app: <b>Settings → API Keys → ${providerName}</b> to add your key, or choose another provider."
            val backRow = JsonArray().apply {
                val backBtn = JsonObject().apply {
                    addProperty("text", "« Back to providers")
                    addProperty("callback_data", "back_to_providers")
                }
                add(backBtn)
            }
            val replyMarkup = JsonObject().apply {
                add("inline_keyboard", JsonArray().apply { add(backRow) })
            }
            editMessageWithKeyboard(token, chatId, messageId, msg, replyMarkup, parseMode = "HTML")
            return
        }

        val staticModels = providerObj?.models ?: emptyList()
        val allAvailableModels = (dynamicModels + staticModels).associateBy { it.id }.values.toList()

        // Filter models by in-app user selection if configured!
        val selectedIdsStr = repository.securePrefs.getSetting("selected_models_$storageId", "")
        val allModels = if (selectedIdsStr.isNotBlank()) {
            val selectedIdSet = selectedIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            allAvailableModels.filter { it.id in selectedIdSet }
        } else {
            allAvailableModels
        }

        if (allModels.isEmpty()) {
            val msg = if (apiKey.isEmpty() && !providerName.contains("Zen", ignoreCase = true)) {
                "⚠️ *${providerName}* requires an API key.\n\nPlease enter your API key in the DeepCode app to view and use models for this provider."
            } else {
                "🤖 *${providerName} Models*\n\nNo models found or allowed. Please configure your models in the DeepCode app."
            }
            val backRow = JsonArray().apply {
                val backBtn = JsonObject().apply {
                    addProperty("text", "« Back to providers")
                    addProperty("callback_data", "back_to_providers")
                }
                add(backBtn)
            }
            val replyMarkup = JsonObject().apply {
                add("inline_keyboard", JsonArray().apply { add(backRow) })
            }
            editMessageWithKeyboard(token, chatId, messageId, msg, replyMarkup)
            return
        }

        val savedId = getSavedModelForChat(chatId)
        val savedProvider = getSavedProviderForChat(chatId)
        val currentModel = allModels.firstOrNull { it.id == savedId && savedProvider == providerName }

        val pageSize = 10
        val totalPages = ((allModels.size - 1) / pageSize) + 1
        val currentPage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        val pagedModels = allModels.drop(currentPage * pageSize).take(pageSize)

        val header = buildString {
            append("🤖 *${providerName} Models* (${allModels.size} available")
            if (selectedIdsStr.isNotBlank()) append(" · Selected in-app")
            if (totalPages > 1) append(" · Page ${currentPage + 1}/$totalPages")
            append(")\n\n")
            if (currentModel != null) {
                append("Current: *${currentModel.name}*\n\n")
            } else {
                append("Select any model below to activate:\n\n")
            }
            append("Tap a model to switch:")
        }

        val rows = JsonArray()
        for (model in pagedModels) {
            val row = JsonArray()
            val btn = JsonObject()
            val isSelected = model.id == savedId && savedProvider == providerName
            val isModelFree = model.isFree || model.name.contains("(Free)", ignoreCase = true)
            val baseName = model.name.replace("(Free)", "").replace("(free)", "").trim()
            val label = if (isSelected) "✓ $baseName" else if (isModelFree) "$baseName (Free)" else baseName
            btn.addProperty("text", label)

            val fullCallback = "select_model:${providerName}:${model.id}"
            val callbackData = if (fullCallback.toByteArray(Charsets.UTF_8).size <= 64) {
                fullCallback
            } else {
                val shortKey = "sm:" + getShortModelKey(providerName, model.id)
                modelCallbackRegistry[shortKey] = providerName to model.id
                shortKey
            }
            btn.addProperty("callback_data", callbackData)
            row.add(btn)
            rows.add(row)
        }

        // Pagination controls if > 1 page
        if (totalPages > 1) {
            val navRow = JsonArray()
            if (currentPage > 0) {
                val prevBtn = JsonObject()
                prevBtn.addProperty("text", "⬅️ Prev")
                prevBtn.addProperty("callback_data", "models_page:${providerName}:${currentPage - 1}")
                navRow.add(prevBtn)
            }
            val pageIndBtn = JsonObject()
            pageIndBtn.addProperty("text", "${currentPage + 1} / $totalPages")
            pageIndBtn.addProperty("callback_data", "models_page:${providerName}:${currentPage}")
            navRow.add(pageIndBtn)
            if (currentPage < totalPages - 1) {
                val nextBtn = JsonObject()
                nextBtn.addProperty("text", "Next ➡️")
                nextBtn.addProperty("callback_data", "models_page:${providerName}:${currentPage + 1}")
                navRow.add(nextBtn)
            }
            rows.add(navRow)
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
            callbackData == "tasks_list" -> {
                showTasksList(token, chatId, messageId)
                answerCallbackQuery(token, callbackId, "Refreshed tasks")
                AppLogger.d(TAG, "handleCallbackQuery finish (tasks_list).")
            }
            callbackData.startsWith("task_output:") -> {
                val taskId = callbackData.removePrefix("task_output:")
                showTaskOutput(token, chatId, taskId, messageId)
                answerCallbackQuery(token, callbackId, "Loading output...")
                AppLogger.d(TAG, "handleCallbackQuery finish (task_output).")
            }
            callbackData.startsWith("task_manage:") -> {
                val taskId = callbackData.removePrefix("task_manage:")
                showTaskManage(token, chatId, taskId, messageId)
                answerCallbackQuery(token, callbackId, "Task settings")
                AppLogger.d(TAG, "handleCallbackQuery finish (task_manage).")
            }
            callbackData.startsWith("task_run:") -> {
                val taskId = callbackData.removePrefix("task_run:")
                val repo = AutomationRepository(applicationContext)
                val task = repo.getAutomationById(taskId)
                if (task != null) {
                    answerCallbackQuery(token, callbackId, "⚡ Running ${task.name}...")
                    sendMessage(token, chatId, "⏳ Executing <b>${task.name}</b> now...", parseMode = "HTML")
                    serviceScope.launch {
                        try {
                            AutomationRunner.executeAutomation(applicationContext, task.id, forceRun = true)
                            showTaskOutput(token, chatId, task.id)
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed running automation $taskId", e)
                            sendMessage(token, chatId, "❌ Error executing task: ${e.message}")
                        }
                    }
                } else {
                    answerCallbackQuery(token, callbackId, "Task not found")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish (task_run).")
            }
            callbackData.startsWith("task_toggle_tg:") -> {
                val taskId = callbackData.removePrefix("task_toggle_tg:")
                val repo = AutomationRepository(applicationContext)
                val task = repo.getAutomationById(taskId)
                if (task != null) {
                    val configObj = try {
                        Gson().fromJson(task.configJson, JsonObject::class.java)
                    } catch (_: Exception) { JsonObject() }
                    val currentTg = configObj.get("telegram_chat_id")?.asString
                    val isCurrentlyOn = currentTg == chatId.toString()
                    val newTgOn = !isCurrentlyOn
                    if (newTgOn) {
                        configObj.addProperty("telegram_chat_id", chatId.toString())
                    } else {
                        configObj.remove("telegram_chat_id")
                    }
                    val updated = task.copy(configJson = Gson().toJson(configObj))
                    repo.insertAutomation(updated)
                    val toast = if (newTgOn) "🔔 Telegram auto-forward enabled!" else "🔕 Telegram auto-forward disabled."
                    answerCallbackQuery(token, callbackId, toast)
                    showTaskManage(token, chatId, taskId, messageId)
                } else {
                    answerCallbackQuery(token, callbackId, "Task not found")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish (task_toggle_tg).")
            }
            callbackData.startsWith("task_toggle_status:") -> {
                val taskId = callbackData.removePrefix("task_toggle_status:")
                val repo = AutomationRepository(applicationContext)
                val task = repo.getAutomationById(taskId)
                if (task != null) {
                    val newStatus = !task.isEnabled
                    repo.updateEnabledStatus(taskId, newStatus)
                    val scheduler = AutomationScheduler(applicationContext)
                    if (newStatus) {
                        scheduler.schedule(task.copy(isEnabled = true), forceRecalculate = false)
                        answerCallbackQuery(token, callbackId, "▶️ Task resumed!")
                    } else {
                        scheduler.cancel(taskId)
                        answerCallbackQuery(token, callbackId, "⏸️ Task paused!")
                    }
                    showTaskManage(token, chatId, taskId, messageId)
                } else {
                    answerCallbackQuery(token, callbackId, "Task not found")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish (task_toggle_status).")
            }
            callbackData == "back_to_providers" -> {
                showProviderSelection(token, chatId, messageId)
                answerCallbackQuery(token, callbackId, "")
                AppLogger.d(TAG, "handleCallbackQuery finish (back).")
            }
            callbackData.startsWith("select_provider:") -> {
                val providerName = callbackData.removePrefix("select_provider:")
                val providerObj: AIProvider? = AIProviderFactory.providers.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: (OPENAI_PROVIDERS.find { it.name.equals(providerName, ignoreCase = true) }?.let { GenericOpenAIProvider(it) })
                val dynamicModels = ModelCatalog.getModelsForProvider(providerName, repository.securePrefs)
                val allProviderModels = (dynamicModels + (providerObj?.models ?: emptyList())).associateBy { it.id }.values.toList()
                val storageId = providerStorageId(providerName)
                val defaultModelSetting = repository.securePrefs.getSetting("default_model_$storageId", "")
                var defaultModelId = if (defaultModelSetting.isNotEmpty() && allProviderModels.any { it.id == defaultModelSetting }) {
                    defaultModelSetting
                } else {
                    if (providerName.contains("Zen", ignoreCase = true)) {
                        allProviderModels.firstOrNull { it.id == ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE }?.id
                            ?: allProviderModels.firstOrNull { it.id == "ling-3.0-flash-fin-free" }?.id
                            ?: allProviderModels.firstOrNull { it.isFree }?.id
                            ?: ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE
                    } else {
                        allProviderModels.firstOrNull { it.isFree }?.id
                            ?: allProviderModels.firstOrNull()?.id
                            ?: ""
                    }
                }
                if (providerName.contains("Zen", ignoreCase = true)) {
                    defaultModelId = ai.deepcode.android.data.remote.ZenModels.sanitize(defaultModelId, allProviderModels)
                }
                repository.securePrefs.saveSetting("tg_provider_$chatId", providerName)
                if (defaultModelId.isNotEmpty()) {
                    repository.securePrefs.saveSetting("tg_model_$chatId", defaultModelId)
                }
                showModelsForProvider(token, chatId, messageId, providerName)
                answerCallbackQuery(token, callbackId, "Switched to $providerName")
                AppLogger.d(TAG, "handleCallbackQuery finish (provider switched to $providerName).")
            }
            callbackData.startsWith("models_page:") -> {
                val parts = callbackData.removePrefix("models_page:").split(":")
                val providerName = parts.getOrNull(0) ?: "Zen AI"
                val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
                showModelsForProvider(token, chatId, messageId, providerName, page)
                answerCallbackQuery(token, callbackId, "Page ${page + 1}")
                AppLogger.d(TAG, "handleCallbackQuery finish (models_page).")
            }
            callbackData.startsWith("sm:") -> {
                val hash = callbackData.removePrefix("sm:")
                val resolved = resolveModelFromHash(hash)
                if (resolved != null) {
                    handleSelectModel(token, chatId, messageId, callbackId, resolved.first, resolved.second)
                } else {
                    answerCallbackQuery(token, callbackId, "Selection expired. Please open /models again.")
                }
                AppLogger.d(TAG, "handleCallbackQuery finish (sm).")
            }
            callbackData.startsWith("select_model:") -> {
                val parts = callbackData.removePrefix("select_model:").split(":", limit = 2)
                val providerName = if (parts.size == 2) parts[0] else null
                val modelId = if (parts.size == 2) parts[1] else parts[0]
                handleSelectModel(token, chatId, messageId, callbackId, providerName, modelId)
                AppLogger.d(TAG, "handleCallbackQuery finish (select_model).")
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

    private suspend fun handleSelectModel(
        token: String,
        chatId: Long,
        messageId: Long,
        callbackId: String,
        providerName: String?,
        modelId: String
    ) {
        AppLogger.d(TAG, "Selected model ID: $modelId from provider: $providerName")
        saveModelForChat(chatId, modelId, providerName)
        val model = ModelCatalog.models.value[providerName]?.firstOrNull { it.id == modelId }
            ?: AIProviderFactory.providers.flatMap { it.models }.firstOrNull { it.id == modelId && (providerName == null || it.provider == providerName) }
        val rawName = model?.name ?: formatModelTitle(modelId)
        val name = rawName.replace("(Free)", "").replace("(free)", "").trim()
        val providerLabel = if (providerName != null) " ($providerName)" else ""

        val effectiveProvider = providerName
            ?: inferProviderForModel(modelId)
            ?: "Zen AI"
        val isFree = AIProviderFactory.providers.firstOrNull { it.name.equals(effectiveProvider, ignoreCase = true) }?.isFree == true ||
            effectiveProvider.contains("Zen", ignoreCase = true)
        val storageId = providerStorageId(effectiveProvider)
        val hasCreds = hasProviderCredentials(storageId, isFree)

        val warning = if (!hasCreds && !isFree) {
            "\n\n⚠️ <b>Note:</b> No API key configured for $effectiveProvider. Please add your key in DeepCode app (Settings → API Keys → $effectiveProvider) before chatting."
        } else ""

        val escapedName = TelegramFormatter.escapeHtml(name)
        val escapedLabel = TelegramFormatter.escapeHtml(providerLabel)
        AppLogger.d(TAG, "Editing bot selection message to model name: $name")
        editMessage(token, chatId, messageId, "✅ Switched to <b>$escapedName</b>$escapedLabel!$warning\n\nSend a message to chat with this model.", "HTML")
        AppLogger.d(TAG, "Sending answerCallbackQuery response...")
        answerCallbackQuery(token, callbackId, "Model switched to $name")
        AppLogger.d(TAG, "handleSelectModel finish.")
    }

    private fun stripThoughts(text: String): String {
        var cleaned = text.replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<thought>[\\s\\S]*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</thought>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("<think>[\\s\\S]*", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("</think>", RegexOption.IGNORE_CASE), "")
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

    private fun sendMessage(token: String, chatId: Long, text: String, parseMode: String = "HTML"): Long? {
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (parseMode.isNotEmpty() && (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>")))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "HTML") TelegramFormatter.formatMarkdownToTelegramHtml(cleanText) else preprocessMarkdown(cleanText)

        if (targetParseMode == "HTML" && processed.length > 3900) {
            val chunks = TelegramFormatter.chunkTelegramHtml(processed, maxLen = 3900)
            var lastId: Long? = null
            for (chunk in chunks) {
                lastId = sendSingleRawMessage(token, chatId, chunk, "HTML", cleanText)
            }
            return lastId
        } else if (processed.length > 3900) {
            val chunks = processed.chunked(3900)
            var lastId: Long? = null
            for (chunk in chunks) {
                lastId = sendSingleRawMessage(token, chatId, chunk, targetParseMode, cleanText)
            }
            return lastId
        }
        return sendSingleRawMessage(token, chatId, processed, targetParseMode, cleanText)
    }

    private fun sendSingleRawMessage(token: String, chatId: Long, processedText: String, targetParseMode: String, cleanText: String): Long? {
        return try {
            val url = "${API_BASE}${token}/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", processedText)
                if (targetParseMode.isNotEmpty()) {
                    addProperty("parse_mode", targetParseMode)
                }
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
                    if (targetParseMode.isNotEmpty()) {
                        shouldRetry = true
                    }
                }
            }

            if (shouldRetry) {
                AppLogger.d(TAG, "Retrying sendMessage without parse_mode for chat $chatId")
                val plainFallback = TelegramFormatter.stripHtml(cleanText).take(3900)
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("text", plainFallback)
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

    private fun sendChatAction(token: String, chatId: Long, action: String = "typing") {
        try {
            val url = "${API_BASE}${token}/sendChatAction"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("action", action)
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun editMessage(token: String, chatId: Long, messageId: Long, text: String, parseMode: String = "HTML"): Boolean {
        AppLogger.d(TAG, "editMessage entry: messageId=$messageId, text.length=${text.length}, parseMode=$parseMode")
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (parseMode.isNotEmpty() && (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>")))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "HTML") TelegramFormatter.formatMarkdownToTelegramHtml(cleanText) else preprocessMarkdown(cleanText)
        try {
            val url = "${API_BASE}${token}/editMessageText"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
                addProperty("text", processed)
                if (targetParseMode.isNotEmpty()) {
                    addProperty("parse_mode", targetParseMode)
                }
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()

            var succeeded = false
            var shouldRetry = false
            AppLogger.d(TAG, "Executing editMessage HTTP POST request...")
            client.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string() ?: ""
                AppLogger.d(TAG, "editMessage HTTP response received: code=$code")
                if (response.isSuccessful) {
                    AppLogger.d(TAG, "editMessage succeeded.")
                    succeeded = true
                } else {
                    AppLogger.e(TAG, "editMessage failed (code $code): $body")
                    if (code == 400 && body.contains("message is not modified", ignoreCase = true)) {
                        succeeded = true
                    } else if (targetParseMode.isNotEmpty()) {
                        shouldRetry = true
                    }
                }
            }

            if (succeeded) return true

            if (shouldRetry && targetParseMode.isNotEmpty()) {
                AppLogger.d(TAG, "Retrying editMessage without parse_mode for chat $chatId, message $messageId")
                val plainFallback = TelegramFormatter.stripHtml(cleanText)
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("message_id", messageId)
                    addProperty("text", plainFallback)
                }
                val retryRequest = Request.Builder()
                    .url(url)
                    .post(gson.toJson(retryPayload).toRequestBody(jsonMediaType))
                    .build()
                client.newCall(retryRequest).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string() ?: ""
                    AppLogger.d(TAG, "editMessage retry response: code=$code")
                    if (response.isSuccessful || (code == 400 && body.contains("message is not modified", ignoreCase = true))) {
                        AppLogger.d(TAG, "editMessage retry succeeded.")
                        return true
                    } else {
                        AppLogger.e(TAG, "editMessage retry failed: $body")
                        return false
                    }
                }
            }
            return false
        } catch (e: Exception) {
            AppLogger.e(TAG, "editMessage error", e)
            return false
        }
    }

    private fun editMessageWithKeyboard(
        token: String,
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: JsonObject,
        parseMode: String = "HTML"
    ) {
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>"))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "HTML") TelegramFormatter.formatMarkdownToTelegramHtml(cleanText) else preprocessMarkdown(cleanText)
        try {
            val url = "${API_BASE}${token}/editMessageText"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("message_id", messageId)
                addProperty("text", processed)
                if (targetParseMode.isNotEmpty()) {
                    addProperty("parse_mode", targetParseMode)
                }
                add("reply_markup", Gson().toJsonTree(replyMarkup))
            }
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string()
                    AppLogger.e(TAG, "editMessageWithKeyboard failed: $body")
                    if (targetParseMode.isNotEmpty() && body != null && (body.contains("can't parse entities", ignoreCase = true) || body.contains("parse_mode", ignoreCase = true))) {
                        val plainFallback = TelegramFormatter.stripHtml(cleanText)
                        val retryPayload = JsonObject().apply {
                            addProperty("chat_id", chatId)
                            addProperty("message_id", messageId)
                            addProperty("text", plainFallback)
                            add("reply_markup", Gson().toJsonTree(replyMarkup))
                        }
                        val retryRequest = Request.Builder()
                            .url(url)
                            .post(gson.toJson(retryPayload).toRequestBody(jsonMediaType))
                            .build()
                        client.newCall(retryRequest).execute().close()
                    }
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

    private fun buildSafeCaptionParts(caption: String?): Pair<String?, String?> {
        if (caption.isNullOrBlank()) return Pair(null, null)
        val clean = stripThoughts(caption)
        val formatted = TelegramFormatter.formatMarkdownToTelegramHtml(clean)
        return if (formatted.length <= 1024) {
            Pair(formatted, null)
        } else {
            Pair(formatted.take(1020) + "...", clean)
        }
    }

    private fun sendAudio(token: String, chatId: Long, audioFile: File, caption: String? = null): Boolean {
        try {
            val (safeCaption, overflow) = buildSafeCaptionParts(caption)
            val url = "${API_BASE}${token}/sendAudio"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("audio", audioFile.name, audioFile.asRequestBody(null))
                .apply {
                    safeCaption?.let {
                        addFormDataPart("caption", it)
                        addFormDataPart("parse_mode", "HTML")
                    }
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            var success = false
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    success = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                } else {
                    AppLogger.e(TAG, "sendAudio failed: $body")
                }
            }
            if (success) {
                if (overflow != null) {
                    sendMessage(token, chatId, overflow)
                }
                return true
            }

            AppLogger.w(TAG, "sendAudio failed, falling back to sendDocument for: ${audioFile.absolutePath}")
            return sendDocument(token, chatId, audioFile, caption)
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendAudio error", e)
            return false
        }
    }

    private fun sendPhoto(token: String, chatId: Long, photoUrlOrPath: String, caption: String? = null): Boolean {
        try {
            val (safeCaption, overflow) = buildSafeCaptionParts(caption)
            val cleanPath = photoUrlOrPath.removePrefix("file://").trim()
            val localFile = File(cleanPath)
            if (localFile.exists() && localFile.isFile) {
                val url = "${API_BASE}${token}/sendPhoto"
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("photo", localFile.name, localFile.asRequestBody(null))
                    .apply {
                        safeCaption?.let {
                            addFormDataPart("caption", it)
                            addFormDataPart("parse_mode", "HTML")
                        }
                    }
                    .build()
                val request = Request.Builder().url(url).post(requestBody).build()
                var success = false
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        success = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                    } else {
                        AppLogger.e(TAG, "sendPhoto (multipart) failed: $body")
                    }
                }
                if (success) {
                    if (overflow != null) {
                        sendMessage(token, chatId, overflow)
                    }
                    return true
                }

                AppLogger.w(TAG, "sendPhoto failed, falling back to sendDocument for: $cleanPath")
                return sendDocument(token, chatId, localFile, caption)
            } else if (photoUrlOrPath.startsWith("http://", ignoreCase = true) || photoUrlOrPath.startsWith("https://", ignoreCase = true)) {
                val url = "${API_BASE}${token}/sendPhoto"
                val payload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("photo", photoUrlOrPath)
                    safeCaption?.let {
                        addProperty("caption", it)
                        addProperty("parse_mode", "HTML")
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                    .build()
                var remoteSuccess = false
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        remoteSuccess = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                    } else {
                        AppLogger.w(TAG, "sendPhoto (URL) failed: $body, will attempt download & upload")
                    }
                }
                if (remoteSuccess) {
                    if (overflow != null) {
                        sendMessage(token, chatId, overflow)
                    }
                    return true
                }

                try {
                    val downloadReq = Request.Builder().url(photoUrlOrPath).build()
                    val downloadedFile = client.newCall(downloadReq).execute().use { dlResp ->
                        if (dlResp.isSuccessful && dlResp.body != null) {
                            val tempFile = File.createTempFile("tg_photo_", ".png", cacheDir)
                            tempFile.outputStream().use { out ->
                                dlResp.body!!.byteStream().copyTo(out)
                            }
                            tempFile
                        } else null
                    }
                    if (downloadedFile != null && downloadedFile.exists()) {
                        try {
                            val mpUrl = "${API_BASE}${token}/sendPhoto"
                            val mpBody = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chat_id", chatId.toString())
                                .addFormDataPart("photo", downloadedFile.name, downloadedFile.asRequestBody(null))
                                .apply {
                                    safeCaption?.let {
                                        addFormDataPart("caption", it)
                                        addFormDataPart("parse_mode", "HTML")
                                    }
                                }
                                .build()
                            val mpReq = Request.Builder().url(mpUrl).post(mpBody).build()
                            var mpSuccess = false
                            client.newCall(mpReq).execute().use { resp ->
                                val body = resp.body?.string()
                                if (resp.isSuccessful) {
                                    mpSuccess = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                                }
                            }
                            if (mpSuccess) {
                                if (overflow != null) {
                                    sendMessage(token, chatId, overflow)
                                }
                                return true
                            }
                            return sendDocument(token, chatId, downloadedFile, caption)
                        } finally {
                            downloadedFile.delete()
                        }
                    }
                } catch (dlEx: Exception) {
                    AppLogger.e(TAG, "Failed to download and upload remote photo: ${dlEx.message}")
                }
            } else {
                AppLogger.e(TAG, "sendPhoto: file does not exist or URL is invalid: $photoUrlOrPath")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendPhoto error", e)
        }
        return false
    }

    private fun sendVideo(token: String, chatId: Long, videoUrlOrPath: String, caption: String? = null): Boolean {
        try {
            val (safeCaption, overflow) = buildSafeCaptionParts(caption)
            val cleanPath = videoUrlOrPath.removePrefix("file://").trim()
            val localFile = File(cleanPath)
            if (localFile.exists() && localFile.isFile) {
                val url = "${API_BASE}${token}/sendVideo"
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId.toString())
                    .addFormDataPart("video", localFile.name, localFile.asRequestBody(null))
                    .apply {
                        safeCaption?.let {
                            addFormDataPart("caption", it)
                            addFormDataPart("parse_mode", "HTML")
                        }
                    }
                    .build()
                val request = Request.Builder().url(url).post(requestBody).build()
                var success = false
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        success = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                    } else {
                        AppLogger.e(TAG, "sendVideo (multipart) failed: $body")
                    }
                }
                if (success) {
                    if (overflow != null) {
                        sendMessage(token, chatId, overflow)
                    }
                    return true
                }

                AppLogger.w(TAG, "sendVideo failed, falling back to sendDocument for: $cleanPath")
                return sendDocument(token, chatId, localFile, caption)
            } else if (videoUrlOrPath.startsWith("http://", ignoreCase = true) || videoUrlOrPath.startsWith("https://", ignoreCase = true)) {
                val url = "${API_BASE}${token}/sendVideo"
                val payload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("video", videoUrlOrPath)
                    safeCaption?.let {
                        addProperty("caption", it)
                        addProperty("parse_mode", "HTML")
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                    .build()
                var remoteSuccess = false
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        remoteSuccess = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                    } else {
                        AppLogger.w(TAG, "sendVideo (URL) failed: $body, will attempt download & upload")
                    }
                }
                if (remoteSuccess) {
                    if (overflow != null) {
                        sendMessage(token, chatId, overflow)
                    }
                    return true
                }

                try {
                    val downloadReq = Request.Builder().url(videoUrlOrPath).build()
                    val downloadedFile = client.newCall(downloadReq).execute().use { dlResp ->
                        if (dlResp.isSuccessful && dlResp.body != null) {
                            val tempFile = File.createTempFile("tg_video_", ".mp4", cacheDir)
                            tempFile.outputStream().use { out ->
                                dlResp.body!!.byteStream().copyTo(out)
                            }
                            tempFile
                        } else null
                    }
                    if (downloadedFile != null && downloadedFile.exists()) {
                        try {
                            val mpUrl = "${API_BASE}${token}/sendVideo"
                            val mpBody = MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("chat_id", chatId.toString())
                                .addFormDataPart("video", downloadedFile.name, downloadedFile.asRequestBody(null))
                                .apply {
                                    safeCaption?.let {
                                        addFormDataPart("caption", it)
                                        addFormDataPart("parse_mode", "HTML")
                                    }
                                }
                                .build()
                            val mpReq = Request.Builder().url(mpUrl).post(mpBody).build()
                            var mpSuccess = false
                            client.newCall(mpReq).execute().use { resp ->
                                val body = resp.body?.string()
                                if (resp.isSuccessful) {
                                    mpSuccess = try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                                }
                            }
                            if (mpSuccess) {
                                if (overflow != null) {
                                    sendMessage(token, chatId, overflow)
                                }
                                return true
                            }
                            return sendDocument(token, chatId, downloadedFile, caption)
                        } finally {
                            downloadedFile.delete()
                        }
                    }
                } catch (dlEx: Exception) {
                    AppLogger.e(TAG, "Failed to download and upload remote video: ${dlEx.message}")
                }
            } else {
                AppLogger.e(TAG, "sendVideo: file does not exist or URL is invalid: $videoUrlOrPath")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendVideo error", e)
        }
        return false
    }

    private fun sendDocument(token: String, chatId: Long, file: File, caption: String? = null): Boolean {
        try {
            val (safeCaption, overflow) = buildSafeCaptionParts(caption)
            val url = "${API_BASE}${token}/sendDocument"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId.toString())
                .addFormDataPart("document", file.name, file.asRequestBody(null))
                .apply {
                    safeCaption?.let {
                        addFormDataPart("caption", it)
                        addFormDataPart("parse_mode", "HTML")
                    }
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            val success = client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    try { gson.fromJson(body, JsonObject::class.java).get("ok")?.asBoolean == true } catch (_: Exception) { false }
                } else {
                    AppLogger.e(TAG, "sendDocument failed: $body")
                    false
                }
            }
            if (success && overflow != null) {
                sendMessage(token, chatId, overflow)
            }
            return success
        } catch (e: Exception) {
            AppLogger.e(TAG, "sendDocument error", e)
            return false
        }
    }

    private fun sendMessageWithKeyboard(token: String, chatId: Long, text: String, replyMarkup: JsonObject, parseMode: String = "HTML"): Long? {
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (parseMode.isNotEmpty() && (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>")))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "HTML") TelegramFormatter.formatMarkdownToTelegramHtml(cleanText) else preprocessMarkdown(cleanText)
        return try {
            val url = "${API_BASE}${token}/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", processed)
                if (targetParseMode.isNotEmpty()) {
                    addProperty("parse_mode", targetParseMode)
                }
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
                AppLogger.d(TAG, "Retrying sendMessageWithKeyboard without parse_mode for chat $chatId")
                val plainFallback = TelegramFormatter.stripHtml(cleanText)
                val retryPayload = JsonObject().apply {
                    addProperty("chat_id", chatId)
                    addProperty("text", plainFallback)
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
