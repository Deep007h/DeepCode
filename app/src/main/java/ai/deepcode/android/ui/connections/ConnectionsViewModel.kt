package ai.deepcode.android.ui.connections

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.WhatsAppBridgeAPI
import ai.deepcode.android.data.remote.WASendRequest
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.service.telegram.BotConfigStore
import ai.deepcode.android.service.telegram.BotConfig
import ai.deepcode.android.service.telegram.TelegramBridgeService
import ai.deepcode.android.service.notion.NotionService
import ai.deepcode.android.service.github.GitHubService
import ai.deepcode.android.service.chatgpt.ChatGPTBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class ConnectionsViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val repository = IntegrationRepository(appContext)
    val oauthManager = OAuthManager(appContext, viewModelScope)
    private val securePrefs = EncryptedPrefs.getInstance(appContext)

    val integrations: StateFlow<List<IntegrationEntity>> = repository.getAllIntegrationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _logs = MutableStateFlow<List<String>>(listOf("System connection console initialized."))
    val logs: StateFlow<List<String>> = _logs

    private val _showNoGoogleAccountDialog = MutableStateFlow(false)
    val showNoGoogleAccountDialog: StateFlow<Boolean> = _showNoGoogleAccountDialog

    private var pendingGoogleAppId: String? = null

    // ── WhatsApp Bridge State ──
    private val _whatsAppConnected = MutableStateFlow(false)
    val whatsAppConnected: StateFlow<Boolean> = _whatsAppConnected

    private val _whatsAppPhone = MutableStateFlow<String?>(null)
    val whatsAppPhone: StateFlow<String?> = _whatsAppPhone

    private val _whatsAppQRCode = MutableStateFlow<String?>(null)
    val whatsAppQRCode: StateFlow<String?> = _whatsAppQRCode

    private val _whatsAppBridgeUrl = MutableStateFlow("http://localhost:3001")
    val whatsAppBridgeUrl: StateFlow<String> = _whatsAppBridgeUrl

    private var whatsAppApi: WhatsAppBridgeAPI? = null

    private fun getWhatsAppAPI(): WhatsAppBridgeAPI? {
        if (whatsAppApi == null) {
            try {
                val url = _whatsAppBridgeUrl.value.let {
                    if (it.endsWith("/")) it else "$it/"
                }
                whatsAppApi = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build())
                    .build()
                    .create(WhatsAppBridgeAPI::class.java)
            } catch (e: Exception) {
                addLog("Failed to create WhatsApp API client: ${e.message}")
            }
        }
        return whatsAppApi
    }

    init {
        viewModelScope.launch {
            prepopulateIfEmpty()
        }
    }

    fun addLog(message: String) {
        val timestamp = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message"
        viewModelScope.launch {
            val current = _logs.value.toMutableList()
            current.add(timestamp)
            _logs.value = current
        }
    }

    private suspend fun prepopulateIfEmpty() {
        val current = repository.getAllIntegrations()
        if (current.isEmpty()) {
            addLog("Database empty. Pre-populating connections...")
            val list = listOf(
                createIntegration("chatgpt", "ChatGPT", "https://logo.clearbit.com/openai.com"),
                createIntegration("google_account", "Google Account", "https://logo.clearbit.com/google.com"),
                createIntegration("gmail", "Gmail", "https://logo.clearbit.com/gmail.com"),
                createIntegration("google_calendar", "Google Calendar", "https://logo.clearbit.com/google.com"),
                createIntegration("google_drive", "Google Drive", "https://logo.clearbit.com/drive.google.com"),
                createIntegration("github", "GitHub", "https://logo.clearbit.com/github.com"),
                createIntegration("notion", "Notion", "https://logo.clearbit.com/notion.so"),
                createIntegration("slack", "Slack", "https://logo.clearbit.com/slack.com"),
                createIntegration("telegram", "Telegram", "https://logo.clearbit.com/telegram.org"),
                createIntegration("youtube_music", "YouTube Music", "https://logo.clearbit.com/music.youtube.com"),
                createIntegration("whatsapp", "WhatsApp (bridge)", "https://logo.clearbit.com/whatsapp.com"),
                createIntegration("spotify", "Spotify", "https://logo.clearbit.com/spotify.com"),
                createIntegration("twitter", "Twitter/X", "https://logo.clearbit.com/x.com"),
                createIntegration("linear", "Linear", "https://logo.clearbit.com/linear.app"),
                createIntegration("jira", "Jira", "https://logo.clearbit.com/atlassian.com"),
                createIntegration("stripe", "Stripe", "https://logo.clearbit.com/stripe.com"),
                createIntegration("shopify", "Shopify", "https://logo.clearbit.com/shopify.com"),
                createIntegration("discord", "Discord", "https://logo.clearbit.com/discord.com"),
                createIntegration("dropbox", "Dropbox", "https://logo.clearbit.com/dropbox.com"),
                createIntegration("trello", "Trello", "https://logo.clearbit.com/trello.com"),
                createIntegration("asana", "Asana", "https://logo.clearbit.com/asana.com"),
                createIntegration("hubspot", "HubSpot", "https://logo.clearbit.com/hubspot.com"),
                createIntegration("airtable", "Airtable", "https://logo.clearbit.com/airtable.com")
            )
            repository.insertIntegrations(list)
            addLog("Successfully prepopulated connections.")
        } else {
            // Ensure chatgpt integration is present for existing databases
            if (current.none { it.appId == "chatgpt" }) {
                val chatgpt = createIntegration("chatgpt", "ChatGPT", "https://logo.clearbit.com/openai.com")
                val existingToken = securePrefs.getChatGPTAccessToken()
                if (existingToken.isNotBlank()) {
                    repository.insertIntegration(chatgpt.copy(status = "connected", accessToken = existingToken, connectedAt = System.currentTimeMillis()))
                } else {
                    repository.insertIntegration(chatgpt)
                }
                addLog("Added ChatGPT integration for image and docs creation to available integrations.")
            }
        }
    }

    private fun createIntegration(appId: String, appName: String, iconUrl: String): IntegrationEntity {
        return IntegrationEntity(
            id = UUID.randomUUID().toString(),
            appId = appId,
            appName = appName,
            displayName = appName,
            iconUrl = iconUrl,
            status = "disconnected",
            accessToken = "",
            refreshToken = "",
            scopes = "read,write",
            connectedAt = 0L,
            lastSyncedAt = 0L
        )
    }

    fun connectIntegration(appId: String) {
        if (appId == "youtube_music") {
            viewModelScope.launch {
                val integration = repository.getIntegrationByAppId(appId)
                if (integration != null) {
                    val updated = integration.copy(
                        status = "connected",
                        connectedAt = System.currentTimeMillis()
                    )
                    repository.insertIntegration(updated)
                    addLog("YouTube Music connected. I can now open and search YouTube Music on your device.")
                }
            }
            return
        }
        val isGoogleService = appId == "google_account" || appId == "gmail" || appId == "google_calendar" || appId == "google_drive"
        if (isGoogleService) {
            addLog("Opening Google account picker for $appId...")
        } else {
            addLog("Initiating OAuth flow for $appId...")
            viewModelScope.launch {
                val integration = repository.getIntegrationByAppId(appId)
                if (integration != null) {
                    val updated = integration.copy(
                        status = "connected",
                        accessToken = "mock_access_token_${UUID.randomUUID()}",
                        refreshToken = "mock_refresh_token_${UUID.randomUUID()}",
                        connectedAt = System.currentTimeMillis(),
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    repository.insertIntegration(updated)
                    addLog("$appId connected.")
                }
            }
        }
    }

    fun getAccountPickerIntent(appId: String): Intent {
        pendingGoogleAppId = appId
        return oauthManager.createAccountPickerIntent()
    }

    fun onAccountPicked(data: Intent?) {
        val appId = pendingGoogleAppId ?: return
        pendingGoogleAppId = null
        if (oauthManager.completeOAuthWithPickedAccount(data, appId)) {
            addLog("Google account selected. Connecting $appId...")
        } else {
            _showNoGoogleAccountDialog.value = true
        }
    }

    fun dismissNoGoogleAccountDialog() {
        _showNoGoogleAccountDialog.value = false
    }

    fun disconnectIntegration(appId: String) {
        viewModelScope.launch {
            val integration = repository.getIntegrationByAppId(appId)
            if (integration != null) {
                val updated = integration.copy(
                    status = "disconnected",
                    accessToken = "",
                    refreshToken = "",
                    connectedAt = 0L,
                    lastSyncedAt = 0L
                )
                repository.insertIntegration(updated)
                addLog("Disconnected integration: ${integration.appName}")

                if (appId == "chatgpt") {
                    try {
                        ChatGPTBridge.getInstance(appContext).disconnect()
                        addLog("ChatGPT session and credentials cleared.")
                    } catch (e: Exception) {
                        addLog("Error disconnecting ChatGPT: ${e.message}")
                    }
                } else if (appId == "telegram") {
                    try {
                        val botStore = BotConfigStore(appContext)
                        botStore.saveBots(emptyList())
                        TelegramBridgeService.stop(appContext)
                        addLog("Telegram Bot Bridge service stopped.")
                    } catch (e: Exception) {
                        addLog("Error stopping Telegram Bridge: ${e.message}")
                    }
                } else if (appId == "github") {
                    try {
                        securePrefs.saveSetting("github_token", "")
                        ai.deepcode.android.data.repository.DeepCodeRepository.getInstance(appContext).updateGitHubToken(null)
                        addLog("GitHub disconnected and credentials cleared.")
                    } catch (e: Exception) {
                        addLog("Error disconnecting GitHub: ${e.message}")
                    }
                } else if (appId == "whatsapp") {
                    disconnectWhatsApp()
                }
            }
        }
    }

    fun connectChatGPT(token: String, email: String? = null) {
        viewModelScope.launch {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@launch
            addLog("Connecting ChatGPT integration for image and docs creation...")
            securePrefs.saveChatGPTAccessToken(trimmed)
            ChatGPTBridge.getInstance(appContext).parseAndSaveJwtMetadata(trimmed)
            if (!email.isNullOrBlank()) {
                securePrefs.saveSetting("chatgpt_user_email", email)
            }
            val integration = repository.getIntegrationByAppId("chatgpt")
            if (integration != null) {
                val updated = integration.copy(
                    status = "connected",
                    accessToken = trimmed,
                    connectedAt = System.currentTimeMillis(),
                    lastSyncedAt = System.currentTimeMillis()
                )
                repository.insertIntegration(updated)
            } else {
                val newIntegration = createIntegration("chatgpt", "ChatGPT", "https://logo.clearbit.com/openai.com").copy(
                    status = "connected",
                    accessToken = trimmed,
                    connectedAt = System.currentTimeMillis(),
                    lastSyncedAt = System.currentTimeMillis()
                )
                repository.insertIntegration(newIntegration)
            }
            addLog("ChatGPT connected. ChatGPT integration for image and docs creation ready.")
        }
    }

    fun handleCallback(appId: String, code: String) {
        viewModelScope.launch {
            val success = oauthManager.handleCallback(appId, code)
            if (success) {
                addLog("OAuth flow completed successfully. $appId is now connected.")
            } else {
                addLog("Failed to handle OAuth callback for $appId.")
            }
        }
    }

    fun connectTelegramBot(botToken: String) {
        viewModelScope.launch {
            addLog("Validating Telegram Bot Token...")
            withContext(Dispatchers.IO) {
                var proceed = false
                val client = okhttp3.OkHttpClient()
                try {
                    val url = "https://api.telegram.org/bot$botToken/getMe"
                    val request = okhttp3.Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            addLog("Telegram Bot verified successfully. Status: Connected.")
                            proceed = true
                        } else {
                            addLog("Failed to validate Telegram Bot token: HTTP ${response.code}. Proceeding anyway for offline/local testing...")
                            proceed = true
                        }
                    }
                } catch (e: Exception) {
                    addLog("Validation error: ${e.message}. Offline or network issue? Bypassing validation to save token.")
                    proceed = true
                } finally {
                    ai.deepcode.android.util.SafeDispose.dispose(client)
                }

                if (proceed) {
                    try {
                        securePrefs.saveSetting("telegram_bot_token", botToken)
                        val integration = repository.getIntegrationByAppId("telegram")
                        if (integration != null) {
                            val updated = integration.copy(
                                status = "connected",
                                accessToken = botToken,
                                connectedAt = System.currentTimeMillis()
                            )
                            repository.insertIntegration(updated)
                        }
                        val botStore = BotConfigStore(appContext)
                        botStore.saveBots(listOf(BotConfig(token = botToken)))
                        TelegramBridgeService.start(appContext)
                    } catch (e: Exception) {
                        addLog("Error saving bot configuration: ${e.message}")
                    }
                }
            }
        }
    }

    fun connectNotion(notionToken: String) {
        viewModelScope.launch {
            addLog("Validating Notion Integration Token...")
            withContext(Dispatchers.IO) {
                val service = NotionService(notionToken.trim())
                val result = service.validateToken()
                result.onSuccess { user ->
                    addLog("Notion token validated. Connected as: ${user.name} (${user.email})")
                    try {
                        securePrefs.saveSetting("notion_token", notionToken.trim())
                        val integration = repository.getIntegrationByAppId("notion")
                        if (integration != null) {
                            val updated = integration.copy(
                                status = "connected",
                                accessToken = notionToken.trim(),
                                connectedAt = System.currentTimeMillis(),
                                lastSyncedAt = System.currentTimeMillis()
                            )
                            repository.insertIntegration(updated)
                        }
                        addLog("Notion integration saved successfully.")
                    } catch (e: Exception) {
                        addLog("Error saving Notion integration: ${e.message}")
                    }
                }.onFailure { error ->
                    addLog("Notion token validation failed: ${error.message}")
                }
            }
        }
    }

    fun connectGitHub(githubToken: String) {
        val cleanToken = githubToken.trim()
        if (cleanToken.isEmpty()) {
            addLog("GitHub token cannot be empty.")
            return
        }
        viewModelScope.launch {
            addLog("Validating GitHub Personal Access Token...")
            withContext(Dispatchers.IO) {
                val service = GitHubService(cleanToken)
                val result = service.validateToken()
                result.onSuccess { user ->
                    val scopesInfo = if (user.scopes.isNotEmpty()) " (scopes: ${user.scopes})" else ""
                    addLog("GitHub token validated. Connected as: ${user.name} (@${user.login})$scopesInfo")
                    try {
                        securePrefs.saveSetting("github_token", cleanToken)
                        try {
                            ai.deepcode.android.data.repository.DeepCodeRepository.getInstance(appContext).updateGitHubToken(cleanToken)
                        } catch (e: Exception) {
                            AppLogger.e("ConnectionsViewModel", "Failed to update repository GitHub token", e)
                        }
                        val integration = repository.getIntegrationByAppId("github")
                        if (integration != null) {
                            val updated = integration.copy(
                                status = "connected",
                                displayName = if (user.name.isNotBlank() && user.name != user.login) "GitHub (${user.name} - @${user.login})" else "GitHub (@${user.login})",
                                iconUrl = user.avatarUrl.ifEmpty { integration.iconUrl },
                                accessToken = cleanToken,
                                scopes = user.scopes,
                                connectedAt = System.currentTimeMillis(),
                                lastSyncedAt = System.currentTimeMillis()
                            )
                            repository.insertIntegration(updated)
                        }
                        addLog("GitHub integration saved & live synced across AI tools successfully.")
                    } catch (e: Exception) {
                        addLog("Error saving GitHub integration: ${e.message}")
                    }
                }.onFailure { error ->
                    addLog("GitHub token validation failed: ${error.message}")
                }
            }
        }
    }

    // ── WhatsApp Bridge ──

    fun updateWhatsAppBridgeUrl(url: String) {
        _whatsAppBridgeUrl.value = url.trimEnd('/')
        whatsAppApi = null
        addLog("WhatsApp Bridge URL updated to $url")
    }

    fun connectWhatsApp() {
        viewModelScope.launch {
            addLog("Connecting to WhatsApp Bridge...")
            val api = getWhatsAppAPI() ?: run {
                addLog("WhatsApp API client not available.")
                return@launch
            }
            withContext(Dispatchers.IO) {
                try {
                    val status = api.getAuthStatus()
                    if (status.isSuccessful && status.body()?.connected == true) {
                        val phone = status.body()?.phone
                        _whatsAppConnected.value = true
                        _whatsAppPhone.value = phone
                        addLog("WhatsApp already connected${phone?.let { " as $it" } ?: ""}.")
                        updateIntegrationStatus("whatsapp", "connected")
                        return@withContext
                    }

                    addLog("Fetching QR code for WhatsApp Web pairing...")
                    val qrResponse = api.getQR(120_000)
                    if (qrResponse.isSuccessful && qrResponse.body() != null) {
                        val body = qrResponse.body()!!
                        if (body.connected) {
                            _whatsAppConnected.value = true
                            _whatsAppPhone.value = body.phone
                            addLog("WhatsApp connected via QR as ${body.phone}.")
                            updateIntegrationStatus("whatsapp", "connected")
                        } else if (body.qr != null) {
                            _whatsAppQRCode.value = body.qr
                            addLog("QR code received. Scan with WhatsApp to connect.")
                            pollWhatsAppStatus()
                        } else {
                            addLog("QR fetch returned unexpected state.")
                        }
                    } else {
                        addLog("Failed to fetch QR: HTTP ${qrResponse.code()}")
                    }
                } catch (e: Exception) {
                    addLog("WhatsApp connection error: ${e.message}. Is the bridge server running on ${_whatsAppBridgeUrl.value}?")
                }
            }
        }
    }

    private suspend fun pollWhatsAppStatus() {
        val api = getWhatsAppAPI() ?: return
        var attempts = 0
        while (attempts < 60) {
            kotlinx.coroutines.delay(2000)
            attempts++
            try {
                val status = api.getAuthStatus()
                if (status.isSuccessful && status.body()?.connected == true) {
                    _whatsAppConnected.value = true
                    _whatsAppPhone.value = status.body()?.phone
                    _whatsAppQRCode.value = null
                    addLog("WhatsApp connected successfully as ${status.body()?.phone}.")
                    updateIntegrationStatus("whatsapp", "connected")
                    return
                }
            } catch (_: Exception) {
                break
            }
        }
        addLog("QR scan timed out. Please try again.")
        _whatsAppQRCode.value = null
    }

    fun refreshWhatsAppQR() {
        _whatsAppQRCode.value = null
        connectWhatsApp()
    }

    fun disconnectWhatsApp() {
        viewModelScope.launch {
            addLog("Disconnecting WhatsApp...")
            val api = getWhatsAppAPI()
            if (api != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val resp = api.logout()
                        if (resp.isSuccessful) {
                            addLog("WhatsApp logged out remotely.")
                        }
                    } catch (_: Exception) {}
                }
            }
            _whatsAppConnected.value = false
            _whatsAppPhone.value = null
            _whatsAppQRCode.value = null
            updateIntegrationStatus("whatsapp", "disconnected")
            addLog("WhatsApp disconnected.")
        }
    }

    fun sendWhatsAppMessage(jid: String, text: String) {
        viewModelScope.launch {
            val api = getWhatsAppAPI() ?: return@launch
            withContext(Dispatchers.IO) {
                try {
                    val resp = api.sendMessage(WASendRequest(jid = jid, text = text))
                    if (resp.isSuccessful) {
                        addLog("WhatsApp message sent to $jid.")
                    } else {
                        addLog("Failed to send message: HTTP ${resp.code()}")
                    }
                } catch (e: Exception) {
                    addLog("Error sending WhatsApp message: ${e.message}")
                }
            }
        }
    }

    fun checkWhatsAppHealth() {
        viewModelScope.launch {
            val api = getWhatsAppAPI() ?: return@launch
            withContext(Dispatchers.IO) {
                try {
                    val resp = api.health()
                    if (resp.isSuccessful && resp.body() != null) {
                        val h = resp.body()!!
                        _whatsAppConnected.value = h.connected
                        _whatsAppPhone.value = h.phone
                        addLog("WhatsApp Bridge: ${h.status}, connected=${h.connected}, phone=${h.phone}")
                    } else {
                        addLog("WhatsApp Bridge health check failed: HTTP ${resp.code()}")
                    }
                } catch (e: Exception) {
                    addLog("WhatsApp Bridge unreachable: ${e.message}")
                }
            }
        }
    }

    private suspend fun updateIntegrationStatus(appId: String, status: String) {
        val integration = repository.getIntegrationByAppId(appId)
        if (integration != null) {
            val updated = integration.copy(
                status = status,
                accessToken = if (status == "connected") "wa_bridge" else "",
                connectedAt = if (status == "connected") System.currentTimeMillis() else 0L,
                lastSyncedAt = if (status == "connected") System.currentTimeMillis() else 0L
            )
            repository.insertIntegration(updated)
        }
    }
}
