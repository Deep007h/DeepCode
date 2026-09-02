package ai.deepcode.android.ui.connections

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "integrations")
data class IntegrationEntity(
    @PrimaryKey val id: String, // UUID
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "app_name") val appName: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "icon_url") val iconUrl: String,
    val status: String, // "connected" / "disconnected" / "error"
    @ColumnInfo(name = "access_token") val accessToken: String,
    @ColumnInfo(name = "refresh_token") val refreshToken: String,
    val scopes: String,
    @ColumnInfo(name = "connected_at") val connectedAt: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
)
