package ai.deepcode.android.data.remote

import ai.deepcode.android.data.local.EncryptedPrefs
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages per-provider API key rotation.
 *
 * When a key hits a rate limit (HTTP 429 / quota exhaustion), it is marked as
 * exhausted with a cooldown period. Subsequent calls to [getNextAvailableKey]
 * and [getAvailableKeyAfter] will skip exhausted keys and return the next usable one.
 *
 * If all keys are currently in cooldown, it seamlessly falls back to the least-recently
 * exhausted configured key to ensure continuous failover rather than stopping.
 */
object ApiKeyRotator {

    // Key: "{storageId}:{slotIndex}" → Value: cooldown expiry timestamp (ms)
    private val exhaustedKeys = ConcurrentHashMap<String, Long>()
    // Key: raw trimmed apiKey string → Value: cooldown expiry timestamp (ms)
    private val exhaustedKeyValues = ConcurrentHashMap<String, Long>()

    private fun slotKey(storageId: String, slotIndex: Int) = "$storageId:$slotIndex"

    /**
     * Mark a specific key slot and/or key string as exhausted with a cooldown period (default 60s).
     */
    fun markKeyExhausted(storageId: String, slotIndex: Int, keyString: String? = null, cooldownMs: Long = 60_000L) {
        val expiry = System.currentTimeMillis() + cooldownMs
        if (slotIndex > 0) {
            exhaustedKeys[slotKey(storageId, slotIndex)] = expiry
        }
        val cleanKey = keyString?.trim().orEmpty()
        if (cleanKey.isNotEmpty() && cleanKey != "zen-free" && cleanKey != "ollama") {
            exhaustedKeyValues[cleanKey] = expiry
        }
        ai.deepcode.android.util.AppLogger.w("ApiKeyRotator", "Marked $storageId slot $slotIndex as exhausted for ${cooldownMs / 1000}s")
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
                } else {
                    return true
                }
            }
        }
        if (slotIndex > 0) {
            val expiry = exhaustedKeys[slotKey(storageId, slotIndex)] ?: return false
            if (now > expiry) {
                exhaustedKeys.remove(slotKey(storageId, slotIndex))
                return false
            }
            return true
        }
        return false
    }

    /**
     * Finds the 1-based slot index (1..6) matching [apiKey] for [storageId].
     * Returns 0 if not found in configured slots.
     */
    fun findSlotForKey(prefs: EncryptedPrefs, storageId: String, apiKey: String): Int {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) return 0
        for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
            val slotKey = prefs.getApiKeySlot(storageId, slot).trim()
            if (slotKey.isNotEmpty() && (slotKey == cleanKey || cleanKey.startsWith(slotKey))) {
                return slot
            }
        }
        return 0
    }

    /**
     * Thorough check for whether an error is caused by rate limiting, quota exhaustion,
     * invalid key/token, balance exhaustion, or server overload that can be solved by rotating keys.
     */
    fun isRotatableError(error: Throwable? = null, httpCode: Int? = null, responseBody: String? = null): Boolean {
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
                textToInspect.contains("free usage") ||
                textToInspect.contains("free tier") ||
                textToInspect.contains("free_tier") ||
                textToInspect.contains("freetier") ||
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
                textToInspect.contains("insufficient") ||
                textToInspect.contains("balance") ||
                textToInspect.contains("credit") ||
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
     * If all configured keys are in cooldown, falls back to the first non-empty key.
     * @return Pair(apiKey, slotIndex) or null if no keys are configured.
     */
    fun getNextAvailableKey(prefs: EncryptedPrefs, storageId: String): Pair<String, Int>? {
        var firstConfiguredKey: Pair<String, Int>? = null
        for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
            val key = prefs.getApiKeySlot(storageId, slot)
            if (key.isNotEmpty()) {
                if (firstConfiguredKey == null) firstConfiguredKey = key to slot
                if (!isKeyExhausted(storageId, slot, key)) return key to slot
            }
        }
        // Fallback: If all are in cooldown, return the first configured key to keep trying
        return firstConfiguredKey
    }

    /**
     * Returns the next available key after [afterSlot], wrapping around.
     * If all other keys are in cooldown, returns the next configured key in the ring.
     * @return Pair(apiKey, slotIndex) or null if no alternative key is configured.
     */
    fun getAvailableKeyAfter(prefs: EncryptedPrefs, storageId: String, afterSlot: Int): Pair<String, Int>? {
        val max = EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER
        var nextConfiguredKey: Pair<String, Int>? = null

        // Pass 1: Look for a non-exhausted non-empty key
        for (offset in 1..max) {
            val slot = ((afterSlot - 1 + offset) % max) + 1
            val key = prefs.getApiKeySlot(storageId, slot)
            if (key.isNotEmpty()) {
                if (nextConfiguredKey == null) nextConfiguredKey = key to slot
                if (!isKeyExhausted(storageId, slot, key)) return key to slot
            }
        }

        // Pass 2: Fallback to next configured key in ring even if in cooldown
        return nextConfiguredKey
    }

    /**
     * Returns total number of configured keys for [storageId].
     */
    fun getConfiguredKeyCount(prefs: EncryptedPrefs, storageId: String): Int {
        var count = 0
        for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
            if (prefs.getApiKeySlot(storageId, slot).isNotEmpty()) count++
        }
        return count
    }

    /**
     * Clear all cooldowns for a specific provider.
     */
    fun clearCooldowns(storageId: String) {
        val prefix = "$storageId:"
        exhaustedKeys.keys.removeAll { it.startsWith(prefix) }
    }

    /**
     * Clear all cooldowns across all providers.
     */
    fun clearAllCooldowns() {
        exhaustedKeys.clear()
        exhaustedKeyValues.clear()
    }
}
