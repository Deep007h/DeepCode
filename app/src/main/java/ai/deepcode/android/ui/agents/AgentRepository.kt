package ai.deepcode.android.ui.agents

import android.content.Context
import ai.deepcode.android.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

class AgentRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val agentDao = database.agentDao()

    fun getAllAgentsFlow(): Flow<List<AgentEntity>> = agentDao.getAllAgentsFlow()
    fun getBuiltinAgentsFlow(): Flow<List<AgentEntity>> = agentDao.getBuiltinAgentsFlow()
    fun getCustomAgentsFlow(): Flow<List<AgentEntity>> = agentDao.getCustomAgentsFlow()

    suspend fun getAllAgents(): List<AgentEntity> = agentDao.getAllAgents()
    suspend fun getAgentById(agentId: String): AgentEntity? = agentDao.getAgentById(agentId)
    suspend fun insertAgent(agent: AgentEntity) = agentDao.insertAgent(agent)
    suspend fun insertAgents(agents: List<AgentEntity>) = agentDao.insertAgents(agents)
    suspend fun deleteAgent(agentId: String) = agentDao.deleteAgent(agentId)
    suspend fun updateEnabledStatus(agentId: String, isEnabled: Boolean) = agentDao.updateEnabledStatus(agentId, isEnabled)
    suspend fun recordRun(agentId: String, lastRunAt: Long) = agentDao.recordRun(agentId, lastRunAt)
    suspend fun updateAgent(
        agentId: String,
        displayName: String,
        description: String,
        systemPrompt: String,
        tools: String,
        temperature: Double,
        maxIterations: Int,
        updatedAt: Long
    ) = agentDao.updateAgent(agentId, displayName, description, systemPrompt, tools, temperature, maxIterations, updatedAt)
}
