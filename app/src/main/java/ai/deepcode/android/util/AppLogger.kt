package ai.deepcode.android.util

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

enum class LogLevel(val tag: String, val priority: Int) {
    DEBUG("D", 0),
    INFO("I", 1),
    WARN("W", 2),
    ERROR("E", 3),
    FATAL("F", 4)
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val formattedTime: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String = Thread.currentThread().name,
    val sourceClass: String? = null,
    val sourceMethod: String? = null,
    val durationMs: Long? = null,
    val activityName: String? = null,
    val category: String? = null
)

/**
 * AppLogger — Full-spectrum logger for the testing build.
 *
 * Captures:
 *  • All app-internal logs (DEBUG → FATAL)
 *  • Logcat output from ALL processes (via LogcatReader / root)
 *  • Network calls (via DeepCodeOkHttpInterceptor)
 *  • Shell commands executed via TerminalRunner
 *  • User UI actions (taps, navigation)
 *  • Activity lifecycle
 *  • ADB bridge command/response pairs
 *  • ANR detection
 *  • Uncaught exception / crash reports
 *
 * Remove the testing-only sections before production release.
 */
object AppLogger {
    // ── constants ─────────────────────────────────────────────────────────────
    private const val MAX_MEMORY_ENTRIES = 20_000   // 4× original — testing build
    private const val MAX_FILE_SIZE = 4L * 1024 * 1024  // 4 MB
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "deepcode.jsonl"
    private const val CRASH_DIR = "DeepCode/crash_logs"

