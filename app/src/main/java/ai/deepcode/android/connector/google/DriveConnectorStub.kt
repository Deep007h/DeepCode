package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*

/**
 * Stub connector for Google Drive. Phase 2 — not yet implemented.
 * Declares correct scopes and capabilities but all operations return not-implemented error.
 */
class DriveConnectorStub(context: Context) : GoogleConnectorBridge(context) {

    override val connectorId = "drive"
    override val displayName = "Google Drive"
    override val description = "Browse, search, and read files from Google Drive (coming soon)"

    override val requiredScopes = listOf(
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/drive.file"
    )

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "drive_list_files",
            description = "List files in Google Drive (coming soon)",
            parameters = mapOf(
                "query" to ParameterDef("Drive search query", required = false),
                "max_results" to ParameterDef("Maximum files to return", type = "integer", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "drive_read_file",
            description = "Read the content of a file from Google Drive (coming soon)",
            parameters = mapOf(
                "file_id" to ParameterDef("The Google Drive file ID", required = true)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult {
        return ConnectorResult.Error(
            ConnectorError.ApiError(501, "Google Drive connector is not yet implemented. Coming soon!")
        )
    }
}

/**
 * Plugin wrapper for the Drive connector stub.
 */
class DriveConnectorPlugin(context: Context) : ai.deepcode.android.connector.ConnectorPlugin() {
    override val bridge: ConnectorBridge = DriveConnectorStub(context)
    override val iconName: String = "folder"
}
