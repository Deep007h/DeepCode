package ai.deepcode.android.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EncryptedPrefs private constructor(context: Context) {
    private val sharedPrefs = try {
        createEncryptedPrefs(context)
    } catch (e: Exception) {
        try {
            context.deleteSharedPreferences("deepcode_secure_prefs")
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (_: Exception) {}
        try {
            createEncryptedPrefs(context)
        } catch (e2: Exception) {
            context.getSharedPreferences("deepcode_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        @Volatile
        private var instance: EncryptedPrefs? = null

        /**
         * Thread-safe singleton accessor. Always uses applicationContext to avoid
         * Activity/Fragment lifecycle leaks. The EncryptedSharedPreferences and
         * MasterKey are initialized exactly once.
         */
        fun getInstance(context: Context): EncryptedPrefs {
            return instance ?: synchronized(this) {
                instance ?: EncryptedPrefs(context.applicationContext).also { instance = it }
            }
        }

        private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "deepcode_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        const val MAX_API_KEYS_PER_PROVIDER = 6
    }

    private val _themeFlow = MutableStateFlow("system")
    val themeFlow: StateFlow<String> = _themeFlow

    private val _accentFlow = MutableStateFlow("amber")
    val accentFlow: StateFlow<String> = _accentFlow

    private val _wallpaperFlow = MutableStateFlow("default")
    val wallpaperFlow: StateFlow<String> = _wallpaperFlow

    private val _customWallpaperFlow = MutableStateFlow("")
    val customWallpaperFlow: StateFlow<String> = _customWallpaperFlow

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            _themeFlow.value = getSetting("theme", "system")
            _accentFlow.value = getSetting("accent", "amber")
            _wallpaperFlow.value = getSetting("chat_wallpaper", "default")
            _customWallpaperFlow.value = getSetting("chat_wallpaper_custom", "")
        }
    }

    fun getApiKey(provider: String): String {
        return sharedPrefs.getString("api_key_$provider", "") ?: ""
    }

    fun saveApiKey(provider: String, key: String) {
        sharedPrefs.edit().putString("api_key_$provider", key).apply()
    }

    /**
     * Returns the API key stored in [slot] (1-based, 1..6).
     * Slot 1 is the primary key stored under `api_key_$provider`.
     * Slots 2–6 are stored under `setting_api_key_${provider}_{slot}`.
     */
    fun getApiKeySlot(provider: String, slot: Int): String {
        return when {
            slot <= 1 -> getApiKey(provider)
            slot in 2..MAX_API_KEYS_PER_PROVIDER -> getSetting("api_key_${provider}_$slot", "")
            else -> ""
        }
    }

    /**
     * Saves an API key to the given [slot] (1-based, 1..6).
     */
    fun saveApiKeySlot(provider: String, slot: Int, key: String) {
        when {
            slot <= 1 -> saveApiKey(provider, key)
            slot in 2..MAX_API_KEYS_PER_PROVIDER -> saveSetting("api_key_${provider}_$slot", key)
        }
    }

    /**
     * Returns all non-empty API keys for [provider], ordered by slot (1..6).
     */
    fun getApiKeys(provider: String): List<String> {
        return (1..MAX_API_KEYS_PER_PROVIDER).mapNotNull { slot ->
            val key = getApiKeySlot(provider, slot)
            key.ifEmpty { null }
        }
    }

    fun getSetting(key: String, default: String): String {
        val resolvedDefault = when (key) {
            "custom_persona" -> ""
            "persona_enabled" -> "false"
            else -> default
        }
        val value = sharedPrefs.getString("setting_$key", null)
        if (key == "custom_persona" && (value == null || value == DEFAULT_CUSTOM_PERSONA)) {
            return ""
        }
        if (key == "persona_enabled" && value == null) {
            return "false"
        }
        return value ?: resolvedDefault
    }

    fun saveSetting(key: String, value: String) {
        sharedPrefs.edit().putString("setting_$key", value).apply()
        when (key) {
            "theme" -> ai.deepcode.android.util.SafeState.tryUpdateStateFlow(_themeFlow, value)
            "accent" -> ai.deepcode.android.util.SafeState.tryUpdateStateFlow(_accentFlow, value)
            "chat_wallpaper" -> ai.deepcode.android.util.SafeState.tryUpdateStateFlow(_wallpaperFlow, value)
            "chat_wallpaper_custom" -> ai.deepcode.android.util.SafeState.tryUpdateStateFlow(_customWallpaperFlow, value)
        }
    }

    fun getBooleanSetting(key: String, default: Boolean): Boolean {
        return sharedPrefs.getBoolean("setting_bool_$key", default)
    }

    fun saveBooleanSetting(key: String, value: Boolean) {
        sharedPrefs.edit().putBoolean("setting_bool_$key", value).apply()
    }

    fun getChatGPTAccessToken(): String = getSetting("chatgpt_access_token", "")
    fun saveChatGPTAccessToken(token: String) = saveSetting("chatgpt_access_token", token)

    fun getChatGPTAccountId(): String = getSetting("chatgpt_account_id", "")
    fun saveChatGPTAccountId(id: String) = saveSetting("chatgpt_account_id", id)

    fun getChatGPTConversationId(): String = getSetting("chatgpt_conversation_id", "").ifEmpty { getSetting("chatgpt_headless_conversation_id", "") }
    fun saveChatGPTConversationId(convId: String) = saveSetting("chatgpt_conversation_id", convId)

    fun getChatGPTParentMessageId(): String = getSetting("chatgpt_parent_message_id", "").ifEmpty { getSetting("chatgpt_headless_parent_message_id", "") }
    fun saveChatGPTParentMessageId(msgId: String) = saveSetting("chatgpt_parent_message_id", msgId)
}

private const val DEFAULT_CUSTOM_PERSONA = ""


