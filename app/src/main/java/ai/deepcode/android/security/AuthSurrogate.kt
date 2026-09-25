package ai.deepcode.android.security

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.OPENAI_PROVIDERS
import ai.deepcode.android.util.AppLogger

/**
 * Model-Blind Credential Surrogation Engine inspired by Meta Muse's `authd`.
 * Masks real API keys, tokens, and credentials into inert surrogate tokens before
 * passing context to LLM models, and unmasks surrogate tokens at the network boundary.
 */
object AuthSurrogate {
    private const val TAG = "AuthSurrogate"

    // Thread-safe immutable snapshots sorted by length descending to prevent prefix collisions
    @Volatile
    private var activeSecrets: List<Pair<String, String>> = emptyList()

    @Volatile
    private var activeSurrogates: List<Pair<String, String>> = emptyList()

    /**
     * Initializes or refreshes surrogate mappings from EncryptedPrefs.
     */
    @Synchronized
    fun refreshSurrogates(context: Context) {
        try {
            val prefs = EncryptedPrefs.getInstance(context)
            val secToSur = mutableMapOf<String, String>()
            val surToSec = mutableMapOf<String, String>()

            // Collect all unique provider keys dynamically
            val providerNames = mutableSetOf(
                "zen", "openai", "anthropic", "gemini", "groq",
                "cerebras", "mistral", "github", "telegram", "cloudflare",
                "atria", "openrouter", "together", "deepseek", "cohere",
                "perplexity", "sambanova", "ai21", "fireworks", "xai"
            )

            try {
                AIProviderFactory.providers.forEach { p ->
                    providerNames.add(p.name.lowercase().replace(" ", "_"))
                }
                OPENAI_PROVIDERS.forEach { p ->
                    providerNames.add(p.name.lowercase().replace(" ", "_"))
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Error collecting dynamic providers: ${e.message}")
            }

            for (p in providerNames) {
                for (slot in 1..EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER) {
                    val key = prefs.getApiKeySlot(p, slot).trim()
                    if (key.length >= 8) {
                        val surrogate = "<SURROGATE_KEY_${p.uppercase()}_S$slot>"
                        secToSur[key] = surrogate
                        surToSec[surrogate] = key
                    }
                }
            }

            // Global bot token & GH token
            val botToken = prefs.getSetting("telegram_bot_token", "").trim()
            if (botToken.length >= 8) {
                val surrogate = "<SURROGATE_KEY_TELEGRAM_BOT>"
                secToSur[botToken] = surrogate
                surToSec[surrogate] = botToken
            }

            val ghToken = prefs.getSetting("github_token", "").trim()
            if (ghToken.length >= 8) {
                val surrogate = "<SURROGATE_KEY_GITHUB_TOKEN>"
                secToSur[ghToken] = surrogate
                surToSec[surrogate] = ghToken
            }

            // Atomically update snapshots sorted by key length descending
            // so longer keys are always matched and replaced before shorter substrings
            activeSecrets = secToSur.toList().sortedByDescending { it.first.length }
            activeSurrogates = surToSec.toList().sortedByDescending { it.first.length }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error refreshing surrogates: ${e.message}")
        }
    }

    /**
     * Masks all known sensitive keys inside text before passing to the AI model.
     * Lock-free and thread-safe.
     */
    fun maskSecrets(rawText: String): String {
        val secrets = activeSecrets
        if (rawText.isEmpty() || secrets.isEmpty()) return rawText
        var masked = rawText
        for ((secret, surrogate) in secrets) {
            if (masked.contains(secret)) {
                masked = masked.replace(secret, surrogate)
            }
        }
        return masked
    }

    /**
     * Replaces surrogate tokens with real credentials right before network/API execution.
     * Lock-free and thread-safe.
     */
    fun unmaskSurrogates(text: String): String {
        val surrogates = activeSurrogates
        if (text.isEmpty() || surrogates.isEmpty()) return text
        var unmasked = text
        for ((surrogate, secret) in surrogates) {
            if (unmasked.contains(surrogate)) {
                unmasked = unmasked.replace(surrogate, secret)
            }
        }
        return unmasked
    }
}
