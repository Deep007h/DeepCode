package ai.deepcode.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import ai.deepcode.android.service.AiBridgeRunner
import ai.deepcode.android.service.TerminalRunner
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.LogLevel
import ai.deepcode.android.util.LogcatReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AdbCommandBridge — BroadcastReceiver that lets AI agents (Antigravity, opencode)
 * send commands to this app over ADB and receive structured JSON responses via logcat.
 *
 * ══════════════════════════════════════════════════════════════════════════════
 *  SEND A COMMAND (from PC / AI agent)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *   adb shell am broadcast -a ai.deepcode.DEBUG_CMD \
 *     --es cmd "ping" --es req_id "r1"
 *
 * ══════════════════════════════════════════════════════════════════════════════
 *  READ THE RESPONSE
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *   adb logcat -s DEEPCODE_AGENT -d       # one-shot read
 *   adb logcat -s DEEPCODE_AGENT          # live stream (all responses)
 *   adb logcat -s DEEPCODE_AI             # live AI token stream (ai_chat only)
 *
 * ══════════════════════════════════════════════════════════════════════════════
 *  COMMAND REFERENCE
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  ── Debug / Log ─────────────────────────────────────────────────────────────
 *   ping                     → pong + uptime
 *   run_shell  [cmd]         → execute root shell command, return stdout
 *   read_logs  [LEVEL]       → dump last 100 log entries (optional level filter)
 *   clear_logs               → clear in-memory log buffer
 *   inject_log LEVEL|TAG|MSG → insert synthetic log entry
 *   get_stats                → JSON stats object
 *   export_logs              → write logs to file, return path
 *   logcat_start             → start full-system logcat capture
 *   logcat_stop              → stop logcat capture
 *   send_message [text]      → log custom agent message
 *
 *  ── AI Chat ─────────────────────────────────────────────────────────────────
 *   ai_chat    [message]     → send message to AI, stream tokens → DEEPCODE_AI
 *                              args can include optional: "MSG|||session_id:SID"
 *   ai_chat_in [SID] [msg]   → chat in a specific session
 *                              args format: "SESSION_ID|||message text here"
 *   ai_get_model             → show current provider + model + active ADB session
 *   ai_set_model             → args: "PROVIDER_NAME|||MODEL_ID"
 *   ai_list_models           → list all providers and their models as JSON
 *
 *  ── Session / History ───────────────────────────────────────────────────────
 *   ai_list_sessions         → list all chat sessions as JSON array
 *   ai_new_session  [title]  → create session, switch ADB bridge to it
 *   ai_switch_session [SID]  → switch ADB bridge to existing session
 *   ai_read_session [SID]    → read all messages in session as JSON
 *   ai_last_messages [SID:N] → last N messages from session (default 5)
 *                              args: "SESSION_ID|||5" or just "5" for ADB session
 *   ai_search_history [q]    → search message text across all sessions
 *   ai_delete_session [SID]  → delete session and all its messages
 *
 * For testing only — remove before production release.
 */
class AdbCommandBridge : BroadcastReceiver() {

