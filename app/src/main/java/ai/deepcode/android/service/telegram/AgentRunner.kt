package ai.deepcode.android.service.telegram

import ai.deepcode.android.data.remote.AIProvider
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.ToolCall
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import java.util.UUID

class AgentRunner(private val repository: DeepCodeRepository) {

    private data class CompletionResult(
        val text: String,
        val toolCalls: List<ToolCall>,
        val reasoning: String
    )

    private suspend fun streamComplete(
        provider: AIProvider,
        messages: List<Message>,
        model: String,
        tools: List<ai.deepcode.android.domain.model.Tool>?,
        apiKey: String,
        customBaseUrl: String?
    ): CompletionResult {
        val done = CompletableDeferred<CompletionResult>()
        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolCall>()

        try {
            provider.streamCompletion(
                messages = messages,
                model = model,
                tools = tools,
                apiKey = apiKey,
                customBaseUrl = customBaseUrl,
                onToken = { textBuilder.append(it) },
                onToolCall = { toolCalls.add(it) },
                onComplete = { reasoning ->
                    done.complete(CompletionResult(textBuilder.toString(), toolCalls.toList(), reasoning))
                },
                onError = { done.completeExceptionally(it) }
            )
        } catch (e: Exception) {
            if (!done.isCompleted) {
                done.completeExceptionally(e)
            }
        }

        return done.await()
    }

    suspend fun runAgentLoop(sessionId: String, userPrompt: String): String {
        val workingDir = repository.securePrefs.getSetting("default_project", repository.getDefaultProjectPath())

        repository.insertMessage(Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "user",
            content = userPrompt,
            timestamp = System.currentTimeMillis()
        ))

        val (provider, _, modelId, apiKey, customUrl) = resolveProvider()
        val declaredTools = repository.getDeclaredTools()

        val systemMsg = Message(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = "system",
            content = buildSystemPrompt(),
            timestamp = 0
        )

        val rawHistory = repository.getMessagesListForSession(sessionId)
        val maxTurns = repository.securePrefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8
        val trimmedHistory = if (maxTurns > 0) ai.deepcode.android.util.TokenSaver.trimHistory(rawHistory, maxTurns) else rawHistory
        var history = listOf(systemMsg) + trimmedHistory
        var loopCount = 0
        val maxLoops = 10
        val executedToolCalls = mutableListOf<String>()

        while (loopCount < maxLoops) {
            loopCount++

            val result = try {
                streamComplete(
                    provider = provider,
                    messages = history,
                    model = modelId,
                    tools = if (declaredTools.isNotEmpty()) declaredTools else null,
                    apiKey = apiKey,
                    customBaseUrl = customUrl
                )
            } catch (e: Exception) {
                AppLogger.e("AgentRunner", "Error in agent runner loop, using fallback", e)
                val reply = getLocalBasicReply(userPrompt)
                try {
                    repository.insertMessage(Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        content = reply,
                        timestamp = System.currentTimeMillis()
                    ))
                } catch (dbEx: Exception) {
                    AppLogger.e("AgentRunner", "Failed to save offline reply to DB", dbEx)
                }
                return reply
            }

