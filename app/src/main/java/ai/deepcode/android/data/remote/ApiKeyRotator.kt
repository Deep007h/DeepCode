package ai.deepcode.android.data.remote

import ai.deepcode.android.data.local.EncryptedPrefs
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages per-provider API key rotation.
 *
 * Supports up to 6 keys per provider with distinct handling for:
 * 1. Rate limiting / quota exhaustion (HTTP 429, temporary cooldown).
 * 2. Invalid authentication / expired keys (HTTP 401/403, extended cooldown / auth-invalid tracking).
 * 3. Bad request / invalid model names (HTTP 400, model not found - NOT rotated).
 *
 * When all keys are currently in cooldown, it seamlessly falls back to the least-recently
 * exhausted configured key (earliest cooldown expiry) skipping any permanently invalid auth keys.
 */
object ApiKeyRotator {

    const val DEFAULT_RATE_LIMIT_COOLDOWN_MS = 60_000L // 60 seconds
    const val DEFAULT_AUTH_INVALID_COOLDOWN_MS = 86_400_000L // 24 hours

    enum class RotationReason {
        RATE_LIMIT,
        INVALID_AUTH,
        SERVER_OVERLOAD,
        NOT_ROTATABLE
    }

    // Key: "{canonicalStorageId}:{slotIndex}" → Value: cooldown expiry timestamp (ms)
    private val exhaustedKeys = ConcurrentHashMap<String, Long>()
    // Key: raw trimmed apiKey string → Value: cooldown expiry timestamp (ms)
    private val exhaustedKeyValues = ConcurrentHashMap<String, Long>()

    // Track keys identified as permanently invalid / unauthorized until updated
    private val authInvalidKeys = ConcurrentHashMap.newKeySet<String>()
    private val authInvalidSlots = ConcurrentHashMap.newKeySet<String>()

    fun getCanonicalStorageId(storageId: String): String {
        return when (storageId.trim().lowercase()) {
            "zen", "opencode-zen", "opencode", "zenmux", "zenmux-free" -> "zen"
            "together", "together-ai" -> "together"
            "fireworks", "fireworks-ai" -> "fireworks"
            "nvidia", "nvidia-nim" -> "nvidia"
            "cerebras", "cerebrus" -> "cerebras"
            "gmi", "gmi-cloud" -> "gmi"
            "atria", "atria-ai" -> "atria"
            "gemini", "google gemini", "google-gemini", "gemini-business", "gemini-web", "google" -> "gemini"
            else -> storageId.trim().lowercase()
        }
    }

    private fun slotKey(storageId: String, slotIndex: Int): String {
        return "${getCanonicalStorageId(storageId)}:$slotIndex"
    }

    /**
     * Mark a specific key slot and/or key string as exhausted with a cooldown period.
     */
    fun markKeyExhausted(
        storageId: String,
        slotIndex: Int,
        keyString: String? = null,
        cooldownMs: Long = DEFAULT_RATE_LIMIT_COOLDOWN_MS,
        reason: RotationReason = RotationReason.RATE_LIMIT
    ) {
        val canonicalId = getCanonicalStorageId(storageId)
        val expiry = System.currentTimeMillis() + cooldownMs
        val cleanKey = keyString?.trim().orEmpty()

        if (slotIndex > 0) {
            exhaustedKeys[slotKey(canonicalId, slotIndex)] = expiry
            if (reason == RotationReason.INVALID_AUTH) {
                authInvalidSlots.add(slotKey(canonicalId, slotIndex))
            }
        }

        if (cleanKey.isNotEmpty() && cleanKey != "zen-free" && cleanKey != "ollama") {
            exhaustedKeyValues[cleanKey] = expiry
            if (reason == RotationReason.INVALID_AUTH) {
                authInvalidKeys.add(cleanKey)
            }
        }

        ai.deepcode.android.util.AppLogger.w(
            "ApiKeyRotator",
            "Marked $canonicalId slot $slotIndex as exhausted for ${cooldownMs / 1000}s (reason: $reason)"
        )
    }

    /**
     * Marks key as rate limited (temporary cooldown, default 60s).
     */
    fun markKeyRateLimited(
        storageId: String,
        slotIndex: Int,
        keyString: String? = null,
        cooldownMs: Long = DEFAULT_RATE_LIMIT_COOLDOWN_MS
    ) {
        markKeyExhausted(storageId, slotIndex, keyString, cooldownMs, RotationReason.RATE_LIMIT)
    }

