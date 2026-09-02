package ai.deepcode.android.service.storage

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class TelegramDriveService(private val context: Context) {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("telegram_drive", Context.MODE_PRIVATE)
    private val fileIndex = java.util.concurrent.ConcurrentHashMap<String, String>() // filename -> file_id

    companion object {
        private const val FILE_INDEX_NAME = ".tgdrive_index.json"
        private const val API_BASE = "https://api.telegram.org/bot"
    }

    fun isConfigured(): Boolean {
        return getBotToken().isNotEmpty() && getChatId().isNotEmpty()
    }

    fun getBotToken(): String = prefs.getString("bot_token", "") ?: ""
    fun getChatId(): String = prefs.getString("chat_id", "") ?: ""
    fun getRootPath(): String = prefs.getString("root_path", "/") ?: "/"

    fun saveConfig(token: String, chatId: String, rootPath: String = "/") {
        prefs.edit()
            .putString("bot_token", token)
            .putString("chat_id", chatId)
            .putString("root_path", rootPath)
            .apply()
        loadIndex()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    private fun loadIndex() {
        synchronized(fileIndex) {
            fileIndex.clear()
            val idx = prefs.getString("file_index", null)
            if (idx != null) {
                try {
                    val type = object : TypeToken<Map<String, String>>() {}.type
                    val map: Map<String, String>? = gson.fromJson(idx, type)
                    if (map != null) {
                        fileIndex.putAll(map)
                    }
                } catch (e: Exception) {
                    // Ignore corrupted preferences entry
                }
            }
        }
    }

    private fun saveIndex() {
        synchronized(fileIndex) {
            prefs.edit().putString("file_index", gson.toJson(fileIndex)).apply()
        }
    }

    fun storeFile(name: String, content: ByteArray): Result<String> {
        if (!isConfigured()) return Result.failure(Exception("Telegram Drive not configured"))
        loadIndex()
        return try {
            val token = getBotToken()
            val chatId = getChatId()
            val url = "${API_BASE}${token}/sendDocument"

            val boundary = "Boundary-${System.currentTimeMillis()}"
            val body = buildMultipartBody(boundary, chatId, name, content)

            val request = Request.Builder().url(url).post(body)
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "No response"
            if (!response.isSuccessful) {
                return Result.failure(Exception("Upload failed: $bodyStr"))
            }

            val json = gson.fromJson(bodyStr, Map::class.java)
            val ok = json["ok"] as? Boolean ?: false
            if (!ok) {
                val desc = json["description"]?.toString() ?: "Unknown error"
                return Result.failure(Exception("Telegram API error: $desc"))
            }

            val result = json["result"] as? Map<*, *>
            val document = result?.get("document") as? Map<*, *>
            val fileId = document?.get("file_id") as? String
                ?: result?.get("document")?.let {
                    val d = it as? Map<*, *>
                    d?.get("file_id") as? String
                } ?: return Result.failure(Exception("No file_id in response"))

            fileIndex[name] = fileId
            saveIndex()
            Result.success(fileId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun retrieveFile(name: String): Result<ByteArray> {
        if (!isConfigured()) return Result.failure(Exception("Telegram Drive not configured"))
        loadIndex()
        val fileId = fileIndex[name] ?: return Result.failure(Exception("File not found in index: $name"))
        return try {
            val token = getBotToken()
            val fileUrl = "${API_BASE}${token}/getFile?file_id=$fileId"
            val fileReq = Request.Builder().url(fileUrl).get().build()
            val fileResp = client.newCall(fileReq).execute()
            val fileBody = fileResp.body?.string() ?: return Result.failure(Exception("No response from getFile"))
            val fileJson = gson.fromJson(fileBody, Map::class.java)
            val ok = fileJson["ok"] as? Boolean ?: false
            if (!ok) return Result.failure(Exception("getFile failed: ${fileJson["description"]}"))
            val fileResult = fileJson["result"] as? Map<*, *>
            val filePath = fileResult?.get("file_path") as? String
                ?: return Result.failure(Exception("No file_path in response"))
            val downloadUrl = "${API_BASE}${token}/$filePath"
            val downloadReq = Request.Builder().url(downloadUrl).get().build()
            val downloadResp = client.newCall(downloadReq).execute()
            val bytes = downloadResp.body?.bytes()
                ?: return Result.failure(Exception("Empty download response"))
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listFiles(): List<String> {
        loadIndex()
        return fileIndex.keys.toList()
    }

    fun deleteFile(name: String): Boolean {
        loadIndex()
        val removed = fileIndex.remove(name)
        if (removed != null) {
            saveIndex()
            true
        }
        return removed != null
    }

    fun fileExists(name: String): Boolean {
        loadIndex()
        return fileIndex.containsKey(name)
    }

    fun storeTextFile(name: String, content: String): Result<String> {
        return storeFile(name, content.toByteArray(Charsets.UTF_8))
    }

    fun retrieveTextFile(name: String): Result<String> {
        return retrieveFile(name).map { String(it, Charsets.UTF_8) }
    }

    private fun buildMultipartBody(boundary: String, chatId: String, fileName: String, data: ByteArray): RequestBody {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        val writer = java.io.OutputStreamWriter(byteArrayOutputStream, Charsets.UTF_8)
        val line = "\r\n"

        writer.write("--${boundary}${line}")
        writer.write("Content-Disposition: form-data; name=\"chat_id\"${line}")
        writer.write("Content-Type: text/plain${line}")
        writer.write("${line}${chatId}${line}")

        writer.write("--${boundary}${line}")
        writer.write("Content-Disposition: form-data; name=\"document\"; filename=\"$fileName\"${line}")
        writer.write("Content-Type: application/octet-stream${line}")
        writer.write("${line}")
        writer.flush()
        byteArrayOutputStream.write(data)
        byteArrayOutputStream.flush()
        writer.write("${line}")

        writer.write("--${boundary}--${line}")
        writer.flush()

        val bodyBytes = byteArrayOutputStream.toByteArray()
        writer.close()
        return bodyBytes.toRequestBody("multipart/form-data; boundary=$boundary".toMediaTypeOrNull())
    }
}
