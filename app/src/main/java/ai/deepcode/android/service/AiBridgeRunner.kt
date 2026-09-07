package ai.deepcode.android.service

import android.content.Context
import android.util.Log
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.ModelCatalog
import ai.deepcode.android.data.remote.providerDefaultBaseUrl
import ai.deepcode.android.data.remote.providerStorageId
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.domain.model.ToolCall
import ai.deepcode.android.receiver.AdbCommandBridge
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.TokenSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * AiBridgeRunner — bridges the ADB Command Bridge to the AI provider system.
 *
 * Lets external AI agents (Antigravity, opencode on PC) send messages to the app's
 * AI, read sessions, read history, and stream responses back to logcat.
 *
 * Response streaming is written to both:
 *   • Logcat tag  DEEPCODE_AI   (token chunks as they arrive)
 *   • Logcat tag  DEEPCODE_AGENT (final complete response + errors)
 *
 * Usage (from PC):
 *   adb shell am broadcast -a ai.deepcode.DEBUG_CMD \
 *     --es cmd "ai_chat" --es args "Hello, what can you do?" --es req_id "r1"
 *   adb logcat -s DEEPCODE_AI    # stream tokens live
 *   adb logcat -s DEEPCODE_AGENT -d  # read final response
 *
 * For testing build only — remove before production.
 */
object AiBridgeRunner {

    const val AI_TAG = "DEEPCODE_AI"   // streaming tokens
    private const val TAG = "AiBridge"

    // Dedicated session for ADB-initiated chats (persisted across invocations)
    private var adbSessionId: String = ""

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── ai_chat ──────────────────────────────────────────────────────────────

