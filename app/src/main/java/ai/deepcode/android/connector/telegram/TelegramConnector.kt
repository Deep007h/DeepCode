package ai.deepcode.android.connector.telegram

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.ui.connections.ConnectionStatus
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class TelegramConnector(private val context: Context) : ConnectorBridge {

    override val connectorId = "telegram"
    override val displayName = "Telegram"
    override val description = "Send messages, broadcast updates, and check bot health via Telegram Bot API"

    override val requiredScopes = emptyList<String>()

    private val securePrefs = EncryptedPrefs.getInstance(context)
    private val integrationRepo = IntegrationRepository(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TelegramConnector"
        private const val API_BASE = "https://api.telegram.org/bot"
    }

    private suspend fun getToken(): String {
        var token = securePrefs.getSetting("telegram_bot_token", "").trim()
        if (token.isEmpty()) {
            token = integrationRepo.getIntegrationByAppId("telegram")?.accessToken?.trim() ?: ""
        }
        return token
    }

    override suspend fun authenticate(context: Context): AuthResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext AuthResult.Failure(
                "Telegram Bot token not configured. Please connect in Settings → Connections.",
                isRecoverable = true
            )
        }
        try {
            val url = "$API_BASE$token/getMe"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    if (json.get("ok")?.asBoolean == true) {
                        val botUser = json.getAsJsonObject("result")?.get("username")?.asString ?: "Telegram Bot"
                        integrationRepo.updateStatusAndSyncTime("telegram", ConnectionStatus.CONNECTED, System.currentTimeMillis())
                        AuthResult.Success(botUser)
                    } else {
                        integrationRepo.updateStatusOnly("telegram", ConnectionStatus.ERROR)
                        AuthResult.Failure("Telegram API error: ${json.get("description")?.asString}")
                    }
                } else {
                    val isExpired = response.code == 401 || response.code == 404
                    integrationRepo.updateStatusOnly("telegram", if (isExpired) ConnectionStatus.EXPIRED else ConnectionStatus.ERROR)
                    AuthResult.Failure("Invalid Telegram Bot token: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Telegram auth failed", e)
            AuthResult.Failure(e.message ?: "Network error validating Telegram Bot")
        }
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) return@withContext false
        try {
            val url = "$API_BASE$token/getMe"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    json.get("ok")?.asBoolean == true
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refreshTokenIfNeeded(): Boolean {
        return isAuthenticated()
    }

    override suspend fun revokeAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            securePrefs.saveSetting("telegram_bot_token", "")
            val integration = integrationRepo.getIntegrationByAppId("telegram")
            if (integration != null) {
                integrationRepo.insertIntegration(
                    integration.copy(
                        status = ConnectionStatus.DISCONNECTED,
                        accessToken = "",
                        refreshToken = "",
                        connectedAt = 0L,
                        lastSyncedAt = 0L
                    )
                )
            }
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to revoke Telegram access", e)
            false
        }
    }

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "telegram_send_message",
            description = "Send a text message to a Telegram chat using the connected bot.",
            parameters = mapOf(
                "chat_id" to ParameterDef("The recipient Telegram chat ID or @username", required = true),
                "text" to ParameterDef("Message text to send", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "telegram_get_me",
            description = "Get information about the connected Telegram bot.",
            parameters = emptyMap()
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val token = getToken()
        if (token.isBlank()) {
            return@withContext ConnectorResult.Error(
                ConnectorError.AuthExpired("Telegram not connected. Please connect your Telegram Bot token in Connections.")
            )
        }
        try {
            when (action.actionName) {
                "telegram_get_me" -> {
                    val url = "$API_BASE$token/getMe"
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JsonParser.parseString(body).asJsonObject
                            val bot = json.getAsJsonObject("result")
                            val username = bot?.get("username")?.asString ?: "Unknown"
                            val firstName = bot?.get("first_name")?.asString ?: ""
                            ConnectorResult.Success("🤖 Telegram Bot: $firstName (@$username)")
                        } else {
                            if (response.code == 401) {
                                ConnectorResult.Error(ConnectorError.AuthExpired("Telegram token expired: HTTP ${response.code}"))
                            } else {
                                ConnectorResult.Error(ConnectorError.ApiError(response.code, body))
                            }
                        }
                    }
                }
                "telegram_send_message" -> {
                    val chatId = action.parameters["chat_id"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "chat_id is required"))
                    val text = action.parameters["text"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "text is required"))

                    val payload = JsonObject().apply {
                        addProperty("chat_id", chatId)
                        addProperty("text", text)
                        addProperty("parse_mode", "HTML")
                    }

                    val url = "$API_BASE$token/sendMessage"
                    val request = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            ConnectorResult.Success("✅ Telegram message sent to $chatId.")
                        } else {
                            ConnectorResult.Error(ConnectorError.ApiError(response.code, "Failed to send message: $body"))
                        }
                    }
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "Telegram action error"))
        }
    }
}
