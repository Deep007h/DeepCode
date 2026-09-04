package ai.deepcode.android.service.chatgpt

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Headless ChatGPT Bridge for DeepCode.
 *
 * Guarantees:
 * 1. Single persistent conversation: Stores and reuses a dedicated conversation_id across its entire lifetime.
 * 2. Headless delivery: Returns clean images, documents, and data without chat UI or casual conversational filler.
 * 3. Robust asset extraction: Resolves DALL-E asset pointers and downloads image bytes directly to local storage.
 */
class ChatGPTHeadlessBridge private constructor(private val context: Context) {

    private val prefs: EncryptedPrefs by lazy { EncryptedPrefs.getInstance(context) }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    companion object {
        private const val TAG = "ChatGPTHeadlessBridge"
        private const val USER_AGENT = "ChatGPT/1.2026.216 (Android 14; Mobile; build 2621621)"

        @Volatile
        private var instance: ChatGPTHeadlessBridge? = null

        fun getInstance(context: Context): ChatGPTHeadlessBridge {
            return instance ?: synchronized(this) {
                instance ?: ChatGPTHeadlessBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    fun isConfigured(): Boolean {
        return getAccessToken().isNotBlank()
    }

    private fun getAccessToken(): String {
        val token = prefs.getChatGPTAccessToken().trim()
        if (token.isNotEmpty()) return token
        val apiKey = prefs.getApiKey("chatgpt").trim()
        if (apiKey.isNotEmpty()) return apiKey
        // Fallback to general setting if stored there
        val general = prefs.getSetting("chatgpt_token", "").trim()
        if (general.isNotEmpty()) return general

        // Direct fallback to Room database integrations table
        try {
            val dbIntegration = ai.deepcode.android.data.local.AppDatabase.getDatabase(context)
                .integrationDao().getIntegrationByAppIdSync("chatgpt")
            if (dbIntegration != null && dbIntegration.status == "connected" && dbIntegration.accessToken.isNotBlank()) {
                val dbToken = dbIntegration.accessToken.trim()
                prefs.saveChatGPTAccessToken(dbToken)
                parseAndSaveJwtMetadata(dbToken)
                return dbToken
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed reading ChatGPT token from database: ${e.message}")
        }
        return ""
    }

    fun parseAndSaveJwtMetadata(token: String) {
        if (!token.startsWith("ey") || !token.contains(".")) return
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
                val payloadJson = String(decoded, Charsets.UTF_8)
                val json = JsonParser.parseString(payloadJson).asJsonObject

                // Extract account ID
                val authObj = if (json.has("https://api.openai.com/auth")) json.getAsJsonObject("https://api.openai.com/auth") else null
                val accountId = authObj?.get("chatgpt_account_id")?.asString
                    ?: if (json.has("chatgpt_account_id")) json.get("chatgpt_account_id").asString else null
                if (!accountId.isNullOrBlank()) {
                    prefs.saveChatGPTAccountId(accountId)
                    AppLogger.i(TAG, "Extracted and saved ChatGPT account ID: $accountId")
                }

                // Extract user profile
                val profileObj = if (json.has("https://api.openai.com/profile")) json.getAsJsonObject("https://api.openai.com/profile") else null
                val email = profileObj?.get("email")?.asString ?: if (json.has("email")) json.get("email").asString else null
                val name = profileObj?.get("name")?.asString ?: if (json.has("name")) json.get("name").asString else null
                if (!email.isNullOrBlank()) prefs.saveSetting("chatgpt_user_email", email)
                if (!name.isNullOrBlank()) prefs.saveSetting("chatgpt_user_name", name)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed parsing JWT claims: ${e.message}")
        }
    }

    private fun getAccountId(): String? {
        val accId = prefs.getChatGPTAccountId().trim()
        if (accId.isNotEmpty()) return accId
        val token = getAccessToken()
        if (token.isNotBlank()) {
            parseAndSaveJwtMetadata(token)
            val updated = prefs.getChatGPTAccountId().trim()
            if (updated.isNotEmpty()) return updated
        }
        return null
    }

    private fun getDeviceId(): String {
        var deviceId = prefs.getSetting("chatgpt_device_id", "")
        if (deviceId.isBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.saveSetting("chatgpt_device_id", deviceId)
        }
        return deviceId
    }

    suspend fun fetchAccessTokenFromSessionCookie(cookies: String): String? = withContext(Dispatchers.IO) {
        val trimmed = cookies.trim()
        if (trimmed.isEmpty()) return@withContext null

        // If user provided a raw JWT token directly
        if (trimmed.startsWith("ey") && trimmed.contains(".")) {
            prefs.saveChatGPTAccessToken(trimmed)
            parseAndSaveJwtMetadata(trimmed)
            AppLogger.i(TAG, "Saved raw access token directly")
            return@withContext trimmed
        }

        val cookieHeader = if (trimmed.contains("=")) trimmed else "__Secure-next-auth.session-token=$trimmed"

        try {
            val req = Request.Builder()
                .url("https://chatgpt.com/api/auth/session")
                .header("Cookie", cookieHeader)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotEmpty()) {
                val json = JsonParser.parseString(body).asJsonObject
                if (json.has("accessToken")) {
                    val token = json.get("accessToken").asString
                    var email: String? = null
                    var name: String? = null
                    if (json.has("user") && !json.get("user").isJsonNull) {
                        val uObj = json.getAsJsonObject("user")
                        if (uObj.has("email")) email = uObj.get("email").asString
                        if (uObj.has("name")) name = uObj.get("name").asString
                    }
                    prefs.saveChatGPTAccessToken(token)
                    parseAndSaveJwtMetadata(token)
                    if (!email.isNullOrEmpty()) prefs.saveSetting("chatgpt_user_email", email)
                    if (!name.isNullOrEmpty()) prefs.saveSetting("chatgpt_user_name", name)
                    AppLogger.i(TAG, "Successfully authenticated ChatGPT via session cookie (User: ${name ?: email})")
                    return@withContext token
                }
            }
            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching access token from session cookies", e)
            null
        }
    }

    fun disconnect() {
        prefs.saveChatGPTAccessToken("")
        prefs.saveChatGPTAccountId("")
        prefs.saveSetting("chatgpt_user_email", "")
        prefs.saveSetting("chatgpt_user_name", "")
    }

    /**
     * Fetches the Sentinel anti-abuse requirement token from ChatGPT.
     */
    private suspend fun fetchSentinelToken(accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("https://chatgpt.com/backend-api/sentinel/chat-requirements")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("oai-device-id", getDeviceId())
                .post("{}".toRequestBody(jsonMediaType))

            getAccountId()?.let { reqBuilder.header("ChatGPT-Account-Id", it) }

            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotEmpty()) {
                val json = JsonParser.parseString(body).asJsonObject
                if (json.has("token")) json.get("token").asString else null
            } else {
                AppLogger.w(TAG, "Sentinel check HTTP ${resp.code}: $body")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Sentinel token fetch failed", e)
            null
        }
    }

    /**
     * Resolves file IDs (like sediment://file-... or file-...) into direct download URLs.
     */
    suspend fun resolveFileDownloadUrl(fileId: String, accessToken: String): String? = withContext(Dispatchers.IO) {
        val cleanFileId = fileId.removePrefix("sediment://").trim()
        val accountId = getAccountId()

        try {
            // 1. Metadata check
            val metaUrl = "https://chatgpt.com/backend-api/files/$cleanFileId"
            val reqMeta = Request.Builder()
                .url(metaUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
            if (accountId != null) reqMeta.header("ChatGPT-Account-Id", accountId)

            val respMeta = client.newCall(reqMeta.build()).execute()
            val bodyMeta = respMeta.body?.string() ?: ""
            if (respMeta.isSuccessful && bodyMeta.isNotEmpty()) {
                try {
                    val jsonMeta = JsonParser.parseString(bodyMeta).asJsonObject
                    if (jsonMeta.has("download_url") && !jsonMeta.get("download_url").isJsonNull) {
                        return@withContext jsonMeta.get("download_url").asString
                    }
                } catch (_: Exception) {}
            }

            // 2. Redirect location check on download endpoint
            val downloadUrl = "https://chatgpt.com/backend-api/files/$cleanFileId/download"
            val reqDownload = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
            if (accountId != null) reqDownload.header("ChatGPT-Account-Id", accountId)

            val respDownload = noRedirectClient.newCall(reqDownload.build()).execute()
            if (respDownload.code in 300..399) {
                val location = respDownload.header("Location")
                if (!location.isNullOrEmpty()) return@withContext location
            }

            val bodyDownload = respDownload.body?.string() ?: ""
            if (respDownload.isSuccessful && bodyDownload.isNotEmpty()) {
                try {
                    val jsonParser = JsonParser.parseString(bodyDownload).asJsonObject
                    if (jsonParser.has("download_url") && !jsonParser.get("download_url").isJsonNull) {
                        return@withContext jsonParser.get("download_url").asString
                    }
                } catch (_: Exception) {}
            }

            // 3. Estuary fallback
            val estuaryUrl = "https://chatgpt.com/backend-api/estuary/content?file_id=$cleanFileId"
            val reqEstuary = Request.Builder()
                .url(estuaryUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
            if (accountId != null) reqEstuary.header("ChatGPT-Account-Id", accountId)

            val respEstuary = noRedirectClient.newCall(reqEstuary.build()).execute()
            if (respEstuary.code in 300..399) {
                val loc = respEstuary.header("Location")
                if (!loc.isNullOrEmpty()) return@withContext loc
            }

            downloadUrl
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resolving file download URL for $fileId", e)
            "https://chatgpt.com/backend-api/files/$cleanFileId/download"
        }
    }

    /**
     * Downloads an image URL or fileId to a local PNG file on disk.
     */
    private suspend fun downloadAndSaveImageLocally(source: String, accessToken: String): String = withContext(Dispatchers.IO) {
        val targetUrl = if (source.startsWith("file-") || source.startsWith("file_") || source.startsWith("sediment://")) {
            resolveFileDownloadUrl(source, accessToken) ?: source
        } else {
            source
        }

        try {
            val reqBuilder = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)

            if (targetUrl.contains("chatgpt.com")) {
                reqBuilder.header("Authorization", "Bearer $accessToken")
                getAccountId()?.let { reqBuilder.header("ChatGPT-Account-Id", it) }
            }

            val resp = client.newCall(reqBuilder.build()).execute()
            if (resp.isSuccessful && resp.body != null) {
                val bytes = resp.body!!.bytes()
                if (bytes.isNotEmpty()) {
                    val picturesDir = File(context.filesDir, "Pictures").apply { if (!exists()) mkdirs() }
                    val outFile = File(picturesDir, "chatgpt_img_${System.currentTimeMillis()}.png")
                    outFile.writeBytes(bytes)
                    AppLogger.i(TAG, "Saved ChatGPT image locally to ${outFile.absolutePath} (${bytes.size} bytes)")
                    return@withContext outFile.absolutePath
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed downloading image from $targetUrl", e)
        }
        source
    }

    /**
     * Recursively extracts file IDs and image URLs from JSON element tree.
     */
    private fun extractFileIdsAndUrls(element: JsonElement, outList: MutableList<String>) {
        if (element.isJsonPrimitive) {
            val str = element.asString
            val fileRegex = Regex("(sediment://file[_-][a-zA-Z0-9_-]+|file-[a-zA-Z0-9_-]{8,}|file_[a-zA-Z0-9_-]{8,})")
            fileRegex.findAll(str).forEach { m -> outList.add(m.groupValues[1]) }

            val urlRegex = Regex("(https?://[^\\s\"'\\]\\)]+)")
            urlRegex.findAll(str).forEach { m ->
                val u = m.groupValues[1]
                if (u.contains("oaiusercontent.com") || u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".webp")) {
                    outList.add(u)
                }
            }
        } else if (element.isJsonObject) {
            val obj = element.asJsonObject
            for ((key, value) in obj.entrySet()) {
                if ((key == "asset_pointer" || key == "file_id" || key == "download_url" || key == "url") && value.isJsonPrimitive) {
                    val valStr = value.asString
                    if (valStr.startsWith("file-") || valStr.startsWith("file_") || valStr.startsWith("sediment://") || valStr.contains("oaiusercontent.com")) {
                        outList.add(valStr)
                    }
                }
                extractFileIdsAndUrls(value, outList)
            }
        } else if (element.isJsonArray) {
            for (item in element.asJsonArray) {
                extractFileIdsAndUrls(item, outList)
            }
        }
    }

    /**
     * Sends a prompt to ChatGPT strictly using the single persistent conversation.
     * Captures text and image outputs and updates conversation/parent message IDs.
     */
    private suspend fun executeSingleSessionTurn(
        prompt: String,
        model: String = "gpt-4o"
    ): SessionTurnResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        if (token.isBlank()) {
            throw IllegalStateException("ChatGPT access token not configured. Please set your token in DeepCode Settings.")
        }

        val sentinelToken = fetchSentinelToken(token)

        // Read single conversation state
        var conversationId: String? = prefs.getChatGPTHeadlessConversationId().takeIf { it.isNotBlank() }
        var parentMessageId: String? = prefs.getChatGPTHeadlessParentMessageId().takeIf { it.isNotBlank() }

        val messageId = UUID.randomUUID().toString()
        val currentParentId = parentMessageId ?: UUID.randomUUID().toString()

        val rootJson = JsonObject().apply {
            addProperty("action", "next")
            addProperty("model", model)
            if (!conversationId.isNullOrBlank()) {
                addProperty("conversation_id", conversationId)
            }
            addProperty("parent_message_id", currentParentId)
            addProperty("timezone_offset_min", -330)

            val messagesArr = JsonArray()
            val userMsg = JsonObject().apply {
                addProperty("id", messageId)
                add("author", JsonObject().apply { addProperty("role", "user") })
                add("content", JsonObject().apply {
                    addProperty("content_type", "text")
                    add("parts", JsonArray().apply { add(prompt) })
                })
                add("metadata", JsonObject())
            }
            messagesArr.add(userMsg)
            add("messages", messagesArr)
        }

        val reqBuilder = Request.Builder()
            .url("https://chatgpt.com/backend-api/conversation")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("oai-device-id", getDeviceId())
            .post(rootJson.toString().toRequestBody(jsonMediaType))

        getAccountId()?.let { reqBuilder.header("ChatGPT-Account-Id", it) }
        if (!sentinelToken.isNullOrEmpty()) {
            reqBuilder.header("OpenAI-Sentinel-Chat-Requirements-Token", sentinelToken)
        }

        val resp = client.newCall(reqBuilder.build()).execute()
        if (!resp.isSuccessful) {
            val errBody = resp.body?.string() ?: ""
            throw IllegalStateException("ChatGPT server HTTP ${resp.code}: $errBody")
        }

        val inputStream = resp.body?.byteStream()
            ?: throw IllegalStateException("Empty response from ChatGPT")

        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val collectedText = StringBuilder()
        val extractedAssets = mutableListOf<String>()
        var returnedConvId: String? = null
        var returnedMsgId: String? = null
        var lastExtractedText = ""

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (!l.startsWith("data:")) continue
                val dataContent = l.substring(5).trim()
                if (dataContent == "[DONE]") break

                try {
                    val json = JsonParser.parseString(dataContent).asJsonObject

                    if (json.has("conversation_id") && !json.get("conversation_id").isJsonNull) {
                        returnedConvId = json.get("conversation_id").asString
                    }

                    if (json.has("error") && !json.get("error").isJsonNull) {
                        val errObj = json.getAsJsonObject("error")
                        val msg = if (errObj.has("message")) errObj.get("message").asString else "Unknown stream error"
                        throw IllegalStateException(msg)
                    }

                    if (json.has("message") && !json.get("message").isJsonNull) {
                        val msgObj = json.getAsJsonObject("message")

                        if (msgObj.has("id")) {
                            returnedMsgId = msgObj.get("id").asString
                        }

                        // Check author role to avoid prompt echo
                        val authorRole = msgObj.getAsJsonObject("author")?.get("role")?.asString
                        if (authorRole == "user") continue

                        // Extract text
                        val contentObj = msgObj.getAsJsonObject("content")
                        if (contentObj != null && contentObj.has("parts")) {
                            val parts = contentObj.getAsJsonArray("parts")
                            if (parts.size() > 0) {
                                val p = parts[0]
                                val text = if (p.isJsonPrimitive) p.asString else ""
                                if (text.isNotEmpty() && text != lastExtractedText) {
                                    lastExtractedText = text
                                    collectedText.clear()
                                    collectedText.append(text)
                                }
                            }
                        }

                        // Extract image assets
                        extractFileIdsAndUrls(msgObj, extractedAssets)
                    }
                } catch (pe: Exception) {
                    if (pe is IllegalStateException) throw pe
                }
            }
        } finally {
            reader.close()
        }

        // Persist single conversation state to guarantee single-session reuse
        if (!returnedConvId.isNullOrBlank()) {
            prefs.saveChatGPTHeadlessConversationId(returnedConvId)
            AppLogger.i(TAG, "Persisted single session conversation_id: $returnedConvId")
        }
        if (!returnedMsgId.isNullOrBlank()) {
            prefs.saveChatGPTHeadlessParentMessageId(returnedMsgId)
        }

        SessionTurnResult(
            text = collectedText.toString().trim(),
            assetPointers = extractedAssets.distinct(),
            conversationId = returnedConvId ?: conversationId ?: "",
            messageId = returnedMsgId ?: ""
        )
    }

