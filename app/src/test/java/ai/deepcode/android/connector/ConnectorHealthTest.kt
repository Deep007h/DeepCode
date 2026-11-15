package ai.deepcode.android.connector

import ai.deepcode.android.ui.connections.ConnectionStatus
import ai.deepcode.android.ui.connections.IntegrationEntity
import org.junit.Assert.*
import org.junit.Test

class ConnectorHealthTest {

    @Test
    fun testConnectionStatusStatesAndHelpers() {
        val connected = IntegrationEntity(
            id = "1",
            appId = "google",
            appName = "Google",
            displayName = "Google Workspace",
            iconUrl = "",
            status = ConnectionStatus.CONNECTED,
            accessToken = "valid_tok",
            refreshToken = "refresh_tok",
            scopes = "",
            connectedAt = System.currentTimeMillis(),
            lastSyncedAt = System.currentTimeMillis()
        )
        assertTrue(connected.isConnected)
        assertFalse(connected.isConnecting)
        assertFalse(connected.isExpired)
        assertFalse(connected.isError)
        assertFalse(connected.isDisconnected)

        val connecting = connected.copy(status = ConnectionStatus.CONNECTING)
        assertTrue(connecting.isConnecting)
        assertFalse(connecting.isConnected)

        val expired = connected.copy(status = ConnectionStatus.EXPIRED)
        assertTrue(expired.isExpired)
        assertFalse(expired.isConnected)

        val error = connected.copy(status = ConnectionStatus.ERROR)
        assertTrue(error.isError)
        assertFalse(error.isConnected)

        val disconnected = connected.copy(status = ConnectionStatus.DISCONNECTED)
        assertTrue(disconnected.isDisconnected)
        assertFalse(disconnected.isConnected)
    }

    @Test
    fun testConnectorErrorClassificationAndFormatting() {
        val authExpired = ConnectorError.AuthExpired("Token expired 5m ago")
        assertEquals("Authentication expired: Token expired 5m ago", authExpired.toDisplayString())

        val permDenied = ConnectorError.PermissionDenied("Scope calendar.events not granted")
        assertEquals("Permission denied: Scope calendar.events not granted", permDenied.toDisplayString())

        val netErr = ConnectorError.NetworkError("Connection refused: 127.0.0.1:3001")
        assertEquals("Network error: Connection refused: 127.0.0.1:3001", netErr.toDisplayString())

        val apiErr = ConnectorError.ApiError(404, "Page not found")
        assertEquals("API error (404): Page not found", apiErr.toDisplayString())
    }

    @Test
    fun testConnectorResultSemantics() {
        val success = ConnectorResult.Success("{\"status\":\"ok\"}", mapOf("latency" to "120ms"))
        assertEquals("{\"status\":\"ok\"}", success.data)
        assertEquals("120ms", success.metadata["latency"])

        val failure = ConnectorResult.Error(ConnectorError.ApiError(500, "Internal Server Error"))
        assertTrue(failure.error is ConnectorError.ApiError)
        assertEquals(500, (failure.error as ConnectorError.ApiError).code)
    }

    @Test
    fun testCapabilityAndParameterDef() {
        val param = ParameterDef(
            description = "Channel / Chat ID",
            type = "string",
            required = true
        )
        assertEquals("string", param.type)
        assertTrue(param.required)

        val capability = ConnectorCapability(
            actionName = "telegram_send_message",
            description = "Send a text message via Telegram Bot",
            parameters = mapOf("chat_id" to param)
        )
        assertEquals("telegram_send_message", capability.actionName)
        assertEquals(1, capability.parameters.size)
        assertTrue(capability.parameters.containsKey("chat_id"))
    }
}