            val dsmlPfx = """(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?"""
            val toolCallCleaner = Regex("""<${dsmlPfx}tool_calls?${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}tool_calls?${dsmlPfx}>|<${dsmlPfx}invoke${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}invoke${dsmlPfx}>|<${dsmlPfx}parameter${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}parameter${dsmlPfx}>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            var textToolCalls = if (result.toolCalls.isEmpty()) {
                val parsed = parseToolCallsFromText(result.text)
                if (parsed.isNotEmpty()) parsed else emptyList()
            } else {
                emptyList()
            }
            val activeToolCalls = if (textToolCalls.isNotEmpty()) textToolCalls else result.toolCalls

            if (activeToolCalls.isEmpty()) {
                val cleanedText = result.text.let { raw ->
                    val normalized = normalizeAscii(raw)
                    val dsmlPfx = """(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?"""
                    val cleaner = Regex("""<${dsmlPfx}tool_calls?${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}tool_calls?${dsmlPfx}>|<${dsmlPfx}invoke${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}invoke${dsmlPfx}>|<${dsmlPfx}parameter${dsmlPfx}[^>]*>.*?<${dsmlPfx}/${dsmlPfx}parameter${dsmlPfx}>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    cleaner.replace(normalized, "").trim().ifEmpty { raw.trim() }
                }
                val assistantMsg = Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = cleanedText,
                    timestamp = System.currentTimeMillis(),
                    toolCallsJson = null
                )
                repository.insertMessage(assistantMsg)
                return cleanedText
            }

            val assistantContent = if (textToolCalls.isNotEmpty()) {
                toolCallCleaner.replace(normalizeAscii(result.text), "").trim()
            } else {
                result.text
            }
            val gson = Gson()
            val tcArray = JsonArray()
            for (tc in activeToolCalls) {
                val obj = JsonObject()
                obj.addProperty("id", tc.id)
                obj.addProperty("name", tc.name)
                obj.addProperty("arguments", tc.arguments)
                tcArray.add(obj)
            }
            val toolCallsJson = tcArray.toString()

            repository.insertMessage(Message(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = assistantContent,
                timestamp = System.currentTimeMillis(),
                isToolCall = true,
                toolCallsJson = toolCallsJson
            ))

            for (tc in activeToolCalls) {
                val toolCallKey = "${tc.name}:${tc.arguments}"
                val occurrences = executedToolCalls.count { it == toolCallKey }

                val toolResult = if (occurrences >= 2) {
                    "Error: Loop detected. You have already executed ${tc.name} with these parameters twice. Do not repeat the same command. Either try a different command or output your final response now."
                } else {
                    executedToolCalls.add(toolCallKey)
                    try {
                        repository.executeTool(tc.name, tc.arguments, workingDir)
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                }

                repository.insertMessage(Message(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "tool",
                    content = toolResult,
                    timestamp = System.currentTimeMillis(),
                    toolCallsJson = tc.id,
                    toolResultsJson = toolResult
                ))
            }

            val rawHistory = repository.getMessagesListForSession(sessionId)
            val maxTurns = repository.securePrefs.getSetting("max_history_turns", "8").toIntOrNull() ?: 8
            val trimmedHistory = if (maxTurns > 0) ai.deepcode.android.util.TokenSaver.trimHistory(rawHistory, maxTurns) else rawHistory
            history = listOf(systemMsg) + trimmedHistory
        }

        return "I reached the maximum number of planning steps without a final answer. Please try rephrasing your request."
    }

    private fun buildSystemPrompt(): String {
        val prefs = repository.securePrefs
        val personaEnabled = prefs.getSetting("persona_enabled", "false") == "true"
        val customPersona = prefs.getSetting("custom_persona", "")
        val lang = prefs.getSetting("tts_voice", "en-US")
        val langName = when (lang) {
            "hi-IN" -> "Hindi"
            "es-ES" -> "Spanish"
            "fr-FR" -> "French"
            "de-DE" -> "German"
            "ja-JP" -> "Japanese"
            "ko-KR" -> "Korean"
            "zh-CN" -> "Chinese (Simplified)"
            "ar-SA" -> "Arabic"
            "pt-BR" -> "Portuguese (Brazil)"
            "ru-RU" -> "Russian"
            "it-IT" -> "Italian"
            "nl-NL" -> "Dutch"
            "tr-TR" -> "Turkish"
            else -> "English"
        }

        val telegramRules = """
Telegram-specific rules (apply these ON TOP of your persona):
- You are chatting on Telegram with Deep.
- IMPORTANT: You MUST respond in $langName. Always write your replies in $langName unless Deep specifically asks otherwise.
- By default, files are stored to Telegram Drive (cloud)

HARD RULES — YOU MUST OBEY:
1. When Deep asks you to create a file, document, or PDF, use write_file (for text files) or create_pdf (for PDFs) immediately. Do NOT output the content in your reply. Do NOT explain how to do it. Just create it and say "Done — [file:/path]".
2. Never show code, data, or file content to Deep. Always use write_file or create_pdf to write it, then include [file:/path] in your response to deliver it.
3. Never give step-by-step instructions or explain how to do something. Use your tools to do the work yourself.
4. For research tasks: search the web with web_search, fetch content with web_fetch, analyze and compile, then use create_pdf to produce the final document.
5. Always format tables using standard markdown pipe syntax (e.g. | Header 1 | Header 2 | followed by separator line | --- | --- |). Never format tables using spaces/tabs for alignment.
6. IMPORTANT: If your built-in function/tool calling mechanism is available, use it. If not, output tool calls in this exact format:
   <invoke name="tool_name">
   <parameter name="param1">value1</parameter>
   <parameter name="param2">value2</parameter>
   </invoke>
   Wrap multiple invocations inside <tool_calls>...</tool_calls>. Do NOT use any other XML format.
""".trimIndent()

        return if (personaEnabled && customPersona.isNotEmpty()) {
            "$customPersona\n\n$telegramRules"
        } else {
            """You are DeepCode, an Android AI assistant with file system access and shell capabilities. You have the Telegram Drive tool for cloud file storage.

You are chatting on Telegram. Use a friendly, conversational tone with plenty of emojis 🎮✨🔥 to match the casual chat environment.

IMPORTANT: You MUST respond in $langName. Always write your replies in $langName unless the user specifically asks otherwise.

$telegramRules"""
        }
    }

    private data class ResolvedProvider(
        val provider: AIProvider,
        val providerName: String,
        val modelId: String,
        val apiKey: String,
        val customUrl: String?
    )

    private fun resolveProvider(): ResolvedProvider {
        val prefs = repository.securePrefs
        val savedModelId = prefs.getSetting("agent_model", "")
        val savedProvider = prefs.getSetting("agent_provider", "")

        if (savedModelId.isNotEmpty() && savedProvider.isNotEmpty()) {
            val providerObj = AIProviderFactory.providers.firstOrNull { it.name == savedProvider }
            if (providerObj != null) {
                val modelObj = providerObj.models.firstOrNull { it.id == savedModelId }
                if (modelObj != null) {
                    val keyName = getApiKeyName(providerObj.name)
                    val apiKey = prefs.getApiKey(keyName)
                    val urlKey = "url_$keyName"
                    val customUrl = prefs.getSetting(urlKey, "")
                    return ResolvedProvider(
                        provider = providerObj,
                        providerName = providerObj.name,
                        modelId = modelObj.id,
                        apiKey = apiKey,
                        customUrl = customUrl.ifEmpty { null }
                    )
                }
            }
        }

        // Fallback: loop to find the first available free or configured provider
        for (p in AIProviderFactory.providers) {
            val keyName = getApiKeyName(p.name)
            val key = prefs.getApiKey(keyName)
            val urlKey = "url_$keyName"
            val url = prefs.getSetting(urlKey, "")

            if (p.isFree || key.isNotEmpty()) {
                val modelId = p.models.firstOrNull()?.id ?: ""
                return ResolvedProvider(p, p.name, modelId, key, url.ifEmpty { null })
            }
        }

        val fallback = AIProviderFactory.providers.firstOrNull()
            ?: return ResolvedProvider(
                ai.deepcode.android.data.remote.ZenProvider(), "Zen AI",
                ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE, "", null
            )
        val fallbackModelId = fallback.models.firstOrNull()?.id ?: ai.deepcode.android.data.remote.ZenModels.DEFAULT_FREE
        return ResolvedProvider(fallback, fallback.name, fallbackModelId, "", null)
    }

    private fun getApiKeyName(providerName: String): String {
        return when (providerName) {
            "Zen AI", "Zen", "Zen (Free)" -> "zen"
            "Google Gemini" -> "gemini"
            "Groq" -> "groq"
            "Cerebrus" -> "cerebrus"
            "OpenRouter" -> "openrouter"
            "OpenAI" -> "openai"
            "Anthropic" -> "anthropic"
            "Mistral AI" -> "mistral"
            "Ollama Cloud" -> "ollama"
            "Agent Router" -> "agentrouter"
            "GMI Cloud" -> "gmi"
            else -> ""
        }
    }

    private fun getLocalBasicReply(prompt: String): String {
        val lower = prompt.trim().lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "Hello! I am DeepCode's offline assistant. I currently don't have internet access or valid AI API keys configuration, but I can help you with local tasks."
            }
            lower.contains("help") || lower.contains("info") || lower.contains("capabilities") -> {
                "I am operating in offline mode. You can check settings, toggle agents, or configure API keys under Settings to restore full AI assistant features. Offline, I can run basic commands or view local integrations."
            }
            lower.contains("time") || lower.contains("date") -> {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                "The current local time is: ${sdf.format(java.util.Date())}"
            }
            lower.contains("weather") -> {
                "Weather information requires an active internet connection."
            }
            lower.contains("name") || lower.contains("who") -> {
                "I am the DeepCode assistant (running in local offline fallback mode)."
            }
            else -> {
                "I'm currently unable to reach the AI service (please check your internet connection or API keys in Settings). Offline fallback response for: \"$prompt\""
            }
        }
    }

    private fun normalizeAscii(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.code
            when {
                c in 0xFF01..0xFF5E -> sb.append((c - 0xFEE0).toChar())
                c == 0xFF3F -> sb.append('_')
                c == 0x3000 -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun parseToolCallsFromText(rawText: String): List<ToolCall> {
        val text = normalizeAscii(rawText)
        val lower = text.lowercase()
        if (!lower.contains("tool_call") && !lower.contains("tool_calls") && !lower.contains("tool__calls") && !lower.contains("dsml")) return emptyList()
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
        if (result.isNotEmpty()) return result

        val pipeRegex = Regex("<tool_calls?>\\s*(\\w+)\\s*\\[([^\\]]*)\\]\\s*</tool_calls?>", RegexOption.IGNORE_CASE)
        for (m in pipeRegex.findAll(text)) {
            val name = m.groupValues[1].replace("__", "_")
            val argsText = m.groupValues[2].trim()
            val args = if (argsText.startsWith("{") && argsText.endsWith("}")) {
                try { com.google.gson.JsonParser.parseString(argsText).asJsonObject } catch (_: Exception) { com.google.gson.JsonObject() }
            } else {
                val obj = com.google.gson.JsonObject()
                val parts = argsText.split("|")
                for (p in parts) {
                    val ci = p.indexOf(':')
                    if (ci > 0) obj.addProperty(p.substring(0, ci).trim(), p.substring(ci + 1).trim())
                }
                obj
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        if (result.isNotEmpty()) return result

        val invokeRegex = Regex("<invoke\\s+name=\"(\\w+)\"[^>]*>(.*?)</invoke>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in invokeRegex.findAll(text)) {
            val name = m.groupValues[1].replace("__", "_")
            val paramsBlock = m.groupValues[2]
            val args = com.google.gson.JsonObject()
            val paramRegex = Regex("""<parameter\s+name="([^"]+)"[^>]*>(.*?)</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in paramRegex.findAll(paramsBlock)) {
                args.addProperty(pm.groupValues[1], pm.groupValues[2].trim())
            }
            result.add(ToolCall("tc_${result.size}", name, args.toString()))
        }
        return result
    }
}