    /**
     * Generates an image using the headless ChatGPT session and returns a local file path
     * formatted as [image:/path/to/img.png] or raw path.
     */
    suspend fun generateImage(userPrompt: String): String = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()
        AppLogger.i(TAG, "Executing headless image generation: $cleanPrompt")

        val token = getAccessToken()
        val turnResult = executeSingleSessionTurn(
            prompt = "Generate an image of: $cleanPrompt. Do not explain, just generate the image.",
            model = "gpt-4o"
        )

        // If asset pointers were captured, download the first one locally
        if (turnResult.assetPointers.isNotEmpty()) {
            for (asset in turnResult.assetPointers) {
                val localPath = downloadAndSaveImageLocally(asset, token)
                if (localPath.isNotBlank() && !localPath.startsWith("http")) {
                    return@withContext localPath.removePrefix("[image:").removeSuffix("]").trim()
                }
            }
        }

        // Check if markdown returned an image tag with an asset pointer
        val regex = Regex("""!\[.*?\]\((sediment://[^\)]+|file-[^\)]+|https?://[^\)]+)\)""")
        val match = regex.find(turnResult.text)
        if (match != null) {
            val imgSource = match.groupValues[1]
            val localPath = downloadAndSaveImageLocally(imgSource, token)
            return@withContext localPath.removePrefix("[image:").removeSuffix("]").trim()
        }

