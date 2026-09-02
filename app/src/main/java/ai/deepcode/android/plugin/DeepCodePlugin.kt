package ai.deepcode.android.plugin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import com.google.gson.JsonObject

/**
 * Core plugin interface for the OpenCode Plugin System.
 *
 * Every plugin is a self-contained capability engine. The AI acts only as a router —
 * it classifies user intent, extracts parameters, and calls the plugin tool.
 * The plugin does 100% of the computation natively on-device.
 *
 * To create a plugin:
 * 1. Implement this interface in a new Kotlin class
 * 2. Register it in PluginRegistry.registerBuiltinPlugins()
 * 3. That's it — the plugin appears in Settings and is callable by the AI
 */
interface DeepCodePlugin {
    /** Unique plugin identifier (lowercase_with_underscores, e.g., "qr_code") */
    val id: String

    /** Human-readable name shown in Settings UI */
    val displayName: String

    /** One-line description of what this plugin does */
    val description: String

    /** Plugin version string (e.g., "1.0") */
    val version: String

    /** Category for grouping in Settings UI */
    val category: PluginCategory

    /** Material icon name for display (e.g., "qr_code", "description", "build") */
    val iconName: String

    val requiredPermissions: List<String> get() = emptyList()

    /**
     * List of tool definitions this plugin exposes to the AI.
     * Each Tool includes a name, description, and JSON Schema inputSchema.
     * Tool names MUST be globally unique — prefix with plugin ID (e.g., "qr_generate").
     */
    fun getTools(): List<Tool>

    /**
     * Execute a tool call. This is where ALL the work happens — no AI involved.
     *
     * @param toolName Which tool within this plugin was called
     * @param args Parsed JSON arguments from the AI's tool call
     * @param context Android context for file I/O, preferences, etc.
     * @return Result string. Conventions:
     *   - Plain text for simple results
     *   - "[file:/path/to/file]" for generated files
     *   - "[image:/path/to/image]" for generated images
     *   - JSON string for structured data
     *   - "Error: ..." for failures
     */
    fun execute(toolName: String, args: JsonObject, context: Context): String

    suspend fun executeSuspend(toolName: String, args: JsonObject, context: Context): String = execute(toolName, args, context)

    /**
     * Optional: Configuration fields rendered in the plugin's Settings detail page.
     * Override to expose user-configurable settings.
     */
    fun getConfigFields(): List<PluginConfigField> = emptyList()

    /** Called when the plugin is enabled by the user in Settings */
    fun onEnable(context: Context) {}

    /** Called when the plugin is disabled by the user in Settings */
    fun onDisable(context: Context) {}
}

/**
 * Plugin categories for grouping in the Settings → Plugins UI.
 */
enum class PluginCategory(val displayName: String, val icon: String) {
    DOCUMENT("Document", "description"),
    MEDIA("Media", "image"),
    UTILITY("Utility", "build"),
    DATA("Data", "data_object"),
    PRODUCTIVITY("Productivity", "event"),
    DEVELOPER("Developer", "code"),
    INTEGRATION("Integration", "link")
}

/**
 * A configuration field that appears in the plugin's detail/settings page.
 */
data class PluginConfigField(
    val key: String,
    val label: String,
    val type: ConfigFieldType,
    val defaultValue: String,
    val options: List<String> = emptyList()
)

/**
 * Types of configuration fields supported in plugin settings.
 */
enum class ConfigFieldType {
    TEXT,
    NUMBER,
    TOGGLE,
    DROPDOWN
}
