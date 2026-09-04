package ai.deepcode.android.ui.agents

import android.content.Context
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.util.AppLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AgentRuntime(private val context: Context) {
    private val prefs = EncryptedPrefs.getInstance(context)
    private val toolExecutor = ToolExecutor(context)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .proxySelector(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI?): List<java.net.Proxy> {
                val activeProxy = ai.deepcode.android.util.VpnManager.getActiveProxy()
                return if (activeProxy != null) {
                    listOf(activeProxy)
                } else {
                    listOf(java.net.Proxy.NO_PROXY)
                }
            }

            override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                ai.deepcode.android.util.VpnManager.handleProxyFailure()
            }
        })
        .proxyAuthenticator { _, response ->
            val credential = ai.deepcode.android.util.VpnManager.getProxyCredentials()
            if (credential != null) {
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            } else {
                null
            }
        }
        .build()

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val workingDir: String
        get() = prefs.getSetting("default_project", "/storage/emulated/0")

    suspend fun executeAgent(
        agent: AgentEntity,
        userInput: String,
        sessionContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = buildSystemPrompt(agent, sessionContext)
        val messages = mutableListOf<JsonObject>()

        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", systemPrompt)
        })
        messages.add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", userInput)
        })

        val (provider, modelId, apiKey) = resolveProvider(agent)
        var result = ""
        val maxIter = if (agent.maxIterations < 1) 15 else agent.maxIterations
        var loopCount = 0
        val executedToolCalls = mutableListOf<String>()

        val toolCallCleaner = Regex("""<(?:tool_calls?|TOOL_CALLS?|Tool_Calls?)[^>]*>.*?</(?:tool_calls?|TOOL_CALLS?|Tool_Calls?)>|<invoke[^>]*>.*?</invoke>|<parameter[^>]*>.*?</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        while (loopCount < maxIter) {
            coroutineContext.ensureActive()
            loopCount++
            val responseText = callLLM(provider, modelId, apiKey, messages)
            if (responseText == null) {
                result = "Error: LLM call failed"
                break
            }

            val cleanText = toolCallCleaner.replace(responseText, "").trim()

            val toolCall = extractToolCall(responseText)
            if (toolCall == null) {
                result = cleanText
                break
            }

            val (toolName, toolArgs) = toolCall
            val toolCallKey = "$toolName:${toolArgs.toString()}"
            val occurrences = executedToolCalls.count { it == toolCallKey }

            val toolResult = if (occurrences >= 2) {
                "Error: Loop detected. You have already executed $toolName with these parameters twice. Do not repeat the same command. Either try a different command or output your final response now."
            } else {
                executedToolCalls.add(toolCallKey)
                try {
                    val raw = toolExecutor.executeTool(toolName, toolArgs.toString(), workingDir, false)
                    if (raw.startsWith("Error", ignoreCase = true)) {
                        AppLogger.w("AgentRuntime", "Tool '$toolName' returned error: ${raw.take(150)}")
                    }
                    raw
                } catch (e: Exception) {
                    AppLogger.e("AgentRuntime", "Tool '$toolName' threw exception", e)
                    "Error: ${e.message}"
                }
            }

            messages.add(JsonObject().apply {
                addProperty("role", "assistant")
                addProperty("content", cleanText)
            })
            messages.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", "Tool execution result for $toolName:\n$toolResult")
            })
        }

        result
    }

    private val toolCallingModels = setOf(
        "deepseek-v4-flash-free", "deepseek-chat", "deepseek-v3", "deepseek-r1",
        "mimo-v2.5-free", "nemotron-3-ultra-free", "nemotron-3.5-lightning-free",
        "llama-3.3-70b-versatile", "llama-3.1-8b-instant",
        "gpt-4o", "gpt-4o-mini", "gpt-4-turbo",
        "claude-3-5-sonnet", "claude-3-haiku", "claude-3-sonnet",
        "gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash",
        "mistral-large", "codestral",
        "qwen-2.5-72b", "qwen-2.5-coder"
    )

    private fun resolveProvider(agent: AgentEntity): Triple<String, String, String> {
        val providers = AIProviderFactory.providers
        if (providers.isEmpty()) throw IllegalStateException("No AI providers configured")

        val configuredProviderName = prefs.getSetting("agent_provider", "Zen AI")
        val configuredModel = prefs.getSetting("agent_model", "deepseek-v4-flash-free")

        fun resolveModelAndKey(provider: ai.deepcode.android.data.remote.AIProvider): Pair<String, String> {
            // Prefer the user's configured model if it supports tool-calling
            if (toolCallingModels.contains(configuredModel) &&
                provider.models.any { it.id == configuredModel }) {
                return configuredModel to getApiKeyForProvider(provider.name)
            }
            // Otherwise, pick first tool-calling model from this provider
            val tcModel = provider.models.firstOrNull { toolCallingModels.contains(it.id) }
            if (tcModel != null) return tcModel.id to getApiKeyForProvider(provider.name)
            // Fallback to first model
            val fallback = provider.models.firstOrNull()?.id ?: "default"
            return fallback to getApiKeyForProvider(provider.name)
        }

        // 1. Try the user's configured provider (must be OpenAI-compatible)
        val configuredProvider = providers.firstOrNull { it.name == configuredProviderName && isCompatibleProvider(it.name) }
        if (configuredProvider != null) {
            val url = getCompatibleBaseUrl(configuredProvider.name)
            val (model, key) = resolveModelAndKey(configuredProvider)
            return Triple(url, model, key)
        }

        // 2. Try name-similar provider that is compatible
        val similarProvider = providers.firstOrNull {
            isCompatibleProvider(it.name) && (
                configuredProviderName.contains(it.name, ignoreCase = true) ||
                it.name.contains(configuredProviderName, ignoreCase = true)
            )
        }
        if (similarProvider != null) {
            val url = getCompatibleBaseUrl(similarProvider.name)
            val (model, key) = resolveModelAndKey(similarProvider)
            return Triple(url, model, key)
        }

        // 3. Fallback to first compatible provider with key or free
        for (p in providers) {
            if (!isCompatibleProvider(p.name)) continue
            val key = getApiKeyForProvider(p.name)
            if (key.isNotEmpty() || p.isFree) {
                val url = getCompatibleBaseUrl(p.name)
                val (model, _) = resolveModelAndKey(p)
                return Triple(url, model, key)
            }
        }

        // 4. Last resort: first compatible provider
        val last = providers.firstOrNull { isCompatibleProvider(it.name) } ?: providers.first()
        val url = getCompatibleBaseUrl(last.name)
        val (model, key) = resolveModelAndKey(last)
        return Triple(url, model, key)
    }

    private fun isCompatibleProvider(name: String): Boolean = when {
        name.contains("Zen", ignoreCase = true) -> true
        name.contains("Groq", ignoreCase = true) -> true
        name.contains("Cerebrus", ignoreCase = true) -> true
        name.contains("OpenRouter", ignoreCase = true) -> true
        name.contains("Omniroute", ignoreCase = true) -> true
        name.contains("OpenAI", ignoreCase = true) -> true
        name.contains("Mistral", ignoreCase = true) -> true
        name.contains("Ollama", ignoreCase = true) -> true
        name.contains("Agent Router", ignoreCase = true) -> true
        else -> false
    }

    private fun getCompatibleBaseUrl(name: String): String {
        return when {
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
    }

    private fun getApiKeyForProvider(name: String): String {
        return when {
            name.contains("Zen", ignoreCase = true) -> prefs.getApiKey("zen")
            name.contains("Gemini", ignoreCase = true) -> prefs.getApiKey("gemini")
            name.contains("Groq", ignoreCase = true) -> prefs.getApiKey("groq")
            name.contains("Cerebrus", ignoreCase = true) -> prefs.getApiKey("cerebrus")
            name.contains("OpenRouter", ignoreCase = true) -> prefs.getApiKey("openrouter")
            name.contains("Omniroute", ignoreCase = true) -> prefs.getApiKey("omniroute")
            name.contains("OpenAI", ignoreCase = true) -> prefs.getApiKey("openai")
            name.contains("Anthropic", ignoreCase = true) -> prefs.getApiKey("anthropic")
            name.contains("Mistral", ignoreCase = true) -> prefs.getApiKey("mistral")
            name.contains("Ollama", ignoreCase = true) -> prefs.getApiKey("ollama")
            name.contains("Agent Router", ignoreCase = true) -> prefs.getApiKey("agentrouter")
            name.contains("GMI Cloud", ignoreCase = true) -> prefs.getApiKey("gmi")
            else -> ""
        }
    }

    private fun buildSystemPrompt(agent: AgentEntity, sessionContext: String): String {
        val personaEnabled = prefs.getSetting("persona_enabled", "false") == "true"
        val customPersona = prefs.getSetting("custom_persona", "")
        return buildString {
            appendLine("You are ${agent.displayName}.")
            if (agent.description.isNotBlank()) {
                appendLine()
                appendLine(agent.description)
            }
            appendLine()
            appendLine(agent.systemPrompt)

            if (personaEnabled && customPersona.isNotEmpty()) {
                appendLine()
                appendLine("## Custom Persona Rules")
                appendLine(customPersona)
            }

            val toolList = agent.tools.split(",").filter { it.isNotBlank() }
            if (toolList.isNotEmpty()) {
                appendLine()
                appendLine("## Available Tools")
                toolList.forEach { appendLine("- $it") }
                appendLine()
            appendLine("## Tool Calling Format")
            appendLine("If you want to use a tool, you MUST output a tool call.")
            appendLine()
            appendLine("For simple values (single-line, no special chars):")
            appendLine("<tool_call>toolName [arg1:val1 | arg2:val2]</tool_call>")
            appendLine("Example: <tool_call>web_search [query:Python tutorials]</tool_call>")
            appendLine()
            appendLine("For complex values (multi-line code, special characters):")
            appendLine("<tool_call>toolName [{\"arg1\":\"value1\",\"arg2\":\"value2\"}]</tool_call>")
            appendLine("Example: <tool_call>file_write [{\"path\":\"/tmp/script.py\",\"content\":\"from reportlab...\\nprint('ok')\"}]</tool_call>")
            appendLine()
            appendLine("You may also use <tool_calls> (plural) with XML-style invoke blocks:")
            appendLine("<tool_calls><invoke name=\"toolName\"><parameter name=\"key\">value</parameter></invoke></tool_calls>")
            appendLine("Do NOT use markdown code blocks or generic XML tags. Only call one tool at a time.")
            }

            if (sessionContext.isNotBlank()) {
                appendLine()
                appendLine("## Context")
                appendLine(sessionContext)
            }
        }
    }

    private suspend fun callLLM(
        baseUrl: String,
        modelId: String,
        apiKey: String,
        messages: List<JsonObject>
    ): String? {
        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl
            else "${baseUrl.trimEnd('/')}/chat/completions"

        val requestBody = JsonObject().apply {
            addProperty("model", modelId)
            add("messages", gson.toJsonTree(messages))
            addProperty("temperature", 0.4)
            addProperty("max_tokens", 8192)
        }

        val bodyStr = requestBody.toString()
        val token = apiKey.ifEmpty { "public" }

        // Try each transport and return the first successful response
        for ((httpClient, label) in listOf(httpClient to "proxied", directClient to "direct")) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-OpenCode-Client", "android/1.0.0")
                    .addHeader("Authorization", "Bearer $token")
                    .post(bodyStr.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                val respBody = response.body?.string() ?: ""
                response.close()

                if (response.isSuccessful) {
                    val parsed = parseLlmResponse(respBody)
                    if (parsed != null) return parsed
                } else {
                    AppLogger.w("AgentRuntime", "$label LLM call: ${response.code} - ${respBody.take(200)}")
                }
            } catch (e: Exception) {
                AppLogger.w("AgentRuntime", "$label LLM call failed: ${e.message}")
            }
        }

        // Fallback: HttpURLConnection (bypasses OkHttp proxy chain entirely)
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("X-DeepCode-Client", "android/1.0.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val writer = java.io.OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(bodyStr)
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val respBody = conn.inputStream.bufferedReader().readText()
                val parsed = parseLlmResponse(respBody)
                if (parsed != null) return parsed
            } else {
                val errBody = try { conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: "" } catch (_: Exception) { "" }
                AppLogger.w("AgentRuntime", "HttpURLConnection LLM fallback failed: $responseCode - $errBody")
            }
        } catch (e: Exception) {
            AppLogger.w("AgentRuntime", "HttpURLConnection LLM fallback threw: ${e.message}")
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }

        // Try with minimal payload (no system prompt, just last user message)
        try {
            val lastUserMsg = messages.lastOrNull { it.get("role")?.asString == "user" }?.get("content")?.asString ?: "hi"
            val minimalPayload = JsonObject().apply {
                addProperty("model", modelId)
                add("messages", gson.toJsonTree(listOf(
                    JsonObject().apply { addProperty("role", "user"); addProperty("content", lastUserMsg) }
                )))
                addProperty("temperature", 0.4)
                addProperty("max_tokens", 8192)
            }
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("X-DeepCode-Client", "android/1.0.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000

            val writer = java.io.OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(minimalPayload.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode == 200) {
                val respBody = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val parsed = parseLlmResponse(respBody)
                if (parsed != null) return parsed
            }
            conn.disconnect()
        } catch (e: Exception) {
            AppLogger.w("AgentRuntime", "Minimal payload LLM fallback threw: ${e.message}")
        }

        return null
    }

    private fun parseLlmResponse(body: String): String? {
        return try {
            val json = JsonParser.parseString(body).asJsonObject
            val msgObj = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
            var msgContent = msgObj?.get("content")?.asString ?: ""
            val reasoningContent = msgObj?.get("reasoning_content")?.asString ?: ""

            if (msgContent.isBlank() && reasoningContent.isNotBlank()) {
                msgContent = reasoningContent
            }

            if (msgContent.isBlank()) {
                val finishReason = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.get("finish_reason")?.asString ?: "none"
                AppLogger.w("AgentRuntime", "LLM returned empty content (finish_reason=$finishReason)")
                null
            } else {
                msgContent
            }
        } catch (e: Exception) {
            AppLogger.e("AgentRuntime", "Failed to parse LLM response: ${body.take(200)}", e)
            null
        }
    }

    private fun extractToolCall(text: String): Pair<String, JsonObject>? {
        val lower = text.lowercase()
        if (!lower.contains("<tool_call>") && !lower.contains("<tool_calls>")) return null

        // Format 1: pipe-delimited — <tool_call> name [key1:val1 | key2:val2] </tool_call>
        val pipeRegex = Regex(
            "<tool_calls?>\\s*(\\w+)\\s*\\[([^\\]]*)\\]\\s*</tool_calls?>",
            RegexOption.IGNORE_CASE
        )
        val pipeMatch = pipeRegex.find(text)
        if (pipeMatch != null) {
            val toolName = pipeMatch.groupValues[1]
            val argsText = pipeMatch.groupValues[2].trim()
            return Pair(toolName, parseArgs(argsText))
        }

        // Format 2: JSON — <tool_call> name {"key1":"val1","key2":"val2"} </tool_call>
        val jsonRegex = Regex(
            "<tool_calls?>\\s*(\\w+)\\s*\\{([^}]+)\\}\\s*</tool_calls?>",
            RegexOption.IGNORE_CASE
        )
        val jsonMatch = jsonRegex.find(text)
        if (jsonMatch != null) {
            val toolName = jsonMatch.groupValues[1]
            val argsText = "{" + jsonMatch.groupValues[2] + "}"
            try {
                val parsed = JsonParser.parseString(argsText).asJsonObject
                return Pair(toolName, parsed)
            } catch (_: Exception) { }
        }

        // Format 3: XML — extract <invoke> block directly
        val xmlRegex = Regex(
            "<invoke\\s+name=\"(\\w+)\">(.*?)</invoke>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val xmlMatch = xmlRegex.find(text)
        if (xmlMatch != null) {
            val toolName = xmlMatch.groupValues[1]
            val paramsBlock = xmlMatch.groupValues[2]
            val args = JsonObject()
            val paramRegex = Regex("""<parameter\s+name="([^"]+)"[^>]*>(.*?)</parameter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (pm in paramRegex.findAll(paramsBlock)) {
                args.addProperty(pm.groupValues[1], pm.groupValues[2].trim())
            }
            return Pair(toolName, args)
        }

        return null
    }

    private fun parseArgs(argsText: String): JsonObject {
        val args = JsonObject()
        if (argsText.isBlank()) return args
        if (argsText.startsWith("{") && argsText.endsWith("}")) {
            try {
                return JsonParser.parseString(argsText).asJsonObject
            } catch (_: Exception) { }
        }
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        for (ch in argsText) {
            when {
                ch == '"' || ch == '\'' -> {
                    if (inQuote && ch == quoteChar) inQuote = false
                    else if (!inQuote) { inQuote = true; quoteChar = ch }
                    current.append(ch)
                }
                ch == '|' && !inQuote -> {
                    val trimmed = current.toString().trim()
                    if (trimmed.isNotEmpty()) parts.add(trimmed)
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        val last = current.toString().trim()
        if (last.isNotEmpty()) parts.add(last)
        for ((idx, arg) in parts.withIndex()) {
            val colonIdx = arg.indexOf(':')
            if (colonIdx > 0) {
                val key = arg.substring(0, colonIdx).trim()
                val value = arg.substring(colonIdx + 1).trim()
                args.addProperty(key, value)
            } else {
                args.addProperty("arg$idx", arg)
            }
        }
        return args
    }

}
