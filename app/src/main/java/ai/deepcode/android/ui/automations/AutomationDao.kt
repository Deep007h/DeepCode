package ai.deepcode.android.ui.automations

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY name ASC")
    fun getAllAutomationsFlow(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations")
    suspend fun getAllAutomations(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id LIMIT 1")
    suspend fun getAutomationById(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAutomation(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteAutomation(id: String)

    @Query("UPDATE automations SET is_enabled = :isEnabled WHERE id = :id")
    suspend fun updateEnabledStatus(id: String, isEnabled: Boolean)
}
