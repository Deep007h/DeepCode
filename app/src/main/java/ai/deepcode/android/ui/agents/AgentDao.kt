package ai.deepcode.android.ui.agents

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {
    @Query("SELECT * FROM agents ORDER BY is_builtin DESC, display_name ASC")
    fun getAllAgentsFlow(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents")
    suspend fun getAllAgents(): List<AgentEntity>

    @Query("SELECT * FROM agents WHERE agent_id = :agentId LIMIT 1")
    suspend fun getAgentById(agentId: String): AgentEntity?

    @Query("SELECT * FROM agents WHERE is_builtin = 1 ORDER BY display_name ASC")
    fun getBuiltinAgentsFlow(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE is_builtin = 0 ORDER BY display_name ASC")
    fun getCustomAgentsFlow(): Flow<List<AgentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgent(agent: AgentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgents(agents: List<AgentEntity>)

    @Query("DELETE FROM agents WHERE agent_id = :agentId")
    suspend fun deleteAgent(agentId: String)

    @Query("UPDATE agents SET is_enabled = :isEnabled WHERE agent_id = :agentId")
    suspend fun updateEnabledStatus(agentId: String, isEnabled: Boolean)

    @Query("UPDATE agents SET last_run_at = :lastRunAt, run_count = run_count + 1 WHERE agent_id = :agentId")
    suspend fun recordRun(agentId: String, lastRunAt: Long)

    @Query("UPDATE agents SET display_name = :displayName, description = :description, system_prompt = :systemPrompt, tools = :tools, temperature = :temperature, max_iterations = :maxIterations, updated_at = :updatedAt WHERE agent_id = :agentId")
    suspend fun updateAgent(
        agentId: String,
        displayName: String,
        description: String,
        systemPrompt: String,
        tools: String,
        temperature: Double,
        maxIterations: Int,
        updatedAt: Long
    )
}
