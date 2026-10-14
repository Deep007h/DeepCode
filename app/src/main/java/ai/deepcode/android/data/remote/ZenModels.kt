package ai.deepcode.android.data.remote

import ai.deepcode.android.domain.model.AIModel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for Zen AI endpoints, model definitions, wire protocol headers, and defaults.
 */
object ZenModels {
    const val BASE_URL = "https://opencode.ai/zen/v1"
    const val GO_BASE_URL = "https://opencode.ai/zen/go/v1"
    const val DEFAULT_FREE = "mimo-v2.6-flash-free"

    // Upstream OpenCode client verification headers
    const val USER_AGENT = "opencode/1.19.0 ai-sdk/provider-utils/4.0.23 runtime/bun/1.3.14"
    const val CLIENT_HEADER_NAME = "x-opencode-client"
    const val CLIENT_HEADER_VALUE = "cli"
    const val PROJECT_HEADER_NAME = "x-opencode-project"
    const val PROJECT_HEADER_VALUE = "global"
    const val HEADER_SESSION_ID = "x-opencode-session"
    const val HEADER_SESSION_ID_ALT = "x-opencode-session-id"
    const val HEADER_REQUEST_ID = "x-opencode-request"
    const val HEADER_SESSION_AFFINITY = "x-session-affinity"

    private var lastTime = 0L
    private var counter = 0L
    private val secureRandom = SecureRandom()
    private val b62Chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()

    // Stable session reuse cache per conversation/identity to avoid 429 FreeUsageLimitError quota exhaustion
    private val sessionCache = ConcurrentHashMap<String, String>()

    /**
     * Canonical OpenCode ID generator matching the official OpenCode CLI algorithm:
     * - Descending for session IDs (ses_): ~n where n = (timestamp_ms * 4096) + counter
     * - Ascending for request IDs (msg_): n where n = (timestamp_ms * 4096) + counter
     * Encoded as 6 bytes hex (12 chars) + 14 random base62 characters (30 chars total).
     * The upstream OpenCode gateway checks the embedded timestamp in the first 12 hex chars
     * to verify the request originates from an authentic, active OpenCode client.
     */
    @Synchronized
    private fun generateCanonicalId(descending: Boolean): String {
        val now = System.currentTimeMillis()
        if (now != lastTime) {
            lastTime = now
            counter = 0L
        }
        counter++
        val n = java.math.BigInteger.valueOf(now).shiftLeft(12).add(java.math.BigInteger.valueOf(counter))
        val a = if (descending) n.not() else n

        val hexSb = StringBuilder(12)
        for (d in 0 until 6) {
            val shift = 40 - 8 * d
            val b = a.shiftRight(shift).and(java.math.BigInteger.valueOf(0xFF)).toInt()
            hexSb.append(String.format(java.util.Locale.US, "%02x", b))
        }

        val randBytes = ByteArray(14)
        secureRandom.nextBytes(randBytes)
        val b62Sb = StringBuilder(14)
        for (b in randBytes) {
            val idx = (b.toInt() and 0xFF) % 62
            b62Sb.append(b62Chars[idx])
        }

        return hexSb.toString() + b62Sb.toString()
    }

    /**
     * Canonical OpenCode session ID generator: "ses_" + 12 hex (timestamp-derived) + 14 base62.
     */
    fun generateSessionId(): String = "ses_" + generateCanonicalId(descending = true)

    /**
     * Canonical OpenCode request ID generator: "msg_" + 12 hex (timestamp-derived) + 14 base62.
     */
    fun generateRequestId(): String = "msg_" + generateCanonicalId(descending = false)

    /**
     * Returns a stable canonical session ID for [conversationId]. If [conversationId] is null or blank,
     * returns a new canonical session ID.
     */
    fun getOrCreateSession(conversationId: String? = null): String {
        val key = conversationId?.trim().orEmpty()
        if (key.isEmpty()) return generateSessionId()
        return sessionCache.computeIfAbsent(key) { generateSessionId() }
    }

    fun clearSessionCache() {
        sessionCache.clear()
    }

    /**
     * OpenCode free tier requires both 'bash' and 'read' tool definitions in the request payload.
     * Inject cloaked decoy tools to satisfy upstream verification without affecting agent operation.
     */
    fun applyDecoyTools(payload: JsonObject, hasCallerTools: Boolean) {
        val toolsArray: JsonArray = if (payload.has("tools") && payload.get("tools").isJsonArray) {
            payload.getAsJsonArray("tools")
        } else {
            JsonArray().also { payload.add("tools", it) }
        }

        val existingNames = mutableSetOf<String>()
        for (elem in toolsArray) {
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val funcName = obj.getAsJsonObject("function")?.get("name")?.asString
                    ?: obj.get("name")?.asString
                if (funcName != null) existingNames.add(funcName.lowercase())
            }
        }

        if (!existingNames.contains("bash")) {
            val bashTool = JsonObject().apply {
                addProperty("type", "function")
                val f = JsonObject().apply {
                    addProperty("name", "bash")
                    addProperty("description", "This tool is currently unavailable and must not be used.")
                    add("parameters", JsonObject().apply { addProperty("type", "object"); add("properties", JsonObject()) })
                }
                add("function", f)
            }
            toolsArray.add(bashTool)
        }

