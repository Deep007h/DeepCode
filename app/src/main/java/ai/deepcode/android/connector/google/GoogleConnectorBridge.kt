package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.service.google.GoogleAuthService
import ai.deepcode.android.ui.connections.IntegrationRepository
import ai.deepcode.android.util.AppLogger

/**
 * Shared OAuth/token plumbing for all Google Workspace connectors.
 *
 * Delegates to the existing [GoogleAuthService] for token management (PKCE flow,
 * refresh, AccountManager fallback) and stores refresh tokens via [EncryptedPrefs]
 * (the fixed singleton version). Access tokens are held in memory only.
 *
 * Each concrete connector (Gmail, Calendar, Drive) specifies its own scope set.
 * Scopes are requested incrementally — only when the connector is first used.
 */
abstract class GoogleConnectorBridge(protected val context: Context) : ConnectorBridge {

    protected val authService = GoogleAuthService(context)
    protected val securePrefs = EncryptedPrefs.getInstance(context)
    private val integrationRepo = IntegrationRepository(context)

    /** In-memory access token — never persisted to disk */
    @Volatile
    protected var cachedAccessToken: String? = null

    @Volatile
    protected var tokenExpiryMs: Long = 0L

    companion object {
        private const val TAG = "GoogleConnectorBridge"
        private const val REVOKE_URL = "https://oauth2.googleapis.com/revoke"
    }

    override suspend fun authenticate(context: Context): AuthResult {
        return try {
            val tokenResult = authService.getValidAccessToken(connectorId)
            if (tokenResult.isSuccess) {
                cachedAccessToken = tokenResult.getOrThrow()
                tokenExpiryMs = System.currentTimeMillis() + 50 * 60 * 1000 // ~50 min
                AuthResult.Success(securePrefs.getSetting("google_account_email", ""))
            } else {
                // Try via google_account integration
                val googleResult = authService.getValidAccessToken("google_account")
                if (googleResult.isSuccess) {
                    cachedAccessToken = googleResult.getOrThrow()
                    tokenExpiryMs = System.currentTimeMillis() + 50 * 60 * 1000
                    AuthResult.Success(securePrefs.getSetting("google_account_email", ""))
                } else {
                    AuthResult.Failure(
                        "Not authenticated with Google. Connect your Google Account in Settings → Connections.",
                        isRecoverable = true
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Authentication failed for $connectorId", e)
            AuthResult.Failure(e.message ?: "Unknown authentication error")
        }
    }

    override suspend fun isAuthenticated(): Boolean {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryMs) {
            return true
        }
        // Try to get a valid token silently
        val result = authService.getValidAccessToken(connectorId)
        if (result.isSuccess) {
            cachedAccessToken = result.getOrThrow()
            tokenExpiryMs = System.currentTimeMillis() + 50 * 60 * 1000
            return true
        }
        // Fallback to google_account
        val googleResult = authService.getValidAccessToken("google_account")
        if (googleResult.isSuccess) {
            cachedAccessToken = googleResult.getOrThrow()
            tokenExpiryMs = System.currentTimeMillis() + 50 * 60 * 1000
            return true
        }
        return false
    }

    override suspend fun refreshTokenIfNeeded(): Boolean {
        val integration = integrationRepo.getIntegrationByAppId(connectorId)
            ?: integrationRepo.getIntegrationByAppId("google_account")
            ?: return false

        if (integration.refreshToken.isBlank()) return false

        val result = authService.refreshAccessToken(integration.refreshToken)
        if (result.isSuccess) {
            val tokens = result.getOrThrow()
            cachedAccessToken = tokens.accessToken
            tokenExpiryMs = System.currentTimeMillis() + (tokens.expiresIn * 1000L) - 60_000

            // Update integration record
            val updated = integration.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken.ifEmpty { integration.refreshToken },
                lastSyncedAt = System.currentTimeMillis()
            )
            integrationRepo.insertIntegration(updated)
            return true
        }
        return false
    }

    override suspend fun revokeAccess(): Boolean {
        return try {
            val token = cachedAccessToken ?: return true

            // Call Google's revoke endpoint
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url("$REVOKE_URL?token=$token")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val response = client.newCall(request).execute()
            response.close()

            // Clear local state
            cachedAccessToken = null
            tokenExpiryMs = 0

            // Update integration status
            val integration = integrationRepo.getIntegrationByAppId(connectorId)
            if (integration != null) {
                val updated = integration.copy(
                    status = "disconnected",
                    accessToken = "",
                    refreshToken = ""
                )
                integrationRepo.insertIntegration(updated)
            }

            AppLogger.i(TAG, "Revoked access for $connectorId")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to revoke access for $connectorId", e)
            false
        }
    }

    /**
     * Get a valid access token, refreshing if needed.
     * For use by concrete connector implementations.
     */
    protected suspend fun getAccessToken(): String {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryMs) {
            return cachedAccessToken!!
        }
        if (refreshTokenIfNeeded()) {
            return cachedAccessToken ?: throw IllegalStateException("Token refresh succeeded but no token available")
        }
        throw IllegalStateException("Not authenticated with ${displayName}. Connect in Settings → Connections.")
    }

    /**
     * Classify an HTTP error code into the appropriate [ConnectorError].
     */
    protected fun classifyHttpError(code: Int, body: String): ConnectorError {
        return when (code) {
            401 -> ConnectorError.AuthExpired("Access token expired")
            403 -> ConnectorError.PermissionDenied("Insufficient permissions: $body")
            429 -> ConnectorError.NetworkError("Rate limited by Google API")
            in 500..599 -> ConnectorError.NetworkError("Google server error: $code")
            else -> ConnectorError.ApiError(code, body)
        }
    }
}

/**
 * Token data returned from Google OAuth token exchange.
 * Note: This shadows the one in GoogleAuthService for connector-internal use.
 */
data class GoogleTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)
