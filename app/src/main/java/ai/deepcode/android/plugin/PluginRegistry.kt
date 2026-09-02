package ai.deepcode.android.plugin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.util.AppLogger
import com.google.gson.JsonObject

/**
 * Singleton registry that discovers, loads, manages, and routes calls to all plugins.
 *
 * Usage:
 *   PluginRegistry.init(context)                // call once in DeepCodeApp.onCreate()
 *   PluginRegistry.getEnabledTools()            // merge into ToolExecutor.getDeclaredTools()
 *   PluginRegistry.executeTool(name, args, ctx) // route tool call to the correct plugin
 */
object PluginRegistry {
    private const val TAG = "PluginRegistry"

    private val plugins = mutableMapOf<String, DeepCodePlugin>()
    private val toolToPluginMap = mutableMapOf<String, String>()
    private var manager: PluginManager? = null
    private var initialized = false

    private var appContext: Context? = null

    /**
     * Initialize the registry and register all built-in plugins.
     * Called once from DeepCodeApp.onCreate().
     */
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        manager = PluginManager(context)
        registerBuiltinPlugins()
        rebuildToolMap()
        initialized = true
        AppLogger.i(TAG, "PluginRegistry initialized with ${plugins.size} plugins, ${toolToPluginMap.size} tools")
    }

    /**
     * Register all built-in plugins. Add new plugins here.
     */
    private fun registerBuiltinPlugins() {
        register(ai.deepcode.android.plugin.builtin.QrCodePlugin())
        register(ai.deepcode.android.plugin.builtin.CsvPlugin())
        register(ai.deepcode.android.plugin.builtin.ZipPlugin())
        register(ai.deepcode.android.plugin.builtin.JsonFormatterPlugin())
        register(ai.deepcode.android.plugin.builtin.HashPlugin())
        register(ai.deepcode.android.plugin.builtin.Base64Plugin())
        register(ai.deepcode.android.plugin.builtin.UnitConverterPlugin())
        register(ai.deepcode.android.plugin.builtin.ColorPalettePlugin())
        register(ai.deepcode.android.plugin.builtin.TextTransformPlugin())
        register(ai.deepcode.android.plugin.builtin.CalendarExportPlugin())
        register(ai.deepcode.android.plugin.builtin.ContactCardPlugin())
        register(ai.deepcode.android.plugin.builtin.MarkdownToPdfPlugin())

        // Google Workspace connector plugins
        appContext?.let { ctx ->
            register(ai.deepcode.android.connector.google.GmailConnectorPlugin(ctx))
            register(ai.deepcode.android.connector.google.CalendarConnectorPlugin(ctx))
            register(ai.deepcode.android.connector.google.DriveConnectorPlugin(ctx))
        }
    }

    /**
     * Register a single plugin. Validates ID uniqueness.
     */
    fun register(plugin: DeepCodePlugin) {
        if (plugins.containsKey(plugin.id)) {
            AppLogger.w(TAG, "Plugin '${plugin.id}' already registered, skipping duplicate")
            return
        }
        plugins[plugin.id] = plugin
        AppLogger.d(TAG, "Registered plugin: ${plugin.id} (${plugin.displayName}) with ${plugin.getTools().size} tools")
    }

    /**
     * Rebuild the tool name → plugin ID lookup map.
     * Called after registration or when plugins are enabled/disabled.
     */
    private fun rebuildToolMap() {
        toolToPluginMap.clear()
        for ((pluginId, plugin) in plugins) {
            for (tool in plugin.getTools()) {
                if (toolToPluginMap.containsKey(tool.name)) {
                    AppLogger.w(TAG, "Tool name conflict: '${tool.name}' already registered by plugin '${toolToPluginMap[tool.name]}', skipping from '$pluginId'")
                } else {
                    toolToPluginMap[tool.name] = pluginId
                }
            }
        }
    }

    // ════════════════════════════════════════════════
    // Query API
    // ════════════════════════════════════════════════

    /** Get all registered plugins */
    fun getAllPlugins(): List<DeepCodePlugin> = plugins.values.toList()

    /** Get a plugin by its ID */
    fun getPlugin(id: String): DeepCodePlugin? = plugins[id]

    /** Get all plugins grouped by category */
    fun getPluginsByCategory(): Map<PluginCategory, List<DeepCodePlugin>> {
        return plugins.values.groupBy { it.category }
    }

    /** Check if a tool name belongs to any registered plugin */
    fun hasToolName(name: String): Boolean = toolToPluginMap.containsKey(name)

    /** Get the count of enabled plugins */
    fun getEnabledCount(): Int {
        val mgr = manager ?: return 0
        return plugins.values.count { mgr.isEnabled(it.id) }
    }

    /** Get the total plugin count */
    fun getTotalCount(): Int = plugins.size

    // ════════════════════════════════════════════════
    // Tool Integration API (called by ToolExecutor)
    // ════════════════════════════════════════════════

    /**
     * Get Tool definitions for all ENABLED plugins.
     * These are merged into ToolExecutor.getDeclaredTools() so the AI can see and call them.
     */
    fun getEnabledTools(): List<Tool> {
        val mgr = manager ?: return emptyList()
        val tools = mutableListOf<Tool>()
        for ((pluginId, plugin) in plugins) {
            if (mgr.isEnabled(pluginId)) {
                tools.addAll(plugin.getTools())
            }
        }
        return tools
    }

    /**
     * Execute a plugin tool call. Routes to the correct plugin based on tool name.
     *
     * @param toolName The tool name from the AI's tool call
     * @param args Parsed JSON arguments
     * @param context Android context
     * @return Result string from the plugin, or error if not found/disabled
     */
    fun executeTool(toolName: String, args: JsonObject, context: Context): String {
        val pluginId = toolToPluginMap[toolName]
            ?: return "Error: No plugin registered for tool '$toolName'"

        val plugin = plugins[pluginId]
            ?: return "Error: Plugin '$pluginId' not found"

        val mgr = manager
        if (mgr != null && !mgr.isEnabled(pluginId)) {
            return "Error: Plugin '${plugin.displayName}' is disabled. Enable it in Settings → Plugins."
        }

        return try {
            AppLogger.d(TAG, "Executing plugin tool: $toolName (plugin: $pluginId)")
            val result = plugin.execute(toolName, args, context)
            mgr?.recordUsage(pluginId)
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Plugin execution failed: $toolName", e)
            "Error: Plugin '${plugin.displayName}' failed — ${e.message}"
        }
    }

    /**
     * Execute a plugin tool call asynchronously. Routes to the correct plugin based on tool name.
     */
    suspend fun executeToolSuspend(toolName: String, args: JsonObject, context: Context): String {
        val pluginId = toolToPluginMap[toolName]
            ?: return "Error: No plugin registered for tool '$toolName'"

        val plugin = plugins[pluginId]
            ?: return "Error: Plugin '$pluginId' not found"

        val mgr = manager
        if (mgr != null && !mgr.isEnabled(pluginId)) {
            return "Error: Plugin '${plugin.displayName}' is disabled. Enable it in Settings → Plugins."
        }

        return try {
            AppLogger.d(TAG, "Executing plugin tool (suspend): $toolName (plugin: $pluginId)")
            val result = plugin.executeSuspend(toolName, args, context)
            mgr?.recordUsage(pluginId)
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "Plugin suspend execution failed: $toolName", e)
            "Error: Plugin '${plugin.displayName}' failed — ${e.message}"
        }
    }

    // ════════════════════════════════════════════════
    // Enable/Disable API (called by PluginsScreen UI)
    // ════════════════════════════════════════════════

    /** Check if a plugin is enabled */
    fun isPluginEnabled(pluginId: String): Boolean {
        return manager?.isEnabled(pluginId) ?: true
    }

    /** Enable a plugin */
    fun enablePlugin(pluginId: String, context: Context) {
        manager?.setEnabled(pluginId, true)
        plugins[pluginId]?.onEnable(context)
        AppLogger.i(TAG, "Plugin enabled: $pluginId")
    }

    /** Disable a plugin */
    fun disablePlugin(pluginId: String, context: Context) {
        manager?.setEnabled(pluginId, false)
        plugins[pluginId]?.onDisable(context)
        AppLogger.i(TAG, "Plugin disabled: $pluginId")
    }

    /** Get usage count for a plugin */
    fun getUsageCount(pluginId: String): Int {
        return manager?.getUsageCount(pluginId) ?: 0
    }

    /** Get a plugin config value */
    fun getConfigValue(pluginId: String, key: String, default: String): String {
        return manager?.getConfig(pluginId, key, default) ?: default
    }

    /** Set a plugin config value */
    fun setConfigValue(pluginId: String, key: String, value: String) {
        manager?.setConfig(pluginId, key, value)
    }

    /** Import a plugin from a URI (JSON or manifest file) */
    fun importPluginFromUri(uri: android.net.Uri, context: Context): Pair<Boolean, String> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: return Pair(false, "Failed to read file content")

            val json = com.google.gson.JsonParser.parseString(content).asJsonObject
            val id = if (json.has("id")) json.get("id").asString else "custom_${System.currentTimeMillis()}"
            val name = if (json.has("name")) json.get("name").asString else if (json.has("displayName")) json.get("displayName").asString else "Custom Plugin"
            val version = if (json.has("version")) json.get("version").asString else "1.0.0"
            val description = if (json.has("description")) json.get("description").asString else "Imported custom plugin"

            val dynamicPlugin = DynamicJsonPlugin(
                id = id,
                displayName = name,
                version = version,
                description = description,
                category = PluginCategory.UTILITY,
                toolList = emptyList()
            )

            register(dynamicPlugin)
            enablePlugin(id, context)
            rebuildToolMap()

            Pair(true, "Successfully imported plugin '$name'")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to import plugin", e)
            Pair(false, "Failed to import plugin: ${e.message}")
        }
    }
}

class DynamicJsonPlugin(
    override val id: String,
    override val displayName: String,
    override val version: String,
    override val description: String,
    override val category: PluginCategory,
    override val iconName: String = "extension",
    private val toolList: List<Tool>
) : DeepCodePlugin {
    override fun getTools(): List<Tool> = toolList
    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return "Executed custom plugin '$displayName' ($toolName)"
    }
}
