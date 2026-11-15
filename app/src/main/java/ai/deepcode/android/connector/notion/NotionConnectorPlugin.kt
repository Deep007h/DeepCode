package ai.deepcode.android.connector.notion

import android.content.Context
import ai.deepcode.android.connector.ConnectorBridge
import ai.deepcode.android.connector.ConnectorPlugin

class NotionConnectorPlugin(context: Context) : ConnectorPlugin() {
    override val bridge: ConnectorBridge = NotionConnector(context)
    override val iconName: String = "description"
}
