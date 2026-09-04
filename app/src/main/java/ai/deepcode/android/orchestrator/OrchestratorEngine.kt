package ai.deepcode.android.orchestrator

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.ui.agents.AgentEntity
import ai.deepcode.android.ui.agents.AgentRepository
import ai.deepcode.android.ui.agents.AgentRuntime
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class OrchestratorEngine(private val context: Context) {
    private val gson = Gson()
    private val prefs = EncryptedPrefs.getInstance(context)
    private val agentRuntime = AgentRuntime(context)
    private val toolExecutor = ToolExecutor(context)

    private val _state = MutableStateFlow(OrchestrationState())
    val state: StateFlow<OrchestrationState> = _state.asStateFlow()

    private val _mainAgentBusy = MutableStateFlow(false)
    val mainAgentBusy: StateFlow<Boolean> = _mainAgentBusy.asStateFlow()

    @Volatile
    private var cancelled = false
    private var executionJob: Job? = null

    private val concurrencyCap = 5
    private val taskTimeoutMs = 90_000L
    private val maxRetries = 1

    // Shared client — building a new OkHttpClient per call churns connection
    // pools and dispatcher threads, so reuse one and derive per-call timeouts.
    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(taskTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    // ════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════

    // ── New multi-agent API ──

    suspend fun callOrchestrator(userMessage: String): ExecutionPlan? {
        val messages = listOf(
            msg("system", orchestratorSystemPrompt()),
            msg("user", userMessage)
        )
        val raw = callLlm(messages) ?: return null
        return parseExecutionPlan(raw)
    }

    suspend fun executePlan(plan: ExecutionPlan, reportProgress: (ExecutionTask, String) -> Unit): List<SubAgentResult> {
        cancelled = false
        val allResults = mutableListOf<SubAgentResult>()
        val levels = buildExecutionLevels(plan.tasks)

        coroutineScope {
            executionJob = coroutineContext[Job]
            for (level in levels) {
                if (cancelled) break
                val semaphore = Semaphore(concurrencyCap)
                val deferred = level.map { task ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit { runTask(task, plan, reportProgress) }
                    }
                }
                val levelResults = deferred.awaitAll()
                allResults.addAll(levelResults)
            }
        }
        return allResults
    }

    suspend fun callAggregator(goal: String, results: List<SubAgentResult>, strategy: String): String {
        val resultsText = results.joinToString("\n\n") { r ->
            """Task ${r.taskId} [${r.status}] (confidence: ${r.confidence}):
${r.result}
${if (r.notes.isNotEmpty()) "Notes: ${r.notes}" else ""}"""
        }
        val prompt = buildString {
            appendLine("## Goal\n$goal\n")
            appendLine("## Aggregation Strategy\n$strategy\n")
            appendLine("## Sub-Agent Results\n$resultsText")
        }
        val messages = listOf(
            msg("system", aggregatorSystemPrompt()),
            msg("user", prompt)
        )
        return callLlm(messages) ?: "Failed to synthesize results."
    }

    fun cancelExecution() {
        cancelled = true
        executionJob?.cancel()
    }

    // ── Legacy API (used by ChatScreen) ──

    suspend fun executePlan(
        plan: TaskPlan,
        sessionId: String,
        reportProgress: (SubAgentInstance) -> Unit,
        onPhaseComplete: (List<SubAgentInstance>) -> Unit
    ): TaskPlan {
        cancelled = false
        var currentPlan = plan.copy(overallStatus = OverallStatus.RUNNING)
        _state.update { OrchestrationState(activePlan = currentPlan, isRunning = true, mainAgentAvailable = true) }

        val allInstances = currentPlan.agents.toMutableList()
        val completed = java.util.Collections.synchronizedList(mutableListOf<SubAgentInstance>())
        val phases = buildPhases(currentPlan)

        coroutineScope {
            for ((phaseIndex, phase) in phases.withIndex()) {
                if (cancelled) break
                val deferred = phase.map { agent ->
                    async(Dispatchers.IO) {
                        if (cancelled) return@async agent
                        val started = System.currentTimeMillis()
                        val running = agent.copy(status = AgentStatus.RUNNING, startedAt = started)
                        reportProgress(running)
                        try {
                            val agentType = agent.subTask.agentType
                            val agentEntity = findAgentEntity(agentType)
                            val result = withTimeout(90_000L) {
                                if (agentEntity != null) {
                                    val contextLines = buildAgentContext(currentPlan, agent, completed)
                                    agentRuntime.executeAgent(agentEntity, userInput = agent.subTask.description, sessionContext = contextLines)
                                } else "Agent type '$agentType' not found."
                            }
                            val tokenCount = estimateTokens(result)
                            val truncated = tokenCount > agent.subTask.tokenBudget
                            val finalResult = if (truncated) result.take(agent.subTask.tokenBudget * 4) + "\n\n[Result truncated]" else result
                            agent.copy(
                                status = if (truncated) AgentStatus.TRUNCATED else AgentStatus.COMPLETE,
                                completedAt = System.currentTimeMillis(),
                                tokensUsed = tokenCount.coerceAtMost(agent.subTask.tokenBudget),
                                result = finalResult, truncated = truncated
                            )
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            agent.copy(status = AgentStatus.FAILED, completedAt = System.currentTimeMillis(), error = "Agent timed out after 90 seconds.")
                        } catch (e: Exception) {
                            agent.copy(status = AgentStatus.FAILED, completedAt = System.currentTimeMillis(), error = e.message)
                        }
                    }
                }
                val phaseResults = deferred.awaitAll()
                completed.addAll(phaseResults)
                phaseResults.forEach { reportProgress(it) }
                currentPlan = currentPlan.copy(agents = completed.toList(), overallStatus = if (cancelled) OverallStatus.FAILED else currentPlan.overallStatus)
                _state.update { OrchestrationState(activePlan = currentPlan, isRunning = !cancelled && phaseIndex < phases.size - 1, mainAgentAvailable = true, overallProgress = completed.size to allInstances.size) }
                onPhaseComplete(phaseResults)
            }
        }

        if (!cancelled) {
            val finalPlan = currentPlan.copy(overallStatus = OverallStatus.COMPLETE, completedAt = System.currentTimeMillis())
            _state.update { OrchestrationState(activePlan = finalPlan, isRunning = false, mainAgentAvailable = true, overallProgress = finalPlan.agents.size to finalPlan.agents.size) }
        }
        return _state.value.activePlan ?: plan
    }

    suspend fun synthesizeResults(plan: TaskPlan, sessionId: String): String {
        val sb = StringBuilder()
        val allComplete = plan.agents.all { it.status == AgentStatus.COMPLETE }
        val anyFailed = plan.agents.any { it.status == AgentStatus.FAILED }
        val hasOutput = plan.agents.any { !it.result.isNullOrBlank() }

        if (allComplete && hasOutput) sb.appendLine("Here's what was done:\n")
        else if (allComplete && !hasOutput) sb.appendLine("The agents finished but did not produce a clear result. Here's a summary:\n")
        else if (anyFailed) sb.appendLine("Some agents encountered issues. Here's what happened:\n")

        for (agent in plan.agents) {
            val header = agent.subTask.agentType.replace("_", " ").replaceFirstChar { it.uppercase() }
            sb.appendLine("**$header**")
            if (!agent.result.isNullOrBlank()) {
                val result = agent.result.trim()
                if (result.startsWith("{")) {
                    try {
                        val json = JsonParser.parseString(result).asJsonObject
                        val output = json.getAsJsonObject("output")
                        if (output != null) {
                            val textContent = output.get("text_content")?.asString
                            if (!textContent.isNullOrBlank()) sb.appendLine(textContent)
                            val imageUrls = output.getAsJsonArray("image_urls")
                            if (imageUrls != null) for (j in 0 until imageUrls.size()) sb.appendLine("![image](${imageUrls[j].asString})")
                        } else {
                            val legacyResult = json.getAsJsonObject("result")
                            if (legacyResult != null) { val t = legacyResult.get("text")?.asString; if (!t.isNullOrBlank()) sb.appendLine(t) }
                        }
                        val siteUsed = json.get("target_site_used")?.asString ?: json.get("site_used")?.asString
                        if (!siteUsed.isNullOrBlank()) sb.appendLine("*(via $siteUsed)*")
                    } catch (_: Exception) { sb.appendLine(result) }
                } else { sb.appendLine(result) }
            }
            if (agent.error != null) sb.appendLine("*Error: ${agent.error}*")
            if (agent.tokensUsed == 0 && agent.result.isNullOrBlank()) sb.appendLine("*This agent did not produce a response.*")
            sb.appendLine()
        }
        sb.appendLine("---\n${plan.agents.size} agent(s) dispatched — ${plan.agents.count { it.status == AgentStatus.COMPLETE }} complete, ${plan.agents.count { it.status == AgentStatus.FAILED }} failed")
        return sb.toString()
    }

    suspend fun analyzeErrors(plan: TaskPlan): String? {
        val failedAgents = plan.agents.filter { it.status == AgentStatus.FAILED || it.status == AgentStatus.TRUNCATED }
        if (failedAgents.isEmpty()) return null
        val summary = plan.agents.joinToString("\n") { a ->
            val status = a.status.name
            val error = a.error ?: ""
            val resultPreview = (a.result ?: "").take(200)
            "Agent: ${a.subTask.agentType}\nStatus: $status\nError: $error\nResult: $resultPreview"
        }
        val prompt = """Analyze this multi-agent orchestration result and suggest how to fix the failed agents:

$summary

Provide a brief actionable suggestion (2-3 sentences). Focus on:
- What went wrong (timeout, missing info, tool error)
- How to fix it (switch model, increase timeout, provide more context, try a different approach)
- Keep it concise and practical."""
        return try {
            val zenApiKey = prefs.getApiKey("zen")
            if (zenApiKey.isEmpty()) return null
            val messages = listOf(
                JsonObject().apply { addProperty("role", "user"); addProperty("content", prompt) }
            )
            val body = JsonObject().apply {
                addProperty("model", "deepseek-v4-flash-free")
                add("messages", gson.toJsonTree(messages))
                addProperty("temperature", 0.3)
                addProperty("max_tokens", 512)
            }
            val request = okhttp3.Request.Builder()
                .url("https://opencode.ai/zen/v1/chat/completions")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $zenApiKey")
                .header("X-OpenCode-Client", "android/1.0.0")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newBuilder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute()
            val respBody = response.body?.string()
            response.close()
            if (response.isSuccessful && respBody != null) {
                val json = JsonParser.parseString(respBody).asJsonObject
                val content = json.getAsJsonArray("choices")?.get(0)?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString
                content?.trim()
            } else null
        } catch (e: Exception) {
            AppLogger.e("Orchestrator", "Error analysis failed", e)
            null
        }
    }

    suspend fun classifyIntent(userMessage: String): OrchestratorDecision {
        val toolJob = classifyToolJob(userMessage)
        if (toolJob != null) return OrchestratorDecision.DirectTool(toolJob)
        return OrchestratorDecision.PassToMain(userMessage)
    }

    // ════════════════════════════════════════════════
    // Internal: Multi-agent task execution
    // ════════════════════════════════════════════════

    private suspend fun runTask(task: ExecutionTask, plan: ExecutionPlan, reportProgress: (ExecutionTask, String) -> Unit): SubAgentResult {
        val start = System.currentTimeMillis()
        reportProgress(task, "running")

        for (attempt in 0..maxRetries) {
            try {
                val result = withTimeout(taskTimeoutMs) { executeSubAgent(task, plan) }
                return try {
                    val json = JsonParser.parseString(result).asJsonObject
                    val status = json.get("status")?.asString ?: "success"
                    val taskResult = json.get("result")?.asString ?: result
                    val confidence = json.get("confidence")?.asDouble ?: 1.0
                    val notes = json.get("notes")?.asString ?: ""
                    if (status == "failed" && attempt < maxRetries) continue
                    SubAgentResult(task.id, status, taskResult, confidence, notes, System.currentTimeMillis() - start)
                } catch (_: Exception) {
                    SubAgentResult(task.id, "success", result, 1.0, "", System.currentTimeMillis() - start)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                if (attempt >= maxRetries) return SubAgentResult(task.id, "failed", "", 0.0, "Timed out after ${taskTimeoutMs}ms", System.currentTimeMillis() - start)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                // Never swallow cancellation — rethrow so structured cancellation works.
                throw e
            } catch (e: Exception) {
                return SubAgentResult(task.id, "failed", "", 0.0, e.message ?: "Unknown error", System.currentTimeMillis() - start)
            }
        }
        return SubAgentResult(task.id, "failed", "", 0.0, "Max retries exceeded", System.currentTimeMillis() - start)
    }

    private suspend fun executeSubAgent(task: ExecutionTask, plan: ExecutionPlan): String {
        val agentEntity = findAgentEntity(task.agentType)
        if (agentEntity != null) {
            val subPrompt = buildSubAgentSystemPrompt(task, "Goal: ${plan.goal}")
            val messages = listOf(msg("system", subPrompt), msg("user", task.description))
            val rawResult = callLlm(messages) ?: """{"task_id":"${task.id}","status":"failed","result":"","confidence":0,"notes":"LLM call returned null"}"""
            return try {
                val json = JsonParser.parseString(rawResult).asJsonObject
                if (json.has("status") || json.has("result")) rawResult
                else """{"task_id":"${task.id}","status":"success","result":${gson.toJson(rawResult)},"confidence":1.0,"notes":""}"""
            } catch (_: Exception) {
                """{"task_id":"${task.id}","status":"success","result":${gson.toJson(rawResult)},"confidence":1.0,"notes":""}"""
            }
        }
        val toolResult = toolExecutor.executeTool(task.agentType, """{"text":${gson.toJson(task.description)}}""", "", false)
        return """{"task_id":"${task.id}","status":"success","result":${gson.toJson(toolResult)},"confidence":1.0,"notes":""}"""
    }

    // ════════════════════════════════════════════════
    // Internal: LLM call
    // ════════════════════════════════════════════════

    private suspend fun callLlm(messages: List<JsonObject>): String? {
        val providers = resolveProviders()
        for ((baseUrl, modelId, apiKey) in providers) {
            val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "${baseUrl.trimEnd('/')}/chat/completions"
            val requestBody = JsonObject().apply {
                addProperty("model", modelId)
                add("messages", gson.toJsonTree(messages))
                addProperty("temperature", 0.4)
                addProperty("max_tokens", 8192)
            }
            try {
                val body = requestBody.toString().toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${apiKey.ifEmpty { "public" }}")
                    .header("Content-Type", "application/json")
                    .header("X-OpenCode-Client", "android/1.0.0")
                    .post(body)
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    AppLogger.e("Orchestrator", "LLM call failed for $baseUrl: ${response.code} ${response.message}")
                    response.close()
                    continue
                }
                val responseBody = response.body?.string()
                response.close()
                val result = parseLlmResponse(responseBody)
                if (result != null) return result
            } catch (e: Exception) {
                AppLogger.e("Orchestrator", "LLM call error for $baseUrl", e)
            }
        }
        return null
    }

    private fun parseLlmResponse(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val choiceObj = choices[0].asJsonObject
                val msg = choiceObj.getAsJsonObject("message") ?: choiceObj.getAsJsonObject("delta")
                val contentEl = msg?.get("content")
                if (contentEl != null && !contentEl.isJsonNull) {
                    contentEl.asString
                } else {
                    val reasoningEl = msg?.get("reasoning_content") ?: msg?.get("reasoning")
                    if (reasoningEl != null && !reasoningEl.isJsonNull) reasoningEl.asString else null
                }
            } else {
                val resp = json.get("response") ?: json.get("output")
                if (resp != null && !resp.isJsonNull) resp.asString else body
            }
        } catch (_: Exception) { null }
    }

    private fun isCompatibleProvider(name: String): Boolean = when {
        name.contains("Zen", ignoreCase = true) -> true
        name.contains("Groq", ignoreCase = true) -> true
        name.contains("Cerebrus", ignoreCase = true) -> true
        name.contains("OpenRouter", ignoreCase = true) -> true
        name.contains("OpenAI", ignoreCase = true) -> true
        name.contains("Mistral", ignoreCase = true) -> true
        name.contains("Ollama", ignoreCase = true) -> true
        name.contains("Agent Router", ignoreCase = true) -> true
        else -> false
    }

    private fun resolveProviders(): List<Triple<String, String, String>> {
        val providers = AIProviderFactory.providers
        if (providers.isEmpty()) throw IllegalStateException("No AI providers configured")
        val configuredName = prefs.getSetting("agent_provider", "Zen AI")
        val configuredModel = prefs.getSetting("agent_model", "deepseek-v4-flash-free")
        
        val sortedProviders = providers.filter { isCompatibleProvider(it.name) }.sortedByDescending {
            when {
                it.name == configuredName -> 100
                getApiKeyForProvider(it.name).isNotEmpty() -> 50
                else -> 0
            }
        }.ifEmpty { providers }
        
        return sortedProviders.map { provider ->
            val baseUrl = getCompatibleBaseUrl(provider.name)
            val apiKey = getApiKeyForProvider(provider.name)
            val modelId = if (provider.name == configuredName && provider.models.any { it.id == configuredModel }) configuredModel else provider.models.firstOrNull { it.isFree }?.id ?: provider.models.firstOrNull()?.id ?: "deepseek-v4-flash-free"
            Triple(baseUrl, modelId, apiKey)
        }
    }

    private fun getCompatibleBaseUrl(name: String): String = when {
        name.contains("Zen", ignoreCase = true) -> "https://opencode.ai/zen/v1"
        name.contains("Groq", ignoreCase = true) -> "https://api.groq.com/openai/v1"
        name.contains("Cerebrus", ignoreCase = true) -> "https://api.cerebras.ai/v1"
        name.contains("OpenRouter", ignoreCase = true) -> "https://openrouter.ai/api/v1"
        name.contains("Omniroute", ignoreCase = true) -> "http://10.0.2.2:20128/v1"
        name.contains("OpenAI", ignoreCase = true) -> "https://api.openai.com/v1"
        name.contains("Mistral", ignoreCase = true) -> "https://api.mistral.ai/v1"
        name.contains("Ollama", ignoreCase = true) -> "https://ollama.com/v1"
        name.contains("Agent Router", ignoreCase = true) -> "https://agentrouter.org/v1"
        name.contains("GMI Cloud", ignoreCase = true) -> "https://api.gmi-serving.com/v1"
        else -> "https://api.openai.com/v1"
    }

    private fun getApiKeyForProvider(name: String): String = when {
        name.contains("Zen", ignoreCase = true) -> prefs.getApiKey("zen")
        name.contains("Groq", ignoreCase = true) -> prefs.getApiKey("groq")
        name.contains("Cerebrus", ignoreCase = true) -> prefs.getApiKey("cerebrus")
        name.contains("OpenRouter", ignoreCase = true) -> prefs.getApiKey("openrouter")
        name.contains("Omniroute", ignoreCase = true) -> prefs.getApiKey("omniroute")
        name.contains("OpenAI", ignoreCase = true) -> prefs.getApiKey("openai")
        name.contains("Mistral", ignoreCase = true) -> prefs.getApiKey("mistral")
        name.contains("Ollama", ignoreCase = true) -> prefs.getApiKey("ollama")
        name.contains("Agent Router", ignoreCase = true) -> prefs.getApiKey("agentrouter")
        name.contains("GMI Cloud", ignoreCase = true) -> prefs.getApiKey("gmi")
        else -> ""
    }

    // ════════════════════════════════════════════════
    // Internal: Scheduling & helper methods
    // ════════════════════════════════════════════════

    private fun buildExecutionLevels(tasks: List<ExecutionTask>): List<List<ExecutionTask>> {
        val remaining = tasks.associateBy { it.id }.toMutableMap()
        val done = mutableSetOf<String>()
        val levels = mutableListOf<List<ExecutionTask>>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { t -> t.dependsOn.all { it in done } }
            if (ready.isEmpty()) { levels.add(remaining.values.toList()); break }
            levels.add(ready)
            for (t in ready) { remaining.remove(t.id); done.add(t.id) }
        }
        return levels
    }

    private fun buildPhases(plan: TaskPlan): List<List<SubAgentInstance>> {
        val phases = mutableListOf<MutableList<SubAgentInstance>>()
        val assigned = mutableSetOf<String>()
        var remaining = plan.agents.toMutableList()
        while (remaining.isNotEmpty()) {
            val phase = mutableListOf<SubAgentInstance>()
            val toRemove = mutableListOf<SubAgentInstance>()
            for (agent in remaining) {
                val deps = agent.subTask.dependsOn
                if (deps.isEmpty() || deps.all { it in assigned }) { phase.add(agent); toRemove.add(agent) }
            }
            if (phase.isEmpty()) { remaining.firstOrNull()?.let { phase.add(it); toRemove.add(it) } }
            phase.forEach { assigned.add(it.subTask.id) }
            remaining.removeAll(toRemove.toSet())
            if (phase.isNotEmpty()) phases.add(phase)
        }
        return phases
    }

    private fun buildAgentContext(plan: TaskPlan, current: SubAgentInstance, completed: List<SubAgentInstance>): String {
        val sb = StringBuilder()
        sb.appendLine("## Task Context\nOriginal user request: ${plan.originalUserMessage}\n")
        if (completed.isNotEmpty()) {
            sb.appendLine("## Completed Work")
            for (c in completed) sb.appendLine("- ${c.subTask.agentType}: ${c.result?.take(200)}")
            sb.appendLine()
        }
        sb.appendLine("## Current Task\n${current.subTask.description}\nAgent type: ${current.subTask.agentType}")
        return sb.toString()
    }

    private fun estimateTokens(text: String): Int = text.length / 4

    private fun parseExecutionPlan(raw: String): ExecutionPlan? {
        var s = raw.trim()
        if (s.startsWith("```")) s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
        if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        return try {
            val json = JsonParser.parseString(s).asJsonObject
            val goal = json.get("goal")?.asString ?: return null
            val tasks = json.getAsJsonArray("tasks")?.map { t ->
                val o = t.asJsonObject
                ExecutionTask(
                    id = o.get("id")?.asString ?: "t${UUID.randomUUID().toString().take(4)}",
                    description = o.get("description")?.asString ?: "",
                    agentType = o.get("agent_type")?.asString ?: "tools_agent",
                    dependsOn = o.getAsJsonArray("depends_on")?.map { it.asString } ?: emptyList(),
                    priority = o.get("priority")?.asString ?: "normal"
                )
            } ?: return null
            ExecutionPlan(goal, tasks, json.get("aggregation_strategy")?.asString ?: "merge")
        } catch (e: Exception) {
            AppLogger.e("Orchestrator", "Failed to parse execution plan", e); null
        }
    }

    // ── Agent type helpers ──

    private suspend fun findAgentEntity(agentType: String): AgentEntity? {
        val repo = AgentRepository(context); return repo.getAgentById(agentType)
    }

    private suspend fun orchestratorSystemPrompt(): String = buildString {
        appendLine("""
ROLE
You are the Orchestrator, the entry point of a multi-agent system. You never answer the user directly. Your only job is to read their request and output a structured execution plan that a task runner will use to dispatch Sub-Agents.

OBJECTIVE
Turn one user request into the smallest set of atomic, well-scoped subtasks, marking a dependency only where a subtask genuinely cannot start without another task's output. Independent subtasks are the default — they run in parallel, which is what makes this system fast.

AVAILABLE SUB-AGENT TYPES
""".trimIndent())
        val types = getAvailableAgentTypes()
        appendLine(types.ifEmpty { "- tools_agent (general-purpose tool executor)" })
        appendLine("""

PROCESS
1. Identify the user's underlying goal(s) — a request can contain more than one distinct ask.
2. Break each goal into atomic subtasks. A subtask is atomic if one Sub-Agent, using one tool, can finish it without mid-task input from another subtask.
3. Decide dependencies. Default to none. Only add one if a subtask literally requires another subtask's OUTPUT as input — "related topic" is not a dependency.
4. Match each subtask to the closest available agent type. Never invent a type that isn't listed.
5. Choose one aggregation_strategy for how results should be combined.
6. Output the plan and nothing else — no explanation, no markdown, no text outside the JSON object.

OUTPUT — exactly one JSON object, no surrounding text:
{
  "goal": "one-sentence restatement of what the user actually wants",
  "tasks": [
    {
      "id": "t1",
      "description": "a complete, self-contained instruction — the Sub-Agent will NOT see the user's original message, so include every detail it needs here",
      "agent_type": "one of the available sub-agent types",
      "depends_on": [],
      "priority": "high" | "normal" | "low"
    }
  ],
  "aggregation_strategy": "merge" | "summarize" | "vote" | "sequential-narrative"
}

AGGREGATION STRATEGIES
- merge — combine independent pieces into one unified answer
- summarize — condense many results into a shorter synthesis
- vote — several agents independently attempted the same question → return the best-supported answer
- sequential-narrative — tasks build on each other; the final answer should read as one continuous thread

RULES
- Default to parallel. A dependency edge is the exception, not the norm.
- Minimize task count — don't split what one competent agent can finish alone just to manufacture parallelism.
- If the request is simple, return a plan with exactly one task. Don't force decomposition.
- If a detail is missing, make the smallest reasonable assumption and fold it into "goal" — don't stop to ask the user.
- Never solve the task yourself. You only plan.
""".trimIndent())
    }

    private suspend fun buildSubAgentSystemPrompt(task: ExecutionTask, taskContext: String): String = buildString {
        appendLine("""
ROLE
You are a Sub-Agent inside a multi-agent system. You execute exactly one task. You do not know what other Sub-Agents are doing, you cannot see the user's original message, and you cannot ask anyone for clarification — work only from what's given below.

TASK
${task.description}

CONTEXT
$taskContext

TOOLS AVAILABLE TO YOU
""".trimIndent())
        appendLine(getToolsForAgentType(task.agentType))
        appendLine("""

RULES
- Stay inside the scope of TASK. Don't attempt parts of a larger request you might infer exist.
- Use only the tools listed above.
- If you can't complete the task with what's given, don't guess or fabricate — return a "failed" or "partial" status with a clear reason.
- Be concise. Your output feeds another agent, not the end user.

OUTPUT — exactly one JSON object, no surrounding text:
{
  "task_id": "${task.id}",
  "status": "success" | "partial" | "failed",
  "result": "your output, written so the Aggregator can use it directly",
  "confidence": 0.0-1.0,
  "notes": "assumptions made, caveats, or the reason for a partial/failed status"
}
""".trimIndent())
    }

    private fun aggregatorSystemPrompt(): String = """
ROLE
You are the Aggregator. You receive the Orchestrator's original goal and the full array of Sub-Agent results, including any partial or failed ones, and you produce the single response the user actually sees.

RESPONSIBILITIES
1. Synthesize, don't concatenate. Write one coherent, well-organized answer in a natural voice. Never expose task IDs, agent names, JSON, or internal plumbing to the user.
2. Resolve conflicts. If two results disagree, prefer the higher "confidence" score.
3. Degrade gracefully on partial failure. If a failed subtask wasn't essential, proceed without mentioning internal plumbing. If it was essential, say plainly what's missing.
4. Follow the aggregation_strategy.
5. Output only the final user-facing answer — no meta-commentary about how many agents ran.
""".trimIndent()

    private suspend fun getAvailableAgentTypes(): String {
        val agents = AgentRepository(context).getAllAgents()
        return agents.filter { it.isEnabled }.joinToString("\n") { "- ${it.agentId} (${it.displayName}): ${it.description.take(120)}" }
    }

    private suspend fun getToolsForAgentType(agentType: String): String {
        val agent = AgentRepository(context).getAgentById(agentType)
        val toolList = (agent?.tools?.split(",")?.filter { it.isNotBlank() } ?: listOf("shell", "file_read", "file_write", "web_search", "web_fetch")).toMutableList()
        if (agentType == "tools_agent" || agentType == "general") {
            try {
                val enabledPluginTools = ai.deepcode.android.plugin.PluginRegistry.getEnabledTools().map { it.name }
                toolList.addAll(enabledPluginTools)
            } catch (e: Exception) {
                // ignore
            }
        }
        return toolList.distinct().joinToString("\n") { "- $it" }
    }

    private fun msg(role: String, content: String) = JsonObject().apply { addProperty("role", role); addProperty("content", content) }

    // ════════════════════════════════════════════════
    // Deterministic ToolJob router (no LLM)
    // ════════════════════════════════════════════════

    private fun classifyToolJob(userMessage: String): ToolJob? {
        val lower = userMessage.lowercase().trim()
        val genExplicit = Regex("""(?i)\b(generate|create|draw|make)\b.*\b(image|picture|photo|pic|art|artwork|illustration|logo|poster|banner|thumbnail|cover)\b""")
        if (genExplicit.containsMatchIn(lower) || lower.matches(".*\\b(draw|illustrate|paint)\\b.*".toRegex()))
            return ToolJob(TaskType.IMAGE_GENERATION, TargetSite.GEMINI, OutputFormat.IMAGE_URL, userMessage)
        if (lower.contains("image") || lower.contains("picture") || lower.contains("photo") || lower.contains("pic") || lower.contains("logo") || lower.contains("poster") || lower.contains("thumbnail") || lower.contains("banner") || lower.contains("cover") || lower.matches(".*\\b(paint|artwork|illustration)\\b.*".toRegex())) {
            val cleaned = userMessage.replace(Regex("""(?i)\b(show|find|get|search|fetch|me|an?|the|of|a)\b"""), "").replace(Regex("""(?i)\b(image|picture|photo|pic|of|a)\b"""), "").trim()
            return ToolJob(TaskType.IMAGE_SEARCH, TargetSite.AUTO, OutputFormat.IMAGE_URL, cleaned.ifEmpty { userMessage })
        }
        if (lower.contains("describe this image") || lower.contains("describe this picture") || lower.contains("what is in this picture") || lower.contains("what is in this image") || lower.contains("analyze this image") || lower.contains("analyze this picture") || lower.contains("describe image") || lower.contains("analyze image"))
            return ToolJob(TaskType.IMAGE_ANALYSIS, TargetSite.GEMINI, OutputFormat.MARKDOWN, userMessage)
        if (lower.contains("list voices") || lower.contains("list tts") || lower.contains("available voices") || lower.contains("voice list") || lower == "voices" || lower == "voices?")
            return ToolJob(TaskType.TEXT_GENERATION, TargetSite.EDGE_TTS, OutputFormat.TEXT, userMessage)
        if (lower.contains("text to speech") || lower.contains("tts") || lower.contains("voiceover") || lower.contains("generate audio") || lower.contains("generate speech") || lower.contains("create audio") || lower.contains("make audio") || lower.contains("elevenlabs") || lower.contains("audio of") || lower.matches(".*\\b(sing|song|music|melody|audio|speak|say|read aloud|narrate)\\b.*".toRegex())) {
            val cleaned = userMessage.replace(Regex("(?i)^.*?\\b(generate|create|make|play)\\b.*?\\b(audio|speech|voice)\\b(\\s+of|\\s+saying|\\s+that says|\\s+with text)?\\s*"), "").replace(Regex("(?i)^.*?\\b(speak|say|read aloud|narrate)\\b\\s*"), "").trim()
            val wordCount = cleaned.split("\\s+".toRegex()).count { it.isNotBlank() && it.length > 2 }
            val genericPhrases = listOf("for me", "a message", "something", "this", "that")
            val isGeneric = wordCount < 2 || genericPhrases.any { cleaned.lowercase() in listOf(it, "$it ", " $it") } || cleaned.length < 8
            if (!isGeneric) return ToolJob(TaskType.AUDIO_GENERATION, TargetSite.EDGE_TTS, OutputFormat.AUDIO_URL, cleaned)
        }
        val videoExplicit = Regex("""(?i)\b(generate|create|make)\b.*\b(video|animation|clip)\b""")
        if (videoExplicit.containsMatchIn(lower))
            return ToolJob(TaskType.VIDEO_GENERATION, TargetSite.AUTO, OutputFormat.VIDEO_URL, userMessage)
        if (lower.contains("video") || lower.contains("veo") || lower.contains("animation") || lower.contains("text to video"))
            return ToolJob(TaskType.VIDEO_GENERATION, TargetSite.AUTO, OutputFormat.VIDEO_URL, userMessage)
        return null
    }

    // ════════════════════════════════════════════════
    // Legacy: keyword-based decomposition (fallback)
    // ════════════════════════════════════════════════
}
