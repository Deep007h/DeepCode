package ai.deepcode.android.connector.telegram

import android.content.Context
import ai.deepcode.android.connector.ConnectorBridge
import ai.deepcode.android.connector.ConnectorPlugin

class TelegramConnectorPlugin(context: Context) : ConnectorPlugin() {
    override val bridge: ConnectorBridge = TelegramConnector(context)
    override val iconName: String = "send"
}
