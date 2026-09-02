package ai.deepcode.android.service.google

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.ui.connections.IntegrationRepository
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class GoogleAuthService(private val context: Context) {
    private val securePrefs = EncryptedPrefs.getInstance(context)

    private val hardcodedIps = mapOf(
        "accounts.google.com" to listOf(
            InetAddress.getByAddress("accounts.google.com", byteArrayOf(-64, -78, -45, 84))
        ),
        "www.googleapis.com" to listOf(
            InetAddress.getByAddress("www.googleapis.com", byteArrayOf(-40, -17, 34, -33)),
            InetAddress.getByAddress("www.googleapis.com", byteArrayOf(-40, -17, 36, -33)),
            InetAddress.getByAddress("www.googleapis.com", byteArrayOf(-40, -17, 38, -33)),
            InetAddress.getByAddress("www.googleapis.com", byteArrayOf(-40, -17, 32, -33))
        )
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                try {
                    val result = Dns.SYSTEM.lookup(hostname)
                    if (result.isNotEmpty()) return result
                } catch (_: Exception) {}
                hardcodedIps[hostname]?.let { return it }
                throw UnknownHostException("$hostname — not found in system DNS or hardcoded fallback")
            }
        })
        .build()
    private val gson = Gson()
    private val accountManager = AccountManager.get(context)

    companion object {
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://accounts.google.com/o/oauth2/token"
        const val REDIRECT_URI = "deepcode://oauth"
        const val LOOPBACK_HOST = "127.0.0.1"

        val GMAIL_SCOPES = listOf(
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.send",
            "https://www.googleapis.com/auth/gmail.modify"
        )
        val CALENDAR_SCOPES = listOf(
            "https://www.googleapis.com/auth/calendar.readonly",
            "https://www.googleapis.com/auth/calendar.events"
        )
        val DRIVE_SCOPES = listOf(
            "https://www.googleapis.com/auth/drive.readonly",
            "https://www.googleapis.com/auth/drive.file"
        )
        val CONTACTS_SCOPES = listOf("https://www.googleapis.com/auth/contacts.readonly")
        val IDENTITY_SCOPES = listOf("openid", "email", "profile")
    }

    fun getClientId(): String {
        val saved = securePrefs.getSetting("google_client_id", "")
        if (saved.isNotBlank()) return saved
        return "623008392016-3il9petebk83jakmoglmcu242tk5t0sf.apps.googleusercontent.com"
    }

    fun getClientSecret(): String {
        val saved = securePrefs.getSetting("google_client_secret", "")
        if (saved.isNotBlank()) return saved
        return ""
    }

    fun getDeviceGoogleAccounts(): List<Account> {
        return try {
            accountManager.getAccountsByType("com.google").toList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun getAccountEmail(account: Account): String = account.name

    suspend fun getDeviceAuthToken(account: Account, scope: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val future = accountManager.getAuthToken(account, "oauth2:$scope", null, false, null, null)
            val bundle = future?.result
            if (bundle == null) return@withContext Result.failure(Exception("Null bundle from AccountManager"))
            val token = bundle.getString(AccountManager.KEY_AUTHTOKEN)
            if (token != null) Result.success(token)
            else Result.failure(Exception("No token returned from AccountManager"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceAuthTokenForScopes(account: Account, scopes: List<String>): Result<String> {
        return getDeviceAuthToken(account, scopes.joinToString(" "))
    }

    fun generateCodeVerifier(): String {
        val sr = SecureRandom()
        val code = ByteArray(32)
        sr.nextBytes(code)
        return android.util.Base64.encodeToString(code, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    fun computeCodeChallenge(verifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    fun saveCodeVerifier(verifier: String) {
        securePrefs.saveSetting("pkce_code_verifier", verifier)
    }

    fun getSavedCodeVerifier(): String {
        return securePrefs.getSetting("pkce_code_verifier", "")
    }

    fun saveRedirectUri(uri: String) {
        securePrefs.saveSetting("oauth_redirect_uri", uri)
    }

    fun getSavedRedirectUri(): String {
        return securePrefs.getSetting("oauth_redirect_uri", REDIRECT_URI)
    }

    fun buildAuthUrl(scopes: List<String>, state: String = "", customRedirectUri: String? = null, loginHint: String? = null): String {
        val clientId = getClientId()
        val scopeStr = scopes.joinToString(" ")
        val stateParam = if (state.isNotEmpty()) "&state=${encode(state)}" else ""
        val redirectUri = customRedirectUri ?: REDIRECT_URI
        if (customRedirectUri != null) saveRedirectUri(customRedirectUri)
        val codeVerifier = generateCodeVerifier()
        saveCodeVerifier(codeVerifier)
        val codeChallenge = computeCodeChallenge(codeVerifier)
        val hintParam = if (!loginHint.isNullOrBlank()) "&login_hint=${encode(loginHint)}" else ""
        val promptParam = if (!loginHint.isNullOrBlank()) "&prompt=select_account" else "&prompt=consent"
        return "$AUTH_URL?client_id=${encode(clientId)}&redirect_uri=${encode(redirectUri)}&response_type=code&scope=${encode(scopeStr)}&access_type=offline$promptParam$stateParam$hintParam&code_challenge=${encode(codeChallenge)}&code_challenge_method=S256"
    }

    private fun exchangeCodeForTokensImpl(code: String, redirectUri: String, codeVerifier: String): Result<GoogleTokens> {
        val clientId = getClientId()
        if (clientId.isBlank()) {
            return Result.failure(Exception("Google Client ID not configured"))
        }
        val clientSecret = getClientSecret()
        var body = "code=${encode(code)}&client_id=${encode(clientId)}&redirect_uri=${encode(redirectUri)}&grant_type=authorization_code&code_verifier=${encode(codeVerifier)}"
        if (clientSecret.isNotBlank()) {
            body += "&client_secret=${encode(clientSecret)}"
        }
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return Result.failure(Exception("Token exchange failed: HTTP ${response.code} - $respBody"))
            }
            val json = gson.fromJson(respBody, JsonObject::class.java)
            val accessToken = json.get("access_token")?.asString ?: return Result.failure(Exception("No access_token"))
            val refreshToken = json.get("refresh_token")?.asString ?: ""
            val expiresIn = json.get("expires_in")?.asInt ?: 3600
            Result.success(GoogleTokens(accessToken, refreshToken, expiresIn))
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            ai.deepcode.android.util.AppLogger.e("GoogleAuth", "Token exchange to $TOKEN_URL failed", e)
            Result.failure(Exception("Token exchange failed: ${e.message} | ${sw}"))
        }
    }

    suspend fun exchangeCodeForTokens(code: String, redirectUri: String? = null, codeVerifier: String? = null): Result<GoogleTokens> {
        val rUri = redirectUri ?: getSavedRedirectUri()
        val cVerifier = codeVerifier ?: getSavedCodeVerifier()
        if (cVerifier.isBlank()) {
            return Result.failure(Exception("PKCE code verifier not found"))
        }
        return exchangeCodeForTokensImpl(code, rUri, cVerifier)
    }

    fun exchangeCodeForTokensSync(code: String, redirectUri: String? = null, codeVerifier: String? = null): Result<GoogleTokens> {
        val rUri = redirectUri ?: getSavedRedirectUri()
        val cVerifier = codeVerifier ?: getSavedCodeVerifier()
        if (cVerifier.isBlank()) {
            return Result.failure(Exception("PKCE code verifier not found"))
        }
        return exchangeCodeForTokensImpl(code, rUri, cVerifier)
    }

    suspend fun refreshAccessToken(refreshToken: String): Result<GoogleTokens> = withContext(Dispatchers.IO) {
        val clientId = getClientId()
        val clientSecret = getClientSecret()
        try {
            var body = "client_id=${encode(clientId)}&refresh_token=${encode(refreshToken)}&grant_type=refresh_token"
            if (clientSecret.isNotBlank()) {
                body += "&client_secret=${encode(clientSecret)}"
            }
            val request = Request.Builder()
                .url(TOKEN_URL)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Token refresh failed: HTTP ${response.code} - $respBody"))
            }
            val json = gson.fromJson(respBody, JsonObject::class.java)
            val accessToken = json.get("access_token")?.asString ?: return@withContext Result.failure(Exception("No access_token in response"))
            val newRefreshToken = json.get("refresh_token")?.asString ?: refreshToken
            val expiresIn = json.get("expires_in")?.asInt ?: 3600
            Result.success(GoogleTokens(accessToken, newRefreshToken, expiresIn))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(accessToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v3/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .build()
            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to fetch userinfo: HTTP ${response.code}"))
                }
                val json = gson.fromJson(respBody, JsonObject::class.java)
                val sub = json.get("sub")?.asString ?: ""
                val name = json.get("name")?.asString ?: ""
                val email = json.get("email")?.asString ?: ""
                val picture = json.get("picture")?.asString ?: ""
                Result.success(UserProfile(sub, name, email, picture))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveGoogleAccountEmail(email: String) {
        securePrefs.saveSetting("google_account_email", email)
    }

    private fun getSavedGoogleAccountEmail(): String {
        return securePrefs.getSetting("google_account_email", "")
    }

    private fun getSavedGoogleScopes(): String {
        return securePrefs.getSetting("google_account_scopes",
            "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send https://www.googleapis.com/auth/gmail.modify " +
            "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events " +
            "https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file " +
            "https://www.googleapis.com/auth/contacts.readonly openid email profile"
        )
    }

    suspend fun getValidAccessToken(appId: String): Result<String> = withContext(Dispatchers.IO) {
        val repo = IntegrationRepository(context)
        var integration = repo.getIntegrationByAppId(appId)

        if (integration == null || integration.status != "connected") {
            val googleAccount = repo.getIntegrationByAppId("google_account")
            if (googleAccount != null && googleAccount.status == "connected") {
                integration = googleAccount
            }
        }

        if (integration == null) return@withContext Result.failure(Exception("Integration not found"))
        if (integration.status != "connected") return@withContext Result.failure(Exception("Integration not connected"))

        val now = System.currentTimeMillis()
        if (integration.accessToken.isNotBlank() && (now - integration.lastSyncedAt < 50 * 60 * 1000)) {
            return@withContext Result.success(integration.accessToken)
        }

        if (integration.refreshToken.isNotBlank()) {
            val refreshResult = refreshAccessToken(integration.refreshToken)
            if (refreshResult.isSuccess) {
                val tokens = refreshResult.getOrThrow()
                val updated = integration.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken.ifEmpty { integration.refreshToken },
                    lastSyncedAt = System.currentTimeMillis()
                )
                repo.insertIntegration(updated)
                return@withContext Result.success(tokens.accessToken)
            }
        }

        val savedEmail = getSavedGoogleAccountEmail()
        if (savedEmail.isNotBlank()) {
            try {
                val account = Account(savedEmail, "com.google")
                val scopes = getSavedGoogleScopes()
                val freshResult = getDeviceAuthToken(account, scopes)
                if (freshResult.isSuccess) {
                    val newToken = freshResult.getOrThrow()
                    val updated = integration.copy(
                        accessToken = newToken,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    repo.insertIntegration(updated)
                    return@withContext Result.success(newToken)
                }
            } catch (_: Exception) {}
        }

        return@withContext Result.failure(Exception("Gmail auth token expired. Reconnect Gmail in Connections."))
    }

    fun parseTokenResponse(json: String): Result<GoogleTokens> {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val accessToken = obj.get("access_token")?.asString ?: return Result.failure(Exception("No access_token"))
            val refreshToken = obj.get("refresh_token")?.asString ?: ""
            val expiresIn = obj.get("expires_in")?.asInt ?: 3600
            Result.success(GoogleTokens(accessToken, refreshToken, expiresIn))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildTokenRequestBody(code: String, redirectUri: String, codeVerifier: String): String {
        val clientId = getClientId()
        val clientSecret = getClientSecret()
        var body = "code=${encode(code)}&client_id=${encode(clientId)}&redirect_uri=${encode(redirectUri)}&grant_type=authorization_code&code_verifier=${encode(codeVerifier)}"
        if (clientSecret.isNotBlank()) {
            body += "&client_secret=${encode(clientSecret)}"
        }
        return body
    }

    fun exchangeCodeForTokensRaw(code: String, redirectUri: String, codeVerifier: String): Result<GoogleTokens> {
        val body = buildTokenRequestBody(code, redirectUri, codeVerifier)
        val host = "accounts.google.com"
        val ip = InetAddress.getByAddress(host, byteArrayOf(-64, -78, -45, 84))
        val requestText = "POST /o/oauth2/token HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Connection: close\r\n\r\n$body"
        return try {
            var socket: javax.net.ssl.SSLSocket? = null
            try {
                val rawSocket = java.net.Socket()
                rawSocket.connect(java.net.InetSocketAddress(ip, 443), 20000)
                val sslFactory = javax.net.ssl.SSLSocketFactory.getDefault() as javax.net.ssl.SSLSocketFactory
                socket = sslFactory.createSocket(rawSocket, host, 443, true) as javax.net.ssl.SSLSocket
                socket.soTimeout = 30000
                socket.useClientMode = true
                socket.startHandshake()
                socket.outputStream.write(requestText.toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                val reader = socket.inputStream.bufferedReader()
                val statusLine = reader.readLine() ?: return Result.failure(Exception("No response"))
                val statusParts = statusLine.split(" ")
                val statusCode = statusParts.getOrNull(1)?.toIntOrNull() ?: 0
                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isBlank()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }
                val respBody = if (contentLength > 0) {
                    val buf = CharArray(contentLength)
                    reader.read(buf, 0, contentLength)
                    String(buf)
                } else {
                    reader.readText()
                }
                if (statusCode !in 200..299) {
                    return Result.failure(Exception("Token exchange failed: HTTP $statusCode - $respBody"))
                }
                parseTokenResponse(respBody)
            } finally {
                socket?.close()
            }
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            ai.deepcode.android.util.AppLogger.e("GoogleAuth", "Raw token exchange failed", e)
            Result.failure(Exception("Raw exchange: ${e.message} | $sw"))
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

data class GoogleTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val picture: String
)
