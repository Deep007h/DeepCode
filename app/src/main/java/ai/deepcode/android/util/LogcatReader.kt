package ai.deepcode.android.util

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * LogcatReader — spawns a root logcat process and streams every line
 * into AppLogger as a LogEntry with category = "logcat".
 *
 * Uses `su -c logcat` so it captures the full system log from all PIDs.
 * For a testing/debug build only — remove before shipping.
 */
object LogcatReader {

    private const val TAG = "LogcatReader"

    // Regex matching logcat "threadtime" format:
    // MM-DD HH:MM:SS.mmm  PID   TID  LEVEL TAG  : message
    private val LOGCAT_RE = Regex(
        """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$"""
    )

    private var readerJob: Job? = null
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Start streaming logcat (full system, using root). Safe to call multiple times. */
    fun start() {
        if (readerJob?.isActive == true) return

        readerJob = scope.launch {
            try {
                // Clear the existing buffer first, then stream new entries
                val proc = ProcessBuilder(
                    "/system/bin/su", "-c",
                    "logcat -v threadtime"   // stream only new lines
                )
                    .redirectErrorStream(true)
                    .start()
                process = proc

                AppLogger.i(TAG, "Logcat reader started (root, all PIDs)")

                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (isActive) {
                    line = reader.readLine() ?: break
                    parseLine(line)
                }

            } catch (e: CancellationException) {
                // normal stop
            } catch (e: Exception) {
                Log.e(TAG, "LogcatReader error", e)
            } finally {
                try { process?.destroy() } catch (_: Exception) {}
                process = null
                AppLogger.w(TAG, "Logcat reader stopped")
            }
        }
    }

    /** Stop streaming. */
    fun stop() {
        readerJob?.cancel()
        readerJob = null
        try { process?.destroy() } catch (_: Exception) {}
        process = null
    }

    /** Returns true if currently streaming. */
    val isRunning: Boolean get() = readerJob?.isActive == true

    // ── line parser ────────────────────────────────────────────────────────

    private fun parseLine(raw: String) {
        // Skip logcat header lines and our own entries to avoid ping-pong
        if (raw.startsWith("-----") || raw.contains("AppLogger") || raw.contains("LogcatReader")) return

        val match = LOGCAT_RE.matchEntire(raw.trim())
        if (match != null) {
            val (levelChar, tag, msg) = match.destructured
            val level = charToLevel(levelChar)
            // Don't re-emit through AppLogger.log() (which calls android.util.Log)
            // as that would recurse. Write directly into the internal sink.
            AppLogger.ingestExternalEntry(level, "[$tag]", msg, category = "logcat")
        } else {
            // Non-matching lines (multiline stack traces, empty, etc.)
            if (raw.isNotBlank()) {
                AppLogger.ingestExternalEntry(LogLevel.DEBUG, "[logcat]", raw, category = "logcat")
            }
        }
    }

    private fun charToLevel(c: String): LogLevel = when (c) {
        "V", "D" -> LogLevel.DEBUG
        "I"      -> LogLevel.INFO
        "W"      -> LogLevel.WARN
        "E"      -> LogLevel.ERROR
        "F"      -> LogLevel.FATAL
        else     -> LogLevel.DEBUG
    }
}
