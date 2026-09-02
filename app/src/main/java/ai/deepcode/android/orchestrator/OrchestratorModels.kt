package ai.deepcode.android.orchestrator

import java.util.UUID

// ── Deterministic router types (tool-first, no LLM) ──

enum class TaskType {
    TEXT_GENERATION,
    IMAGE_GENERATION,
    IMAGE_SEARCH,
    IMAGE_ANALYSIS,
    SEARCH,
    AUDIO_GENERATION,
    VIDEO_GENERATION,
    DOCUMENT_CREATION
}

enum class TargetSite(val siteName: String) {
    AUTO("auto"),
    GEMINI("gemini"),
    CLAUDE("claude"),
    CHATGPT("chatgpt"),
    PERPLEXITY("perplexity"),
    MIDJOURNEY("midjourney"),
    FIREFLY("firefly"),
    LEONARDO("leonardo"),
    ELEVENLABS("elevenlabs"),
    EDGE_TTS("edge_tts"),
    SUNO("suno"),
    COPILOT("copilot"),
    CANVA("canva"),
    INTERNAL_DOCUMENT_CREATOR("internal_document_creator")
}

enum class OutputFormat {
    TEXT,
    MARKDOWN,
    IMAGE_URL,
    AUDIO_URL,
    VIDEO_URL,
    JSON
}

data class ToolJob(
    val taskType: TaskType,
    val targetSite: TargetSite = TargetSite.AUTO,
    val outputFormat: OutputFormat = OutputFormat.TEXT,
    val userPrompt: String,
    val attachments: List<Map<String, String>> = emptyList()
)

// ── Multi-Agent Orchestration types ──

data class ExecutionTask(
    val id: String = UUID.randomUUID().toString().take(8),
    val description: String,
    val agentType: String,
    val dependsOn: List<String> = emptyList(),
    val priority: String = "normal"
)

data class ExecutionPlan(
    val goal: String,
    val tasks: List<ExecutionTask>,
    val aggregationStrategy: String = "merge"
)

data class SubAgentResult(
    val taskId: String,
    val status: String,
    val result: String,
    val confidence: Double = 1.0,
    val notes: String = "",
    val durationMs: Long = 0
)

// ── Existing types for backward compatibility ──

enum class AgentStatus {
    SUMMONED,
    RUNNING,
    COMPLETE,
    FAILED,
    TRUNCATED
}

enum class OverallStatus {
    PLANNING,
    RUNNING,
    COMPLETE,
    FAILED
}

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val agentType: String,
    val estimatedComplexity: Int = 2,
    val dependsOn: List<String> = emptyList(),
    val tokenBudget: Int = 1500
)

data class SubAgentInstance(
    val subTask: SubTask,
    val status: AgentStatus = AgentStatus.SUMMONED,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val tokensUsed: Int = 0,
    val result: String? = null,
    val error: String? = null,
    val truncated: Boolean = false
)

data class TaskPlan(
    val id: String = UUID.randomUUID().toString(),
    val originalUserMessage: String,
    val subTasks: List<SubTask>,
    val agents: List<SubAgentInstance>,
    val overallStatus: OverallStatus = OverallStatus.PLANNING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val synthesisedResult: String? = null
)

sealed class OrchestratorDecision {
    data class PassToMain(val message: String) : OrchestratorDecision()
    data class Orchestrate(val taskPlan: TaskPlan) : OrchestratorDecision()
    data class DirectTool(val toolJob: ToolJob) : OrchestratorDecision()
}

data class OrchestrationState(
    val activePlan: TaskPlan? = null,
    val isRunning: Boolean = false,
    val mainAgentAvailable: Boolean = true,
    val overallProgress: Pair<Int, Int> = 0 to 0
)