    /**
     * Send [userMessage] to the currently configured AI provider and stream
     * the response back to logcat. The conversation is stored in a dedicated
     * ADB bridge session so history accumulates.
     *
     * @param context  App context
     * @param userMessage  The message text to send
     * @param reqId  Request ID for response correlation
     * @param sessionId  Optional: use a specific session instead of the ADB session
     */
    fun chat(
        context: Context,
        userMessage: String,
        reqId: String,
        sessionId: String? = null
    ) {
        scope.launch {
            try {
                val repo = DeepCodeRepository(context)

                // Resolve or create session
                val sid = when {
                    sessionId != null && sessionId.isNotBlank() -> {
                        if (repo.getSessionById(sessionId) == null) {
                            repo.createSessionWithId(sessionId, "Chat ${sessionId.take(8)}")
                        }
                        sessionId
                    }
                    adbSessionId.isNotEmpty() -> adbSessionId
                    else -> {
                        val newId = repo.createSession("Chat Session")
                        adbSessionId = newId
                        AppLogger.i(TAG, "Created ADB bridge session: $newId")
                        newId
                    }
                }

                // Build user message and persist it
                val msg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sid,
                    role = "user",
                    content = userMessage,
                    timestamp = System.currentTimeMillis()
                )
                repo.insertMessage(msg)
                AppLogger.logUserAction("AdbAiChat", "→ $userMessage")

                // Resolve the configured provider + model
                val prefs = repo.securePrefs
                val providerName = prefs.getSetting("chat_provider", "Zen AI")
                val modelId = prefs.getSetting("chat_model", "")

                val provider = AIProviderFactory.providers.find { it.name.equals(providerName, ignoreCase = true) }
                    ?: AIProviderFactory.providers.firstOrNull()

                if (provider == null) {
                    AdbCommandBridge.respond(reqId, "error", "No AI provider available")
                    return@launch
                }

                val allModels = (ModelCatalog.getModelsForProvider(provider.name, prefs) + provider.models).distinctBy { it.id }
                val model = if (modelId.isNotEmpty()) allModels.find { it.id == modelId || it.name.equals(modelId, ignoreCase = true) }
                    else null
                val resolvedModel = model ?: allModels.firstOrNull() ?: provider.models.firstOrNull()

                if (resolvedModel == null) {
                    AdbCommandBridge.respond(reqId, "error", "No model available for provider: ${provider.name}")
                    return@launch
                }

                // Get API key — refresh Antigravity OAuth token if needed
                if (provider.name == "Antigravity") {
                    val refreshToken = prefs.getSetting("oauth_refresh_antigravity", "")
                    if (refreshToken.isNotEmpty()) {
                        Log.i(TAG, "Refreshing Antigravity token (refresh token exists, length=${refreshToken.length})")
                        try {
                            val googleAuth = ai.deepcode.android.service.google.GoogleAuthService(context)
                            val result = googleAuth.refreshAccessToken(refreshToken)
                            if (result.isSuccess) {
                                val tokens = result.getOrThrow()
                                prefs.saveSetting("oauth_token_antigravity", tokens.accessToken)
                                if (tokens.refreshToken.isNotEmpty()) {
                                    prefs.saveSetting("oauth_refresh_antigravity", tokens.refreshToken)
                                }
                                Log.i(TAG, "Antigravity token refreshed successfully")
                            } else {
                                Log.e(TAG, "Antigravity token refresh failed: ${result.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Antigravity token refresh exception", e)
                        }
                    } else {
                        Log.w(TAG, "No Antigravity refresh token available")
                    }
                }
                val storageId = providerStorageId(provider.name)
                val aliasKeys = mutableListOf(storageId)
                if (storageId.contains("-")) aliasKeys.add(storageId.replace("-", ""))
                if (storageId == "cerebras") aliasKeys.add("cerebrus")
                if (storageId == "cerebrus") aliasKeys.add("cerebras")
                if (storageId == "gemini") aliasKeys.add("google gemini")
                if (storageId == "gmi") aliasKeys.add("gmi-cloud")
                if (storageId == "gmi-cloud") aliasKeys.add("gmi")

                var apiKey = ""
                for (k in aliasKeys) {
                    val rotatorResult = ai.deepcode.android.data.remote.ApiKeyRotator.getNextAvailableKey(prefs, k)
                    if (rotatorResult != null && rotatorResult.first.isNotEmpty()) {
                        apiKey = rotatorResult.first
                        break
                    }
                    val raw = prefs.getApiKey(k)
                    if (raw.isNotEmpty()) {
                        apiKey = raw
                        break
                    }
                    val oauth = prefs.getSetting("oauth_token_$k", "")
                    if (oauth.isNotEmpty()) {
                        apiKey = oauth
                        break
                    }
                }
                if (apiKey.isNotEmpty() && provider.name == "Antigravity") {
                    val projectId = prefs.getSetting("oauth_project_$storageId", "")
                    if (projectId.isNotEmpty()) apiKey += "||$projectId"
                }
                if (apiKey.isEmpty() && (provider.isFree || provider.name.startsWith("Zen"))) {
                    apiKey = "zen-free"
                }
                if (apiKey.isEmpty()) {
                    AdbCommandBridge.respond(reqId, "error",
                        "No API key set for ${provider.name}. Configure it in Settings → API Keys.")
                    return@launch
                }

                // Build message history for the API (last 20 messages)
                val rawHistory = repo.getMessagesListForSession(sid).takeLast(20)
                val maxTurns = prefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8
                val history = if (maxTurns > 0) TokenSaver.trimHistory(rawHistory, maxTurns) else rawHistory
                val finalHistory = history.toMutableList()

                // Inject PDF creation system prompt
                val pdfPrompt = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sid,
                    role = "system",
                    content = "## PDF CREATION RULE (MANDATORY)\nWhen the user asks to create a PDF: \n1. FIRST call list_reference_layouts to see available template layouts.\n2. If a specific layout is requested or if working via ADB/automation/API, do NOT stop to ask. Pick 'academic-paper' or 'blueprint' as the default layout, call get_layout_instructions with layout_id, and then call create_pdf directly.\n3. Only ask the user to pick a layout if interactive selection is explicitly requested.\n4. Read the instructions carefully — they tell you exactly what layout to use, what formatting rules to follow, and what color scheme to apply.\n5. Then call create_pdf. USE the instructions to structure your content with the right headings, tables, blockquotes, and formatting as specified. Follow the instructions EXACTLY.\n- Generate the content from your OWN TRAINING KNOWLEDGE for PYQs.\n- CRITICAL: Write actual question papers with proper answers using ## headings, | tables, and - bullet lists. Cover at least 6 topics relevant to the subject the user requested.",
                    timestamp = 0
                )
                finalHistory.add(0, pdfPrompt)

                val agentTools = repo.getDeclaredTools()

                // Notify: streaming starting
                Log.i(AI_TAG, "[START] req=$reqId provider=${provider.name} model=${resolvedModel.id} session=$sid")
                AdbCommandBridge.respond(reqId, "streaming",
                    "AI response starting — provider=${provider.name} model=${resolvedModel.id}. Listen: adb logcat -s $AI_TAG")

                // Stream the response with a multi-turn tool execution loop (up to 3 turns)
                val responseBuffer = StringBuilder()
                var tokenCount = 0
                val fileMarkers = mutableListOf<String>()
                val baseUrl = providerDefaultBaseUrl(provider.name)

                var currentTurn = 0
                val maxToolTurns = 5

                while (currentTurn < maxToolTurns) {
                    currentTurn++
                    val rawTurnHistory = repo.getMessagesListForSession(sid).takeLast(20)
                    val turnTrimmed = if (maxTurns > 0) TokenSaver.trimHistory(rawTurnHistory, maxTurns) else rawTurnHistory
                    val turnMessages = turnTrimmed.toMutableList()
                    turnMessages.add(0, pdfPrompt)

                    val turnBuffer = StringBuilder()
                    var chunkBuffer = StringBuilder()
                    val turnToolResults = mutableListOf<Pair<ToolCall, String>>()

                    val turnSuccess = withTimeoutOrNull(120_000L) {
                        provider.streamCompletion(
                            messages = turnMessages,
                            model = resolvedModel.id,
                            tools = agentTools,
                            apiKey = apiKey,
                            customBaseUrl = baseUrl,
                            onToken = { token ->
                                turnBuffer.append(token)
                                chunkBuffer.append(token)
                                tokenCount++
                                if (chunkBuffer.length >= 80 || token.contains('\n')) {
                                    Log.i(AI_TAG, chunkBuffer.toString())
                                    chunkBuffer.clear()
                                }
                            },
                            onToolCall = { tc ->
                                Log.i(TAG, "ADB AI tool call (turn $currentTurn): ${tc.name}")
                                val executor = ToolExecutor(context)
                                val result = try {
                                    executor.executeTool(tc.name, tc.arguments, "", false)
                                } catch (e: Exception) {
                                    "Error: ${e.message}"
                                }
                                turnToolResults.add(tc to result)
                                Log.i(TAG, "ADB AI tool result: ${result.take(300)}")
                            },
                            onComplete = {
                                if (chunkBuffer.isNotEmpty()) {
                                    Log.i(AI_TAG, chunkBuffer.toString())
                                    chunkBuffer.clear()
                                }
                            },
                            onError = { error ->
                                AppLogger.e(TAG, "ADB AI stream error (turn $currentTurn)", error)
                            }
                        )
                    }

                    if (turnSuccess == null) {
                        if (currentTurn == 1) {
                            AdbCommandBridge.respond(reqId, "error", "AI request timed out after 120s")
                            return@launch
                        } else break
                    }

                    // Fallback: Check for DSML / text tool calls if no API tool calls emitted
                    if (turnToolResults.isEmpty()) {
                        val rawTurnText = turnBuffer.toString()
                        val parsedToolCalls = parseDsmlToolCalls(rawTurnText)
                        if (parsedToolCalls.isNotEmpty()) {
                            Log.i(TAG, "Extracted ${parsedToolCalls.size} DSML tool calls on turn $currentTurn")
                            for (tc in parsedToolCalls) {
                                Log.i(TAG, "Executing fallback DSML tool call: ${tc.name}")
                                val executor = ToolExecutor(context)
                                val result = try {
                                    executor.executeTool(tc.name, tc.arguments, "", false)
                                } catch (e: Exception) {
                                    "Error: ${e.message}"
                                }
                                turnToolResults.add(tc to result)
                                Log.i(TAG, "Fallback DSML tool result: ${result.take(300)}")
                            }
                        }
                    }

                    // Accumulate clean response text
                    val turnCleanText = cleanResponseText(turnBuffer.toString())
                    if (turnCleanText.isNotBlank()) {
                        if (responseBuffer.isNotEmpty()) responseBuffer.append("\n\n")
                        responseBuffer.append(turnCleanText)
                    }

                    // If tools were executed, record them in DB and loop for follow-up response
                    if (turnToolResults.isNotEmpty()) {
                        val tcArray = com.google.gson.JsonArray()
                        for ((tc, _) in turnToolResults) {
                            val tcObj = com.google.gson.JsonObject()
                            tcObj.addProperty("id", tc.id)
                            tcObj.addProperty("name", tc.name)
                            tcObj.addProperty("arguments", tc.arguments)
                            tcArray.add(tcObj)
                        }
                        val toolNamesStr = turnToolResults.joinToString(", ") { it.first.name }
                        repo.insertMessage(Message(
                            id = UUID.randomUUID().toString(), sessionId = sid,
                            role = "assistant", content = "Executing tool actions: $toolNamesStr",
                            timestamp = System.currentTimeMillis(), isToolCall = true,
                            toolCallsJson = tcArray.toString()
                        ))
                        for ((tc, result) in turnToolResults) {
                            val tcIdObj = com.google.gson.JsonObject().apply {
                                addProperty("id", tc.id)
                                addProperty("name", tc.name)
                            }
                            repo.insertMessage(Message(
                                id = UUID.randomUUID().toString(), sessionId = sid,
                                role = "tool", content = result,
                                timestamp = System.currentTimeMillis(), toolCallsJson = tcIdObj.toString()
                            ))
                            val markerRx = Regex("""\[(?:file|video):([^\]]+)\]""")
                            markerRx.findAll(result).forEach { match ->
                                fileMarkers.add(match.value)
                            }
                        }
                    } else {
                        // Model finished without calling further tools
                        break
                    }
                }

                // Persist assistant response
                var finalResponse = cleanResponseText(responseBuffer.toString())
                if (fileMarkers.isNotEmpty() && !finalResponse.contains("[file:")) {
                    finalResponse += "\n\n" + fileMarkers.joinToString("\n")
                }
                if (finalResponse.isNotBlank()) {
                    repo.insertMessage(Message(
                        id = UUID.randomUUID().toString(), sessionId = sid,
                        role = "assistant", content = finalResponse,
                        timestamp = System.currentTimeMillis()
                    ))
                }

                // Final complete response
                Log.i(AI_TAG, "[END] req=$reqId tokens=$tokenCount")
                val preview = if (finalResponse.length > 500) finalResponse.take(500) + "…[${finalResponse.length} chars total]"
                    else finalResponse
                AdbCommandBridge.respond(reqId, "ok", preview)
                AppLogger.i(TAG, "ADB AI response complete ($tokenCount tokens) for req $reqId")

            } catch (e: Exception) {
                AppLogger.e(TAG, "AiBridgeRunner.chat error", e)
                AdbCommandBridge.respond(reqId, "error", e.message ?: "unknown error")
            }
        }
    }

    // ── session management ───────────────────────────────────────────────────

    /** List all chat sessions as JSON array. */
    suspend fun listSessions(context: Context, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val sessions = repo.getAllSessions().first()
            val sb = StringBuilder("[")
            sessions.forEachIndexed { i, s ->
                if (i > 0) sb.append(",")
                sb.append("{\"id\":\"${esc(s.id)}\",\"title\":\"${esc(s.title)}\",\"created\":${s.createdAt}}")
            }
            sb.append("]")
            AdbCommandBridge.respond(reqId, "ok", sb.toString())
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "list sessions error")
        }
    }

    /** Read all messages in a session as JSON array. */
    suspend fun readSession(context: Context, sessionId: String, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val messages = repo.getMessagesListForSession(sessionId)
            if (messages.isEmpty()) {
                AdbCommandBridge.respond(reqId, "error", "No messages found for session: $sessionId")
                return
            }
            val sb = StringBuilder("[")
            messages.forEachIndexed { i, m ->
                if (i > 0) sb.append(",")
                val contentPreview = if (m.content.length > 300) m.content.take(300) + "…" else m.content
                sb.append("{\"role\":\"${m.role}\",\"ts\":${m.timestamp},\"content\":\"${esc(contentPreview)}\"}")
            }
            sb.append("]")
            AdbCommandBridge.respond(reqId, "ok", sb.toString())
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "read session error")
        }
    }

    /** Get the last N messages from a session (default 5). */
    suspend fun getLastMessages(context: Context, sessionId: String, count: Int, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val sid = sessionId.ifBlank { adbSessionId }
            if (sid.isBlank()) {
                AdbCommandBridge.respond(reqId, "error", "No session active. Use ai_chat first or provide a session_id.")
                return
            }
            val messages = repo.getMessagesListForSession(sid).takeLast(count)
            val sb = StringBuilder("[")
            messages.forEachIndexed { i, m ->
                if (i > 0) sb.append(",")
                val contentPreview = if (m.content.length > 500) m.content.take(500) + "…" else m.content
                sb.append("{\"role\":\"${m.role}\",\"ts\":${m.timestamp},\"content\":\"${esc(contentPreview)}\"}")
            }
            sb.append("]")
            AdbCommandBridge.respond(reqId, "ok", sb.toString())
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "get last messages error")
        }
    }

    /** Create a new session and switch the ADB bridge to it. */
    suspend fun newSession(context: Context, title: String, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val id = repo.createSession(if (title.isBlank()) "[ADB] AI Bridge" else title)
            adbSessionId = id
            AdbCommandBridge.respond(reqId, "ok", "Created session: $id (now active)")
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "new session error")
        }
    }

    /** Switch the ADB AI bridge to use an existing session. */
    fun switchSession(sessionId: String, reqId: String) {
        adbSessionId = sessionId
        AdbCommandBridge.respond(reqId, "ok", "ADB bridge switched to session: $sessionId")
    }

    /** Delete a session and all its messages. */
    suspend fun deleteSession(context: Context, sessionId: String, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            repo.deleteSession(sessionId)
            if (adbSessionId == sessionId) adbSessionId = ""
            AdbCommandBridge.respond(reqId, "ok", "Deleted session: $sessionId")
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "delete session error")
        }
    }

    /** List available AI providers and models. */
    fun listModels(reqId: String) {
        val sb = StringBuilder("[")
        AIProviderFactory.providers.forEachIndexed { pi, p ->
            if (pi > 0) sb.append(",")
            sb.append("{\"provider\":\"${esc(p.name)}\",\"models\":[")
            p.models.forEachIndexed { mi, m ->
                if (mi > 0) sb.append(",")
                sb.append("{\"id\":\"${esc(m.id)}\",\"name\":\"${esc(m.name)}\",\"free\":${m.isFree}}")
            }
            sb.append("]}")
        }
        sb.append("]")
        AdbCommandBridge.respond(reqId, "ok", sb.toString())
    }

    /** Set the active provider + model (persisted to EncryptedPrefs). */
    fun setModel(context: Context, providerName: String, modelId: String, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val provider = AIProviderFactory.providers.find {
                it.name.equals(providerName, ignoreCase = true)
            }
            if (provider == null) {
                AdbCommandBridge.respond(reqId, "error",
                    "Unknown provider: $providerName. Use ai_list_models to see available providers.")
                return
            }
            val allModels = (ModelCatalog.getModelsForProvider(provider.name, repo.securePrefs) + provider.models).distinctBy { it.id }
            val model = if (modelId.isNotBlank()) allModels.find { it.id == modelId || it.name.equals(modelId, ignoreCase = true) }
                else allModels.firstOrNull()
            if (model == null) {
                AdbCommandBridge.respond(reqId, "error",
                    "Model '$modelId' not found in provider '${provider.name}'")
                return
            }
            repo.securePrefs.saveSetting("chat_provider", provider.name)
            repo.securePrefs.saveSetting("chat_model", model.id)
            AdbCommandBridge.respond(reqId, "ok",
                "Model set: ${provider.name} / ${model.name} (${model.id})")
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "set model error")
        }
    }

    /** Get currently configured model. */
    fun getModel(context: Context, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val provider = repo.securePrefs.getSetting("chat_provider", "Zen AI")
            val model = repo.securePrefs.getSetting("chat_model", "(default)")
            val sessionInfo = if (adbSessionId.isNotEmpty()) adbSessionId else "none"
            AdbCommandBridge.respond(reqId, "ok",
                "{\"provider\":\"${esc(provider)}\",\"model\":\"${esc(model)}\",\"adb_session\":\"${esc(sessionInfo)}\"}")
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "get model error")
        }
    }

    /** Search messages across all sessions. */
    suspend fun searchHistory(context: Context, query: String, reqId: String) {
        try {
            val repo = DeepCodeRepository(context)
            val sessions = repo.getAllSessions().first()
            val results = mutableListOf<String>()
            val lq = query.lowercase()
            for (session in sessions) {
                val messages = repo.getMessagesListForSession(session.id)
                messages.filter { it.content.contains(lq, ignoreCase = true) }
                    .takeLast(3)
                    .forEach { m ->
                        val preview = if (m.content.length > 200) m.content.take(200) + "…" else m.content
                        results.add("{\"session\":\"${esc(session.title)}\",\"session_id\":\"${esc(session.id)}\",\"role\":\"${m.role}\",\"content\":\"${esc(preview)}\"}")
                    }
                if (results.size >= 20) break
            }
            AdbCommandBridge.respond(reqId, "ok", "[${results.joinToString(",")}]")
        } catch (e: Exception) {
            AdbCommandBridge.respond(reqId, "error", e.message ?: "search error")
        }
    }

    private fun cleanResponseText(rawText: String): String {
        var text = rawText.replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<thought>[\\s\\S]*", RegexOption.IGNORE_CASE), "")
        val dsmlPfx = """(?:\s*\|{1,2}\s*DSML\s*\|{1,2}\s*)?"""
        text = text.replace(Regex("""<\s*${dsmlPfx}tool_calls?[\s\S]*?<\s*/\s*${dsmlPfx}tool_calls?\s*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""<\s*${dsmlPfx}invoke[\s\S]*?<\s*/\s*${dsmlPfx}invoke\s*>""", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("""<\s*${dsmlPfx}parameter[\s\S]*?<\s*/\s*${dsmlPfx}parameter\s*>""", RegexOption.IGNORE_CASE), "")
        return text.trim()
    }

    private fun parseDsmlToolCalls(text: String): List<ToolCall> {
        val result = mutableListOf<ToolCall>()
        val dsmlInvokeRegex = Regex("""<\s*\|{1,2}\s*DSML\s*\|{1,2}\s*invoke\s+name="([^"]+)"[^>]*>(.*?)</\s*\|{1,2}\s*DSML\s*\|{1,2}\s*invoke>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in dsmlInvokeRegex.findAll(text)) {
            val rawName = m.groupValues[1]
            val name = rawName.replace("__", "_")
            val paramsBlock = m.groupValues[2]
            val args = com.google.gson.JsonObject()
            val dsmlParamRegex = Regex("""<\s*\|{1,2}\s*DSML\s*\|{1,2}\s*parameter\s+name="([^"]+)"[^>]*>(.*?)</\s*\|{1,2}\s*DSML\s*\|{1,2}\s*parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in dsmlParamRegex.findAll(paramsBlock)) {
                val paramValue = pm.groupValues[2].trim()
                val cleanValue = paramValue.replace(Regex("<[^>]*>"), "")
                args.addProperty(pm.groupValues[1], cleanValue)
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        return result
    }

    private fun esc(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
