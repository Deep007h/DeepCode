package ai.deepcode.android.connector.whatsapp

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.data.remote.WASendRequest
import ai.deepcode.android.data.remote.WhatsAppBridgeAPI
import ai.deepcode.android.ui.connections.ConnectionStatus
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WhatsAppConnector(private val context: Context) : ConnectorBridge {

    override val connectorId = "whatsapp"
    override val displayName = "WhatsApp"
    override val description = "Send WhatsApp messages and check pairing status via WhatsApp bridge sidecar"

    override val requiredScopes = emptyList<String>()

    private val integrationRepo = IntegrationRepository(context)

    companion object {
        private const val TAG = "WhatsAppConnector"
        private const val DEFAULT_BRIDGE_URL = "http://localhost:3001/"
    }

    private fun getApi(): WhatsAppBridgeAPI {
        return Retrofit.Builder()
            .baseUrl(DEFAULT_BRIDGE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .build()
            .create(WhatsAppBridgeAPI::class.java)
    }

    override suspend fun authenticate(context: Context): AuthResult = withContext(Dispatchers.IO) {
        try {
            val resp = getApi().getAuthStatus()
            if (resp.isSuccessful && resp.body()?.connected == true) {
                val phone = resp.body()?.phone ?: "connected"
                integrationRepo.updateStatusAndSyncTime("whatsapp", ConnectionStatus.CONNECTED, System.currentTimeMillis())
                AuthResult.Success(phone)
            } else {
                integrationRepo.updateStatusOnly("whatsapp", ConnectionStatus.DISCONNECTED)
                AuthResult.Failure("WhatsApp not paired. Scan QR code in Connections to pair.")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "WhatsApp bridge unreachable during auth", e)
            integrationRepo.updateStatusOnly("whatsapp", ConnectionStatus.ERROR)
            AuthResult.Failure("WhatsApp bridge unreachable: ${e.message}")
        }
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resp = getApi().getAuthStatus()
            resp.isSuccessful && resp.body()?.connected == true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun refreshTokenIfNeeded(): Boolean {
        return isAuthenticated()
    }

    override suspend fun revokeAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            try { getApi().logout() } catch (_: Exception) {}
            val integration = integrationRepo.getIntegrationByAppId("whatsapp")
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
            AppLogger.e(TAG, "Failed to revoke WhatsApp access", e)
            false
        }
    }

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "whatsapp_send_message",
            description = "Send a WhatsApp message to a phone number or group JID via the connected bridge.",
            parameters = mapOf(
                "jid" to ParameterDef("Phone number with country code (e.g. '1234567890') or full JID", required = true),
                "text" to ParameterDef("Message content to send", required = true)
            )
        ),
        ConnectorCapability(
            actionName = "whatsapp_get_status",
            description = "Get connection and pairing health status of the WhatsApp bridge sidecar.",
            parameters = emptyMap()
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult = withContext(Dispatchers.IO) {
        val api = getApi()
        try {
            when (action.actionName) {
                "whatsapp_get_status" -> {
                    val resp = api.health()
                    if (resp.isSuccessful && resp.body() != null) {
                        val h = resp.body()!!
                        val text = if (h.connected) "✅ WhatsApp Bridge: Connected as ${h.phone}" else "⚠️ WhatsApp Bridge: Not connected (status: ${h.status})"
                        ConnectorResult.Success(text)
                    } else {
                        ConnectorResult.Error(ConnectorError.ApiError(resp.code(), "Health check failed: HTTP ${resp.code()}"))
                    }
                }
                "whatsapp_send_message" -> {
                    val jid = action.parameters["jid"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "jid is required"))
                    val text = action.parameters["text"]
                        ?: return@withContext ConnectorResult.Error(ConnectorError.ApiError(400, "text is required"))

                    val resp = api.sendMessage(WASendRequest(jid = jid, text = text))
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        ConnectorResult.Success("✅ WhatsApp message sent to $jid (ID: ${resp.body()?.id}).")
                    } else {
                        val errMsg = resp.body()?.error ?: "HTTP ${resp.code()}"
                        ConnectorResult.Error(ConnectorError.ApiError(resp.code(), "Failed to send WhatsApp message: $errMsg"))
                    }
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "WhatsApp action error"))
        }
    }
}
