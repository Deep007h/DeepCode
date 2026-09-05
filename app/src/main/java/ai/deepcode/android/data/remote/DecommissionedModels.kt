package ai.deepcode.android.data.remote

import ai.deepcode.android.domain.model.AIModel
import org.json.JSONObject

/**
 * Filter and sanitization registry for decommissioned, deprecated, and shutdown models across all providers.
 * Grounded in provider deprecation notices (e.g. https://console.groq.com/docs/deprecations).
 */
object DecommissionedModels {

    /**
     * Official replacements for models that have been deprecated or decommissioned.
     */
    val REPLACEMENTS: Map<String, String> = mapOf(
        // Groq Decommissioned Models
        "llama-3.3-70b-specdec" to "llama-3.3-70b-versatile",
        "llama-3.1-70b-specdec" to "llama-3.3-70b-versatile",
        "llama-3.1-70b-versatile" to "llama-3.3-70b-versatile",
        "deepseek-r1-distill-llama-70b-specdec" to "deepseek-r1-distill-llama-70b",
        "deepseek-r1-distill-qwen-32b" to "deepseek-r1-distill-llama-70b",
        "qwen-2.5-32b" to "llama-3.3-70b-versatile",
        "qwen-2.5-coder-32b" to "openai/gpt-oss-120b",
        "qwen-qwq-32b" to "qwen/qwen3.6-27b",
        "mixtral-8x7b-32768" to "llama-3.3-70b-versatile",
        "llama-3.2-1b-preview" to "llama-3.1-8b-instant",
        "llama-3.2-3b-preview" to "llama-3.1-8b-instant",
        "llama-3.2-11b-vision-preview" to "llama-3.3-70b-versatile",
        "llama-3.2-90b-vision-preview" to "llama-3.3-70b-versatile",
        "llama3-groq-8b-8192-tool-use-preview" to "llama-3.3-70b-versatile",
        "llama3-groq-70b-8192-tool-use-preview" to "llama-3.3-70b-versatile",
        "llama-guard-3-8b" to "meta-llama/llama-guard-4-12b",

        // Legacy / non-existent Zen IDs
        "big-pickle" to ZenModels.DEFAULT_FREE,
        "north-mini-code" to ZenModels.DEFAULT_FREE,

        // Non-existent Gemini models
        "gemini-3.8-flash" to "gemini-2.5-flash",
        "google/gemini-3.8-flash" to "gemini-2.5-flash"
    )

    private val DECOMMISSIONED_SET: Set<String> = REPLACEMENTS.keys.map { it.lowercase() }.toSet()

    /**
     * Checks whether a model ID is known to be decommissioned, deprecated, or shutdown.
     */
    fun isDecommissioned(modelId: String): Boolean {
        val clean = modelId.trim().lowercase()
        if (clean.isEmpty()) return false
        if (clean in DECOMMISSIONED_SET) return true
        if (clean.contains("specdec")) return true
        if (clean.contains("tool-use-preview")) return true
        return false
    }

    /**
     * Inspects a model JSON object from a provider's /models endpoint to determine if it is
     * inactive, decommissioned, deprecated, or archived.
     */
    fun isJsonModelInactiveOrDecommissioned(obj: JSONObject): Boolean {
        val id = obj.optString("id", "")
        if (isDecommissioned(id)) return true

        // Groq & OpenAI spec: "active": false means the model is decommissioned/inactive
        if (obj.has("active") && !obj.optBoolean("active", true)) return true

        // Deprecated flag
        if (obj.optBoolean("deprecated", false)) return true

        // Decommissioned flag
        if (obj.optBoolean("decommissioned", false)) return true

        // Archived flag
        if (obj.optBoolean("archived", false)) return true

        // Status string
        val status = obj.optString("status", "").lowercase()
        if (status in setOf("deprecated", "decommissioned", "inactive", "disabled", "shutdown", "archived")) {
            return true
        }

        return false
    }

    /**
     * Maps a decommissioned or deprecated model ID to its recommended replacement.
     * Returns the original model ID if it is active or not recognized as decommissioned.
     */
    fun sanitize(modelId: String): String {
        val lower = modelId.trim().lowercase()
        REPLACEMENTS[lower]?.let { return it }

        if (lower.contains("specdec")) {
            return if (lower.contains("deepseek")) "deepseek-r1-distill-llama-70b" else "llama-3.3-70b-versatile"
        }
        if (lower.contains("tool-use-preview")) {
            return "llama-3.3-70b-versatile"
        }

        return modelId
    }

    /**
     * Strips decommissioned models from any list of AIModel.
     */
    fun filterValidModels(models: List<AIModel>?): List<AIModel> {
        if (models.isNullOrEmpty()) return emptyList()
        return models.filterNot { isDecommissioned(it.id) }
    }
}