    /**
     * Marks key as authentication invalid (extended 24h cooldown / auth invalid state).
     */
    fun markKeyAuthInvalid(
        storageId: String,
        slotIndex: Int,
        keyString: String? = null
    ) {
        markKeyExhausted(storageId, slotIndex, keyString, DEFAULT_AUTH_INVALID_COOLDOWN_MS, RotationReason.INVALID_AUTH)
    }

    /**
     * Automatically inspects error, httpCode, and responseBody to apply the correct failure cooldown.
     * Guaranteed NOT to rotate or mark keys exhausted on HTTP 400 or bad model name errors!
     */
    fun markKeyFailure(
        storageId: String,
        slotIndex: Int,
        keyString: String? = null,
        error: Throwable? = null,
        httpCode: Int? = null,
        responseBody: String? = null
    ) {
        if (isBadModelOrRequestError(error, httpCode, responseBody)) {
            ai.deepcode.android.util.AppLogger.w(
                "ApiKeyRotator",
                "Non-rotatable error (HTTP ${httpCode ?: "n/a"} / bad model / request error) for $storageId slot $slotIndex. Key NOT marked exhausted."
            )
            return
        }

        if (isAuthError(error, httpCode, responseBody)) {
            markKeyAuthInvalid(storageId, slotIndex, keyString)
        } else {
            markKeyRateLimited(storageId, slotIndex, keyString)
        }
    }

    /**
     * Check whether a specific key slot or key string is marked as invalid auth.
     */
    fun isKeyAuthInvalid(storageId: String, slotIndex: Int, keyString: String? = null): Boolean {
        val cleanKey = keyString?.trim().orEmpty()
        if (cleanKey.isNotEmpty() && authInvalidKeys.contains(cleanKey)) {
            return true
        }
        if (slotIndex > 0 && authInvalidSlots.contains(slotKey(storageId, slotIndex))) {
            return true
        }
        return false
    }

    /**
     * Check whether a specific key slot or key string is currently in cooldown.
     */
    fun isKeyExhausted(storageId: String, slotIndex: Int, keyString: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val cleanKey = keyString?.trim().orEmpty()

        if (cleanKey.isNotEmpty()) {
            val keyExpiry = exhaustedKeyValues[cleanKey]
            if (keyExpiry != null) {
                if (now > keyExpiry) {
                    exhaustedKeyValues.remove(cleanKey)
                    authInvalidKeys.remove(cleanKey)
                } else {
                    return true
                }
            }
        }

        if (slotIndex > 0) {
            val key = slotKey(storageId, slotIndex)
            val expiry = exhaustedKeys[key] ?: return false
            if (now > expiry) {
                exhaustedKeys.remove(key)
                authInvalidSlots.remove(key)
                return false
            }
            return true
        }

        return false
    }

    private fun getExpiry(storageId: String, slotIndex: Int, keyString: String? = null): Long {
        val cleanKey = keyString?.trim().orEmpty()
        if (cleanKey.isNotEmpty()) {
            exhaustedKeyValues[cleanKey]?.let { return it }
        }
        if (slotIndex > 0) {
            exhaustedKeys[slotKey(storageId, slotIndex)]?.let { return it }
        }
        return 0L
    }

    private fun getStorageAliases(storageId: String): List<String> {
        val clean = storageId.trim().lowercase()
        return when (clean) {
            "zen", "opencode-zen", "opencode", "zenmux", "zenmux-free" -> listOf("zen", clean, "opencode-zen", "opencode", "zenmux", "zenmux-free").distinct()
            "together", "together-ai" -> listOf("together", "together-ai")
            "fireworks", "fireworks-ai" -> listOf("fireworks", "fireworks-ai")
            "nvidia", "nvidia-nim" -> listOf("nvidia", "nvidia-nim")
            "cerebras", "cerebrus" -> listOf("cerebras", "cerebrus")
            "gmi", "gmi-cloud" -> listOf("gmi", "gmi-cloud")
            "atria", "atria-ai" -> listOf("atria", "atria-ai")
            "gemini", "google gemini", "google-gemini", "gemini-business", "gemini-web", "google" -> listOf("gemini", "google gemini", "google-gemini", "gemini-business", "gemini-web", "google").distinct()
            else -> listOf(clean)
        }
    }

