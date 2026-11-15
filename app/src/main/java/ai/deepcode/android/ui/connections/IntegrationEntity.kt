package ai.deepcode.android.ui.connections

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object ConnectionStatus {
    const val DISCONNECTED = "disconnected"
    const val CONNECTING = "connecting"
    const val CONNECTED = "connected"
    const val EXPIRED = "expired"
    const val ERROR = "error"
}

@Entity(tableName = "integrations")
data class IntegrationEntity(
    @PrimaryKey val id: String, // UUID
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String,
    val status: String, // "disconnected", "connecting", "connected", "expired", "error"
    @ColumnInfo(name = "access_token") val accessToken: String,
    @ColumnInfo(name = "refresh_token") val refreshToken: String,
    val scopes: String,
    @ColumnInfo(name = "connected_at") val connectedAt: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
) {
    val isConnected: Boolean get() = status.equals(ConnectionStatus.CONNECTED, ignoreCase = true)
    val isConnecting: Boolean get() = status.equals(ConnectionStatus.CONNECTING, ignoreCase = true)
    val isExpired: Boolean get() = status.equals(ConnectionStatus.EXPIRED, ignoreCase = true)
    val isError: Boolean get() = status.equals(ConnectionStatus.ERROR, ignoreCase = true)
    val isDisconnected: Boolean get() = status.equals(ConnectionStatus.DISCONNECTED, ignoreCase = true)
}