        if (!existingNames.contains("read")) {
            val readTool = JsonObject().apply {
                addProperty("type", "function")
                val f = JsonObject().apply {
                    addProperty("name", "read")
                    addProperty("description", "This tool is currently unavailable and must not be used.")
                    add("parameters", JsonObject().apply { addProperty("type", "object"); add("properties", JsonObject()) })
                }
                add("function", f)
            }
            toolsArray.add(readTool)
        }

        if (!hasCallerTools && !payload.has("tool_choice")) {
            payload.addProperty("tool_choice", "none")
        }
    }

    val KNOWN_FREE_IDS = listOf(
        "mimo-v2.6-flash-free",
        "nemotron-3.5-lightning-free",
        "big-pickle",
        "nemotron-3-ultra-free",
        "space-bunny-free",
        "cat-jam-free",
        "longcat-2.5-preview-free",
        "ling-3.1-flash-free"
    )

    val KNOWN_PAID_IDS = listOf(
        "claude-fable-5-1",
        "claude-fable-5.1",
        "gemini-3.8-flash",
        "gemini-3.7-flash",
        "gpt-6-astra",
        "grok-4.6",
        "glm-5.3-flash",
        "glm-5.3",
        "muse-spark-1.3",
        "muse-spark-1.2",
        "deepseek-v4-flash-vision-exp",
        "deepseek-v4-flash",
        "deepseek-v4-pro",
        "claude-fable-5",
        "claude-opus-5",
        "claude-sonnet-5",
        "claude-sonnet-4-6",
        "gemini-3.6-flash",
        "gemini-3.5-flash",
        "gpt-5.6-sol",
        "gpt-5.5",
        "gpt-5.4",
        "glm-5.2",
        "minimax-m3",
        "kimi-k3",
        "qwen3.6-plus"
    )

    val ALL_KNOWN_IDS: Set<String> = (KNOWN_FREE_IDS + KNOWN_PAID_IDS).toSet()

    /**
     * Map legacy, non-existent, or currently non-functional model IDs
     * to active, valid Zen model IDs.
     */
    fun sanitize(requested: String?, available: List<AIModel>? = null): String {
        if (requested.isNullOrBlank()) return DEFAULT_FREE
        val clean = requested.trim()

        // Proactively redirect known-broken or decommissioned models to the working default free model
        val brokenModels = setOf(
            "deepseek-v4-flash-free",
            "muse-spark-1.3-contributor-free", "muse-spark-1.3-free",
            "muse-spark-1.2-contributor-free", "muse-spark-1.2-free",
            "laguna-s-2.1-free", "laguna-s-2.1",
            "north-mini-code", "pickle",
            "mimo-v2.5-free", "ling-3.0-flash-fin-free", "jev-1.13-free", "step-5-preview-free"
        )
        if (clean.lowercase() in brokenModels) {
            return DEFAULT_FREE
        }

        // Direct match with available or known models (case-insensitive)
        if (available != null && available.any { it.id.equals(clean, ignoreCase = true) }) {
            return available.first { it.id.equals(clean, ignoreCase = true) }.id
        }
        val knownMatch = ALL_KNOWN_IDS.firstOrNull { it.equals(clean, ignoreCase = true) }
        if (knownMatch != null) return knownMatch

        val lower = clean.lowercase()

        // Family / alias mapping
        return when {
            lower.contains("mimo") -> "mimo-v2.6-flash-free"
            lower.contains("ling") -> "ling-3.1-flash-free"
            lower.contains("nemotron") -> "nemotron-3.5-lightning-free"
            lower.contains("pickle") -> "big-pickle"
            lower == "claude-fable-5.1" -> "claude-fable-5-1"
            lower.contains("free") -> DEFAULT_FREE
            else -> available?.firstOrNull { it.id == DEFAULT_FREE }?.id
                ?: available?.firstOrNull { it.isFree }?.id
                ?: DEFAULT_FREE
        }
    }

    /**
     * Identifies upstream OpenCode Zen free-tier gatekeeping or payment/model restriction errors.
     * Note: 429 and FreeUsageLimitError are rotatable rate limits, not fatal restrictions.
     */
    fun isZenFreeTierOrRestrictionError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
        val text = buildString {
            if (httpCode != null) append("HTTP $httpCode ")
            if (error != null) {
                append(error.javaClass.simpleName).append(": ")
                append(error.message.orEmpty()).append(" ")
                append(error.cause?.message.orEmpty()).append(" ")
            }
            if (!responseBody.isNullOrEmpty()) append(responseBody)
        }.lowercase()

        // 429 and rate limit errors should be rotated, not treated as permanent fatal restrictions
        if (text.contains("freeusagelimiterror") || text.contains("rate limit")) {
            return false
        }

        return text.contains("freetiererror") ||
                text.contains("can only be used from within opencode") ||
                text.contains("can only be used in opencode") ||
                text.contains("no payment method") ||
                text.contains("insufficient account funds") ||
                text.contains("model access is disabled")
    }
}
