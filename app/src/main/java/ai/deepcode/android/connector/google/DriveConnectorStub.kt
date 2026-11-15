package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*

/**
 * Connector implementation for Google Drive.
 * Supports browsing root files, searching files, and reading file content.
 */
class DriveConnectorStub(context: Context) : GoogleConnectorBridge(context) {

    override val connectorId = "google_drive"
    override val displayName = "Google Drive"
    override val description = "Browse, search, and read files from Google Drive"

    override val requiredScopes = listOf(
        "https://www.googleapis.com/auth/drive.readonly",
        "https://www.googleapis.com/auth/drive.file"
    )

    private val driveService = ai.deepcode.android.service.google.DriveService(context)

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "drive_list_files",
            description = "List or search files in Google Drive. Returns file names, IDs, MIME types, and sizes.",
            parameters = mapOf(
                "query" to ParameterDef("Drive search query (searches file names). If omitted, lists root files.", required = false),
                "max_results" to ParameterDef("Maximum files to return (defaults to 10)", type = "integer", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "drive_read_file",
            description = "Read the text content of a file from Google Drive by file ID.",
            parameters = mapOf(
                "file_id" to ParameterDef("The Google Drive file ID", required = true)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult {
        return try {
            when (action.actionName) {
                "drive_list_files" -> {
                    val query = action.parameters["query"]?.trim()
                    val maxResults = action.parameters["max_results"]?.toIntOrNull() ?: 10
                    val result = if (!query.isNullOrBlank()) {
                        driveService.searchFiles(query, maxResults)
                    } else {
                        driveService.listRootFiles(maxResults)
                    }

                    result.fold(
                        onSuccess = { files ->
                            if (files.isEmpty()) {
                                ConnectorResult.Success("📁 No files found in Google Drive.")
                            } else {
                                val text = buildString {
                                    appendLine("📁 Found ${files.size} file(s):")
                                    appendLine()
                                    for (file in files) {
                                        val typeIcon = if (file.isFolder) "📁" else "📄"
                                        val sizeStr = if (file.size > 0) " (${file.size / 1024} KB)" else ""
                                        appendLine("$typeIcon **${file.name}**$sizeStr")
                                        appendLine("   ID: ${file.id}")
                                        appendLine("   Type: ${file.mimeType}")
                                        if (file.modifiedTime.isNotBlank()) appendLine("   Modified: ${file.modifiedTime}")
                                        appendLine()
                                    }
                                }
                                ConnectorResult.Success(text.trimEnd())
                            }
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(classifyHttpError(500, error.message ?: "Failed to list Drive files"))
                        }
                    )
                }
                "drive_read_file" -> {
                    val fileId = action.parameters["file_id"]?.trim()
                        ?: return ConnectorResult.Error(ConnectorError.ApiError(400, "file_id is required"))

                    val result = driveService.getFileContent(fileId)
                    result.fold(
                        onSuccess = { content ->
                            ConnectorResult.Success(content)
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(classifyHttpError(500, error.message ?: "Failed to read Drive file"))
                        }
                    )
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "Drive action failed"))
        }
    }
}

/**
 * Plugin wrapper for the Drive connector stub.
 */
class DriveConnectorPlugin(context: Context) : ai.deepcode.android.connector.ConnectorPlugin() {
    override val bridge: ConnectorBridge = DriveConnectorStub(context)
    override val iconName: String = "folder"
}
