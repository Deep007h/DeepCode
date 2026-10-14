package com.jarves.mh.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.ChangeItem
import com.jarves.mh.model.ChatMessage
import com.jarves.mh.model.ProjectKind
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.RuntimeEvent
import com.jarves.mh.model.ToolRequest
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class OpencodeRuntimeBridge(
    private val context: Context,
    private val secretFor: (ProviderProfile) -> String?,
) : RuntimeBridge {

    private val checkpoints = WorkspaceCheckpoints(context.filesDir)
    private val installer = RuntimeInstaller(context)
    private val eventBus = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 64)
    override val events: Flow<RuntimeEvent> = eventBus.asSharedFlow()

    private val finishedSessions = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activeProcess: Process? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var userStopRequested = false
    @Volatile private var foregroundResultPosted = false

    override suspend fun startSession(
        projectId: String,
        projectSlug: String,
        projectKind: ProjectKind,
        prompt: String,
        conversationHistory: List<ChatMessage>,
        provider: ProviderProfile
    ): String = withContext(Dispatchers.IO + NonCancellable) {
        val sessionId = UUID.randomUUID().toString()
        finishedSessions.remove(sessionId)
        activeSessionId = sessionId
        userStopRequested = false
        foregroundResultPosted = false

        eventBus.emit(RuntimeEvent.SessionStarted(sessionId))
        pushForegroundProgress("Starting OpenCode CLI…")

        val secret = secretFor(provider).orEmpty()
        val isZen = provider.kind == ProviderKind.OPENCODE_ZEN
        val effectiveSecret = if (secret.isBlank()) {
            if (isZen) {
                "zen-free"
            } else {
                val prefs = ai.deepcode.android.data.local.EncryptedPrefs.getInstance(context)
                when (provider.kind) {
                    ProviderKind.LLM_ROUTER -> prefs.getApiKey("openrouter")
                        .ifBlank { prefs.getSetting("api_key_openrouter", "") }
                    ProviderKind.DEEPSEEK -> prefs.getApiKey("deepseek")
                        .ifBlank { prefs.getSetting("api_key_deepseek", "") }
                    ProviderKind.ANTHROPIC -> prefs.getApiKey("anthropic")
                        .ifBlank { prefs.getSetting("api_key_anthropic", "") }
                    else -> prefs.getApiKey("openrouter")
                        .ifBlank { prefs.getApiKey("deepseek") }
                        .ifBlank { prefs.getApiKey("openai") }
                        .ifBlank { prefs.getSetting("api_key_openrouter", "") }
                        .ifBlank { prefs.getSetting("api_key_deepseek", "") }
                        .ifBlank { prefs.getSetting("api_key_openai", "") }
                }.ifBlank { "zen-free" }
            }
        } else secret

        val providerTag = when (provider.kind) {
            ProviderKind.OPENCODE_ZEN -> "opencode_zen"
            ProviderKind.LLM_ROUTER -> "openrouter"
            ProviderKind.DEEPSEEK -> "deepseek"
            ProviderKind.ANTHROPIC -> "anthropic"
            ProviderKind.CUSTOM -> "custom"
            else -> "opencode_zen"
        }

        runCatching {
            RuntimeTaskController.stopAction = {
                userStopRequested = true
                val running = activeProcess
                if (running != null) {
                    Thread {
                        running.destroy()
                        Thread.sleep(500)
                        if (running.isAlive) running.destroyForcibly()
                    }.start()
                }
            }

            startForegroundRuntime(projectSlug)
            val installed = installer.installedRuntime()
            installer.ensureOpencodeRunnerScript()
            if (!installer.isAgentInstalled(AgentKind.OPENCODE)) {
                pushForegroundProgress("Installing OpenCode CLI…")
                installer.ensureAgentInstalled(AgentKind.OPENCODE) { prog ->
                    pushForegroundProgress("Installing OpenCode: ${prog.message}")
                }
            }

            val workspace = checkpoints.ensureWorkspace(projectId)
            checkpoints.createCheckpoint(projectId, workspace)
            val before = checkpoints.snapshot(workspace)

            var formatGateway: LocalFormatGateway? = null
            if (provider.kind == ProviderKind.OPENCODE_ZEN || provider.resolvedBaseUrl.contains("opencode.ai/zen", ignoreCase = true)) {
                formatGateway = LocalFormatGateway(provider, effectiveSecret).start()
            }

            val environment = buildMap {
                put("OPENCODE_HOME", "/root/.opencode")
                put("WORKSPACE", "/workspace/$projectSlug")
                put("DEBIAN_FRONTEND", "noninteractive")
                put("OPENCODE_PROVIDER", providerTag)
                put("OPENCODE_BASE_URL", formatGateway?.url ?: provider.resolvedBaseUrl)
                put("OPENCODE_MODEL", provider.model)
                if (effectiveSecret.isNotBlank()) {
                    put("OPENCODE_API_KEY", effectiveSecret)
                    when (provider.kind) {
                        ProviderKind.LLM_ROUTER -> put("OPENROUTER_API_KEY", effectiveSecret)
                        ProviderKind.DEEPSEEK -> put("DEEPSEEK_API_KEY", effectiveSecret)
                        ProviderKind.ANTHROPIC -> put("ANTHROPIC_API_KEY", effectiveSecret)
                        ProviderKind.OPENCODE_ZEN -> {}
                        else -> put("OPENAI_API_KEY", effectiveSecret)
                    }
                }
            }

            val guestCommand = listOf(
                RuntimeInstaller.OPENCODE_GUEST_PATH,
                "run",
                prompt,
                "--model", provider.model
            )

            val outputFile = File(context.cacheDir, "opencode-output-${System.nanoTime()}.log")
            val process = installer.process(
                proot = installed.proot,
                rootfs = installed.rootfs,
                workspace = workspace,
                environment = environment,
                guestCommand = guestCommand,
                guestWorkspacePath = "/workspace/$projectSlug",
                emulateHardLinks = false,
                outputFile = outputFile,
            )
            activeProcess = process

            val pending = StringBuilder()
            var offset = 0L
            var currentTool = ""
            var hasEmittedContent = false

            fun processLine(rawLine: String) {
                val line = rawLine.trim()
                if (line.isEmpty()) return

                if (line.startsWith("{") && line.endsWith("}")) {
                    val parsed = runCatching { JSONObject(line) }.getOrNull()
                    if (parsed != null) {
                        when (parsed.optString("event")) {
                            "delta" -> {
                                val text = parsed.optString("text")
                                if (text.isNotEmpty()) {
                                    hasEmittedContent = true
                                    eventBus.tryEmit(RuntimeEvent.AssistantDelta(sessionId, text))
                                }
                                return
                            }
                            "thought" -> {
                                val text = parsed.optString("text")
                                if (text.isNotEmpty()) {
                                    hasEmittedContent = true
                                    eventBus.tryEmit(RuntimeEvent.ReasoningSummary(sessionId, text, 1L))
                                }
                                return
                            }
                            "tool" -> {
                                val name = parsed.optString("name")
                                val detail = parsed.optString("detail")
                                currentTool = name
                                hasEmittedContent = true
                                eventBus.tryEmit(RuntimeEvent.ToolStarted(sessionId, name, detail.ifBlank { "Executing $name" }))
                                pushForegroundProgress("OpenCode: $name")
                                return
                            }
                            "tool_result" -> {
                                val summary = parsed.optString("summary")
                                val tool = currentTool.ifBlank { "Tool" }
                                hasEmittedContent = true
                                eventBus.tryEmit(RuntimeEvent.ToolCompleted(sessionId, tool, summary.take(200)))
                                currentTool = ""
                                return
                            }
                        }
                    }
                }

                when {
                    line.startsWith("[Delta] ") -> {
                        hasEmittedContent = true
                        eventBus.tryEmit(RuntimeEvent.AssistantDelta(sessionId, line.removePrefix("[Delta] ")))
                    }
                    line.startsWith("[Tool:") || line.startsWith("Tool:") -> {
                        val toolName = line.substringAfter("Tool:").substringBefore("]").substringBefore("\n").trim()
                        val detail = line.substringAfter("]", "").trim()
                        currentTool = toolName
                        hasEmittedContent = true
                        eventBus.tryEmit(RuntimeEvent.ToolStarted(sessionId, toolName, detail.ifBlank { "Executing $toolName" }))
                        pushForegroundProgress("OpenCode: $toolName")
                    }
                    line.startsWith("[Tool Result]") || line.startsWith("Result:") -> {
                        val summary = line.substringAfter("Result]").substringAfter("Result:").trim()
                        val tool = currentTool.ifBlank { "Tool" }
                        hasEmittedContent = true
                        eventBus.tryEmit(RuntimeEvent.ToolCompleted(sessionId, tool, summary.take(200)))
                        currentTool = ""
                    }
                    line.startsWith("[Thought]") || line.startsWith("<thought>") || line.startsWith("Thinking:") -> {
                        val thought = line.removePrefix("[Thought]").removePrefix("<thought>").removePrefix("Thinking:").trim()
                        hasEmittedContent = true
                        eventBus.tryEmit(RuntimeEvent.ReasoningSummary(sessionId, thought, 1L))
                    }
                    else -> {
                        hasEmittedContent = true
                        eventBus.tryEmit(RuntimeEvent.AssistantDelta(sessionId, rawLine + "\n"))
                    }
                }
            }

            val readBuffer = ByteArray(32 * 1024)
            var idleCount = 0
            var raf: RandomAccessFile? = null
            try {
                while (process.isAlive || (outputFile.exists() && outputFile.length() > offset)) {
                    if (!outputFile.exists()) {
                        if (!process.isAlive) break
                        delay(10)
                        continue
                    }
                    if (raf == null) {
                        raf = runCatching { RandomAccessFile(outputFile, "r") }.getOrNull()
                        if (raf == null) {
                            if (!process.isAlive) break
                            delay(10)
                            continue
                        }
                    }
                    val fileLength = raf.length()
                    val available = fileLength - offset
                    if (available <= 0) {
                        if (!process.isAlive) break
                        val pollDelay = when {
                            idleCount < 2 -> 8L
                            idleCount < 8 -> 18L
                            else -> 35L
                        }
                        idleCount++
                        delay(pollDelay)
                        continue
                    }
                    idleCount = 0
                    val toRead = minOf(available, readBuffer.size.toLong()).toInt()
                    raf.seek(offset)
                    val count = raf.read(readBuffer, 0, toRead)
                    if (count <= 0) continue
                    offset += count
                    pending.append(readBuffer.decodeToString(0, count))
                    var newline = pending.indexOf('\n')
                    while (newline >= 0) {
                        val line = pending.substring(0, newline).trimEnd('\r')
                        pending.delete(0, newline + 1)
                        processLine(line)
                        newline = pending.indexOf('\n')
                    }
                }
            } finally {
                runCatching { formatGateway?.close() }
                runCatching { raf?.close() }
                runCatching { outputFile.delete() }
            }

            if (pending.isNotEmpty()) {
                processLine(pending.toString())
                pending.clear()
            }

            val exit = process.waitFor()
            Log.d("OpencodeBridge", "OpenCode process exited with code $exit")

            val changed = checkpoints.changedFiles(workspace, before)
            if (changed.isNotEmpty()) {
                checkpoints.saveChangedPaths(projectId, changed)
                val details = checkpoints.buildChangeDetails(projectId, workspace, changed)
                eventBus.emit(RuntimeEvent.FilesChanged(sessionId, details))
            } else {
                checkpoints.checkpointDir(projectId).deleteRecursively()
            }

            if (exit == 0 && !userStopRequested && hasEmittedContent) {
                emitCompletedOnce(sessionId)
                finishForegroundRuntime(
                    completed = true,
                    projectName = projectSlug,
                    detail = "OpenCode CLI completed the task in $projectSlug.",
                )
            } else if (!userStopRequested) {
                val errorReason = if (!hasEmittedContent) {
                    "OpenCode CLI process completed without generating an answer."
                } else {
                    "OpenCode CLI process stopped with exit code $exit"
                }
                emitFailureOnce(sessionId, errorReason)
                finishForegroundRuntime(
                    completed = false,
                    projectName = projectSlug,
                    detail = errorReason,
                )
            }
        }.onFailure { error ->
            Log.e("OpencodeBridge", "Session failed", error)
            val message = error.message ?: "OpenCode CLI execution failed"
            emitFailureOnce(sessionId, message)
            if (userStopRequested) {
                cancelForegroundRuntime()
            } else {
                finishForegroundRuntime(
                    completed = false,
                    projectName = projectSlug,
                    detail = message,
                )
            }
        }

        activeProcess = null
        activeSessionId = null
        RuntimeTaskController.stopAction = null
        sessionId
    }

    private fun emitCompletedOnce(sessionId: String) {
        if (!finishedSessions.add(sessionId)) return
        eventBus.tryEmit(RuntimeEvent.SessionCompleted(sessionId))
    }

    private fun emitFailureOnce(sessionId: String, message: String) {
        if (!finishedSessions.add(sessionId)) return
        eventBus.tryEmit(RuntimeEvent.SessionFailed(sessionId, message))
    }

    private fun startForegroundRuntime(projectName: String) {
        val intent = Intent(context, RuntimeExecutionService::class.java).apply {
            action = RuntimeExecutionService.ACTION_START
            putExtra(RuntimeExecutionService.EXTRA_PROJECT_NAME, projectName)
            putExtra(RuntimeExecutionService.EXTRA_CAN_STOP, true)
            putExtra(RuntimeExecutionService.EXTRA_TITLE, "OpenCode CLI")
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: Exception) {
            Log.w("OpencodeBridge", "Could not start foreground runtime service", error)
        }
    }

    private fun pushForegroundProgress(detail: String) {
        val intent = Intent(context, RuntimeExecutionService::class.java).apply {
            action = RuntimeExecutionService.ACTION_PROGRESS
            putExtra(RuntimeExecutionService.EXTRA_DETAIL, detail)
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.w("OpencodeBridge", "Could not update foreground progress", error)
        }
    }

    private fun finishForegroundRuntime(completed: Boolean, projectName: String, detail: String) {
        if (foregroundResultPosted) return
        foregroundResultPosted = true
        val intent = Intent(context, RuntimeExecutionService::class.java).apply {
            action = if (completed) RuntimeExecutionService.ACTION_COMPLETE else RuntimeExecutionService.ACTION_FAILED
            putExtra(RuntimeExecutionService.EXTRA_PROJECT_NAME, projectName)
            putExtra(RuntimeExecutionService.EXTRA_DETAIL, detail)
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.w("OpencodeBridge", "Could not finish foreground runtime", error)
        }
    }

    private fun cancelForegroundRuntime() {
        val intent = Intent(context, RuntimeExecutionService::class.java).apply {
            action = RuntimeExecutionService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.w("OpencodeBridge", "Could not stop foreground runtime", error)
        }
    }

    override suspend fun respondToApproval(request: ToolRequest, approved: Boolean) = withContext(Dispatchers.IO) {
        eventBus.emit(
            if (approved) RuntimeEvent.ToolApproved(request.sessionId, request.approvalId)
            else RuntimeEvent.ToolRejected(request.sessionId, request.approvalId)
        )
    }

    override suspend fun stopSession(sessionId: String) = withContext(Dispatchers.IO) {
        if (activeSessionId == sessionId) {
            userStopRequested = true
            activeProcess?.destroy()
            delay(500)
            if (activeProcess?.isAlive == true) activeProcess?.destroyForcibly()
            emitFailureOnce(sessionId, "Stopped by user")
        }
    }

    override suspend fun stopActiveSession() {
        activeSessionId?.let { stopSession(it) }
    }

    private fun restore(workspace: File, backup: File, path: String) {
        val target = checkpoints.safeWorkspaceFile(workspace, path)
        val original = checkpoints.safeWorkspaceFile(backup, path)
        if (original.isFile) {
            target.parentFile?.mkdirs()
            original.copyTo(target, overwrite = true)
        } else target.delete()
    }

    override suspend fun undoLastChanges(projectId: String): Boolean = withContext(Dispatchers.IO) {
        val checkpoint = checkpoints.checkpointDir(projectId)
        val backup = File(checkpoint, "project")
        val paths = checkpoints.readChangedPaths(projectId)
        if (!backup.isDirectory || paths.isEmpty()) return@withContext false
        val workspace = checkpoints.ensureWorkspace(projectId)
        paths.forEach { restore(workspace, backup, it) }
        checkpoint.deleteRecursively()
        true
    }

    override suspend fun acceptLastChanges(projectId: String): Unit = withContext(Dispatchers.IO) {
        checkpoints.checkpointDir(projectId).deleteRecursively()
        Unit
    }

    override suspend fun loadPendingChanges(projectId: String): List<ChangeItem> = withContext(Dispatchers.IO) {
        val paths = checkpoints.readChangedPaths(projectId)
        if (paths.isEmpty()) emptyList() else checkpoints.buildChangeDetails(projectId, checkpoints.ensureWorkspace(projectId), paths)
    }

    override suspend fun undoFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (path !in checkpoints.readChangedPaths(projectId)) return@withContext false
        restore(checkpoints.ensureWorkspace(projectId), File(checkpoints.checkpointDir(projectId), "project"), path)
        checkpoints.removeChangedPath(projectId, path)
        true
    }

    override suspend fun acceptFileChange(projectId: String, path: String): Boolean = withContext(Dispatchers.IO) {
        if (path !in checkpoints.readChangedPaths(projectId)) return@withContext false
        val workspace = checkpoints.ensureWorkspace(projectId)
        val backup = File(checkpoints.checkpointDir(projectId), "project")
        val current = checkpoints.safeWorkspaceFile(workspace, path)
        val baseline = checkpoints.safeWorkspaceFile(backup, path)
        if (current.isFile) {
            baseline.parentFile?.mkdirs()
            current.copyTo(baseline, overwrite = true)
        } else baseline.delete()
        checkpoints.removeChangedPath(projectId, path)
        true
    }
}
