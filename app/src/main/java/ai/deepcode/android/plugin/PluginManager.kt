package ai.deepcode.android.plugin

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs

/**
 * Handles plugin enable/disable persistence, configuration storage, and usage tracking.
 * All data stored via EncryptedPrefs (AES-256 encrypted).
 *
 * Key naming conventions:
 *   plugin_{id}_enabled     → "true" / "false"
 *   plugin_{id}_usage_count → integer as string
 *   plugin_{id}_{configKey} → config value
 */
class PluginManager(context: Context) {
    private val prefs = EncryptedPrefs.getInstance(context)

    companion object {
        private const val PREFIX = "plugin_"
        private const val SUFFIX_ENABLED = "_enabled"
        private const val SUFFIX_USAGE = "_usage_count"
    }

    /**
     * Check if a plugin is enabled. Defaults to true for new plugins.
     */
    fun isEnabled(pluginId: String): Boolean {
        val value = prefs.getSetting("$PREFIX${pluginId}$SUFFIX_ENABLED", "true")
        return value == "true"
    }

    /**
     * Set plugin enabled/disabled state.
     */
    fun setEnabled(pluginId: String, enabled: Boolean) {
        prefs.saveSetting("$PREFIX${pluginId}$SUFFIX_ENABLED", if (enabled) "true" else "false")
    }

    /**
     * Record a usage event for the plugin (increments counter).
     */
    fun recordUsage(pluginId: String) {
        val current = getUsageCount(pluginId)
        prefs.saveSetting("$PREFIX${pluginId}$SUFFIX_USAGE", (current + 1).toString())
    }

    /**
     * Get total usage count for a plugin.
     */
    fun getUsageCount(pluginId: String): Int {
        return prefs.getSetting("$PREFIX${pluginId}$SUFFIX_USAGE", "0").toIntOrNull() ?: 0
    }

    /**
     * Get a plugin configuration value.
     */
    fun getConfig(pluginId: String, key: String, default: String): String {
        return prefs.getSetting("$PREFIX${pluginId}_$key", default)
    }

    /**
     * Set a plugin configuration value.
     */
    fun setConfig(pluginId: String, key: String, value: String) {
        prefs.saveSetting("$PREFIX${pluginId}_$key", value)
    }

    /**
     * Reset a plugin's usage counter.
     */
    fun resetUsage(pluginId: String) {
        prefs.saveSetting("$PREFIX${pluginId}$SUFFIX_USAGE", "0")
    }

    /**
     * Get all config values for a plugin as a map.
     */
    fun getAllConfig(pluginId: String, fields: List<PluginConfigField>): Map<String, String> {
        return fields.associate { field ->
            field.key to getConfig(pluginId, field.key, field.defaultValue)
        }
    }
}
