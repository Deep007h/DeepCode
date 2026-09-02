package ai.deepcode.android.util

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DeepCodeOkHttpInterceptor — logs every HTTP request and response through
 * AppLogger so the Log Viewer captures all AI API traffic.
 *
 * Truncates response bodies at MAX_BODY_CHARS to avoid filling memory.
 * For the testing build only.
 */
class DeepCodeOkHttpInterceptor : Interceptor {

    companion object {
        private const val TAG = "Network"
        private const val MAX_BODY_CHARS = 500  // truncate huge AI responses
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request  = chain.request()
        val url      = request.url.toString()
        val method   = request.method

        // Log outgoing request (safe peek at body)
        val reqBodyPreview: String? = try {
            val rb = request.body
            if (rb != null) {
                val buf = okio.Buffer()
                rb.writeTo(buf)
                val raw = buf.readUtf8()
                if (raw.length > MAX_BODY_CHARS) raw.take(MAX_BODY_CHARS) + "…" else raw
            } else null
        } catch (_: Exception) { null }

        AppLogger.d(TAG, "→ $method $url${if (reqBodyPreview != null) "\n  body: $reqBodyPreview" else ""}")

        val startNs = System.nanoTime()

        return try {
            val response     = chain.proceed(request)
            val durationMs   = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            val code         = response.code
            val contentType  = response.body?.contentType()
            val contentTypeStr = contentType?.toString() ?: ""
            val isStreaming  = contentTypeStr.contains("text/event-stream", ignoreCase = true) ||
                    contentTypeStr.contains("application/x-ndjson", ignoreCase = true) ||
                    contentTypeStr.contains("application/stream+json", ignoreCase = true) ||
                    response.header("Transfer-Encoding")?.equals("chunked", ignoreCase = true) == true ||
                    request.header("Accept")?.contains("text/event-stream", ignoreCase = true) == true

            if (isStreaming && response.isSuccessful) {
                AppLogger.logNetwork(
                    method     = method,
                    url        = url,
                    statusCode = code,
                    durationMs = durationMs,
                    error      = null
                )
                AppLogger.d(TAG, "← $code (Streaming SSE started in ${durationMs}ms)")
                return response
            }

            // For non-streaming responses or errors, safely read body bytes with preview
            val bodyBytes    = response.body?.bytes()
            val bodyStr      = bodyBytes?.toString(Charsets.UTF_8) ?: ""
            val bodyPreview  = if (bodyStr.length > MAX_BODY_CHARS) bodyStr.take(MAX_BODY_CHARS) + "…" else bodyStr
            val newBody      = bodyBytes?.toResponseBody(contentType)

            AppLogger.logNetwork(
                method     = method,
                url        = url,
                statusCode = code,
                durationMs = durationMs,
                error      = if (code >= 400) "HTTP $code" else null
            )
            if (bodyPreview.isNotBlank() && code >= 400) {
                AppLogger.d(TAG, "← $code body: $bodyPreview")
            }

            response.newBuilder().body(newBody).build()

        } catch (e: IOException) {
            val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            AppLogger.logNetwork(method = method, url = url, durationMs = durationMs, error = e.message)
            throw e
        }
    }
}
