package ai.deepcode.android.plugin.builtin

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import ai.deepcode.android.plugin.PluginConfigField
import ai.deepcode.android.plugin.ConfigFieldType
import ai.deepcode.android.util.RootSystem
import com.google.gson.JsonObject

/**
 * Built-in System Toolkit Plugin.
 * Exposes device diagnostics, hardware telemetry, package management,
 * and screenshot capture capabilities to the AI and user.
 */
class SystemToolkitPlugin : DeepCodePlugin {
    override val id: String = "system_toolkit"
    override val displayName: String = "System Toolkit & Hardware"
    override val description: String = "On-device battery telemetry, RAM/storage stats, app lifecycle control, and screenshot capture"
    override val version: String = "1.0.0"
    override val category: PluginCategory = PluginCategory.UTILITY
    override val iconName: String = "build"

    override fun getTools(): List<Tool> {
        return listOf(
            Tool("system_battery_info", "Get real-time device battery percentage, health, temperature, voltage, and charging status", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )),
            Tool("system_memory_info", "Get RAM breakdown (/proc/meminfo) and storage disk usage (df -h)", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )),
            Tool("system_device_info", "Get device model, manufacturer, Android SDK, Linux kernel release, and SELinux status", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )),
            Tool("system_screenshot", "Capture an instant screenshot of the current Android screen and return it for viewing in chat", mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>()
            )),
            Tool("system_app_control", "Manage installed Android packages: freeze (disable), unfreeze (enable), force-stop, clear cache, or launch", mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "action" to mapOf(
                        "type" to "string",
                        "description" to "Action to execute: 'freeze', 'unfreeze', 'force_stop', 'clear_cache', 'launch', or 'list'",
                        "enum" to listOf("freeze", "unfreeze", "force_stop", "clear_cache", "launch", "list")
                    ),
                    "package_name" to mapOf(
                        "type" to "string",
                        "description" to "Target Android package name (e.g. 'com.example.app') or filter for 'list'"
                    )
                ),
                "required" to listOf("action")
            ))
        )
    }

    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return when (toolName) {
            "system_battery_info" -> RootSystem.getBatteryInfo()
            "system_memory_info" -> RootSystem.getMemoryInfo()
            "system_device_info" -> RootSystem.getDeviceInfo()
            "system_screenshot" -> RootSystem.takeScreenshot()
            "system_app_control" -> {
                val action = args.get("action")?.asString ?: "list"
                val pkg = args.get("package_name")?.asString ?: ""
                if (action.equals("list", ignoreCase = true)) {
                    RootSystem.listInstalledPackages(pkg)
                } else {
                    RootSystem.appControl(action, pkg)
                }
            }
            else -> "Unknown system toolkit tool: $toolName"
        }
    }

    override fun getConfigFields(): List<PluginConfigField> {
        return listOf(
            PluginConfigField(
                key = "system_toolkit_save_dir",
                label = "Screenshot Save Directory",
                type = ConfigFieldType.TEXT,
                defaultValue = "/storage/emulated/0/Download"
            )
        )
    }
}
