package ai.deepcode.android.connector.github

import android.content.Context
import ai.deepcode.android.connector.ConnectorBridge
import ai.deepcode.android.connector.ConnectorPlugin

class GitHubConnectorPlugin(context: Context) : ConnectorPlugin() {
    override val bridge: ConnectorBridge = GitHubConnector(context)
    override val iconName: String = "code"
}
