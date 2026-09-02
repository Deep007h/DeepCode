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

    private fun slotKey(storageId: String, slotIndex: Int) = "$storageId:$slotIndex"

    /**
     * Mark a specific key slot as exhausted with a cooldown period (default 15s).
     */
    fun markKeyExhausted(storageId: String, slotIndex: Int, cooldownMs: Long = 15_000) {
        exhaustedKeys[slotKey(storageId, slotIndex)] = System.currentTimeMillis() + cooldownMs
    }

    /**
     * Check whether a specific key slot is currently in cooldown.
     */
    fun isKeyExhausted(storageId: String, slotIndex: Int): Boolean {
        val expiry = exhaustedKeys[slotKey(storageId, slotIndex)] ?: return false
        if (System.currentTimeMillis() > expiry) {
            exhaustedKeys.remove(slotKey(storageId, slotIndex))
            return false
        }
        return true
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
                if (!isKeyExhausted(storageId, slot)) return key to slot
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
                if (!isKeyExhausted(storageId, slot)) return key to slot
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
    }
}
