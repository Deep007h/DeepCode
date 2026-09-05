package ai.deepcode.android.service.chatgpt

import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ChatGPT integration bridge for image and docs creation in DeepCode.
 *
 * Guarantees:
 * 1. Single persistent conversation: Stores and reuses a dedicated conversation_id across its entire lifetime.
 * 2. High-precision delivery: Returns clean images, documents, and data via ChatGPT integration for image and docs creation.
 * 3. Robust asset extraction: Resolves DALL-E asset pointers and downloads image bytes directly to local storage.
 */
class ChatGPTBridge private constructor(private val context: Context) {

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
        private const val TAG = "ChatGPTBridge"
        private const val USER_AGENT = "ChatGPT/1.2026.216 (Android 14; Mobile; build 2621621)"

        @Volatile
        private var instance: ChatGPTBridge? = null

        fun getInstance(context: Context): ChatGPTBridge {
            return instance ?: synchronized(this) {
                instance ?: ChatGPTBridge(context.applicationContext).also { instance = it }
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
        val openAiKey = prefs.getApiKey("openai").trim().ifEmpty { prefs.getSetting("openai_api_key", "").trim() }
        if (openAiKey.isNotEmpty()) return openAiKey
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
     * Uploads an image file to ChatGPT web backend for multimodal prompts.
     */
    suspend fun uploadFileForMultimodal(file: File, accessToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val reqJson = JsonObject().apply {
                addProperty("file_name", file.name)
                addProperty("file_size", file.length())
                addProperty("use_case", "multimodal")
            }
            val reqBuilder = Request.Builder()
                .url("https://chatgpt.com/backend-api/files")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("oai-device-id", getDeviceId())
                .post(reqJson.toString().toRequestBody(jsonMediaType))

            getAccountId()?.let { reqBuilder.header("ChatGPT-Account-Id", it) }

            val resp = client.newCall(reqBuilder.build()).execute()
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful || body.isEmpty()) {
                AppLogger.w(TAG, "File upload registration failed HTTP ${resp.code}: $body")
                return@withContext null
            }

            val json = JsonParser.parseString(body).asJsonObject
            val uploadUrl = json.get("upload_url")?.asString ?: return@withContext null
            val fileId = json.get("file_id")?.asString ?: return@withContext null

            // Upload the file bytes to Azure Blob
            val putReq = Request.Builder()
                .url(uploadUrl)
                .header("x-ms-blob-type", "BlockBlob")
                .put(file.readBytes().toRequestBody("application/octet-stream".toMediaType()))
                .build()

            val putResp = client.newCall(putReq).execute()
            if (!putResp.isSuccessful) {
                AppLogger.w(TAG, "File blob PUT failed HTTP ${putResp.code}")
                return@withContext null
            }

            // Mark uploaded
            val completeReq = Request.Builder()
                .url("https://chatgpt.com/backend-api/files/$fileId/uploaded")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("oai-device-id", getDeviceId())
                .post("{}".toRequestBody(jsonMediaType))
            getAccountId()?.let { completeReq.header("ChatGPT-Account-Id", it) }

            val completeResp = client.newCall(completeReq.build()).execute()
            if (completeResp.isSuccessful) {
                AppLogger.i(TAG, "Successfully uploaded image file to ChatGPT: $fileId")
                fileId
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed uploading file to ChatGPT: ${e.message}", e)
            null
        }
    }

    /**
     * Resolves a local image path or URL to base64 Data URL for OpenAI vision API.
     */
    fun resolveImageToBase64DataUrl(pathOrUrl: String): String? {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("data:image/")) {
            return pathOrUrl
        }
        val file = File(pathOrUrl.removePrefix("file://"))
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val maxDim = 1536
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (newW, newH) = if (ratio > 1f) {
                    maxDim to (maxDim / ratio).toInt()
                } else {
                    (maxDim * ratio).toInt() to maxDim
                }
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val bytes = stream.toByteArray()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed resolving image to base64: ${e.message}", e)
            null
        }
    }

    private suspend fun streamOpenAiCompletions(
        apiKey: String,
        prompt: String,
        imageFile: File?,
        onToken: ((String) -> Unit)?,
        model: String = "gpt-4o"
    ): SessionTurnResult = withContext(Dispatchers.IO) {
        val rootJson = JsonObject().apply {
            addProperty("model", model)
            addProperty("stream", true)
            val messagesArr = JsonArray()
            val userMsg = JsonObject().apply {
                addProperty("role", "user")
                if (imageFile != null && imageFile.exists()) {
                    val parts = JsonArray()
                    parts.add(JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", prompt)
                    })
                    val base64DataUrl = resolveImageToBase64DataUrl(imageFile.absolutePath)
                    if (base64DataUrl != null) {
                        parts.add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", base64DataUrl)
                            })
                        })
                    }
                    add("content", parts)
                } else {
                    addProperty("content", prompt)
                }
            }
            messagesArr.add(userMsg)
            add("messages", messagesArr)
        }

        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(rootJson.toString().toRequestBody(jsonMediaType))
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            val errBody = resp.body?.string() ?: ""
            throw IllegalStateException("OpenAI API HTTP ${resp.code}: $errBody")
        }

        val inputStream = resp.body?.byteStream() ?: throw IllegalStateException("Empty response from OpenAI")
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val collected = StringBuilder()

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.substring(5).trim()
                if (data == "[DONE]") break
                try {
                    val json = JsonParser.parseString(data).asJsonObject
                    val choices = json.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val delta = choices[0].asJsonObject.getAsJsonObject("delta")
                        val content = delta?.get("content")?.asString
                        if (!content.isNullOrEmpty()) {
                            collected.append(content)
                            onToken?.invoke(content)
                        }
                    }
                } catch (_: Exception) {}
            }
        } finally {
            reader.close()
        }

        SessionTurnResult(
            text = collected.toString().trim(),
            assetPointers = emptyList(),
            conversationId = "",
            messageId = ""
        )
    }

    /**
     * Sends a prompt to ChatGPT strictly using the single persistent conversation.
     * Captures text and image outputs and updates conversation/parent message IDs.
     */
    private suspend fun executeSingleSessionTurn(
        prompt: String,
        model: String = "gpt-4o",
        imageFile: File? = null,
        onToken: ((String) -> Unit)? = null
    ): SessionTurnResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        if (token.isBlank()) {
            throw IllegalStateException("ChatGPT access token not configured. Please set your token in DeepCode Settings.")
        }

        if (token.startsWith("sk-")) {
            return@withContext streamOpenAiCompletions(token, prompt, imageFile, onToken, model)
        }

        var fileId: String? = null
        if (imageFile != null && imageFile.exists()) {
            fileId = uploadFileForMultimodal(imageFile, token)
        }

        val sentinelToken = fetchSentinelToken(token)

        // Read single conversation state
        var conversationId: String? = prefs.getChatGPTConversationId().takeIf { it.isNotBlank() }
        var parentMessageId: String? = prefs.getChatGPTParentMessageId().takeIf { it.isNotBlank() }

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
                if (fileId != null) {
                    val contentObj = JsonObject().apply {
                        addProperty("content_type", "multimodal_text")
                        val partsArr = JsonArray()
                        val assetObj = JsonObject().apply {
                            addProperty("content_type", "image_asset_pointer")
                            addProperty("asset_pointer", "file-service://$fileId")
                            addProperty("size_bytes", imageFile!!.length())
                        }
                        partsArr.add(assetObj)
                        partsArr.add(prompt)
                        add("parts", partsArr)
                    }
                    add("content", contentObj)
                } else {
                    add("content", JsonObject().apply {
                        addProperty("content_type", "text")
                        add("parts", JsonArray().apply { add(prompt) })
                    })
                }
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
                                    val delta = if (text.startsWith(lastExtractedText)) {
                                        text.substring(lastExtractedText.length)
                                    } else {
                                        text
                                    }
                                    lastExtractedText = text
                                    collectedText.clear()
                                    collectedText.append(text)
                                    onToken?.invoke(delta)
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
            prefs.saveChatGPTConversationId(returnedConvId)
            AppLogger.i(TAG, "Persisted single session conversation_id: $returnedConvId")
        }
        if (!returnedMsgId.isNullOrBlank()) {
            prefs.saveChatGPTParentMessageId(returnedMsgId)
        }

        SessionTurnResult(
            text = collectedText.toString().trim(),
            assetPointers = extractedAssets.distinct(),
            conversationId = returnedConvId ?: conversationId ?: "",
            messageId = returnedMsgId ?: ""
        )
    }

    /**
     * Generates an image using the ChatGPT integration for image and docs creation and returns a local file path
     * formatted as [image:/path/to/img.png] or raw path.
     */
    suspend fun generateImage(userPrompt: String): String = withContext(Dispatchers.IO) {
        val cleanPrompt = userPrompt.trim()
        AppLogger.i(TAG, "Executing ChatGPT image generation: $cleanPrompt")

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
     * Generates a structured document using the ChatGPT integration for image and docs creation and returns
     * either clean Markdown or writes it to a file.
     */
    suspend fun generateDocument(prompt: String, docType: String = "markdown"): String = withContext(Dispatchers.IO) {
        val cleanPrompt = prompt.trim()
        AppLogger.i(TAG, "Executing ChatGPT document generation: $cleanPrompt")

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
     * Executes a general task in the ChatGPT single session.
     */
    suspend fun executeTask(prompt: String): String = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Executing ChatGPT task: ${prompt.take(60)}")
        val turnResult = executeSingleSessionTurn(prompt, model = "gpt-4o")
        turnResult.text
    }

    /**
     * Streams a turn to ChatGPT (either session token or OpenAI API key) with optional image input.
     */
    suspend fun streamTurn(
        prompt: String,
        imagePath: String? = null,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            if (token.isBlank()) {
                throw IllegalStateException("ChatGPT access token not configured. Please connect ChatGPT in Integrations or add an OpenAI API key in Settings.")
            }

            var file: File? = null
            if (!imagePath.isNullOrBlank()) {
                val cleanPath = imagePath.trim().removePrefix("[image:").removeSuffix("]").trim()
                if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
                    val local = downloadAndSaveImageLocally(cleanPath, token)
                    val f = File(local.removePrefix("file://"))
                    if (f.exists() && f.isFile) file = f
                } else {
                    val f = File(cleanPath.removePrefix("file://"))
                    if (f.exists() && f.isFile) file = f
                }
            }

            val result = executeSingleSessionTurn(
                prompt = prompt,
                model = "gpt-4o",
                imageFile = file,
                onToken = onToken
            )
            var finalText = result.text
            if (result.assetPointers.isNotEmpty()) {
                for (asset in result.assetPointers) {
                    val local = downloadAndSaveImageLocally(asset, token)
                    if (local.isNotBlank() && !local.startsWith("http")) {
                        finalText = if (finalText.isBlank()) "[image:$local]" else "$finalText\n\n[image:$local]"
                    }
                }
            }
            // Check for sediment / file- pointers in markdown
            val regex = Regex("""!\[.*?\]\((sediment://[^\)]+|file-[^\)]+)\)""")
            val matches = regex.findAll(finalText).toList()
            for (m in matches) {
                val source = m.groupValues[1]
                val local = downloadAndSaveImageLocally(source, token)
                if (local.isNotBlank() && !local.startsWith("http")) {
                    finalText = finalText.replace(m.value, "[image:$local]")
                }
            }
            onComplete(finalText)
        } catch (t: Throwable) {
            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
            AppLogger.e(TAG, "streamTurn failed: ${t.message}", t)
            onError(t)
        }
    }

    private data class SessionTurnResult(
        val text: String,
        val assetPointers: List<String>,
        val conversationId: String,
        val messageId: String
    )
}
