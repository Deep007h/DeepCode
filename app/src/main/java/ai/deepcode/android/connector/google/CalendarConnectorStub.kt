package ai.deepcode.android.connector.google

import android.content.Context
import ai.deepcode.android.connector.*
import ai.deepcode.android.service.google.GoogleAuthService

/**
 * Connector implementation for Google Calendar.
 * Supports viewing upcoming events and scheduling new events.
 */
class CalendarConnectorStub(context: Context) : GoogleConnectorBridge(context) {

    override val connectorId = "google_calendar"
    override val displayName = "Google Calendar"
    override val description = "View and manage Google Calendar events"

    override val requiredScopes = listOf(
        "https://www.googleapis.com/auth/calendar.readonly",
        "https://www.googleapis.com/auth/calendar.events"
    )

    private val calendarService = ai.deepcode.android.service.google.CalendarService(context)

    override fun capabilities(): List<ConnectorCapability> = listOf(
        ConnectorCapability(
            actionName = "calendar_list_events",
            description = "List upcoming calendar events. Returns event title, start and end time, location, and description.",
            parameters = mapOf(
                "max_results" to ParameterDef("Maximum events to return (defaults to 10)", type = "integer", required = false),
                "time_min" to ParameterDef("Start time in ISO 8601 format (e.g. 2026-11-15T00:00:00Z)", required = false)
            )
        ),
        ConnectorCapability(
            actionName = "calendar_create_event",
            description = "Create a new event in Google Calendar.",
            parameters = mapOf(
                "summary" to ParameterDef("Event title/summary", required = true),
                "start" to ParameterDef("Start time in ISO 8601 format (e.g. 2026-11-15T14:00:00Z)", required = true),
                "end" to ParameterDef("End time in ISO 8601 format (e.g. 2026-11-15T15:00:00Z)", required = true),
                "description" to ParameterDef("Detailed description of the event", required = false),
                "location" to ParameterDef("Physical location or virtual meeting link", required = false)
            )
        )
    )

    override suspend fun invoke(action: ConnectorAction): ConnectorResult {
        return try {
            when (action.actionName) {
                "calendar_list_events" -> {
                    val maxResults = action.parameters["max_results"]?.toIntOrNull() ?: 10
                    val timeMin = action.parameters["time_min"]
                    val result = calendarService.getUpcomingEvents(maxResults, timeMin)
                    result.fold(
                        onSuccess = { events ->
                            if (events.isEmpty()) {
                                ConnectorResult.Success("📅 No upcoming calendar events found.")
                            } else {
                                val text = buildString {
                                    appendLine("📅 Found ${events.size} upcoming event(s):")
                                    appendLine()
                                    for (event in events) {
                                        val timeStr = if (event.isAllDay) "All Day (${event.startTime})" else "${event.startTime} - ${event.endTime}"
                                        appendLine("• **${event.title}**")
                                        appendLine("  Time: $timeStr")
                                        if (event.location.isNotBlank()) appendLine("  Location: ${event.location}")
                                        if (event.description.isNotBlank()) appendLine("  Description: ${event.description}")
                                        appendLine()
                                    }
                                }
                                ConnectorResult.Success(text.trimEnd())
                            }
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(classifyHttpError(500, error.message ?: "Failed to list events"))
                        }
                    )
                }
                "calendar_create_event" -> {
                    val summary = action.parameters["summary"]
                        ?: return ConnectorResult.Error(ConnectorError.ApiError(400, "Event 'summary' is required"))
                    val start = action.parameters["start"]
                        ?: return ConnectorResult.Error(ConnectorError.ApiError(400, "Event 'start' time is required"))
                    val end = action.parameters["end"]
                        ?: return ConnectorResult.Error(ConnectorError.ApiError(400, "Event 'end' time is required"))
                    val description = action.parameters["description"] ?: ""
                    val location = action.parameters["location"] ?: ""

                    val result = calendarService.createEvent(summary, start, end, description, location)
                    result.fold(
                        onSuccess = { resp ->
                            ConnectorResult.Success("✅ Calendar event \"$summary\" scheduled successfully.")
                        },
                        onFailure = { error ->
                            ConnectorResult.Error(classifyHttpError(500, error.message ?: "Failed to create event"))
                        }
                    )
                }
                else -> ConnectorResult.Error(ConnectorError.ApiError(400, "Unknown action: ${action.actionName}"))
            }
        } catch (e: Exception) {
            ConnectorResult.Error(ConnectorError.NetworkError(e.message ?: "Calendar action failed"))
        }
    }
}

/**
 * Plugin wrapper for the Calendar connector stub.
 */
class CalendarConnectorPlugin(context: Context) : ai.deepcode.android.connector.ConnectorPlugin() {
    override val bridge: ConnectorBridge = CalendarConnectorStub(context)
    override val iconName: String = "event"
}
