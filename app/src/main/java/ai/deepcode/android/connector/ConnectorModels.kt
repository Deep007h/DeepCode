package ai.deepcode.android.connector

/**
 * Result of an authentication attempt.
 */
sealed class AuthResult {
    /** Authentication succeeded */
    data class Success(val accountEmail: String = "") : AuthResult()

    /** Authentication failed */
    data class Failure(val error: String, val isRecoverable: Boolean = false) : AuthResult()

    /** Authentication was cancelled by the user */
    data object Cancelled : AuthResult()
}

/**
 * An action to be performed by a connector.
 * Actions are identified by name and carry typed parameters.
 */
data class ConnectorAction(
    /** Action name matching a [ConnectorCapability.actionName] */
    val actionName: String,
    /** Action parameters as key-value pairs */
    val parameters: Map<String, String> = emptyMap()
)

/**
 * Result of executing a connector action.
 */
sealed class ConnectorResult {
    /** Action completed successfully */
    data class Success(val data: String, val metadata: Map<String, String> = emptyMap()) : ConnectorResult()

    /** Action failed with a categorized error */
    data class Error(val error: ConnectorError) : ConnectorResult()
}

/**
 * Categorized connector errors for proper handling by the orchestrator.
 *
 * - [AuthExpired]: trigger silent token refresh, then retry
 * - [PermissionDenied]: surface to user, do not retry
 * - [NetworkError]: backoff + retry (consistent with provider routing failover)
 * - [ApiError]: log and return error string
 */
sealed class ConnectorError {
    /** Access token expired — should trigger silent refresh */
    data class AuthExpired(val message: String) : ConnectorError()

    /** Insufficient permissions — surface to user, don't retry */
    data class PermissionDenied(val message: String) : ConnectorError()

    /** Transient network failure — backoff and retry */
    data class NetworkError(val message: String) : ConnectorError()

    /** API-level error (4xx/5xx not auth-related) */
    data class ApiError(val code: Int, val message: String) : ConnectorError()

    fun toDisplayString(): String = when (this) {
        is AuthExpired -> "Authentication expired: $message"
        is PermissionDenied -> "Permission denied: $message"
        is NetworkError -> "Network error: $message"
        is ApiError -> "API error ($code): $message"
    }
}

/**
 * Describes a single action/capability that a connector supports.
 * Used to generate tool definitions for the orchestrator.
 */
data class ConnectorCapability(
    /** Unique action name (e.g., "gmail_list_messages") */
    val actionName: String,
    /** Human-readable description of what this action does */
    val description: String,
    /** Parameter definitions: name -> description */
    val parameters: Map<String, ParameterDef> = emptyMap()
)

/**
 * Definition of an action parameter.
 */
data class ParameterDef(
    val description: String,
    val type: String = "string",
    val required: Boolean = false
)