    companion object {
        const val ACTION        = "ai.deepcode.DEBUG_CMD"
        const val RESPONSE_TAG  = "DEEPCODE_AGENT"
        private const val TAG   = "AdbBridge"

        /** Emit a structured JSON response readable via: adb logcat -s DEEPCODE_AGENT */
        fun respond(reqId: String, status: String, data: Any?) {
            val json = buildString {
                append("{")
                append("\"req_id\":\"${esc(reqId)}\",")
                append("\"status\":\"${esc(status)}\",")
                when (data) {
                    null       -> append("\"data\":null")
                    is String  -> {
                        // If data already looks like JSON array/object, embed as-is
                        val d = data.trim()
                        if ((d.startsWith("{") && d.endsWith("}")) ||
                            (d.startsWith("[") && d.endsWith("]"))) {
                            append("\"data\":$d")
                        } else {
                            append("\"data\":\"${esc(d)}\"")
                        }
                    }
                    is Number  -> append("\"data\":$data")
                    else       -> append("\"data\":\"${esc(data.toString())}\"")
                }
                append("}")
            }
            Log.i(RESPONSE_TAG, json)
            AppLogger.logAdbResponse(reqId, status, json)
        }

        private fun esc(s: String) = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val cmd   = intent.getStringExtra("cmd")?.trim() ?: run {
            Log.w(TAG, "AdbCommandBridge: no 'cmd' extra in intent")
            return
        }
        val args  = intent.getStringExtra("args")?.trim() ?: ""
        val reqId = intent.getStringExtra("req_id")?.trim() ?: "no-id"

        AppLogger.logAdbCommand(cmd, args, reqId)

        val pendingResult = goAsync()
        scope.launch {
            try {
                handleCommand(context, cmd, args, reqId)
            } catch (e: Exception) {
                AppLogger.e(TAG, "ADB cmd error: $e", e)
                respond(reqId, "error", e.message ?: "unknown error")
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ── command router ────────────────────────────────────────────────────────

    private suspend fun handleCommand(context: Context, cmd: String, args: String, reqId: String) {
        when (cmd.lowercase()) {

            // ── debug / log commands ──────────────────────────────────────────

            "ping" -> {
                val uptime = android.os.SystemClock.elapsedRealtime()
                respond(reqId, "ok", "pong — uptime ${uptime / 1000}s, logcat=${LogcatReader.isRunning}")
            }

            "run_shell" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (shell command) required"); return }
                val output = TerminalRunner.runCommand(
                    command    = args,
                    workingDir = context.filesDir.absolutePath,
                    useRoot    = false
                )
                val truncated = if (output.length > 3000) output.take(3000) + "\n…[truncated]" else output
                respond(reqId, "ok", truncated)
            }

            "read_logs" -> {
                val levelFilter = if (args.isNotBlank()) {
                    try { LogLevel.valueOf(args.uppercase()) } catch (_: Exception) { null }
                } else null
                val entries = AppLogger.getEntries()
                    .let { if (levelFilter != null) it.filter { e -> e.level == levelFilter } else it }
                    .takeLast(100)
                val sb = StringBuilder()
                entries.forEach { e ->
                    sb.append("[${e.formattedTime}][${e.level.tag}][${e.tag}] ${e.message}\n")
                }
                respond(reqId, "ok", sb.toString().trimEnd())
            }

            "clear_logs" -> {
                AppLogger.clearLogs()
                respond(reqId, "ok", "logs cleared")
            }

            "inject_log" -> {
                val parts = args.split("|", limit = 3)
                if (parts.size < 3) { respond(reqId, "error", "args must be LEVEL|TAG|MESSAGE"); return }
                val level = try { LogLevel.valueOf(parts[0].uppercase()) } catch (_: Exception) { LogLevel.DEBUG }
                AppLogger.ingestExternalEntry(level, parts[1], parts[2], category = "injected")
                respond(reqId, "ok", "injected: [${level.tag}][${parts[1]}] ${parts[2]}")
            }

            "get_stats" -> {
                val s = AppLogger.getStats()
                val json = "{\"total\":${s.totalEntries}," +
                        "\"error\":${s.errorCount}," +
                        "\"warn\":${s.warnCount}," +
                        "\"info\":${s.infoCount}," +
                        "\"debug\":${s.debugCount}," +
                        "\"fatal\":${s.fatalCount}," +
                        "\"fileKb\":${s.fileSizeKb}," +
                        "\"logcat_running\":${LogcatReader.isRunning}}"
                respond(reqId, "ok", json)
            }

            "export_logs" -> {
                val path = AppLogger.exportLogs()
                respond(reqId, if (path != null) "ok" else "error", path ?: "export failed")
            }

            "logcat_start" -> {
                LogcatReader.start()
                respond(reqId, "ok", "logcat reader started")
            }

            "logcat_stop" -> {
                LogcatReader.stop()
                respond(reqId, "ok", "logcat reader stopped")
            }

            "send_message" -> {
                AppLogger.i(RESPONSE_TAG, "[AGENT_MSG] $args")
                respond(reqId, "ok", "message logged")
            }

            // ── PDF commands ──
            "create_pdf" -> {
                // args format: "TITLE|||CONTENT|||AUTHOR|||FILENAME"
                val parts = args.split("|||")
                if (parts.size < 2) { respond(reqId, "error", "args must be TITLE|||CONTENT[|||AUTHOR[|||FILENAME]]"); return }
                val title = parts[0].trim()
                val content = parts[1].trim()
                val author = parts.getOrNull(2)?.trim() ?: ""
                val filename = parts.getOrNull(3)?.trim() ?: ""
                try {
                    val executor = ai.deepcode.android.service.tools.ToolExecutor(context)
                    val result = executor.executeTool("create_pdf",
                        """{"title":${org.json.JSONObject.quote(title)},"content":${org.json.JSONObject.quote(content)},"author":${org.json.JSONObject.quote(author)},"filename":${org.json.JSONObject.quote(filename)}}""",
                        "", false)
                    respond(reqId, "ok", result)
                } catch (e: Exception) {
                    respond(reqId, "error", "PDF creation failed: ${e.message}")
                }
            }

            // ── AI chat commands ──────────────────────────────────────────────

            "ai_chat" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (message text) required"); return }
                // Optional: pass a session_id after ||| separator
                // args format: "your message" or "your message|||session_id:abc-123"
                val (message, sessionId) = parseSessionArgs(args)
                AiBridgeRunner.chat(context, message, reqId, sessionId)
                // Note: respond() is called inside AiBridgeRunner.chat() asynchronously
            }

            "ai_chat_in" -> {
                // args format: "SESSION_ID|||message text"
                val sep = args.indexOf("|||")
                if (sep < 0) { respond(reqId, "error", "args must be SESSION_ID|||message"); return }
                val sid = args.substring(0, sep).trim()
                val msg = args.substring(sep + 3).trim()
                if (msg.isBlank()) { respond(reqId, "error", "message cannot be empty"); return }
                AiBridgeRunner.chat(context, msg, reqId, sid)
            }

            "ai_get_model" -> {
                AiBridgeRunner.getModel(context, reqId)
            }

            "ai_set_model" -> {
                // args format: "PROVIDER_NAME|||MODEL_ID"
                val sep = args.indexOf("|||")
                val providerName = if (sep >= 0) args.substring(0, sep).trim() else args.trim()
                val modelId      = if (sep >= 0) args.substring(sep + 3).trim() else ""
                AiBridgeRunner.setModel(context, providerName, modelId, reqId)
            }

            "ai_list_models" -> {
                AiBridgeRunner.listModels(reqId)
            }

            // ── session / history commands ────────────────────────────────────

            "ai_list_sessions" -> {
                AiBridgeRunner.listSessions(context, reqId)
            }

            "ai_new_session" -> {
                AiBridgeRunner.newSession(context, args, reqId)
            }

            "ai_switch_session" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (session_id) required"); return }
                AiBridgeRunner.switchSession(args, reqId)
            }

            "ai_read_session" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (session_id) required"); return }
                AiBridgeRunner.readSession(context, args, reqId)
            }

            "ai_last_messages" -> {
                // args: "SESSION_ID|||5"  or just "5" (uses active ADB session)
                val sep = args.indexOf("|||")
                val sid   = if (sep >= 0) args.substring(0, sep).trim() else ""
                val countStr = if (sep >= 0) args.substring(sep + 3).trim() else args.trim()
                val count = countStr.toIntOrNull() ?: 5
                AiBridgeRunner.getLastMessages(context, sid, count, reqId)
            }

            "ai_search_history" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (search query) required"); return }
                AiBridgeRunner.searchHistory(context, args, reqId)
            }

            "ai_delete_session" -> {
                if (args.isBlank()) { respond(reqId, "error", "args (session_id) required"); return }
                AiBridgeRunner.deleteSession(context, args, reqId)
            }

            else -> {
                respond(reqId, "error",
                    "Unknown command: '$cmd'. " +
                    "Debug: ping run_shell read_logs clear_logs inject_log get_stats export_logs logcat_start logcat_stop send_message. " +
                    "AI: ai_chat ai_chat_in ai_get_model ai_set_model ai_list_models. " +
                    "Sessions: ai_list_sessions ai_new_session ai_switch_session ai_read_session ai_last_messages ai_search_history ai_delete_session."
                )
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Parse optional session_id from end of args: "message|||session_id:SID" */
    private fun parseSessionArgs(args: String): Pair<String, String?> {
        val sep = args.indexOf("|||")
        return if (sep >= 0) {
            val message = args.substring(0, sep).trim()
            val extra   = args.substring(sep + 3).trim()
            val sid     = if (extra.startsWith("session_id:")) extra.removePrefix("session_id:").trim() else extra
            message to sid.ifBlank { null }
        } else {
            args to null
        }
    }
}
