package ai.deepcode.android.service.google

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: String,
    val isFolder: Boolean
)

class DriveService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val authService = GoogleAuthService(context)

    suspend fun listRootFiles(maxResults: Int = 20): Result<List<DriveFile>> {
        return listFiles("'root' in parents and trashed = false", maxResults)
    }

    suspend fun listFilesInFolder(folderId: String, maxResults: Int = 20): Result<List<DriveFile>> {
        return listFiles("'$folderId' in parents and trashed = false", maxResults)
    }

    suspend fun searchFiles(query: String, maxResults: Int = 10): Result<List<DriveFile>> {
        return listFiles("name contains '$query' and trashed = false", maxResults)
    }

    private suspend fun listFiles(query: String, maxResults: Int): Result<List<DriveFile>> {
        val tokenResult = authService.getValidAccessToken("google_drive")
        if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
        val token = tokenResult.getOrThrow()
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&pageSize=$maxResults&fields=files(id,name,mimeType,size,modifiedTime)"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Drive API error: HTTP ${response.code} - $body"))
                val json = gson.fromJson(body, JsonObject::class.java)
                val files = json.getAsJsonArray("files") ?: return@withContext Result.success(emptyList())
                val results = files.mapNotNull { item ->
                    val obj = item.asJsonObject
                    val mimeType = obj.get("mimeType")?.asString ?: ""
                    DriveFile(
                        id = obj.get("id")?.asString ?: "",
                        name = obj.get("name")?.asString ?: "",
                        mimeType = mimeType,
                        size = obj.get("size")?.asLong ?: 0L,
                        modifiedTime = obj.get("modifiedTime")?.asString ?: "",
                        isFolder = mimeType == "application/vnd.google-apps.folder"
                    )
                }
                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getFileContent(fileId: String): Result<String> {
        val tokenResult = authService.getValidAccessToken("google_drive")
        if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
        val token = tokenResult.getOrThrow()
        return withContext(Dispatchers.IO) {
            try {
                val exportUrl = "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=text/plain"
                val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"

                val exportRequest = Request.Builder()
                    .url(exportUrl)
                    .header("Authorization", "Bearer $token")
                    .build()
                val exportResponse = client.newCall(exportRequest).execute()
                if (exportResponse.isSuccessful) {
                    return@withContext Result.success(exportResponse.body?.string() ?: "")
                }

                val downloadRequest = Request.Builder()
                    .url(downloadUrl)
                    .header("Authorization", "Bearer $token")
                    .build()
                val downloadResponse = client.newCall(downloadRequest).execute()
                val body = downloadResponse.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!downloadResponse.isSuccessful) return@withContext Result.failure(Exception("Drive download error: HTTP ${downloadResponse.code}"))
                Result.success(body)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFile(name: String, content: String, mimeType: String = "text/plain"): Result<String> {
        val tokenResult = authService.getValidAccessToken("google_drive")
        if (tokenResult.isFailure) return Result.failure(tokenResult.exceptionOrNull()!!)
        val token = tokenResult.getOrThrow()
        return withContext(Dispatchers.IO) {
            try {
                val metadata = JsonObject().apply {
                    addProperty("name", name)
                    addProperty("mimeType", mimeType)
                }
                val multipartBoundary = "drive_upload_${System.currentTimeMillis()}"
                val body = buildMultipartUpload(multipartBoundary, metadata.toString(), content, mimeType)

                val request = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "multipart/related; boundary=$multipartBoundary")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Drive upload error: HTTP ${response.code} - $respBody"))
                val json = gson.fromJson(respBody, JsonObject::class.java)
                val fileId = json.get("id")?.asString ?: return@withContext Result.failure(Exception("No file ID in response"))
                Result.success(fileId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildMultipartUpload(boundary: String, metadata: String, content: String, mimeType: String): okhttp3.RequestBody {
        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        val writer = java.io.OutputStreamWriter(byteArrayOutputStream, Charsets.UTF_8)
        val line = "\r\n"

        writer.write("--${boundary}${line}")
        writer.write("Content-Type: application/json; charset=UTF-8${line}")
        writer.write("${line}${metadata}${line}")

        writer.write("--${boundary}${line}")
        writer.write("Content-Type: $mimeType${line}")
        writer.write("${line}")
        writer.flush()
        byteArrayOutputStream.write(content.toByteArray(Charsets.UTF_8))
        byteArrayOutputStream.flush()
        writer.write("${line}")

        writer.write("--${boundary}--${line}")
        writer.flush()

        val bodyBytes = byteArrayOutputStream.toByteArray()
        writer.close()
        return bodyBytes.toRequestBody(null)
    }
}
