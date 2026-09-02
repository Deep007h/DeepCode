package ai.deepcode.android.ui.agents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

class AgentsViewModel(private val context: Context) : ViewModel() {
    private val repository = AgentRepository(context)

    val agents: StateFlow<List<AgentEntity>> = repository.getAllAgentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val builtinAgents: StateFlow<List<AgentEntity>> = repository.getBuiltinAgentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customAgents: StateFlow<List<AgentEntity>> = repository.getCustomAgentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Volatile
    private var seeded = false

    fun seedBuiltinAgentsIfNeeded() {
        if (seeded) return
        seeded = true
        viewModelScope.launch {
            val existing = repository.getAllAgents()
            if (existing.isNotEmpty()) return@launch

            val agents = withContext(Dispatchers.IO) {
                val jsonText = context.assets.open("agents/builtin_agents.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonText)
                val now = System.currentTimeMillis()
                val result = mutableListOf<AgentEntity>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val toolsArray = obj.optJSONArray("tools")
                    val tools = if (toolsArray != null) {
                        (0 until toolsArray.length()).map { toolsArray.getString(it) }.joinToString(",")
                    } else ""

                    val subagentsArray = obj.optJSONArray("subagents")
                    val subagents = if (subagentsArray != null) {
                        (0 until subagentsArray.length()).map { subagentsArray.getString(it) }.joinToString(",")
                    } else ""

                    result.add(AgentEntity(
                        agentId = obj.getString("agent_id"),
                        displayName = obj.getString("display_name"),
                        description = obj.getString("description"),
                        agentTier = obj.optString("agent_tier", "worker"),
                        temperature = obj.optDouble("temperature", 0.4),
                        maxIterations = obj.optInt("max_iterations", 6),
                        sandboxMode = obj.optString("sandbox_mode", "none"),
                        omitIdentity = obj.optBoolean("omit_identity", true),
                        omitMemoryContext = obj.optBoolean("omit_memory_context", true),
                        omitSafetyPreamble = obj.optBoolean("omit_safety_preamble", true),
                        omitSkillsCatalog = obj.optBoolean("omit_skills_catalog", true),
                        omitProfile = obj.optBoolean("omit_profile", false),
                        omitMemoryMd = obj.optBoolean("omit_memory_md", false),
                        modelHint = obj.optString("model_hint", "agentic"),
                        delegateName = if (obj.has("delegate_name")) { val dn = obj.optString("delegate_name", ""); if (dn.isEmpty()) null else dn } else null,
                        systemPrompt = obj.getString("system_prompt"),
                        tools = tools,
                        subagents = subagents,
                        isBuiltin = obj.optBoolean("is_builtin", true),
                        isEnabled = true,
                        createdAt = now,
                        updatedAt = now,
                        lastRunAt = 0L,
                        runCount = 0
                    ))
                }
                result
            }
            repository.insertAgents(agents)
        }
    }

    fun toggleAgent(agentId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateEnabledStatus(agentId, isEnabled)
            val agent = repository.getAgentById(agentId)
            if (agent != null) {
                val scheduler = AgentScheduler(context)
                if (isEnabled) {
                    if (agent.agentId == "morning_briefing") {
                        scheduler.schedule(agent)
                    }
                } else {
                    scheduler.cancel(agent.agentId)
                }
            }
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            AgentScheduler(context).cancel(agentId)
            repository.deleteAgent(agentId)
        }
    }

    fun recordRun(agentId: String) {
        viewModelScope.launch {
            repository.recordRun(agentId, System.currentTimeMillis())
        }
    }
}