    // ── state ─────────────────────────────────────────────────────────────────
    private var logFile: File? = null
    private var appContext: android.content.Context? = null
    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    private val fileDateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US) }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _logEntries = java.util.ArrayDeque<LogEntry>(MAX_MEMORY_ENTRIES)
    private val _logEntriesLock = Any()
    private val _logFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logFlow: StateFlow<List<LogEntry>> = _logFlow.asStateFlow()

    private val _statsFlow = MutableStateFlow(LogStats())
    val statsFlow: StateFlow<LogStats> = _statsFlow.asStateFlow()

    private val entryCounter = AtomicLong(0L)
    private var lifecycleCallbacksRegistered = false

    private var errorCount = 0
    private var warnCount = 0
    private var infoCount = 0
    private var debugCount = 0
    private var fatalCount = 0
    private var pendingStatsUpdate = false

    private val activeTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val currentActivity = MutableStateFlow<String?>(null)
    val currentActivityFlow: StateFlow<String?> = currentActivity.asStateFlow()

    // ── stats ─────────────────────────────────────────────────────────────────
    data class LogStats(
        val totalEntries: Int = 0,
        val errorCount: Int = 0,
        val warnCount: Int = 0,
        val infoCount: Int = 0,
        val debugCount: Int = 0,
        val fatalCount: Int = 0,
        val fileSizeKb: Long = 0,
        val LogLevelCounts: Map<String, Int> = emptyMap()
    )

    // ── init ──────────────────────────────────────────────────────────────────
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        // Defer disk touch to the IO dispatcher so the caller (Application.onCreate)
        // never blocks on log-file creation.
        val logDir = File(context.filesDir, LOG_DIR)
        logFile = File(logDir, LOG_FILE)
        ioScope.launch {
            try {
                if (!logDir.exists()) logDir.mkdirs()
                val f = logFile ?: return@launch
                if (!f.exists()) f.createNewFile()
                i("AppLogger", "Logger initialized — file: ${f.absolutePath} [TESTING BUILD — full capture mode]")
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to prepare log file", e)
            }
            updateStats()
        }

        registerLifecycleCallbacks(context)
        registerAnrDetector()
    }

    // ── logcat reader control ─────────────────────────────────────────────────

    /** Start capturing full-system logcat (root) into the log viewer. */
    fun startLogcat() {
        LogcatReader.start()
        i("AppLogger", "Full logcat capture STARTED (root, all PIDs)")
    }

    /** Stop the logcat reader. */
    fun stopLogcat() {
        LogcatReader.stop()
        i("AppLogger", "Logcat capture STOPPED")
    }

    // ── public log API ────────────────────────────────────────────────────────

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)
    fun e(tag: String, message: String, throwable: Throwable?) {
        val st = if (throwable != null) Log.getStackTraceString(throwable) else null
        val level = if (throwable != null &&
            (throwable is java.lang.OutOfMemoryError || throwable is java.lang.StackOverflowError))
            LogLevel.FATAL else LogLevel.ERROR
        log(level, tag, "$message | ${throwable?.message}", throwable, st)
    }
    fun f(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.FATAL, tag, message, throwable, if (throwable != null) Log.getStackTraceString(throwable) else null)
    }

    // ── specialised event loggers ─────────────────────────────────────────────

    fun logNavigation(from: String?, to: String) {
        val msg = if (from != null) "Navigate: $from → $to" else "Navigate: $to"
        log(LogLevel.INFO, "Navigation", msg, category = "navigation")
    }

    fun logNetwork(method: String, url: String, statusCode: Int? = null, durationMs: Long? = null, error: String? = null) {
        val level = if (error != null || (statusCode != null && statusCode >= 400)) LogLevel.WARN else LogLevel.DEBUG
        val msg = buildString {
            append("$method $url")
            if (statusCode != null) append(" → $statusCode")
            if (durationMs != null) append(" (${durationMs}ms)")
            if (error != null) append(" ERROR: $error")
        }
        log(level, "Network", msg, category = "network", durationMs = durationMs)
    }

    /** Log a user UI action (tap, swipe, button press, etc.). Category = "user_action". */
    fun logUserAction(action: String, detail: String = "") {
        val msg = if (detail.isNotBlank()) "$action — $detail" else action
        log(LogLevel.INFO, "UserAction", msg, category = "user_action")
    }

    /** Log a touch event (tap). Category = "touch". */
    fun logTouchEvent(x: Float, y: Float, actionName: String) {
        log(LogLevel.DEBUG, "Touch", "$actionName @ (${x.toInt()}, ${y.toInt()})", category = "touch")
    }

    /** Log a DB operation. Category = "database". */
    fun logDbQuery(table: String, op: String, rowCount: Int = -1) {
        val msg = if (rowCount >= 0) "$op $table → $rowCount rows" else "$op $table"
        log(LogLevel.DEBUG, "Database", msg, category = "database")
    }

    /** Log a shell command execution. Category = "shell". */
    fun logShellCmd(command: String, exitCode: Int = 0, output: String = "") {
        val level = if (exitCode != 0) LogLevel.WARN else LogLevel.DEBUG
        val preview = if (output.length > 300) output.take(300) + "…" else output
        val msg = "$ $command\n  exit=$exitCode${if (preview.isNotBlank()) "\n  $preview" else ""}"
        log(level, "Shell", msg, category = "shell")
    }

    /** Log an ADB bridge command received. Category = "adb_bridge". */
    fun logAdbCommand(cmd: String, args: String, reqId: String) {
        log(LogLevel.INFO, "AdbBridge", "← CMD[$reqId] $cmd${if (args.isNotBlank()) " | args: $args" else ""}",
            category = "adb_bridge")
    }

    /** Log an ADB bridge response sent. Category = "adb_bridge". */
    fun logAdbResponse(reqId: String, status: String, payload: String) {
        log(LogLevel.INFO, "AdbBridge", "→ RSP[$reqId] $status: $payload",
            category = "adb_bridge")
        appContext?.let { ctx ->
            ioScope.launch {
                try {
                    val extDir = ctx.getExternalFilesDir(null)
                    if (extDir != null) {
                        val responseFile = File(extDir, "adb_response.json")
                        responseFile.writeText(payload)
                    }
                } catch (e: Exception) {
                    Log.e("AppLogger", "Failed to write adb response file", e)
                }
            }
        }
    }

    // ── performance timers ────────────────────────────────────────────────────

    fun startTimer(key: String) {
        activeTimers[key] = System.currentTimeMillis()
    }

    fun stopTimer(key: String, tag: String = "Perf", message: String = "") {
        val start = activeTimers.remove(key) ?: return
        val duration = System.currentTimeMillis() - start
        log(LogLevel.DEBUG, tag, if (message.isEmpty()) "$key took ${duration}ms" else "$message (${duration}ms)",
            category = "performance", durationMs = duration)
    }

    fun traceOperation(tag: String, operation: String, block: () -> Unit) {
        startTimer(operation)
        try { block() } finally { stopTimer(operation, tag, operation) }
    }

    // ── external entry ingestion (used by LogcatReader) ───────────────────────

    /**
     * Ingest a log entry from an external source (e.g., LogcatReader).
     * Does NOT re-emit to android.util.Log to avoid recursion.
     */
    fun ingestExternalEntry(
        level: LogLevel,
        tag: String,
        message: String,
        category: String? = null
    ) {
        val now = System.currentTimeMillis()
        val timestamp = dateFormat.get().format(Date(now))
        val id = entryCounter.incrementAndGet()

        val entry = LogEntry(
            id = id,
            timestamp = now,
            formattedTime = timestamp,
            level = level,
            tag = tag,
            message = message,
            threadName = Thread.currentThread().name,
            activityName = currentActivity.value,
            category = category
        )

        addEntry(entry)
        writeToFileAsync(entry, timestamp)
        // intentionally no android.util.Log call here
    }

    // ── getters ───────────────────────────────────────────────────────────────

    fun getEntries(): List<LogEntry> = synchronized(_logEntriesLock) { _logEntries.toList() }
    fun getStats(): LogStats = _statsFlow.value
    fun getLogFile(): File? = logFile

    // ── export ────────────────────────────────────────────────────────────────

    fun exportCrashLog(throwable: Throwable? = null) {
        try {
            val crashDir = File(appContext?.filesDir, CRASH_DIR)
            if (!crashDir.exists()) crashDir.mkdirs()
            val timestamp = fileDateFormat.get().format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.log")
            val sb = StringBuilder()
            sb.appendLine("DEEPCODE CRASH REPORT — ${dateFormat.get().format(Date())}")
            sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            sb.appendLine("========================================")
            sb.appendLine()
            if (throwable != null) {
                sb.appendLine("EXCEPTION: ${throwable::class.java.name}: ${throwable.message}")
                sb.appendLine("Stack Trace:")
                Log.getStackTraceString(throwable).lines().forEach { sb.appendLine("  $it") }
                sb.appendLine()
            }
            sb.appendLine("--- LAST ${_logEntries.size} LOG ENTRIES ---")
            _logEntries.forEach { entry ->
                sb.appendLine("[${entry.formattedTime}] [${entry.level.tag}] [${entry.tag}] ${entry.message}")
                entry.stackTrace?.let { trace -> trace.lines().forEach { sb.appendLine("  $it") } }
            }
            crashFile.writeText(sb.toString())
            Log.i("AppLogger", "Crash report → ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to export crash log", e)
        }
    }

    fun exportLogs(): String? {
        return try {
            val crashDir = File(appContext?.filesDir, CRASH_DIR)
            if (!crashDir.exists()) crashDir.mkdirs()
            val timestamp = fileDateFormat.get().format(Date())
            val exportFile = File(crashDir, "deepcode_log_$timestamp.log")
            val sb = StringBuilder()
            _logEntries.forEach { entry ->
                sb.appendLine("[${entry.formattedTime}] [${entry.level.tag}] [${entry.tag}] ${entry.message}")
                entry.stackTrace?.let { trace -> trace.lines().forEach { sb.appendLine("  $it") } }
            }
            exportFile.writeText(sb.toString())
            exportFile.absolutePath
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to export logs", e)
            null
        }
    }

    fun exportLogsAsJson(): String {
        val sb = StringBuilder("[\n")
        _logEntries.forEachIndexed { i, entry ->
            if (i > 0) sb.append(",\n")
            sb.append("  {")
            sb.append("\"id\":${entry.id},")
            sb.append("\"time\":\"${entry.formattedTime}\",")
            sb.append("\"level\":\"${entry.level.tag}\",")
            sb.append("\"tag\":\"${escapeJson(entry.tag)}\",")
            sb.append("\"msg\":\"${escapeJson(entry.message)}\"")
            if (entry.category != null) sb.append(",\"cat\":\"${escapeJson(entry.category)}\"")
            if (entry.durationMs != null) sb.append(",\"dur\":${entry.durationMs}")
            sb.append("}")
        }
        sb.append("\n]")
        return sb.toString()
    }

    @Synchronized
    fun clearLogs() {
        synchronized(_logEntriesLock) {
            _logEntries.clear()
            errorCount = 0; warnCount = 0; infoCount = 0; debugCount = 0; fatalCount = 0
        }
        _logFlow.value = emptyList()
        logFile?.let { file -> ioScope.launch { try { file.writeText("") } catch (_: Exception) {} } }
        scheduleStatsUpdate()
    }

    fun readLogs(): String = try {
        logFile?.readText() ?: "No logs available"
    } catch (e: Exception) { "Error: ${e.message}" }

    fun getRotatedLogs(): List<File> {
        val dir = logFile?.parentFile ?: return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".gz") }
            ?.sortedByDescending { it.lastModified() }
            ?.toList() ?: emptyList()
    }

    // ── private internals ──────────────────────────────────────────────────────

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        stackTraceOverride: String? = null,
        category: String? = null,
        activityName: String? = null,
        durationMs: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val timestamp = dateFormat.get().format(Date(now))
        val st = stackTraceOverride ?: if (throwable != null) Log.getStackTraceString(throwable) else null
        val id = entryCounter.incrementAndGet()

        val entry = LogEntry(
            id = id,
            timestamp = now,
            formattedTime = timestamp,
            level = level,
            tag = tag,
            message = message,
            stackTrace = st,
            threadName = Thread.currentThread().name,
            sourceClass = null,
            sourceMethod = null,
            durationMs = durationMs,
            activityName = activityName ?: currentActivity.value,
            category = category
        )

        addEntry(entry)
        writeToFileAsync(entry, timestamp)

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO  -> Log.i(tag, message)
            LogLevel.WARN  -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
            LogLevel.FATAL -> Log.e(tag, "FATAL: $message", throwable)
        }
    }

    private fun addEntry(entry: LogEntry) {
        val snapshot: List<LogEntry>
        synchronized(_logEntriesLock) {
            _logEntries.addLast(entry)
            when (entry.level) {
                LogLevel.ERROR -> errorCount++
                LogLevel.WARN  -> warnCount++
                LogLevel.INFO  -> infoCount++
                LogLevel.DEBUG -> debugCount++
                LogLevel.FATAL -> fatalCount++
            }
            val overflow = _logEntries.size - MAX_MEMORY_ENTRIES
            if (overflow > 0) {
                repeat(overflow) {
                    val r = _logEntries.pollFirst() ?: return@repeat
                    when (r.level) {
                        LogLevel.ERROR -> errorCount--
                        LogLevel.WARN  -> warnCount--
                        LogLevel.INFO  -> infoCount--
                        LogLevel.DEBUG -> debugCount--
                        LogLevel.FATAL -> fatalCount--
                    }
                }
            }
            snapshot = _logEntries.toList()
        }
        _logFlow.value = snapshot
        scheduleStatsUpdate()
    }

    private fun scheduleStatsUpdate() {
        if (pendingStatsUpdate) return
        pendingStatsUpdate = true
        ioScope.launch {
            kotlinx.coroutines.yield()
            pendingStatsUpdate = false
            updateStats()
        }
    }

    private fun updateStats() {
        var totalEntries = 0
        var ec = 0; var wc = 0; var ic = 0; var dc = 0; var fc = 0
        var fsize = 0L
        synchronized(_logEntriesLock) {
            totalEntries = _logEntries.size
            ec = errorCount; wc = warnCount; ic = infoCount; dc = debugCount; fc = fatalCount
            fsize = logFile?.length() ?: 0L
        }
        _statsFlow.value = LogStats(
            totalEntries = totalEntries,
            errorCount = ec,
            warnCount = wc,
            infoCount = ic,
            debugCount = dc,
            fatalCount = fc,
            fileSizeKb = fsize / 1024,
            LogLevelCounts = mapOf(
                "ERROR" to ec, "WARN" to wc, "INFO" to ic, "DEBUG" to dc, "FATAL" to fc
            )
        )
    }

    private fun writeToFileAsync(entry: LogEntry, formattedTime: String) {
        // Capture a snapshot of mutable state on the caller thread, then do all
        // disk work on the IO dispatcher. This keeps the hot path allocation-free
        // for main-thread callers (Compose recomposition, lifecycle callbacks).
        val file = logFile ?: return
        val json = buildString {
            append("{\"t\":\"${escapeJson(formattedTime)}\"")
            append(",\"l\":\"${entry.level.tag}\"")
            append(",\"tag\":\"${escapeJson(entry.tag)}\"")
            append(",\"msg\":\"${escapeJson(entry.message)}\"")
            append(",\"thr\":\"${escapeJson(entry.threadName)}\"")
            if (entry.category != null) append(",\"cat\":\"${escapeJson(entry.category)}\"")
            if (entry.activityName != null) append(",\"act\":\"${escapeJson(entry.activityName)}\"")
            if (entry.durationMs != null) append(",\"dur\":${entry.durationMs}")
            if (entry.stackTrace != null) append(",\"st\":\"${escapeJson(entry.stackTrace)}\"")
            append("}\n")
        }
        ioScope.launch {
            try {
                if (file.length() > MAX_FILE_SIZE) rotateLogs()
                file.appendText(json)
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to write log", e)
            }
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun rotateLogs() {
        val file = logFile ?: return
        try {
            val backupName = "deepcode_${fileDateFormat.get().format(Date())}.jsonl.gz"
            val backup = File(file.parent, backupName)
            FileOutputStream(backup).use { fos ->
                java.util.zip.GZIPOutputStream(fos).use { gzip ->
                    file.inputStream().use { ins -> ins.copyTo(gzip) }
                }
            }
            file.writeText("")
            i("AppLogger", "Log rotated → $backupName (${backup.length() / 1024}KB compressed)")
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to rotate logs", e)
        }
    }

    private fun getCallerInfo(): Pair<String?, String>? = null

    // ── lifecycle tracking ────────────────────────────────────────────────────

    private fun registerLifecycleCallbacks(context: android.content.Context) {
        if (lifecycleCallbacksRegistered) return
        lifecycleCallbacksRegistered = true

        if (context is Application) {
            context.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                private val activityStartTimes = mutableMapOf<String, Long>()

                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                    val name = activity.localClassName
                    log(LogLevel.INFO, "Lifecycle", "← $name.onCreate(savedState=${savedInstanceState != null})",
                        category = "lifecycle", activityName = name)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityPreStarted(activity: Activity) {
                    val name = activity.localClassName
                    log(LogLevel.INFO, "Lifecycle", "→ $name.onStart",
                        category = "lifecycle", activityName = name)
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityPreResumed(activity: Activity) {
                    val name = activity.localClassName
                    currentActivity.value = name
                    activityStartTimes[name] = System.currentTimeMillis()
                    log(LogLevel.INFO, "Lifecycle", "▶ $name.onResume",
                        category = "lifecycle", activityName = name)
                }

                override fun onActivityResumed(activity: Activity) {}

                override fun onActivityPrePaused(activity: Activity) {
                    val name = activity.localClassName
                    val startTime = activityStartTimes.remove(name)
                    val duration = if (startTime != null) System.currentTimeMillis() - startTime else null
                    log(LogLevel.INFO, "Lifecycle",
                        "▷ $name.onPause${if (duration != null) " (${duration}ms visible)" else ""}",
                        category = "lifecycle", activityName = name, durationMs = duration)
                }

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityPreStopped(activity: Activity) {
                    val name = activity.localClassName
                    log(LogLevel.INFO, "Lifecycle", "○ $name.onStop",
                        category = "lifecycle", activityName = name)
                }

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivityPreDestroyed(activity: Activity) {
                    val name = activity.localClassName
                    log(LogLevel.INFO, "Lifecycle", "✕ $name.onDestroy",
                        category = "lifecycle", activityName = name)
                }

                override fun onActivityDestroyed(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            })
            i("AppLogger", "Activity lifecycle tracking registered")
        }

        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                fun onForeground() { log(LogLevel.INFO, "Lifecycle", "App → FOREGROUND", category = "lifecycle") }

                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                fun onBackground() { log(LogLevel.INFO, "Lifecycle", "App → BACKGROUND", category = "lifecycle") }
            })
        } catch (_: Exception) {
            w("AppLogger", "ProcessLifecycleOwner not available")
        }
    }

    // ── ANR detector ──────────────────────────────────────────────────────────

    private fun registerAnrDetector() {
        val anrThread = Thread({
            while (true) {
                try {
                    Thread.sleep(5000)
                    if (Looper.getMainLooper().thread?.isAlive == true) {
                        val stackTrace = Looper.getMainLooper().thread.stackTrace
                        if (stackTrace.isNotEmpty()) {
                            val significant = stackTrace.any { frame ->
                                frame.className.startsWith("ai.deepcode") &&
                                        !frame.className.contains("AppLogger")
                            }
                            if (significant) {
                                val sb = StringBuilder()
                                sb.appendLine("POSSIBLE ANR — main thread stuck (5s sample)")
                                stackTrace.take(20).forEach { frame ->
                                    sb.appendLine("    at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})")
                                }
                                w("ANR-Detector", sb.toString().trimEnd())
                            }
                        }
                    }
                } catch (_: InterruptedException) { break }
            }
        }, "ANR-Detector")
        anrThread.isDaemon = true
        anrThread.start()
    }
}
