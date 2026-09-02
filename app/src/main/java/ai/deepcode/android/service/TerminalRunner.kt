package ai.deepcode.android.service

import ai.deepcode.android.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

class TerminalRunner {
    @Volatile
    private var process: Process? = null
    @Volatile
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _outputFlow = MutableSharedFlow<String>(replay = 50)
    val outputFlow = _outputFlow.asSharedFlow()

    fun start(workingDir: String, useRoot: Boolean) {
        synchronized(this) {
            if (process != null) return
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            val shellCmd = if (useRoot) "/system/bin/su" else "/system/bin/sh"
            try {
                val workingDirFile = File(workingDir)
                if (!workingDirFile.exists()) {
                    workingDirFile.mkdirs()
                }
                val pb = ProcessBuilder(shellCmd)
                    .directory(workingDirFile)
                    .redirectErrorStream(true)

                val env = pb.environment()
                env["TERM"] = "screen"
                env["PATH"] = (env["PATH"] ?: "") + ":/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin"

                val proc = pb.start()
                process = proc
                writer = BufferedWriter(OutputStreamWriter(proc.outputStream))

                readerJob = scope.launch {
                    var exitedNormally = false
                    try {
                        InputStreamReader(proc.inputStream).use { reader ->
                            val buffer = CharArray(1024)
                            var count: Int
                            while (reader.read(buffer).also { count = it } != -1) {
                                val out = String(buffer, 0, count)
                                _outputFlow.emit(out)
                            }
                        }
                        exitedNormally = true
                    } catch (e: Exception) {
                        _outputFlow.tryEmit("\n[Terminal error: ${e.message}]\n")
                    } finally {
                        synchronized(this@TerminalRunner) {
                            // Only tear down if this reader still owns the live
                            // process — a quick stop()+start() may already have
                            // installed a new one.
                            if (process === proc) {
                                process = null
                                writer = null
                            }
                        }
                        if (exitedNormally && process === proc) {
                            _outputFlow.emit("\n[Process exited]\n")
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    _outputFlow.emit("Failed to start shell process (${if (useRoot) "root" else "normal"}): ${e.message}\n")
                }
            }
        }
    }

    fun write(command: String) {
        val w = writer ?: return
        if (process == null) return
        // Log the interactive command (strip trailing newline for readability)
        val logCmd = command.trimEnd('\n', '\r')
        if (logCmd.isNotBlank()) AppLogger.logShellCmd(logCmd)
        scope.launch {
            try {
                w.write(command)
                w.flush()
            } catch (e: Exception) {
                _outputFlow.emit("\nError writing to terminal: ${e.message}\n")
            }
        }
    }

    fun stop() {
        val proc: Process?
        synchronized(this) {
            readerJob?.cancel()
            scope.coroutineContext[Job]?.let { it.cancelChildren() }
            try {
                writer?.close()
            } catch (_: Exception) {}
            writer = null
            proc = process
            process = null
        }
        try {
            proc?.destroy()
        } catch (_: Exception) {}
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 256 * 1024
        private const val COMMAND_TIMEOUT_SECONDS = 60L

        fun runCommand(command: String, workingDir: String, useRoot: Boolean): String {
            return try {
                val workingDirFile = File(workingDir)
                if (!workingDirFile.exists()) {
                    workingDirFile.mkdirs()
                }
                val shellCmd = if (useRoot) {
                    arrayOf("/system/bin/su", "-c", command)
                } else {
                    arrayOf("/system/bin/sh", "-c", command)
                }
                val proc = ProcessBuilder(*shellCmd)
                    .directory(workingDirFile)
                    .redirectErrorStream(true)
                    .start()
                try {
                    // Read output in chunks with a hard cap (prevents OOM from
                    // infinite-output commands like `yes` or `cat /dev/urandom`).
                    val output = StringBuilder()
                    val reader = proc.inputStream.bufferedReader()
                    val buffer = CharArray(8192)
                    val readerThread = Thread {
                        try {
                            var count = 0
                            while (output.length < MAX_OUTPUT_CHARS &&
                                reader.read(buffer).also { count = it } != -1
                            ) {
                                output.append(buffer, 0, count)
                            }
                        } catch (_: Exception) {
                            // Stream closed (process killed / timed out)
                        }
                    }
                    readerThread.isDaemon = true
                    readerThread.start()

                    val finished = proc.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    if (!finished) {
                        proc.destroyForcibly()
                    }
                    readerThread.join(2000)

                    val outputText = output.toString()
                    val exitCode = if (finished) proc.exitValue() else -1
                    AppLogger.logShellCmd(
                        command = command,
                        exitCode = exitCode,
                        output = outputText.take(400)
                    )
                    if (!finished) {
                        outputText + "\n[COMMAND TIMED OUT after $COMMAND_TIMEOUT_SECONDS seconds — process was killed]"
                    } else {
                        outputText
                    }
                } finally {
                    try { proc.destroy() } catch (_: Exception) {}
                }
            } catch (t: Throwable) {
                val errMsg = "Error running command: ${t.message}"
                AppLogger.logShellCmd(command, exitCode = -1, output = errMsg)
                errMsg
            }
        }
    }
}
