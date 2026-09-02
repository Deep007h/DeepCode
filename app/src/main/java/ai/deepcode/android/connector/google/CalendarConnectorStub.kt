package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.service.google.GoogleAuthService

/**
 * Stub connector for Google Calendar. Phase 2 — not yet implemented.
 * Declares correct scopes and capabilities but all operations throw TODO.
 */
class CalendarConnectorStub(context: Context) : GoogleConnectorBridge(context) {

    override val connectorId = "calendar"
    override val displayName = "Google Calendar"
    override val description = "View and manage Google Calendar events (coming soon)"

    override val requiredScopes = listOf(
        "https://www.googleapis.com/auth/calendar.readonly",
        "https://www.googleapis.com/auth/calendar.events"
    )

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "calendar_list_events",
            description = "List upcoming calendar events (coming soon)",
            parameters = mapOf(
                "max_results" to ParameterDef("Maximum events to return", type = "integer", required = false),
                "time_min" to ParameterDef("Start time in ISO 8601 format", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "calendar_create_event",
            description = "Create a new calendar event (coming soon)",
            parameters = mapOf(
                "summary" to ParameterDef("Event title", required = true),
                "start" to ParameterDef("Start time in ISO 8601 format", required = true),
                "end" to ParameterDef("End time in ISO 8601 format", required = true),
                "description" to ParameterDef("Event description", required = false)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult {
        return ConnectorResult.Error(
            ConnectorError.ApiError(501, "Google Calendar connector is not yet implemented. Coming soon!")
        )
    }
}

/**
 * Plugin wrapper for the Calendar connector stub.
 */
class CalendarConnectorPlugin(context: Context) : ai.deepcode.android.connector.ConnectorPlugin() {
    override val bridge: ConnectorBridge = CalendarConnectorStub(context)
    override val iconName: String = "event"
}
