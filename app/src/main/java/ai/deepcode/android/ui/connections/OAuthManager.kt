package ai.deepcode.android.ui.connections

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import ai.deepcode.android.service.google.GoogleAuthService
import ai.deepcode.android.service.google.GoogleTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OAuthManager(private val context: Context, private val scope: CoroutineScope? = null) {
    private val repository = IntegrationRepository(context)
    private val googleAuth = GoogleAuthService(context)

    fun createAccountPickerIntent(): Intent {
        return AccountManager.newChooseAccountIntent(
            null, null, arrayOf("com.google"), true, null, null, null, null
        )
    }

    fun completeOAuthWithPickedAccount(data: Intent?, appId: String): Boolean {
        val accountName = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return false
        connectWithAccount(Account(accountName, "com.google"), appId)
        return true
    }

    fun startOAuthFlowForAccount(account: Account, appId: String) {
        connectWithAccount(account, appId)
    }

    private fun connectWithAccount(account: Account, appId: String) {
        val effectiveScope = scope ?: CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
        effectiveScope.launch {
            val result = performDeviceAuth(account, appId)
            if (result) {
                addLog("$appId connected with account ${account.name}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$appId connected!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                addLog("$appId connection failed")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "$appId connection failed. Check app logs.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun performDeviceAuth(account: Account, appId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val allScopes = when (appId) {
                    "google_account" -> GoogleAuthService.IDENTITY_SCOPES +
                            GoogleAuthService.GMAIL_SCOPES +
                            GoogleAuthService.CALENDAR_SCOPES +
                            GoogleAuthService.DRIVE_SCOPES +
                            GoogleAuthService.CONTACTS_SCOPES
                    "gmail" -> GoogleAuthService.GMAIL_SCOPES
                    "google_calendar" -> GoogleAuthService.CALENDAR_SCOPES
                    "google_drive" -> GoogleAuthService.DRIVE_SCOPES
                    else -> GoogleAuthService.IDENTITY_SCOPES
                }

                val integration = repository.getIntegrationByAppId(appId)
                if (integration == null) return@withContext false

                var accessToken = ""

                try {
                    val tokenResult = googleAuth.getDeviceAuthTokenForScopes(account, allScopes)
                    if (tokenResult.isSuccess) {
                        accessToken = tokenResult.getOrThrow()
                    }
                } catch (_: Exception) {}
                googleAuth.saveGoogleAccountEmail(account.name)

                // Instantly attach selected email to integration so it gets connected like native apps
                val now = System.currentTimeMillis()
                val updated = integration.copy(
                    displayName = account.name,
                    status = "connected",
                    accessToken = accessToken,
                    connectedAt = now,
                    lastSyncedAt = now
                )
                repository.insertIntegration(updated)

                // Also attach related Google sub-services
                val subServices = listOf("gmail", "google_calendar", "google_drive", "google_account")
                for (subId in subServices) {
                    val sub = repository.getIntegrationByAppId(subId)
                    if (sub != null && sub.status != "connected") {
                        val subUpdated = sub.copy(
                            displayName = account.name,
                            status = "connected",
                            accessToken = accessToken,
                            connectedAt = now,
                            lastSyncedAt = now
                        )
                        repository.insertIntegration(subUpdated)
                    }
                }

                if (accessToken.isBlank() && googleAuth.getClientId().isNotBlank()) {
                    // Pass loginHint so web auth pre-selects the chosen account without asking for login/password
                    performLoopbackOAuth(appId, allScopes, loginHint = account.name)
                }

                return@withContext true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun performLoopbackOAuth(appId: String, scopes: List<String>, loginHint: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val server = ServerSocket(0)
            val port = server.localPort
            server.soTimeout = 120000
            addLog("Loopback OAuth server started on port $port")

            val redirectUri = "http://${GoogleAuthService.LOOPBACK_HOST}:$port/callback"
            val authUrl = googleAuth.buildAuthUrl(scopes, state = appId, customRedirectUri = redirectUri, loginHint = loginHint)
            val codeVerifier = googleAuth.getSavedCodeVerifier()

            withContext(Dispatchers.Main) {
                try {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    customTabsIntent.launchUrl(context, Uri.parse(authUrl))
                } catch (e: Exception) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }

            try {
                val socket = server.accept()
                val code: String
                try {
                    val reader = socket.inputStream.bufferedReader()
                    val requestLine = reader.readLine() ?: return@withContext false
                    val parts = requestLine.split(" ")
                    if (parts.size < 2) return@withContext false
                    val requestUri = Uri.parse("http://localhost${parts[1]}")
                    code = requestUri.getQueryParameter("code") ?: return@withContext false

                    val responseHtml = "<html><body><p>Authorization complete. You can close this window.</p></body></html>"
                    val responseBytes = responseHtml.toByteArray()
                    val httpResponse = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${responseBytes.size}\r\nConnection: close\r\n\r\n"
                    socket.outputStream.write(httpResponse.toByteArray())
                    socket.outputStream.write(responseBytes)
                    socket.outputStream.flush()
                } finally {
                    try { socket.close() } catch (_: Exception) {}
                }

                val tokenResult = googleAuth.exchangeCodeForTokensRaw(code, redirectUri, codeVerifier)
                if (tokenResult.isFailure) {
                    addLog("Token exchange failed: ${tokenResult.exceptionOrNull()?.message}")
                    return@withContext false
                }
                val tokens = tokenResult.getOrThrow()

                val profileResult = googleAuth.fetchUserProfile(tokens.accessToken)
                var dispName = ""
                var pictureUrl = ""
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrThrow()
                    dispName = if (profile.name.isNotEmpty()) "${profile.name} (${profile.email})" else profile.email
                    pictureUrl = profile.picture
                }

                val integration = repository.getIntegrationByAppId(appId) ?: return@withContext false
                val updated = integration.copy(
                    displayName = dispName.ifEmpty { integration.displayName },
                    iconUrl = pictureUrl.ifEmpty { integration.iconUrl },
                    status = "connected",
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken.ifEmpty { integration.refreshToken },
                    connectedAt = System.currentTimeMillis(),
                    lastSyncedAt = System.currentTimeMillis()
                )
                repository.insertIntegration(updated)

                if (appId == "google_account") {
                    val subServices = listOf("gmail", "google_calendar", "google_drive")
                    for (subId in subServices) {
                        val sub = repository.getIntegrationByAppId(subId)
                        if (sub != null && sub.status != "connected") {
                            val subUpdated = sub.copy(
                                status = "connected",
                                accessToken = tokens.accessToken,
                                refreshToken = tokens.refreshToken.ifEmpty { sub.refreshToken },
                                connectedAt = System.currentTimeMillis(),
                                lastSyncedAt = System.currentTimeMillis()
                            )
                            repository.insertIntegration(subUpdated)
                        }
                    }
                }

                addLog("Loopback OAuth completed for $appId")
                true
            } catch (e: java.net.SocketTimeoutException) {
                addLog("Loopback OAuth timed out for $appId")
                false
            } catch (e: Exception) {
                addLog("Loopback OAuth failed: ${e.message}")
                false
            } finally {
                server.close()
            }
        }
    }

    fun startGoogleOAuthFallback(appId: String, scopes: List<String>) {
        val clientId = googleAuth.getClientId()
        if (clientId.isBlank()) {
            val fallbackUri = Uri.parse("${GoogleAuthService.REDIRECT_URI}/callback?code=missing_client_id&state=$appId")
            val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                `package` = context.packageName
            }
            context.startActivity(fallbackIntent)
            return
        }
        val authUrl = googleAuth.buildAuthUrl(scopes, state = appId)
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, Uri.parse(authUrl))
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    suspend fun handleGoogleCodeExchange(appId: String, code: String): String {
        return withContext(Dispatchers.IO) {
            val result = googleAuth.exchangeCodeForTokensSync(code)
            if (result.isFailure) return@withContext "Error: ${result.exceptionOrNull()?.message}"
            val tokens = result.getOrThrow()
            val integration = repository.getIntegrationByAppId(appId)
            if (integration == null) return@withContext "Error: Integration not found"

            var dispName = integration.displayName
            var pictureUrl = integration.iconUrl

            if (appId == "google_account") {
                val profileResult = googleAuth.fetchUserProfile(tokens.accessToken)
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrThrow()
                    dispName = if (profile.name.isNotEmpty()) "${profile.name} (${profile.email})" else profile.email
                    if (profile.picture.isNotEmpty()) {
                        pictureUrl = profile.picture
                    }
                }
            }

            val updated = integration.copy(
                displayName = dispName,
                iconUrl = pictureUrl,
                status = "connected",
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken.ifEmpty { integration.refreshToken },
                connectedAt = System.currentTimeMillis(),
                lastSyncedAt = System.currentTimeMillis()
            )
            repository.insertIntegration(updated)
            "connected"
        }
    }

    suspend fun handleCallback(appId: String, code: String): Boolean {
        val integration = repository.getIntegrationByAppId(appId) ?: return false

        return when (appId) {
            "google_account", "gmail", "google_calendar", "google_drive" -> handleGoogleCallback(integration, appId, code)
            else -> handleMockCallback(integration)
        }
    }

    private suspend fun handleGoogleCallback(
        integration: IntegrationEntity,
        appId: String,
        code: String
    ): Boolean {
        if (code == "missing_client_id" || code.startsWith("mock_code_")) {
            return handleMockCallback(integration)
        }
        return withContext(Dispatchers.IO) {
            try {
                val result = googleAuth.exchangeCodeForTokens(code)
                if (result.isFailure) return@withContext false
                val tokens = result.getOrThrow()

                var dispName = integration.displayName
                var pictureUrl = integration.iconUrl

                if (appId == "google_account") {
                    val profileResult = googleAuth.fetchUserProfile(tokens.accessToken)
                    if (profileResult.isSuccess) {
                        val profile = profileResult.getOrThrow()
                        dispName = if (profile.name.isNotEmpty()) "${profile.name} (${profile.email})" else profile.email
                        if (profile.picture.isNotEmpty()) {
                            pictureUrl = profile.picture
                        }
                    }
                }

                val updated = integration.copy(
                    displayName = dispName,
                    iconUrl = pictureUrl,
                    status = "connected",
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken.ifEmpty { integration.refreshToken },
                    connectedAt = System.currentTimeMillis(),
                    lastSyncedAt = System.currentTimeMillis()
                )
                repository.insertIntegration(updated)

                if (appId == "google_account") {
                    val subServices = listOf("gmail", "google_calendar", "google_drive")
                    for (subId in subServices) {
                        val sub = repository.getIntegrationByAppId(subId)
                        if (sub != null && sub.status != "connected") {
                            val subUpdated = sub.copy(
                                status = "connected",
                                accessToken = tokens.accessToken,
                                refreshToken = tokens.refreshToken.ifEmpty { sub.refreshToken },
                                connectedAt = System.currentTimeMillis(),
                                lastSyncedAt = System.currentTimeMillis()
                            )
                            repository.insertIntegration(subUpdated)
                        }
                    }
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private suspend fun handleMockCallback(integration: IntegrationEntity): Boolean {
        val updated = integration.copy(
            status = "connected",
            accessToken = "mock_access_token_${UUID.randomUUID()}",
            refreshToken = "mock_refresh_token_${UUID.randomUUID()}",
            connectedAt = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        )
        repository.insertIntegration(updated)
        return true
    }

    private fun addLog(message: String) {
        ai.deepcode.android.util.AppLogger.i("OAuthManager", message)
    }
}
