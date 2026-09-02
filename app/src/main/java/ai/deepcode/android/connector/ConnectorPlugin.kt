package ai.deepcode.android.connector

import android.content.Context
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.plugin.DeepCodePlugin
import ai.deepcode.android.plugin.PluginCategory
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking

/**
 * Abstract adapter that bridges [ConnectorBridge] into the [DeepCodePlugin] interface.
 *
 * This allows any connector to be registered in [ai.deepcode.android.plugin.PluginRegistry]
 * and appear as a standard plugin with tool definitions callable by the orchestrator.
 *
 * Subclasses only need to provide a [bridge] instance — all plugin interface methods
 * are automatically derived from the connector's capabilities.
 */
abstract class ConnectorPlugin : DeepCodePlugin {

    /** The underlying connector bridge this plugin wraps */
    abstract val bridge: ConnectorBridge

    override val id: String get() = "connector_${bridge.connectorId}"
    override val displayName: String get() = bridge.displayName
    override val description: String get() = bridge.description
    override val version: String = "1.0"
    override val category: PluginCategory = PluginCategory.INTEGRATION
    override val iconName: String = "link"

    override val requiredPermissions: List<String>
        get() = bridge.requiredScopes

    /**
     * Generate [Tool] definitions from the connector's declared capabilities.
     * Each capability becomes a tool the LLM can call.
     */
    override fun getTools(): List<Tool> {
        return bridge.capabilities().map { capability ->
            val properties = mutableMapOf<String, Any>()
            val required = mutableListOf<String>()

            for ((paramName, paramDef) in capability.parameters) {
                properties[paramName] = mapOf(
                    "type" to paramDef.type,
                    "description" to paramDef.description
                )
                if (paramDef.required) required.add(paramName)
            }

            Tool(
                name = capability.actionName,
                description = capability.description,
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to properties,
                    "required" to required
                )
            )
        }
    }

    /**
     * Synchronous execution — delegates to [executeSuspend] via runBlocking.
     * Prefer [executeSuspend] for coroutine contexts.
     */
    override fun execute(toolName: String, args: JsonObject, context: Context): String {
        return runBlocking { executeSuspend(toolName, args, context) }
    }

    /**
     * Async execution path — invokes the connector bridge with proper error handling.
     * Auth-expired errors trigger automatic refresh and retry.
     */
    override suspend fun executeSuspend(toolName: String, args: JsonObject, context: Context): String {
        // Ensure authenticated
        if (!bridge.isAuthenticated()) {
            val authResult = bridge.authenticate(context)
            if (authResult !is AuthResult.Success) {
                return "Error: Not authenticated with ${bridge.displayName}. Please connect in Settings → Connections."
            }
        }

        // Build action from tool call
        val params = mutableMapOf<String, String>()
        for (key in args.keySet()) {
            params[key] = args.get(key)?.asString ?: ""
        }
        val action = ConnectorAction(actionName = toolName, parameters = params)

        // Execute with auto-refresh on auth expiry
        val result = bridge.invoke(action)
        return when (result) {
            is ConnectorResult.Success -> result.data
            is ConnectorResult.Error -> when (result.error) {
                is ConnectorError.AuthExpired -> {
                    // Attempt silent refresh and retry once
                    if (bridge.refreshTokenIfNeeded()) {
                        val retryResult = bridge.invoke(action)
                        when (retryResult) {
                            is ConnectorResult.Success -> retryResult.data
                            is ConnectorResult.Error -> "Error: ${retryResult.error.toDisplayString()}"
                        }
                    } else {
                        "Error: Authentication expired for ${bridge.displayName}. Please reconnect in Settings → Connections."
                    }
                }
                else -> "Error: ${result.error.toDisplayString()}"
            }
        }
    }
}
