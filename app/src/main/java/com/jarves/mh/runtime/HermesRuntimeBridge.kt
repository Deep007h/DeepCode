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

class HermesRuntimeBridge(
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
        pushForegroundProgress("Starting Hermes Agent…")

        val secret = secretFor(provider).orEmpty()
        val effectiveSecret = if (secret.isBlank() && provider.kind == ProviderKind.OPENCODE_ZEN) "zen-free" else secret

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
            if (!installer.isAgentInstalled(AgentKind.HERMES)) {
                pushForegroundProgress("Installing Hermes Agent…")
                installer.ensureAgentInstalled(AgentKind.HERMES) { prog ->
                    pushForegroundProgress("Installing Hermes: ${prog.message}")
                }
            }

            val workspace = checkpoints.ensureWorkspace(projectId)
            checkpoints.createCheckpoint(projectId, workspace)
            val before = checkpoints.snapshot(workspace)

            val environment = buildMap {
                put("HERMES_HOME", "/root/.hermes")
                put("WORKSPACE", "/workspace/$projectSlug")
                put("DEBIAN_FRONTEND", "noninteractive")
                if (effectiveSecret.isNotBlank()) {
                    put("OPENROUTER_API_KEY", effectiveSecret)
                    put("DEEPSEEK_API_KEY", effectiveSecret)
                    put("OPENAI_API_KEY", effectiveSecret)
                    put("HERMES_API_KEY", effectiveSecret)
                    put("ANTHROPIC_API_KEY", effectiveSecret)
                    put("NOUS_API_KEY", effectiveSecret)
                }
            }

            val guestCommand = listOf(
                RuntimeInstaller.HERMES_GUEST_PATH,
                "chat",
                "-q", prompt,
                "--oneshot",
                "--yolo",
                "--model", provider.model
            )

            val outputFile = File(context.cacheDir, "hermes-output-${System.nanoTime()}.log")
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

            fun processLine(rawLine: String) {
                val line = rawLine.trim()
                if (line.isEmpty()) return

                if (line.startsWith("{") && line.endsWith("}")) {
                    val parsed = runCatching { JSONObject(line) }.getOrNull()
                    if (parsed != null) {
                        when (parsed.optString("event")) {
                            "delta" -> {
                                val text = parsed.optString("text")
                                if (text.isNotEmpty()) eventBus.tryEmit(RuntimeEvent.AssistantDelta(sessionId, text))
                                return
                            }
                            "thought" -> {
                                val text = parsed.optString("text")
                                if (text.isNotEmpty()) eventBus.tryEmit(RuntimeEvent.ReasoningSummary(sessionId, text, 1L))
                                return
                            }
                            "tool" -> {
                                val name = parsed.optString("name")
                                val detail = parsed.optString("detail")
                                currentTool = name
                                eventBus.tryEmit(RuntimeEvent.ToolStarted(sessionId, name, detail.ifBlank { "Executing $name" }))
                                pushForegroundProgress("Hermes: $name")
                                return
                            }
                            "tool_result" -> {
                                val summary = parsed.optString("summary")
                                val tool = currentTool.ifBlank { "Tool" }
                                eventBus.tryEmit(RuntimeEvent.ToolCompleted(sessionId, tool, summary.take(200)))
                                currentTool = ""
                                return
                            }
                        }
                    }
                }

                when {
                    line.startsWith("[Delta] ") -> {
                        eventBus.tryEmit(RuntimeEvent.AssistantDelta(sessionId, line.removePrefix("[Delta] ")))
                    }
                    line.startsWith("[Tool:") || line.startsWith("Tool:") -> {
                        val toolName = line.substringAfter("Tool:").substringBefore("]").substringBefore("\n").trim()
                        val detail = line.substringAfter("]", "").trim()
                        currentTool = toolName
                        eventBus.tryEmit(RuntimeEvent.ToolStarted(sessionId, toolName, detail.ifBlank { "Executing $toolName" }))
                        pushForegroundProgress("Hermes: $toolName")
                    }
                    line.startsWith("[Tool Result]") || line.startsWith("Result:") -> {
                        val summary = line.substringAfter("Result]").substringAfter("Result:").trim()
                        val tool = currentTool.ifBlank { "Tool" }
                        eventBus.tryEmit(RuntimeEvent.ToolCompleted(sessionId, tool, summary.take(200)))
                        currentTool = ""
                    }
                    line.startsWith("[Thought]") || line.startsWith("<thought>") || line.startsWith("Thinking:") -> {
                        val thought = line.removePrefix("[Thought]").removePrefix("<thought>").removePrefix("Thinking:").trim()
                        eventBus.tryEmit(RuntimeEvent.ReasoningSummary(sessionId, thought, 1L))
                    }
                    else -> {
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
                runCatching { raf?.close() }
                runCatching { outputFile.delete() }
            }

            if (pending.isNotEmpty()) {
                processLine(pending.toString())
                pending.clear()
            }

            val exit = process.waitFor()
            Log.d("HermesBridge", "Hermes process exited with code $exit")

            val changed = checkpoints.changedFiles(workspace, before)
            if (changed.isNotEmpty()) {
                checkpoints.saveChangedPaths(projectId, changed)
                val details = checkpoints.buildChangeDetails(projectId, workspace, changed)
                eventBus.emit(RuntimeEvent.FilesChanged(sessionId, details))
            } else {
                checkpoints.checkpointDir(projectId).deleteRecursively()
            }

            if (exit == 0 || !userStopRequested) {
                emitCompletedOnce(sessionId)
                finishForegroundRuntime(
                    completed = true,
                    projectName = projectSlug,
                    detail = "Hermes Agent completed the task in $projectSlug.",
                )
            } else {
                emitFailureOnce(sessionId, "Hermes Agent process stopped with exit code $exit")
                finishForegroundRuntime(
                    completed = false,
                    projectName = projectSlug,
                    detail = "Hermes Agent stopped with exit code $exit",
                )
            }
        }.onFailure { error ->
            Log.e("HermesBridge", "Session failed", error)
            val message = error.message ?: "Hermes Agent execution failed"
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
            putExtra(RuntimeExecutionService.EXTRA_TITLE, "Hermes Agent")
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: Exception) {
            Log.w("HermesBridge", "Could not start foreground runtime service", error)
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
            Log.w("HermesBridge", "Could not update foreground progress", error)
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
            Log.w("HermesBridge", "Could not finish foreground service", error)
        }
    }

    private fun cancelForegroundRuntime() {
        val intent = Intent(context, RuntimeExecutionService::class.java).apply {
            action = RuntimeExecutionService.ACTION_CANCELLED
        }
        try {
            context.startService(intent)
        } catch (error: Exception) {
            Log.w("HermesBridge", "Could not cancel foreground service", error)
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
