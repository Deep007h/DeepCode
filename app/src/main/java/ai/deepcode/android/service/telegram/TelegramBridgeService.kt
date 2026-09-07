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
                val zenModels = fetchModels("zen-free", providerDefaultBaseUrl("Zen AI"), "Zen AI")
                if (zenModels.isNotEmpty()) {
                    ModelCatalog.setModels("Zen AI", zenModels)
                    AppLogger.i(TAG, "Prewarmed ${zenModels.size} live Zen AI models")
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
        if (isFree || storageKey == "zen") return true
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
            ?: "Zen AI"
        repository.securePrefs.saveSetting("tg_provider_$chatId", prov)
        repository.securePrefs.saveSetting("agent_provider", prov)
        repository.securePrefs.saveSetting("agent_model", modelId)
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
        val effectiveProvider = savedProviderName ?: inferProviderForModel(savedModelId ?: "") ?: "Zen AI"

        val providerObj: AIProvider? = AIProviderFactory.providers.firstOrNull { it.name.equals(effectiveProvider, ignoreCase = true) }
            ?: (OPENAI_PROVIDERS.find { it.name.equals(effectiveProvider, ignoreCase = true) }?.let { GenericOpenAIProvider(it) })
        val dynamicModels = ModelCatalog.getModelsForProvider(effectiveProvider, repository.securePrefs)
        val allProviderModels = (dynamicModels + (providerObj?.models ?: emptyList())).associateBy { it.id }.values.toList()

        var effectiveModelId = savedModelId ?: ""
        if (effectiveModelId.isEmpty() || (allProviderModels.isNotEmpty() && allProviderModels.none { it.id == effectiveModelId })) {
            val storageId = providerStorageId(effectiveProvider)
            val defaultModelSetting = repository.securePrefs.getSetting("default_model_$storageId", "")
            effectiveModelId = if (defaultModelSetting.isNotEmpty() && allProviderModels.any { it.id == defaultModelSetting }) {
                defaultModelSetting
            } else {
                allProviderModels.firstOrNull { it.isFree }?.id
                    ?: allProviderModels.firstOrNull()?.id
                    ?: (if (effectiveProvider.contains("Zen", ignoreCase = true)) "deepseek-v4-flash-free" else effectiveModelId)
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

        val isProviderFree = AIProviderFactory.providers.firstOrNull { it.name.equals(effectiveProvider, ignoreCase = true) }?.isFree == true ||
            effectiveProvider.contains("Zen", ignoreCase = true)
        val storageKey = providerStorageId(effectiveProvider)

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

            // Fall back to the main AI agent with live streaming to Telegram
            if (finalResponse.isEmpty()) {
                if (!isProviderFree && !hasProviderCredentials(storageKey, false)) {
                    val modelTitle = modelInfo?.name ?: formatModelTitle(effectiveModelId)
                    val missingKeyMsg = "⚠️ <b>$effectiveProvider API Key Required</b>\n\n" +
                        "Your chat is currently set to use <b>$modelTitle</b> ($effectiveProvider), but no API key is configured.\n\n" +
                        "👉 Please add your key in DeepCode app (<b>Settings → API Keys → $effectiveProvider</b>), or switch to a free model with /models."
                    if (processingMsgId != null) {
                        editMessage(token, chatId, processingMsgId, missingKeyMsg, parseMode = "HTML")
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

                                // Live progressive edit to Telegram message (throttled every 1.2s to comply with Telegram rate limits)
                                if (processingMsgId != null) {
                                    val now = System.currentTimeMillis()
                                    val cleanSoFar = stripThoughts(statusOrText).trim()
                                    if (cleanSoFar.isNotEmpty() && cleanSoFar.length > lastEditedText.length + 6 && (now - lastEditTime > 1200)) {
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
            if (processingMsgId != null) {
                editMessage(token, chatId, processingMsgId, errorMsg, parseMode = "HTML")
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
                sendMessage(token, chatId, "Unknown command. Try /models, /voice, /language, /persona, /start, or /help")
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
                append("Current: *DeepSeek V4 Flash* (Zen AI - Free)\n\n")
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
            apiKey = "zen-free"
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
                val defaultModelId = if (defaultModelSetting.isNotEmpty() && allProviderModels.any { it.id == defaultModelSetting }) {
                    defaultModelSetting
                } else {
                    allProviderModels.firstOrNull { it.isFree }?.id
                        ?: allProviderModels.firstOrNull()?.id
                        ?: (if (providerName.contains("Zen", ignoreCase = true)) "deepseek-v4-flash-free" else "")
                }
                repository.securePrefs.saveSetting("tg_provider_$chatId", providerName)
                repository.securePrefs.saveSetting("agent_provider", providerName)
                if (defaultModelId.isNotEmpty()) {
                    repository.securePrefs.saveSetting("tg_model_$chatId", defaultModelId)
                    repository.securePrefs.saveSetting("agent_model", defaultModelId)
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
            "\n\n⚠️ *Note:* No API key configured for $effectiveProvider. Please add your key in DeepCode app (Settings → API Keys → $effectiveProvider) before chatting."
        } else ""

        AppLogger.d(TAG, "Editing bot selection message to model name: $name")
        editMessage(token, chatId, messageId, "✅ Switched to *$name*$providerLabel!$warning\n\nSend a message to chat with this model.", "Markdown")
        AppLogger.d(TAG, "Sending answerCallbackQuery response...")
        answerCallbackQuery(token, callbackId, "Model switched to $name")
        AppLogger.d(TAG, "handleSelectModel finish.")
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

    private fun sendMessage(token: String, chatId: Long, text: String, parseMode: String = "Markdown"): Long? {
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (parseMode.isNotEmpty() && (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>")))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "Markdown") preprocessMarkdown(cleanText) else cleanText
        return try {
            val url = "${API_BASE}${token}/sendMessage"
            val payload = JsonObject().apply {
                addProperty("chat_id", chatId)
                addProperty("text", processed)
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

    private fun editMessage(token: String, chatId: Long, messageId: Long, text: String, parseMode: String = "Markdown") {
        AppLogger.d(TAG, "editMessage entry: messageId=$messageId, text.length=${text.length}, parseMode=$parseMode")
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (parseMode.isNotEmpty() && (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>")))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "Markdown") preprocessMarkdown(cleanText) else cleanText
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

            if (shouldRetry && targetParseMode.isNotEmpty()) {
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

    private fun editMessageWithKeyboard(
        token: String,
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: JsonObject,
        parseMode: String = "Markdown"
    ) {
        val cleanText = stripThoughts(text)
        val isHtml = parseMode.equals("HTML", ignoreCase = true) || (cleanText.contains("<b>") || cleanText.contains("</b>") || cleanText.contains("<code>"))
        val targetParseMode = if (isHtml) "HTML" else parseMode
        val processed = if (targetParseMode == "Markdown") preprocessMarkdown(cleanText) else cleanText
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
                        val retryPayload = JsonObject().apply {
                            addProperty("chat_id", chatId)
                            addProperty("message_id", messageId)
                            addProperty("text", cleanText)
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
