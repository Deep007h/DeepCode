package ai.deepcode.android.ui.connections

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

class IntegrationRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val integrationDao = database.integrationDao()

    fun getAllIntegrationsFlow(): Flow<List<IntegrationEntity>> = integrationDao.getAllIntegrationsFlow()

    suspend fun getAllIntegrations(): List<IntegrationEntity> = integrationDao.getAllIntegrations()

    suspend fun getIntegrationByAppId(appId: String): IntegrationEntity? = integrationDao.getIntegrationByAppId(appId)

    suspend fun insertIntegration(integration: IntegrationEntity) = integrationDao.insertIntegration(integration)

    suspend fun insertIntegrations(integrations: List<IntegrationEntity>) = integrationDao.insertIntegrations(integrations)

    suspend fun deleteIntegration(id: String) = integrationDao.deleteIntegration(id)

    suspend fun updateConnectionStatus(appId: String, status: String, accessToken: String, refreshToken: String, connectedAt: Long) {
        integrationDao.updateConnectionStatus(appId, status, accessToken, refreshToken, connectedAt)
    }
}
