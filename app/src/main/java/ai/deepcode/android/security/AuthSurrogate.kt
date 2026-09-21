package ai.deepcode.android.security

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.util.AppLogger

/**
 * Model-Blind Credential Surrogation Engine inspired by Meta Muse's `authd`.
 * Masks real API keys, tokens, and credentials into inert surrogate tokens before
 * passing context to LLM models, and unmasks surrogate tokens at the network boundary.
 */
object AuthSurrogate {
    private const val TAG = "AuthSurrogate"

    // Maps real secret value -> surrogate identifier
    private val secretToSurrogate = mutableMapOf<String, String>()
    // Maps surrogate identifier -> real secret value
    private val surrogateToSecret = mutableMapOf<String, String>()

    /**
     * Initializes or refreshes surrogate mappings from EncryptedPrefs.
     */
    @Synchronized
    fun refreshSurrogates(context: Context) {
        try {
            val prefs = EncryptedPrefs.getInstance(context)
            secretToSurrogate.clear()
            surrogateToSecret.clear()

            // Registered providers
            val providers = listOf(
                "zen", "openai", "anthropic", "gemini", "groq",
                "cerebras", "mistral", "github", "telegram", "cloudflare"
            )

            for (p in providers) {
                for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                    val key = prefs.getApiKeySlot(p, slot).trim()
                    if (key.length >= 8) {
                        val surrogate = "<SURROGATE_KEY_${p.uppercase()}_S$slot>"
                        secretToSurrogate[key] = surrogate
                        surrogateToSecret[surrogate] = key
                    }
                }
            }

            // Global bot token & GH token
            val botToken = prefs.getSetting("telegram_bot_token", "").trim()
            if (botToken.length >= 8) {
                val surrogate = "<SURROGATE_KEY_TELEGRAM_BOT>"
                secretToSurrogate[botToken] = surrogate
                surrogateToSecret[surrogate] = botToken
            }

            val ghToken = prefs.getSetting("github_token", "").trim()
            if (ghToken.length >= 8) {
                val surrogate = "<SURROGATE_KEY_GITHUB_TOKEN>"
                secretToSurrogate[ghToken] = surrogate
                surrogateToSecret[surrogate] = ghToken
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing surrogates: ${e.message}")
        }
    }

    /**
     * Masks all known sensitive keys inside text before passing to the AI model.
     */
    fun maskSecrets(rawText: String): String {
        if (rawText.isEmpty() || secretToSurrogate.isEmpty()) return rawText
        var masked = rawText
        for ((secret, surrogate) in secretToSurrogate) {
            if (masked.contains(secret)) {
                masked = masked.replace(secret, surrogate)
            }
        }
        return masked
    }

    /**
     * Replaces surrogate tokens with real credentials right before network/API execution.
     */
    fun unmaskSurrogates(text: String): String {
        if (text.isEmpty() || surrogateToSecret.isEmpty()) return text
        var unmasked = text
        for ((surrogate, secret) in surrogateToSecret) {
            if (unmasked.contains(surrogate)) {
                unmasked = unmasked.replace(surrogate, secret)
            }
        }
        return unmasked
    }
}
