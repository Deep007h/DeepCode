package ai.deepcode.android.ui.automations

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.flow.Flow

class AutomationRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val automationDao = database.automationDao()

    fun getAllAutomationsFlow(): Flow<List<AutomationEntity>> = automationDao.getAllAutomationsFlow()

    suspend fun getAllAutomations(): List<AutomationEntity> {
        val result = automationDao.getAllAutomations()
        AppLogger.d("AutomationRepository", "Fetched ${result.size} automations")
        return result
    }

    suspend fun getAutomationById(id: String): AutomationEntity? {
        val result = automationDao.getAutomationById(id)
        AppLogger.d("AutomationRepository", "Fetched automation $id: ${if (result != null) "FOUND" else "NOT_FOUND"}")
        return result
    }

    suspend fun insertAutomation(automation: AutomationEntity) {
        AppLogger.d("AutomationRepository", "Inserting automation: ${automation.name} (${automation.id})")
        automationDao.insertAutomation(automation)
    }

    suspend fun deleteAutomation(id: String) {
        AppLogger.d("AutomationRepository", "Deleting automation: $id")
        automationDao.deleteAutomation(id)
    }

    suspend fun updateEnabledStatus(id: String, isEnabled: Boolean) {
        AppLogger.d("AutomationRepository", "Updating automation $id enabled=$isEnabled")
        automationDao.updateEnabledStatus(id, isEnabled)
    }
}
