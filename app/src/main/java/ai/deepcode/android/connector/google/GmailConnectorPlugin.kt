package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.ConnectorBridge
import ai.deepcode.android.connector.ConnectorPlugin

/**
 * Plugin wrapper for the Gmail connector.
 * Registered in [ai.deepcode.android.plugin.PluginRegistry] to make Gmail
 * tools visible to the orchestrator and AI.
 */
class GmailConnectorPlugin(context: Context) : ConnectorPlugin() {
    override val bridge: ConnectorBridge = GmailConnector(context)
    override val iconName: String = "email"
}
