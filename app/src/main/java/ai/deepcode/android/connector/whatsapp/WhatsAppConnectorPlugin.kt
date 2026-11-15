package ai.deepcode.android.connector.whatsapp

import android.content.Context
import ai.deepcode.android.connector.ConnectorBridge
import ai.deepcode.android.connector.ConnectorPlugin

class WhatsAppConnectorPlugin(context: Context) : ConnectorPlugin() {
    override val bridge: ConnectorBridge = WhatsAppConnector(context)
    override val iconName: String = "chat"
}