        if (turnResult.text.isNotEmpty()) {
            // Check if text itself contains an image URL
            val urlRegex = Regex("""(https?://\S+\.(?:png|jpg|jpeg|webp))""")
            val urlMatch = urlRegex.find(turnResult.text)
            if (urlMatch != null) {
                val localPath = downloadAndSaveImageLocally(urlMatch.groupValues[1], token)
                return@withContext localPath.removePrefix("[image:").removeSuffix("]").trim()
            }
        }

        throw IllegalStateException("No image generated by ChatGPT for: $cleanPrompt. Response: ${turnResult.text.take(150)}")
    }

    /**
     * Generates a structured document using the headless ChatGPT session and returns
     * either clean Markdown or writes it to a file.
     */
    suspend fun generateDocument(prompt: String, docType: String = "markdown"): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        AppLogger.i(TAG, "Executing headless document generation: $cleanPrompt")

        val directive = buildString {
            appendLine("You are an autonomous document generation engine.")
            appendLine("Format: $docType (rich Markdown with clear title, headers, sections, bullet points, and tables where applicable).")
            appendLine("CRITICAL INSTRUCTION: Output ONLY the document content itself. Do NOT include conversational greetings, preambles, intros (e.g. 'Sure, here is...'), or concluding remarks.")
            appendLine()
            appendLine("Document requirements:")
            append(cleanPrompt)
        }

        val turnResult = executeSingleSessionTurn(directive, model = "gpt-4o")
        val docContent = turnResult.text

        // Save document to local disk
        val docsDir = File(context.filesDir, "Documents").apply { if (!exists()) mkdirs() }
        val slug = cleanPrompt.take(24).replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifEmpty { "doc" }
        val outFile = File(docsDir, "${slug}_${System.currentTimeMillis()}.md")
        outFile.writeText(docContent)
        AppLogger.i(TAG, "Saved generated document to ${outFile.absolutePath}")

        "[file:${outFile.absolutePath}]\n\n$docContent"
    }

    /**
     * Executes a general task in the headless ChatGPT single session.
     */
    suspend fun executeTask(prompt: String): String = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Executing headless task: ${prompt.take(60)}")
        val turnResult = executeSingleSessionTurn(prompt, model = "gpt-4o")
        turnResult.text
    }

    private data class SessionTurnResult(
        val text: String,
        val assetPointers: List<String>,
        val conversationId: String,
        val messageId: String
    )
}
