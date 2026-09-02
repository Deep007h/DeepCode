package ai.deepcode.android.ui.agents

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val description: String,
    @ColumnInfo(name = "agent_tier") val agentTier: String,
    val temperature: Double,
    @ColumnInfo(name = "max_iterations") val maxIterations: Int,
    @ColumnInfo(name = "sandbox_mode") val sandboxMode: String,
    @ColumnInfo(name = "omit_identity") val omitIdentity: Boolean,
    @ColumnInfo(name = "omit_memory_context") val omitMemoryContext: Boolean,
    @ColumnInfo(name = "omit_safety_preamble") val omitSafetyPreamble: Boolean,
    @ColumnInfo(name = "omit_skills_catalog") val omitSkillsCatalog: Boolean,
    @ColumnInfo(name = "omit_profile") val omitProfile: Boolean,
    @ColumnInfo(name = "omit_memory_md") val omitMemoryMd: Boolean,
    @ColumnInfo(name = "model_hint") val modelHint: String,
    @ColumnInfo(name = "delegate_name") val delegateName: String?,
    @ColumnInfo(name = "system_prompt") val systemPrompt: String,
    val tools: String,
    val subagents: String,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long,
    @ColumnInfo(name = "run_count") val runCount: Int
)
