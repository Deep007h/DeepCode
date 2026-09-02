package ai.deepcode.android.connector

import android.content.Context

/**
 * Core interface for third-party service integrations (Google Workspace, etc.).
 *
 * Every connector implements this interface to provide:
 * - Authentication lifecycle (OAuth, token refresh, revocation)
 * - Capability declaration (what actions the connector supports)
 * - Action invocation (execute a specific action)
 *
 * Connectors are registered through the existing plugin system via [ConnectorPlugin],
 * so the orchestrator sees them as regular tools.
 */
interface ConnectorBridge {
    /** Unique connector identifier (e.g., "gmail", "calendar", "drive") */
    val connectorId: String

    /** Human-readable name for UI display */
    val displayName: String

    /** Description of what this connector does */
    val description: String

    /** OAuth scopes required by this connector */
    val requiredScopes: List<String>

    /**
     * Authenticate with the service. Initiates OAuth flow if needed.
     * @return [AuthResult] indicating success/failure
     */
    suspend fun authenticate(context: Context): AuthResult

    /** Check if the connector currently has valid credentials */
    suspend fun isAuthenticated(): Boolean

    /**
     * Refresh the access token if expired, using the stored refresh token.
     * @return true if refresh succeeded or token was still valid
     */
    suspend fun refreshTokenIfNeeded(): Boolean

    /**
     * Revoke all tokens and disconnect this connector.
     * Calls the service's revoke endpoint and clears local tokens.
     */
    suspend fun revokeAccess(): Boolean

    /**
     * Execute an action on the connected service.
     * @param action The action to perform, with parameters
     * @return [ConnectorResult] with the result or error
     */
    suspend fun invoke(action: ConnectorAction): ConnectorResult

    /** List all capabilities/actions this connector supports */
    fun capabilities(): List<ConnectorCapability>
}