    /**
     * Finds the 1-based slot index (1..6) matching [apiKey] for [storageId].
     * Returns 0 if not found in configured slots.
     */
    fun findSlotForKey(prefs: EncryptedPrefs, storageId: String, apiKey: String): Int {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return 0
        val aliases = getStorageAliases(storageId)
        for (targetId in aliases) {
            for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                val slotKey = prefs.getApiKeySlot(targetId, slot).trim()
                if (slotKey.isNotEmpty() && slotKey == cleanKey) {
                    return slot
                }
            }
        }
        return 0
    }

    /**
     * Identifies errors that must NOT trigger key rotation (HTTP 400 Bad Request, bad model name,
     * invalid parameters, context length, content safety, etc.).
     */
    fun isBadModelOrRequestError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
        if (httpCode != null && (httpCode == 400 || httpCode == 404 || httpCode == 422)) {
            val errText = (responseBody.orEmpty() + " " + error?.message.orEmpty()).lowercase()
            // If the provider returned 400 specifically with an explicit invalid api key string, let auth handle it
            if (errText.contains("invalid_api_key") || errText.contains("incorrect api key")) {
                return false
            }
            return true
        }

        val textToInspect = buildString {
            if (httpCode != null) append("HTTP $httpCode ")
            if (error != null) {
                append(error.javaClass.simpleName).append(": ")
                append(error.message.orEmpty()).append(" ")
                append(error.cause?.message.orEmpty()).append(" ")
            }
            if (!responseBody.isNullOrEmpty()) {
                append(responseBody)
            }
        }.lowercase()

        // Explicit HTTP 400 / Bad Request markers
        if (textToInspect.contains("http 400") ||
            textToInspect.contains("400 bad request") ||
            textToInspect.contains("status code: 400") ||
            textToInspect.contains("status code 400") ||
            textToInspect.contains("code\": 400") ||
            textToInspect.contains("code\":400") ||
            textToInspect.contains("invalid_request_error")
        ) {
            if (!textToInspect.contains("invalid_api_key") && !textToInspect.contains("incorrect api key")) {
                return true
            }
        }

        // Model-level errors & bad model name patterns across providers
        val isModelMentioned = textToInspect.contains("model") || textToInspect.contains("engine")
        if (isModelMentioned && (
                textToInspect.contains("not found") ||
                textToInspect.contains("does not exist") ||
                textToInspect.contains("not exist") ||
                textToInspect.contains("is not available") ||
                textToInspect.contains("unavailable") ||
                textToInspect.contains("unknown") ||
                textToInspect.contains("invalid") ||
                textToInspect.contains("unrecognized") ||
                textToInspect.contains("unsupported") ||
                textToInspect.contains("no such") ||
                textToInspect.contains("disabled") ||
                textToInspect.contains("deactivated")
            )
        ) {
            return true
        }

        if (textToInspect.contains("model_not_found") ||
            textToInspect.contains("modelerror") ||
            textToInspect.contains("invalid_model") ||
            textToInspect.contains("invalid model") ||
            textToInspect.contains("bad model") ||
            textToInspect.contains("not_found_error") ||
            textToInspect.contains("engine_not_found") ||
            textToInspect.contains("model access is disabled") ||
            textToInspect.contains("model is disabled")
        ) {
            return true
        }

        // Missing session headers, free tier gateway restrictions, or parameter limits
        return textToInspect.contains("missingsessionid") ||
            textToInspect.contains("freetiererror") ||
            textToInspect.contains("can only be used from within opencode") ||
            textToInspect.contains("can only be used in opencode") ||
            textToInspect.contains("creditserror") ||
            textToInspect.contains("no payment method") ||
            textToInspect.contains("insufficient account funds") ||
            textToInspect.contains("context length exceeded") ||
            textToInspect.contains("maximum context length") ||
            textToInspect.contains("prompt too long") ||
            textToInspect.contains("content filter") ||
            textToInspect.contains("safety filter")
    }

    /**
     * Checks if error is a rate limit or quota exhaustion (temporary).
     */
    fun isRateLimitError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
        if (isBadModelOrRequestError(error, httpCode, responseBody)) return false
        if (error is RateLimitException) return true
        if (httpCode != null && (httpCode == 429 || httpCode == 402 || httpCode == 503 || httpCode == 529)) return true

        val text = (responseBody.orEmpty() + " " + error?.message.orEmpty()).lowercase()
        return text.contains("429") ||
            text.contains("rate limit") ||
            text.contains("rate_limit") ||
            text.contains("ratelimit") ||
            text.contains("too many requests") ||
            text.contains("quota") ||
            text.contains("freeusagelimiterror") ||
            text.contains("free usage limit") ||
            text.contains("free tier limit") ||
            text.contains("free tier quota") ||
            text.contains("free tier exhausted") ||
            text.contains("limit reached") ||
            text.contains("limit exceeded") ||
            text.contains("usage limit") ||
            text.contains("daily limit") ||
            text.contains("resource_exhausted") ||
            text.contains("resource exhausted") ||
            text.contains("tokens per minute") ||
            text.contains("requests per minute") ||
            text.contains("overloaded") ||
            text.contains("capacity") ||
            text.contains("busy")
    }

    /**
     * Checks if error is an invalid or expired authentication key (permanent until updated).
     */
    fun isAuthError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
        if (isBadModelOrRequestError(error, httpCode, responseBody)) return false
        if (httpCode != null && (httpCode == 401 || httpCode == 403)) return true

        val text = (responseBody.orEmpty() + " " + error?.message.orEmpty()).lowercase()
        return text.contains("401") ||
            text.contains("403") ||
            text.contains("unauthorized") ||
            text.contains("invalid_api_key") ||
            text.contains("invalid api key") ||
            text.contains("incorrect api key") ||
            text.contains("invalid_token") ||
            text.contains("invalid token") ||
            text.contains("token expired") ||
            text.contains("key expired") ||
            text.contains("revoked") ||
            text.contains("permission_denied") ||
            text.contains("auth failed") ||
            text.contains("authentication failed")
    }

    /**
     * Thorough check for whether an error is caused by rate limiting, quota exhaustion,
     * invalid key/token, or server overload that can be solved by rotating keys.
     * Guaranteed false for HTTP 400 Bad Request or bad model name!
     */
    fun isRotatableError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
        if (isBadModelOrRequestError(error, httpCode, responseBody)) {
            return false
        }

        if (error is RateLimitException) return true

        if (httpCode != null && httpCode in listOf(401, 402, 403, 429)) {
            return true
        }

        val textToInspect = buildString {
            if (httpCode != null) append("HTTP $httpCode ")
            if (error != null) {
                append(error.javaClass.simpleName).append(": ")
                append(error.message.orEmpty()).append(" ")
                append(error.cause?.message.orEmpty()).append(" ")
            }
            if (!responseBody.isNullOrEmpty()) {
                append(responseBody)
            }
        }.lowercase()

        return textToInspect.contains("429") ||
            textToInspect.contains("401") ||
            textToInspect.contains("402") ||
            textToInspect.contains("403") ||
            textToInspect.contains("rate limit") ||
            textToInspect.contains("rate_limit") ||
            textToInspect.contains("ratelimit") ||
            textToInspect.contains("too many requests") ||
            textToInspect.contains("too_many_requests") ||
            textToInspect.contains("quota") ||
            textToInspect.contains("freeusagelimiterror") ||
            textToInspect.contains("free usage limit") ||
            textToInspect.contains("free tier limit") ||
            textToInspect.contains("free_tier_limit") ||
            textToInspect.contains("free tier quota") ||
            textToInspect.contains("free tier exhausted") ||
            textToInspect.contains("free tier balance") ||
            textToInspect.contains("freetierlimit") ||
            textToInspect.contains("limit reached") ||
            textToInspect.contains("limit exceeded") ||
            textToInspect.contains("limit_exceeded") ||
            textToInspect.contains("usage limit") ||
            textToInspect.contains("usage_limit") ||
            textToInspect.contains("daily limit") ||
            textToInspect.contains("reached its request limit") ||
            textToInspect.contains("request limit reached") ||
            textToInspect.contains("exceeded your") ||
            textToInspect.contains("resource_exhausted") ||
            textToInspect.contains("resource exhausted") ||
            textToInspect.contains("insufficient_quota") ||
            textToInspect.contains("unauthorized") ||
            textToInspect.contains("invalid_api_key") ||
            textToInspect.contains("invalid api key") ||
            textToInspect.contains("incorrect api key") ||
            textToInspect.contains("invalid_token") ||
            textToInspect.contains("invalid token") ||
            textToInspect.contains("token expired") ||
            textToInspect.contains("key expired") ||
            textToInspect.contains("overloaded") ||
            textToInspect.contains("capacity") ||
            textToInspect.contains("busy")
    }

    /**
     * Returns the first non-exhausted, non-empty key for [storageId].
     * If all configured keys are in cooldown, falls back to the earliest cooling down key,
     * skipping any keys marked as permanently auth-invalid.
     * @return Pair(apiKey, slotIndex) or null if no keys are configured.
     */
    fun getNextAvailableKey(prefs: EncryptedPrefs, storageId: String): Pair<String, Int>? {
        val aliases = getStorageAliases(storageId)
        val candidateCooldowns = mutableListOf<Triple<String, Int, Long>>()

        for (targetId in aliases) {
            for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                val key = prefs.getApiKeySlot(targetId, slot).trim()
                if (key.isNotEmpty()) {
                    if (!isKeyExhausted(targetId, slot, key)) {
                        return key to slot
                    }
                    val expiry = getExpiry(targetId, slot, key)
                    val isAuth = isKeyAuthInvalid(targetId, slot, key)
                    if (!isAuth) {
                        candidateCooldowns.add(Triple(key, slot, expiry))
                    }
                }
            }
        }

        // Seamless failover: pick non-auth-invalid key whose cooldown expires earliest
        if (candidateCooldowns.isNotEmpty()) {
            val best = candidateCooldowns.minByOrNull { it.third }!!
            return best.first to best.second
        }

        // If only auth-invalid keys configured, fallback to first configured key as last resort
        for (targetId in aliases) {
            for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                val key = prefs.getApiKeySlot(targetId, slot).trim()
                if (key.isNotEmpty()) {
                    return key to slot
                }
            }
        }

        return null
    }

    /**
     * Returns the next available key after [afterSlot], wrapping around.
     * If all other keys are in cooldown, returns the non-auth-invalid key with earliest expiry.
     * @return Pair(apiKey, slotIndex) or null if no alternative key is configured.
     */
    fun getAvailableKeyAfter(prefs: EncryptedPrefs, storageId: String, afterSlot: Int): Pair<String, Int>? {
        val max = EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER
        val aliases = getStorageAliases(storageId)
        val candidateCooldowns = mutableListOf<Triple<String, Int, Long>>()

        for (targetId in aliases) {
            for (offset in 1..max) {
                val slot = ((afterSlot - 1 + offset) % max) + 1
                val key = prefs.getApiKeySlot(targetId, slot).trim()
                if (key.isNotEmpty()) {
                    if (!isKeyExhausted(targetId, slot, key)) {
                        return key to slot
                    }
                    val expiry = getExpiry(targetId, slot, key)
                    val isAuth = isKeyAuthInvalid(targetId, slot, key)
                    if (!isAuth) {
                        candidateCooldowns.add(Triple(key, slot, expiry))
                    }
                }
            }
        }

        if (candidateCooldowns.isNotEmpty()) {
            val best = candidateCooldowns.minByOrNull { it.third }!!
            return best.first to best.second
        }

        // Ring fallback
        for (targetId in aliases) {
            for (offset in 1..max) {
                val slot = ((afterSlot - 1 + offset) % max) + 1
                val key = prefs.getApiKeySlot(targetId, slot).trim()
                if (key.isNotEmpty()) {
                    return key to slot
                }
            }
        }

        return null
    }

    /**
     * Returns total number of configured keys for [storageId].
     */
    fun getConfiguredKeyCount(prefs: EncryptedPrefs, storageId: String): Int {
        val aliases = getStorageAliases(storageId)
        for (targetId in aliases) {
            var count = 0
            for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                if (prefs.getApiKeySlot(targetId, slot).trim().isNotEmpty()) count++
            }
            if (count > 0) return count
        }
        return 0
    }

    /**
     * Clear all cooldowns and auth invalid flags for a specific provider.
     */
    fun clearCooldowns(storageId: String, prefs: EncryptedPrefs? = null) {
        val aliases = getStorageAliases(storageId)
        for (alias in aliases) {
            val prefix = "${getCanonicalStorageId(alias)}:"
            exhaustedKeys.keys.removeAll { it.startsWith(prefix) }
            authInvalidSlots.removeAll { it.startsWith(prefix) }

            if (prefs != null) {
                for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                    val k = prefs.getApiKeySlot(alias, slot).trim()
                    if (k.isNotEmpty()) {
                        exhaustedKeyValues.remove(k)
                        authInvalidKeys.remove(k)
                    }
                }
            }
        }
    }

    /**
     * Clear cooldown and auth invalid flags for a specific raw key string.
     */
    fun clearKeyCooldown(keyString: String) {
        val clean = keyString.trim()
        if (clean.isNotEmpty()) {
            exhaustedKeyValues.remove(clean)
            authInvalidKeys.remove(clean)
        }
    }

    /**
     * Clear all cooldowns across all providers.
     */
    fun clearAllCooldowns() {
        exhaustedKeys.clear()
        exhaustedKeyValues.clear()
        authInvalidKeys.clear()
        authInvalidSlots.clear()
    }
}
