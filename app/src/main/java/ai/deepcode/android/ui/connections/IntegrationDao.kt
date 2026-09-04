package ai.deepcode.android.ui.connections

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IntegrationDao {
    @Query("SELECT * FROM integrations ORDER BY app_name ASC")
    fun getAllIntegrationsFlow(): Flow<List<IntegrationEntity>>

    @Query("SELECT * FROM integrations")
    suspend fun getAllIntegrations(): List<IntegrationEntity>

    @Query("SELECT * FROM integrations WHERE app_id = :appId LIMIT 1")
    suspend fun getIntegrationByAppId(appId: String): IntegrationEntity?

    @Query("SELECT * FROM integrations WHERE app_id = :appId LIMIT 1")
    fun getIntegrationByAppIdSync(appId: String): IntegrationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntegration(integration: IntegrationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntegrations(integrations: List<IntegrationEntity>)

    @Query("DELETE FROM integrations WHERE id = :id")
    suspend fun deleteIntegration(id: String)

    @Query("UPDATE integrations SET status = :status, access_token = :accessToken, refresh_token = :refreshToken, connected_at = :connectedAt WHERE app_id = :appId")
    suspend fun updateConnectionStatus(appId: String, status: String, accessToken: String, refreshToken: String, connectedAt: Long)
}
