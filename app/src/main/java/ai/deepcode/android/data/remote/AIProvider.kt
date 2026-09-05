package ai.deepcode.android.data.remote

import com.google.gson.Gson
import okio.buffer
import okio.source
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ai.deepcode.android.data.local.TurnTokenUsage
import ai.deepcode.android.domain.model.AIModel
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.domain.model.Tool
import ai.deepcode.android.domain.model.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.DeepCodeOkHttpInterceptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File


fun shouldIncludeTools(messages: List<Message>, tools: List<Tool>?): Boolean {
    if (tools.isNullOrEmpty()) return false
    val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content?.lowercase() ?: ""
    val toolTriggers = listOf(
        "search", "google", "web", "fetch", "url", "http", "image", "video", "audio", "tts", "speak", "voice",
        "pdf", "file", "read", "write", "list", "directory", "dir", "grep", "command", "exec", "terminal", "run",
        "qr", "csv", "zip", "json", "hash", "base64", "calendar", "contact", "vcard",
        "schedule", "automation", "automate", "cron", "daily", "weekly", "hourly", "recurring", "remind", "reminder", "task", "tasks"
    )
    return toolTriggers.any { lastUserMsg.contains(it) } || messages.any { it.role == "tool" || it.isToolCall }
}
interface AIProvider {
    val name: String
    val isFree: Boolean
    val models: List<AIModel>
    suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)? = null
    )
}



/**
 * Thrown when an API returns HTTP 429 or indicates quota/rate-limit exhaustion.
 * Caught by ChatViewModel to trigger silent key rotation.
 */
class RateLimitException(
    val providerName: String,
    val httpCode: Int,
    message: String
) : Exception(message)

data class OpenAIProviderConfig(
    val name: String,
    val baseUrl: String,
    val models: List<AIModel>,
    val isFree: Boolean = false
)

class GenericOpenAIProvider(private val config: OpenAIProviderConfig) : AIProvider {
    override val name = config.name
    override val isFree = config.isFree
    override val models = config.models

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey,
            baseUrl = resolveBaseUrl(customBaseUrl, config.baseUrl),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

private fun genProvider(name: String, baseUrl: String, modelIds: List<Pair<String, String>>, isFree: Boolean = false): OpenAIProviderConfig {
    return OpenAIProviderConfig(
        name = name,
        baseUrl = baseUrl,
        models = modelIds.map { (id, label) ->
            AIModel(id, label, name, isFree, "", if (isFree) "Free" else "Paid")
        },
        isFree = isFree
    )
}

val OPENAI_PROVIDERS = listOf(
    genProvider("OpenAI", "https://api.openai.com/v1", listOf(
        "gpt-6-astra" to "GPT 6 Astra", "gpt-5.6-sol" to "GPT 5.6 Sol", "gpt-4o" to "GPT-4o", "gpt-4o-mini" to "GPT-4o Mini", "o1" to "OpenAI o1", "o1-mini" to "OpenAI o1 Mini",
        "o3-mini" to "OpenAI o3 Mini", "gpt-4.5-preview" to "GPT-4.5 Preview", "gpt-4-turbo" to "GPT-4 Turbo", "gpt-3.5-turbo" to "GPT-3.5 Turbo"
    )),
    genProvider("Anthropic", "https://api.anthropic.com/v1", listOf(
        "claude-fable-5.1" to "Claude Fable 5.1", "claude-opus-5" to "Claude Opus 5", "claude-sonnet-5" to "Claude Sonnet 5",
        "claude-3-7-sonnet-latest" to "Claude 3.7 Sonnet", "claude-3-5-sonnet-latest" to "Claude 3.5 Sonnet",
        "claude-3-5-haiku-latest" to "Claude 3.5 Haiku", "claude-3-opus-latest" to "Claude 3 Opus",
        "claude-4.5-sonnet" to "Claude Sonnet 4.5", "claude-4.5-opus" to "Claude Opus 4.5"
    )),
    genProvider("Groq", "https://api.groq.com/openai/v1", listOf(
        "llama-3.3-70b-versatile" to "Llama 3.3 70B Versatile",
        "llama-3.1-8b-instant" to "Llama 3.1 8B Instant",
        "openai/gpt-oss-120b" to "GPT OSS 120B",
        "openai/gpt-oss-20b" to "GPT OSS 20B",
        "qwen/qwen3.6-27b" to "Qwen 3.6 27B",
        "deepseek-r1-distill-llama-70b" to "DeepSeek R1 Distill 70B",
        "groq/compound" to "Groq Compound",
        "gemma2-9b-it" to "Gemma 2 9B"
    )),
    genProvider("Mistral AI", "https://api.mistral.ai/v1", listOf(
        "mistral-large-latest" to "Mistral Large", "mistral-small-latest" to "Mistral Small",
        "codestral-latest" to "Codestral", "pixtral-12b-2409" to "Pixtral 12B", "open-mistral-nemo" to "Mistral NeMo"
    )),
    genProvider("DeepSeek", "https://api.deepseek.com/v1", listOf(
        "deepseek-v4-flash" to "DeepSeek V4 Flash", "deepseek-v4-pro" to "DeepSeek V4 Pro",
        "deepseek-chat" to "DeepSeek V3 (Chat)", "deepseek-reasoner" to "DeepSeek R1 (Reasoner)", "deepseek-coder" to "DeepSeek Coder"
    )),
    genProvider("OpenRouter", "https://openrouter.ai/api/v1", listOf(
        "deepseek/deepseek-r1:free" to "DeepSeek R1 (Free)", "deepseek/deepseek-chat:free" to "DeepSeek V3 (Free)",
        "meta-llama/llama-3.3-70b-instruct:free" to "Llama 3.3 70B (Free)", "meta-llama/llama-3.1-8b-instruct:free" to "Llama 3.1 8B (Free)",
        "google/gemma-2-9b-it:free" to "Gemma 2 9B (Free)", "qwen/qwen-2.5-72b-instruct:free" to "Qwen 2.5 72B (Free)",
        "openai/gpt-4o" to "GPT-4o", "openai/gpt-4o-mini" to "GPT-4o Mini", "anthropic/claude-3.5-sonnet" to "Claude 3.5 Sonnet"
    )),
    genProvider("Together AI", "https://api.together.xyz/v1", listOf(
        "meta-llama/Llama-3.3-70B-Instruct-Turbo" to "Llama 3.3 70B Turbo", "meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo" to "Llama 3.1 8B Turbo",
        "deepseek-ai/DeepSeek-V3" to "DeepSeek V3", "deepseek-ai/DeepSeek-R1" to "DeepSeek R1",
        "Qwen/Qwen2.5-Coder-32B-Instruct" to "Qwen 2.5 Coder 32B", "mistralai/Mixtral-8x22B-Instruct-v0.1" to "Mixtral 8x22B"
    )),
    genProvider("Perplexity", "https://api.perplexity.ai", listOf(
        "sonar-pro" to "Sonar Pro", "sonar" to "Sonar", "sonar-deep-research" to "Sonar Deep Research",
        "sonar-reasoning-pro" to "Sonar Reasoning Pro", "sonar-reasoning" to "Sonar Reasoning"
    )),
    genProvider("xAI", "https://api.x.ai/v1", listOf(
        "grok-2" to "Grok 2", "grok-2-mini" to "Grok 2 Mini", "grok-2-vision" to "Grok 2 Vision", "grok-beta" to "Grok Beta"
    )),
    genProvider("Cohere", "https://api.cohere.com/v1", listOf(
        "command-r-plus" to "Command R+", "command-r" to "Command R", "command-light" to "Command Light", "command-a" to "Command A"
    )),
    genProvider("DeepInfra", "https://api.deepinfra.com/v1/openai", listOf(
        "deepseek-ai/DeepSeek-V3" to "DeepSeek V3", "deepseek-ai/DeepSeek-R1" to "DeepSeek R1",
        "meta-llama/Llama-3.3-70B-Instruct" to "Llama 3.3 70B", "Qwen/Qwen2.5-72B-Instruct" to "Qwen 2.5 72B",
        "Qwen/Qwen2.5-Coder-32B-Instruct" to "Qwen 2.5 Coder 32B"
    )),
    genProvider("Fireworks AI", "https://api.fireworks.ai/inference/v1", listOf(
        "accounts/fireworks/models/llama-v3p3-70b-instruct" to "Llama 3.3 70B", "accounts/fireworks/models/deepseek-v3" to "DeepSeek V3",
        "accounts/fireworks/models/deepseek-r1" to "DeepSeek R1", "accounts/fireworks/models/qwen2p5-coder-32b-instruct" to "Qwen 2.5 Coder"
    )),
    genProvider("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", listOf(
        "meta/llama-3.3-70b-instruct" to "Llama 3.3 70B", "deepseek-ai/deepseek-r1" to "DeepSeek R1",
        "nvidia/llama-3.3-nemotron-super-49b-v1" to "Nemotron Super 49B", "qwen/qwen2.5-coder-32b-instruct" to "Qwen 2.5 Coder 32B"
    )),
    genProvider("SambaNova", "https://api.sambanova.ai/v1", listOf(
        "Meta-Llama-3.3-70B-Instruct" to "Llama 3.3 70B", "DeepSeek-R1" to "DeepSeek R1",
        "DeepSeek-V3" to "DeepSeek V3", "Qwen2.5-Coder-32B-Instruct" to "Qwen 2.5 Coder"
    )),
    genProvider("Cerebrus", "https://api.cerebrus.com/v1", listOf(
        "llama-3.3-70b" to "Llama 3.3 70B", "llama3.1-8b" to "Llama 3.1 8B", "qwen2.5-72b" to "Qwen 2.5 72B"
    )),
    genProvider("Hyperbolic", "https://api.hyperbolic.xyz/v1", listOf(
        "meta-llama/Meta-Llama-3.3-70B-Instruct" to "Llama 3.3 70B", "deepseek-ai/DeepSeek-R1" to "DeepSeek R1",
        "deepseek-ai/DeepSeek-V3" to "DeepSeek V3", "Qwen/Qwen2.5-Coder-32B-Instruct" to "Qwen 2.5 Coder"
    )),
    genProvider("GitHub Models", "https://models.inference.ai.azure.com", listOf(
        "gpt-4o" to "GPT-4o", "gpt-4o-mini" to "GPT-4o Mini", "DeepSeek-R1" to "DeepSeek R1",
        "Phi-3.5-mini-instruct" to "Phi 3.5 Mini", "Meta-Llama-3.1-70B-Instruct" to "Llama 3.1 70B"
    )),
    genProvider("Novita AI", "https://api.novita.ai/v1", listOf(
        "deepseek/deepseek-r1" to "DeepSeek R1", "deepseek/deepseek-v3" to "DeepSeek V3",
        "meta-llama/llama-3.3-70b-instruct" to "Llama 3.3 70B"
    )),
    genProvider("SiliconFlow", "https://api.siliconflow.cn/v1", listOf(
        "deepseek-ai/DeepSeek-V3" to "DeepSeek V3", "deepseek-ai/DeepSeek-R1" to "DeepSeek R1",
        "Qwen/Qwen2.5-72B-Instruct" to "Qwen 2.5 72B", "Qwen/Qwen2.5-Coder-32B-Instruct" to "Qwen 2.5 Coder"
    )),
    genProvider("Agent Router", "https://agentrouter.org/v1", listOf(
        "gpt-5.6-sol" to "GPT 5.6 Sol", "claude-opus-4-8" to "Claude Opus 4.8",
        "claude-opus-5" to "Claude Opus 5", "claude-opus-4-7" to "Claude Opus 4.7",
        "claude-opus-4-6" to "Claude Opus 4.6", "glm-5.2" to "GLM 5.2", "gpt-5.5" to "GPT 5.5"
    )),
    genProvider("GMI Cloud", "https://api.gmi-serving.com/v1", listOf(
        "Qwen/Qwen3.8-Flash" to "Qwen 3.8 Flash", "deepseek-ai/DeepSeek-V4-Flash" to "DeepSeek V4 Flash",
        "google/gemini-3.8-flash" to "Gemini 3.8 Flash", "moonshotai/kimi-k3" to "Kimi K3",
        "zai-org/GLM-5.3-Flash" to "GLM 5.3 Flash", "openai/gpt-5.4" to "GPT 5.4",
        "anthropic/claude-sonnet-4.6" to "Claude Sonnet 4.6", "MiniMaxAI/MiniMax-M3" to "MiniMax M3",
        "MiniMax-M3" to "MiniMax M3", "minimax-m3" to "MiniMax M3",
        "MiniMaxAI/MiniMax-M2.7" to "MiniMax M2.7",
        "nvidia/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-BF16" to "Nemotron 3.5 Lightning"
    )),
)

object RateLimitTracker {
    private val cooldowns = ConcurrentHashMap<String, Long>()

    fun recordRateLimit(providerName: String, backoffMs: Long = 60_000) {
        cooldowns[providerName] = System.currentTimeMillis() + backoffMs
    }

    fun isInCooldown(providerName: String): Boolean {
        val expiry = cooldowns[providerName] ?: return false
        if (System.currentTimeMillis() > expiry) {
            cooldowns.remove(providerName)
            return false
        }
        return true
    }

    fun getAvailableProviders(providers: List<AIProvider>): List<AIProvider> {
        return providers.filterNot { isInCooldown(it.name) }
    }
}

fun selectProvider(providers: List<AIProvider>, preferredName: String, apiKeyLookup: (String) -> String): AIProvider? {
    val available = RateLimitTracker.getAvailableProviders(providers)
    
    // 1. Try preferred provider if it has a key (or doesn't need one)
    val preferred = available.find { it.name == preferredName }
    if (preferred != null && (preferred.isFree || apiKeyLookup(preferred.name).isNotEmpty())) {
        return preferred
    }

    // 2. Try any provider that has an API key configured
    val withKey = available.find { !it.isFree && apiKeyLookup(it.name).isNotEmpty() }
    if (withKey != null) {
        return withKey
    }

    // 3. Fallback to any free provider
    return available.find { it.isFree }
}

class AIProviderFactory {
    companion object {
        val providers = listOf(
            ZenProvider(),
            GeminiProvider(),
            GroqProvider(),
            CerebrusProvider(),
            OpenRouterProvider(),
            OmnirouteProvider(),
            OpenAIProvider(),
            AnthropicProvider(),
            MistralProvider(),
            OllamaCloudProvider(),
            AntigravityProvider(),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "DeepSeek" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Together AI" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Perplexity" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "xAI" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Cohere" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "DeepInfra" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Fireworks AI" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "NVIDIA NIM" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "SambaNova" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Hyperbolic" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "GitHub Models" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "Agent Router" }!!),
            GenericOpenAIProvider(OPENAI_PROVIDERS.find { it.name == "GMI Cloud" }!!),
        )
    }
}


class KeepAliveSocketFactory(private val delegate: javax.net.SocketFactory = javax.net.SocketFactory.getDefault()) : javax.net.SocketFactory() {
    override fun createSocket(): java.net.Socket {
        val socket = delegate.createSocket()
        socket.keepAlive = true
        return socket
    }

    override fun createSocket(host: String?, port: Int): java.net.Socket {
        val socket = delegate.createSocket(host, port)
        socket.keepAlive = true
        return socket
    }

    override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): java.net.Socket {
        val socket = delegate.createSocket(host, port, localHost, localPort)
        socket.keepAlive = true
        return socket
    }

    override fun createSocket(host: java.net.InetAddress?, port: Int): java.net.Socket {
        val socket = delegate.createSocket(host, port)
        socket.keepAlive = true
        return socket
    }

    override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): java.net.Socket {
        val socket = delegate.createSocket(address, port, localAddress, localPort)
        socket.keepAlive = true
        return socket
    }
}
private val client = OkHttpClient.Builder()
    .socketFactory(KeepAliveSocketFactory())
    .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
    .connectionPool(okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES))
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(90, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
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
    // Log AI API network metrics without buffering streaming bodies
    .addInterceptor(DeepCodeOkHttpInterceptor())
    .build()

private val gson = Gson()

// ==========================================
// ==========================================
// 0. ZEN PROVIDER
// ==========================================
class ZenProvider : AIProvider {
    override val name = "Zen AI"
    override val isFree = true
    override val models = listOf(
        AIModel("deepseek-v4-flash-free", "DeepSeek V4 Flash (Free)", "Zen AI", true, "1M tokens", "Free"),
        AIModel("muse-spark-1.3-contributor-free", "Muse Spark 1.3 (Free)", "Zen AI", true, "1M tokens", "Free"),
        AIModel("muse-spark-1.2-contributor-free", "Muse Spark 1.2 (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("mimo-v2.5-free", "Mimo V2.5 (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("ling-3.0-flash-fin-free", "Ling 3.0 Flash Fin (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("nemotron-3-ultra-free", "Nemotron 3 Ultra (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("nemotron-3.5-lightning-free", "Nemotron 3.5 Lightning (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("laguna-s-2.1-free", "Laguna S 2.1 (Free)", "Zen AI", true, "128k tokens", "Free"),
        AIModel("claude-fable-5.1", "Claude Fable 5.1", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("claude-fable-5", "Claude Fable 5", "Zen AI", false, "200k tokens", "Paid"),
        AIModel("claude-opus-5", "Claude Opus 5", "Zen AI", false, "200k tokens", "Paid"),
        AIModel("claude-sonnet-5", "Claude Sonnet 5", "Zen AI", false, "200k tokens", "Paid"),
        AIModel("claude-sonnet-4-6", "Claude Sonnet 4.6", "Zen AI", false, "200k tokens", "Paid"),
        AIModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("gemini-3.7-flash", "Gemini 3.7 Flash", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("gemini-3.6-flash", "Gemini 3.6 Flash", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("gemini-3.5-flash", "Gemini 3.5 Flash", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("gpt-6-astra", "GPT 6 Astra", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("gpt-5.6-sol", "GPT 5.6 Sol", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("gpt-5.5", "GPT 5.5", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("gpt-5.4", "GPT 5.4", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("grok-4.6", "Grok 4.6", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("deepseek-v4-flash", "DeepSeek V4 Flash", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("deepseek-v4-pro", "DeepSeek V4 Pro", "Zen AI", false, "1M tokens", "Paid"),
        AIModel("glm-5.2", "GLM 5.2", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("minimax-m3", "MiniMax M3", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("kimi-k3", "Kimi K3", "Zen AI", false, "128k tokens", "Paid"),
        AIModel("qwen3.6-plus", "Qwen 3.6 Plus", "Zen AI", false, "128k tokens", "Paid")
    )

    companion object {
        private val zenHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .socketFactory(KeepAliveSocketFactory())
                .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
                .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .proxySelector(object : java.net.ProxySelector() {
                    override fun select(uri: java.net.URI?): List<java.net.Proxy> {
                        val activeProxy = ai.deepcode.android.util.VpnManager.getActiveProxy()
                        return if (activeProxy != null) listOf(activeProxy) else listOf(java.net.Proxy.NO_PROXY)
                    }
                    override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {
                        ai.deepcode.android.util.VpnManager.handleProxyFailure()
                    }
                })
                .build()
        }
    }

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        if (apiKey.isBlank()) {
            onError(IllegalArgumentException("Zen API key is required. Please configure your Zen API key in Settings → API Keys."))
            return
        }

        val baseUrl = resolveBaseUrl(customBaseUrl, "https://opencode.ai/zen/v1")
        var lastException: Throwable? = null

        val sanitized = ZenModels.sanitize(model, models)
        val isFreeModel = sanitized.contains("free", ignoreCase = true)
        val candidateModels = linkedSetOf<String>().apply {
            add(sanitized)
            if (isFreeModel) {
                addAll(ZenModels.KNOWN_FREE_IDS)
            } else {
                addAll(ZenModels.KNOWN_PAID_IDS)
                addAll(ZenModels.KNOWN_FREE_IDS)
            }
        }

        for (candidate in candidateModels) {
            val payload = buildZenPayload(messages, candidate, tools)
            val payloadJson = gson.toJson(payload)

            // 1. Pooled HTTP/2 OkHttp streaming
            try {
                streamZenOkHttp(payloadJson, baseUrl, apiKey, zenHttpClient, onToken, onToolCall, onComplete, onUsage)
                return
            } catch (e: Throwable) {
                ai.deepcode.android.util.AppLogger.w("ZenProvider", "Candidate $candidate OkHttp failed: ${e.message}")
                lastException = e
            }

            val errStr = lastException?.message ?: ""
            val isAuthOrRateLimit = ApiKeyRotator.isRotatableError(lastException, null, errStr)

            if (isAuthOrRateLimit) {
                val ex = (lastException as? RateLimitException) ?: RateLimitException("Zen AI", 429, errStr)
                try { onError(ex) } catch (_: Throwable) {}
                throw ex
            }

            val isHttpError = errStr.contains("503") || errStr.contains("502") ||
                errStr.contains("500") || errStr.contains("400") || errStr.contains("404") ||
                errStr.contains("Endpoint is unavailable") || errStr.contains("server_error") ||
                errStr.contains("model_not_found", ignoreCase = true)

            // 2. Only try HttpURLConnection backup if it was a transport/connection error, not server refusal
            if (!isHttpError) {
                try {
                    streamZenHttp(payloadJson, baseUrl, apiKey, onToken, onToolCall, onComplete, onUsage)
                    return
                } catch (e: Throwable) {
                    ai.deepcode.android.util.AppLogger.w("ZenProvider", "Candidate $candidate HttpURL failed: ${e.message}")
                    lastException = e
                }
            }

            val errStr2 = lastException?.message ?: errStr
            val isNotFound = errStr2.contains("404") || errStr2.contains("model_not_found", ignoreCase = true)
            if (isNotFound) {
                continue // Try next candidate model
            }
        }

        val errMsg = "Zen API error: ${lastException?.message ?: "All transports failed"}"
        ai.deepcode.android.util.AppLogger.e("ZenProvider", errMsg)
        onError(lastException ?: java.net.ConnectException(errMsg))
    }

    private fun buildZenPayload(messages: List<Message>, model: String, tools: List<Tool>?): com.google.gson.JsonObject {
        val messagesArray = normalizeMessagesForApi(messages)
        val payload = com.google.gson.JsonObject()
        payload.addProperty("model", model)
        payload.add("messages", messagesArray)
        payload.addProperty("stream", true)

        if (!tools.isNullOrEmpty()) {
            val toolsArray = com.google.gson.JsonArray()
            for (tool in tools) {
                val tObj = com.google.gson.JsonObject()
                tObj.addProperty("type", "function")
                val funcObj = com.google.gson.JsonObject()
                funcObj.addProperty("name", tool.name)
                funcObj.addProperty("description", tool.description)
                try {
                    val schemaJson = gson.toJsonTree(tool.inputSchema)
                    funcObj.add("parameters", schemaJson)
                } catch (e: Exception) {
                    val params = com.google.gson.JsonObject()
                    params.addProperty("type", "object")
                    funcObj.add("parameters", params)
                }
                tObj.add("function", funcObj)
                toolsArray.add(tObj)
            }
            if (toolsArray.size() > 0) {
                payload.add("tools", toolsArray)
            }
        }
        return payload
    }

    private suspend fun streamZenHttp(
        payloadJson: String,
        baseUrl: String,
        token: String,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            val url = java.net.URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "text/event-stream")
                conn.setRequestProperty("Cache-Control", "no-cache")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-OpenCode-Client", "android/1.0.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 60000

                val writer = java.io.OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(payloadJson)
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    val errBody = try { conn.errorStream?.bufferedReader()?.readText()?.take(1024) ?: "" } catch (_: Exception) { "" }
                    if (ApiKeyRotator.isRotatableError(null, responseCode, errBody)) {
                        throw RateLimitException("Zen AI", responseCode, "Zen API Error $responseCode: $errBody")
                    }
                    throw Exception("Zen API Error $responseCode: $errBody")
                }

                val contentType = conn.contentType ?: ""
                if (!contentType.contains("text/event-stream") && !contentType.contains("application/x-ndjson") && !contentType.contains("application/stream+json")) {
                    // Not SSE — try reading as plain JSON
                    val body = conn.inputStream.bufferedReader().readText()
                    parseNonStreamingResponse(body, onToken, onToolCall, onComplete, onUsage)
                    return@withContext
                }

                val source = conn.inputStream.source().buffer()
                parseSseStream(source, onToken, onToolCall, onComplete, onUsage)
            } finally {
                try { conn.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun streamZenOkHttp(
        payloadJson: String,
        baseUrl: String,
        token: String,
        httpClient: OkHttpClient,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .post(payloadJson.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-OpenCode-Client", "android/1.0.0")
                .build()

            val call = httpClient.newCall(request)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                call.cancel()
            }
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string()?.take(1024) ?: ""
                    if (ApiKeyRotator.isRotatableError(null, resp.code, errBody)) {
                        throw RateLimitException("Zen AI", resp.code, "Zen API Error ${resp.code}: $errBody")
                    }
                    throw Exception("Zen API Error ${resp.code}: $errBody")
                }
                val contentType = resp.header("Content-Type") ?: ""
                val body = resp.body ?: throw Exception("Empty body")
                if (!contentType.contains("text/event-stream") && !contentType.contains("application/x-ndjson") && !contentType.contains("application/stream+json")) {
                    val bodyStr = body.string()
                    parseNonStreamingResponse(bodyStr, onToken, onToolCall, onComplete, onUsage)
                    return@withContext
                }
                val source = body.source()
                parseSseStream(source, onToken, onToolCall, onComplete, onUsage)
            }
        }
    }

    private suspend fun streamZenNonStreaming(
        payloadJson: String,
        baseUrl: String,
        token: String,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onToolCall: ((ToolCall) -> Unit)? = null
    ) {
        withContext(Dispatchers.IO) {
            val nonStreamPayload = com.google.gson.JsonParser.parseString(payloadJson).asJsonObject
            nonStreamPayload.addProperty("stream", false)
            val body = gson.toJson(nonStreamPayload)

            val url = java.net.URL("$baseUrl/chat/completions")
            val conn = url.openConnection() as java.net.HttpURLConnection
            try {
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-OpenCode-Client", "android/1.0.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 90000

                val writer = java.io.OutputStreamWriter(conn.outputStream, "UTF-8")
                writer.write(body)
                writer.flush()
                writer.close()

                if (conn.responseCode != 200) {
                    val errBody = try { conn.errorStream?.bufferedReader()?.readText()?.take(500) ?: "" } catch (_: Exception) { "" }
                    throw Exception("Zen API Error ${conn.responseCode}: $errBody")
                }

                val respBody = conn.inputStream.bufferedReader().readText()
                val json = try { com.google.gson.JsonParser.parseString(respBody).asJsonObject } catch (_: Exception) { com.google.gson.JsonObject() }
                val choice = try { json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject } catch (_: Exception) { null }
                val msgObj = try { choice?.getAsJsonObject("message") } catch (_: Exception) { null } ?: choice
                var text = if (msgObj != null) safeStr(msgObj, "content") ?: "" else ""
                if (text.isNotEmpty()) {
                    onToken(text)
                }
                // Parse tool calls from non-streaming response
                val tcArray = try { msgObj?.getAsJsonArray("tool_calls") } catch (_: Exception) { null }
                if (tcArray != null) {
                    for (i in 0 until tcArray.size()) {
                        val tcElem = try { tcArray.get(i).asJsonObject } catch (_: Exception) { continue }
                        val id = safeStr(tcElem, "id") ?: "call_$i"
                        val func = try { tcElem.getAsJsonObject("function") } catch (_: Exception) { null } ?: continue
                        val name = safeStr(func, "name") ?: continue
                        if (name.isBlank()) continue
                        val args = safeStr(func, "arguments") ?: "{}"
                        onToolCall?.invoke(ToolCall(id, name, args))
                    }
                }
                onComplete(text)
            } finally {
                try { conn.disconnect() } catch (_: Exception) {}
            }
        }
    }

    private fun safeStr(obj: com.google.gson.JsonObject?, key: String): String? {
        if (obj == null || !obj.has(key)) return null
        val el = try { obj.get(key) } catch (_: Exception) { return null }
        if (el == null || el.isJsonNull) return null
        return try {
            if (el.isJsonPrimitive) el.asString else gson.toJson(el)
        } catch (_: Exception) { null }
    }

    private fun parseNonStreamingResponse(
        body: String,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        val json = try { com.google.gson.JsonParser.parseString(body).asJsonObject } catch (_: Exception) { onComplete(""); return }
        if (json.has("error") && !json.get("error").isJsonNull) {
            val errObj = json.get("error")
            val errMsg = if (errObj.isJsonObject) {
                errObj.asJsonObject.get("message")?.asString ?: gson.toJson(errObj)
            } else {
                errObj.asString
            }
            if (ApiKeyRotator.isRotatableError(null, 429, errMsg)) {
                throw RateLimitException("Zen AI", 429, errMsg)
            }
            throw Exception("Zen API Error: $errMsg")
        }
        val choice = try { json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject } catch (_: Exception) { null } ?: run { onComplete(""); return }
        // Some providers return delta-shaped choices even in non-streaming mode.
        val msg = try { choice.getAsJsonObject("message") } catch (_: Exception) { null }
            ?: try { choice.getAsJsonObject("delta") } catch (_: Exception) { null }
            ?: choice
        var text = safeStr(msg, "content") ?: ""
        val tcArray = try { msg.getAsJsonArray("tool_calls") } catch (_: Exception) { null }

        if (tcArray != null && tcArray.size() > 0) {
            for (i in 0 until tcArray.size()) {
                try {
                    val tc = tcArray.get(i).asJsonObject
                    val id = safeStr(tc, "id") ?: UUID.randomUUID().toString()
                    val func = try { tc.getAsJsonObject("function") } catch (_: Exception) { null } ?: continue
                    val name = safeStr(func, "name") ?: continue
                    if (name.isBlank()) continue
                    val args = safeStr(func, "arguments") ?: "{}"
                    onToolCall(ToolCall(id, name, args))
                } catch (_: Exception) { continue }
            }
        }

        if (text.isNotEmpty()) onToken(text)

        if (json.has("usage") && !json.get("usage").isJsonNull) {
            try {
                val u = json.getAsJsonObject("usage")
                val input = try { u.get("prompt_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                val output = try { u.get("completion_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                val reasoningTokens = try { u.get("reasoning_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                if (input > 0 || output > 0) onUsage?.invoke(TurnTokenUsage(input, output, reasoningTokens))
            } catch (_: Exception) {}
        }
        onComplete(text)
    }

    private fun parseSseStream(
        source: okio.BufferedSource,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        val accumulatedReasoning = StringBuilder()
        val accumulatedContent = StringBuilder()
        val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
        var usageInput = 0
        var usageOutput = 0
        var usageReasoning = 0

        var line: String?
        while (true) {
            try {
                val raw = source.readUtf8Line() ?: break
                line = raw
            } catch (_: Exception) { break }
            val cleaned = line!!.trim()
            if (cleaned.isEmpty() || cleaned.startsWith(":")) continue
            // Tolerate "data:", "data: ", "event:", and bare JSON lines.
            if (cleaned.startsWith("event:")) continue
            val dataVal = if (cleaned.startsWith("data:")) cleaned.substring(5).trim() else cleaned
            if (dataVal == "[DONE]") break
            if (dataVal.isEmpty()) continue
            // Server-sent error payloads look like {"error": {...}} without choices.
            try {
                val chunk = gson.fromJson(dataVal, com.google.gson.JsonObject::class.java) ?: continue
                if (chunk.has("error") && !chunk.get("error").isJsonNull) {
                    val errObj = chunk.get("error")
                    val errMsg = if (errObj.isJsonObject) {
                        errObj.asJsonObject.get("message")?.asString ?: gson.toJson(errObj)
                    } else {
                        errObj.asString
                    }
                    if (ApiKeyRotator.isRotatableError(null, 429, errMsg)) {
                        throw RateLimitException("Zen AI", 429, errMsg)
                    }
                    throw Exception("Zen stream error: $errMsg")
                }
                if (chunk.has("usage") && !chunk.get("usage").isJsonNull) {
                    try {
                        val u = chunk.getAsJsonObject("usage")
                        usageInput = try { u.get("prompt_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                        usageOutput = try { u.get("completion_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                        usageReasoning = try { u.get("reasoning_tokens")?.asInt } catch (_: Exception) { 0 } ?: 0
                    } catch (_: Exception) {}
                }
                val choices = try { chunk.getAsJsonArray("choices") } catch (_: Exception) { null }
                if (choices != null && choices.size() > 0) {
                    val choice = try { choices.get(0).asJsonObject } catch (_: Exception) { continue }
                    // Support both streaming (delta) and non-streaming (message) shapes.
                    val delta = try { choice.getAsJsonObject("delta") } catch (_: Exception) { null }
                        ?: try { choice.getAsJsonObject("message") } catch (_: Exception) { null }
                        ?: choice
                    val reasoningText = safeStr(delta, "reasoning_content") ?: safeStr(delta, "reasoning")
                    if (reasoningText != null) {
                        accumulatedReasoning.append(reasoningText)
                    }
                    val contentText = safeStr(delta, "content")
                    if (contentText != null) {
                        accumulatedContent.append(contentText)
                        val curr = accumulatedContent.toString()
                        val hasEmbeddedToolCallStart = curr.contains("]<]minimax") || curr.contains("<tool_call")
                        if (!hasEmbeddedToolCallStart) {
                            try { onToken(contentText) } catch (_: Exception) {}
                        }
                    }
                    if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull) {
                        val tcArray = try { delta.getAsJsonArray("tool_calls") } catch (_: Exception) { null } ?: continue
                        for (i in 0 until tcArray.size()) {
                            val tcElem = try { tcArray.get(i).asJsonObject } catch (_: Exception) { continue }
                            val index = try { tcElem.get("index")?.asInt ?: 0 } catch (_: Exception) { 0 }
                            val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
                            safeStr(tcElem, "id")?.let { builder.id = it }
                            val func = try { tcElem.getAsJsonObject("function") } catch (_: Exception) { null } ?: continue
                            safeStr(func, "name")?.let { if (it.isNotBlank()) builder.name = it }
                            // arguments may arrive as string chunks OR as JSON object — handle both.
                            if (func.has("arguments") && !func.get("arguments").isJsonNull) {
                                val argEl = func.get("arguments")
                                val argChunk = try {
                                    if (argEl.isJsonPrimitive) argEl.asString else gson.toJson(argEl)
                                } catch (_: Exception) { "" }
                                builder.arguments.append(argChunk)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Preserve real server errors; ignore only malformed keep-alive chunks.
                val msg = e.message ?: ""
                if (msg.startsWith("Zen stream error:")) throw e
            }
        }
        try { source.close() } catch (_: Exception) {}

        if (toolCallBuilders.isEmpty()) {
            val contentStr = accumulatedContent.toString()
            val rawToolCallPattern = Regex("""(?s)(?:\]<\]minimax\[>\[\s*)?<tool_call>\s*(\{[^<]+\})\s*(?:</tool_call>)?""")
            val m = rawToolCallPattern.find(contentStr)
            if (m != null) {
                val jsonStr = m.groupValues[1].trim()
                try {
                    val jsonObj = com.google.gson.JsonParser.parseString(jsonStr).asJsonObject
                    val name = jsonObj.get("name")?.asString ?: ""
                    val argsObj = jsonObj.get("arguments")
                    val argsStr = when {
                        argsObj == null -> "{}"
                        argsObj.isJsonPrimitive -> argsObj.asString
                        else -> gson.toJson(argsObj)
                    }
                    if (name.isNotEmpty()) {
                        val tcId = "call_" + UUID.randomUUID().toString().take(8)
                        val cleanRemaining = contentStr.removeRange(m.range).trim()
                        accumulatedContent.setLength(0)
                        accumulatedContent.append(cleanRemaining)
                        try { onToolCall(ToolCall(tcId, name, argsStr)) } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    AppLogger.w("AIProvider", "Failed to parse raw embedded tool call: ${e.message}")
                }
            }
        }

        toolCallBuilders.values.forEach { builder ->
            if (builder.name.isNotEmpty()) {
                try { onToolCall(ToolCall(builder.getValidId(), builder.name, builder.arguments.toString())) } catch (_: Exception) {}
            }
        }
        if (usageInput > 0 || usageOutput > 0) {
            try { onUsage?.invoke(TurnTokenUsage(usageInput, usageOutput, usageReasoning)) } catch (_: Exception) {}
        } else if (onUsage != null) {
            val estimatedOutput = (accumulatedContent.length / 4).coerceAtLeast(1)
            val estimatedReasoning = (accumulatedReasoning.length / 4)
            val estimatedInput = ((accumulatedReasoning.length + accumulatedContent.length) / 4 + 120).coerceAtLeast(10)
            try { onUsage.invoke(TurnTokenUsage(estimatedInput, estimatedOutput, estimatedReasoning)) } catch (_: Exception) {}
        }
        // Thinking stripped entirely — complete with answer only, never reasoning.
        onComplete(accumulatedContent.toString())
    }
}

// ==========================================
// 1. GEMINI PROVIDER
// ==========================================
class GeminiProvider : AIProvider {
    override val name = "Google Gemini"
    override val isFree = false
    override val models = listOf(
        AIModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Google Gemini", true, "1M tokens", "Free"),
        AIModel("gemini-2.5-pro", "Gemini 2.5 Pro", "Google Gemini", false, "2M tokens", "Paid"),
        AIModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Google Gemini", true, "1M tokens", "Free"),
        AIModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", "Google Gemini", true, "1M tokens", "Free"),
        AIModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Google Gemini", true, "1M tokens", "Free"),
        AIModel("gemini-1.5-pro", "Gemini 1.5 Pro", "Google Gemini", false, "2M tokens", "Paid"),
        AIModel("imagen-3.0-generate-002", "Imagen 3 (Image Creation)", "Google Gemini", false, "Image Gen", "Free"),
        AIModel("imagen-3.0-fast-generate-001", "Imagen 3 Fast (Image Creation)", "Google Gemini", false, "Image Gen", "Free")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val finalKey = apiKey.trim()
                val targetModel = when {
                    model.startsWith("imagen-") -> model
                    model in listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro") -> model
                    model.startsWith("gemini-") && !model.contains("3.8") -> model
                    else -> "gemini-2.5-flash"
                }

                // Handle Imagen 3 Image Generation Models directly
                if (targetModel.startsWith("imagen-")) {
                    val prompt = messages.lastOrNull { it.role == "user" }?.content ?: "a beautiful landscape"
                    val imgUrl = try {
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$targetModel:predict?key=$finalKey"
                        val payload = JsonObject().apply {
                            val instances = JsonArray().apply {
                                val inst = JsonObject()
                                inst.addProperty("prompt", prompt)
                                add(inst)
                            }
                            add("instances", instances)
                            val params = JsonObject().apply {
                                addProperty("sampleCount", 1)
                                addProperty("aspectRatio", "1:1")
                                addProperty("outputMimeType", "image/jpeg")
                            }
                            add("parameters", params)
                        }
                        val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
                        val request = Request.Builder().url(url).header("Content-Type", "application/json").post(requestBody).build()
                        val resp = client.newCall(request).execute()
                        resp.use { response ->
                            if (!response.isSuccessful) null
                            else {
                                val bodyStr = response.body?.string() ?: ""
                                val json = gson.fromJson(bodyStr, JsonObject::class.java)
                                val predictions = json?.getAsJsonArray("predictions")
                                val b64 = predictions?.get(0)?.asJsonObject?.get("bytesBase64Encoded")?.asString
                                if (!b64.isNullOrEmpty()) {
                                    "data:image/jpeg;base64,$b64"
                                } else null
                            }
                        }
                    } catch (_: Exception) { null }

                    val finalImgUrl = if (!imgUrl.isNullOrBlank()) imgUrl else {
                        val enc = java.net.URLEncoder.encode(prompt, "UTF-8")
                        "https://image.pollinations.ai/prompt/$enc?width=1024&height=1024&nologo=true"
                    }
                    onToken("[image:$finalImgUrl]")
                    onComplete("")
                    return@withContext
                }

                ai.deepcode.android.util.AppLogger.i("GeminiProvider", "model=$targetModel keySet=${finalKey.isNotEmpty()} keyLen=${finalKey.length}")
                val baseUrl = resolveBaseUrl(customBaseUrl, "https://generativelanguage.googleapis.com")
                val url = "$baseUrl/v1beta/models/$targetModel:streamGenerateContent?alt=sse&key=$finalKey"

                val contentsArray = JsonArray()
                var systemText: String? = null

                fun addOrMergeTurn(role: String, parts: JsonArray) {
                    if (parts.size() == 0) return
                    if (contentsArray.size() > 0) {
                        val lastObj = contentsArray.get(contentsArray.size() - 1).asJsonObject
                        val lastRole = lastObj.get("role")?.asString
                        if (lastRole == role) {
                            val lastParts = lastObj.getAsJsonArray("parts")
                            for (p in parts) {
                                lastParts.add(p)
                            }
                            return
                        }
                    }
                    val turnObj = JsonObject().apply {
                        addProperty("role", role)
                        add("parts", parts)
                    }
                    contentsArray.add(turnObj)
                }

                for (msg in messages) {
                    if (msg.role == "system") {
                        systemText = (systemText ?: "") + msg.content + "\n"
                        continue
                    }

                    val partsArray = JsonArray()

                    if (msg.role == "tool") {
                        var toolName = "tool"
                        val rawId = msg.toolCallsJson ?: ""
                        try {
                            val parsed = JsonParser.parseString(rawId)
                            if (parsed.isJsonObject && parsed.asJsonObject.has("name")) {
                                toolName = parsed.asJsonObject.get("name").asString
                            }
                        } catch (_: Exception) {}

                        if (toolName == "tool" || toolName.isBlank()) {
                            // Find the corresponding tool call name from assistant history
                            val lastAssistantWithTool = messages.lastOrNull { it.role == "assistant" && it.isToolCall && !it.toolCallsJson.isNullOrEmpty() }
                            if (lastAssistantWithTool != null) {
                                try {
                                    val tcArray = JsonParser.parseString(lastAssistantWithTool.toolCallsJson).asJsonArray
                                    for (i in 0 until tcArray.size()) {
                                        val tc = tcArray.get(i).asJsonObject
                                        if (tc.has("id") && tc.get("id").asString == rawId) {
                                            toolName = tc.get("name")?.asString ?: toolName
                                            break
                                        }
                                    }
                                    if (toolName == "tool" && tcArray.size() > 0) {
                                        toolName = tcArray.get(0).asJsonObject.get("name")?.asString ?: toolName
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        val fnRespPart = JsonObject().apply {
                            val fnRespObj = JsonObject().apply {
                                addProperty("name", toolName)
                                val responseObj = JsonObject().apply {
                                    addProperty("result", msg.content)
                                }
                                add("response", responseObj)
                            }
                            add("functionResponse", fnRespObj)
                        }
                        partsArray.add(fnRespPart)
                        addOrMergeTurn("user", partsArray)
                    } else if (msg.role == "assistant" && msg.isToolCall && !msg.toolCallsJson.isNullOrEmpty()) {
                        if (msg.content.isNotBlank()) {
                            val textObj = JsonObject().apply { addProperty("text", msg.content) }
                            partsArray.add(textObj)
                        }
                        try {
                            val tcArray = JsonParser.parseString(msg.toolCallsJson).asJsonArray
                            for (i in 0 until tcArray.size()) {
                                val tcObj = tcArray.get(i).asJsonObject
                                val fnCallPart = JsonObject().apply {
                                    val fnCallObj = JsonObject().apply {
                                        addProperty("name", tcObj.get("name")?.asString ?: "")
                                        val argEl = tcObj.get("arguments")
                                        val argsStr = if (argEl != null && argEl.isJsonPrimitive) argEl.asString else argEl?.toString() ?: "{}"
                                        val argsJson = try { JsonParser.parseString(argsStr).asJsonObject } catch (_: Exception) { JsonObject() }
                                        add("args", argsJson)
                                    }
                                    add("functionCall", fnCallObj)
                                }
                                partsArray.add(fnCallPart)
                            }
                        } catch (_: Exception) {}
                        addOrMergeTurn("model", partsArray)
                    } else {
                        val role = if (msg.role == "assistant") "model" else "user"
                        if (msg.content.isNotBlank()) {
                            val partObj = JsonObject().apply { addProperty("text", msg.content) }
                            partsArray.add(partObj)
                        } else if (messages.size == 1) {
                            partsArray.add(JsonObject().apply { addProperty("text", " ") })
                        }
                        addOrMergeTurn(role, partsArray)
                    }
                }

                // Ensure Gemini conversation starts with user turn
                if (contentsArray.size() > 0 && contentsArray.get(0).asJsonObject.get("role")?.asString == "model") {
                    val dummyUser = JsonObject().apply {
                        addProperty("role", "user")
                        val dummyParts = JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", "Hello") })
                        }
                        add("parts", dummyParts)
                    }
                    val newContentsArray = JsonArray().apply {
                        add(dummyUser)
                        for (c in contentsArray) add(c)
                    }
                    contentsArray.apply {
                        while (size() > 0) remove(0)
                        for (c in newContentsArray) add(c)
                    }
                }

                val payload = JsonObject()
                payload.add("contents", contentsArray)

                if (!systemText.isNullOrBlank()) {
                    val sysObj = JsonObject()
                    val sysParts = JsonArray()
                    val p = JsonObject()
                    p.addProperty("text", systemText.trim())
                    sysParts.add(p)
                    sysObj.add("parts", sysParts)
                    payload.add("systemInstruction", sysObj)
                }

                if (shouldIncludeTools(messages, tools)) {
                    val toolsArray = JsonArray()
                    val functionDeclarations = JsonArray()
                    for (tool in tools!!) {
                        val fd = JsonObject()
                        fd.addProperty("name", tool.name)
                        fd.addProperty("description", tool.description)
                        
                        val params = JsonObject()
                        params.addProperty("type", "OBJECT")
                        val properties = JsonObject()
                        val required = JsonArray()
                        
                        val schemaProps = tool.inputSchema["properties"] as? Map<*, *>
                        schemaProps?.forEach { (k, v) ->
                            val propKey = k.toString()
                            val propVal = v as? Map<*, *>
                            properties.add(propKey, convertSchemaObj(propVal))
                            
                            val isReq = (tool.inputSchema["required"] as? List<*>)?.contains(propKey) ?: false
                            if (isReq) {
                                required.add(propKey)
                            }
                        }
                        params.add("properties", properties)
                        if (required.size() > 0) {
                            params.add("required", required)
                        }
                        fd.add("parameters", params)
                        functionDeclarations.add(fd)
                    }
                    val toolDeclObj = JsonObject()
                    toolDeclObj.add("functionDeclarations", functionDeclarations)
                    toolsArray.add(toolDeclObj)
                    payload.add("tools", toolsArray)
                }
                ai.deepcode.android.util.AppLogger.i("GeminiProvider", "Request URL: ${url.take(120)}...")
                val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (finalKey.isNotEmpty()) addHeader("x-goog-api-key", finalKey)
                    }
                    .post(requestBody)
                    .build()
                val call = client.newCall(request)
                coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                    call.cancel()
                }
                var responseCode = 0
                var totalCandidates = 0
                call.execute().use { response ->
                    responseCode = response.code
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(1024) ?: ""
                        if (ApiKeyRotator.isRotatableError(null, response.code, errBody)) {
                            throw RateLimitException("Google Gemini", response.code, "Gemini API Error ${response.code}: $errBody")
                        }
                        throw Exception("API Error ${response.code}: $errBody")
                    }
                    val body = response.body ?: throw Exception("Empty response body")
                    val source = body.source()
                    var line: String?
                    val accumulatedJson = StringBuilder()
                    val collectedGeminiText = StringBuilder()
                    var inThoughtBlock = false
                    var geminiInputTokens = 0
                    var geminiOutputTokens = 0

                    fun handleGeminiChunk(chunk: JsonObject) {
                        if (chunk.has("usageMetadata")) {
                            val u = chunk.getAsJsonObject("usageMetadata")
                            geminiInputTokens = u.get("promptTokenCount")?.asInt ?: geminiInputTokens
                            geminiOutputTokens = u.get("candidatesTokenCount")?.asInt ?: geminiOutputTokens
                        }
                        val candidates = chunk.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            totalCandidates++
                            val firstCand = candidates.get(0).asJsonObject
                            val content = firstCand.getAsJsonObject("content")
                            if (content != null) {
                                val parts = content.getAsJsonArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.size()) {
                                        val part = parts.get(i).asJsonObject
                                        if (part.has("functionCall")) {
                                            val fc = part.getAsJsonObject("functionCall")
                                            val name = fc.get("name")?.asString ?: "unknown_function"
                                            val args = fc.getAsJsonObject("args")?.toString() ?: "{}"
                                            val callId = UUID.randomUUID().toString()
                                            onToolCall(ToolCall(callId, name, args))
                                        } else if (part.has("text") && !part.get("text").isJsonNull) {
                                            val text = part.get("text").asString ?: ""
                                            if (text.isNotEmpty()) {
                                                val isThought = (part.has("thought") && part.get("thought")?.asBoolean == true) || part.has("thoughtSignature")
                                                if (isThought) {
                                                    if (!inThoughtBlock) {
                                                        inThoughtBlock = true
                                                        onToken("<thought>")
                                                        collectedGeminiText.append("<thought>")
                                                    }
                                                    collectedGeminiText.append(text)
                                                    onToken(text)
                                                } else {
                                                    if (inThoughtBlock) {
                                                        inThoughtBlock = false
                                                        onToken("</thought>\n\n")
                                                        collectedGeminiText.append("</thought>\n\n")
                                                    }
                                                    collectedGeminiText.append(text)
                                                    onToken(text)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    while (source.readUtf8Line().also { line = it } != null) {
                        val rawLine = line!!.trim()
                        if (rawLine.isEmpty()) continue

                        // 1. Native SSE parsing from alt=sse
                        if (rawLine.startsWith("data: ")) {
                            val dataVal = rawLine.substring(6).trim()
                            if (dataVal == "[DONE]" || dataVal.isEmpty()) continue
                            try {
                                val chunk = gson.fromJson(dataVal, JsonObject::class.java)
                                handleGeminiChunk(chunk)
                            } catch (_: Exception) {}
                            continue
                        }

                        // 2. Fallback JSON Array parser
                        var cleanLine = rawLine
                        if (cleanLine == "[" || cleanLine == "]") continue
                        if (cleanLine.startsWith("[")) {
                            cleanLine = cleanLine.substring(1).trim()
                            if (cleanLine.isEmpty()) continue
                        }
                        if (cleanLine.endsWith("]")) {
                            cleanLine = cleanLine.substring(0, cleanLine.length - 1).trim()
                            if (cleanLine.isEmpty()) continue
                        }
                        if (cleanLine.startsWith(",")) {
                            accumulatedJson.setLength(0)
                            accumulatedJson.append(cleanLine.substring(1))
                        } else {
                            accumulatedJson.append(cleanLine)
                        }

                        try {
                            val chunk = gson.fromJson(accumulatedJson.toString(), JsonObject::class.java)
                            handleGeminiChunk(chunk)
                            accumulatedJson.setLength(0)
                        } catch (_: Exception) {
                            // Keep accumulating for multi-line JSON
                        }
                    }
                    ai.deepcode.android.util.AppLogger.i("GeminiProvider", "Response: code=$responseCode candidates=$totalCandidates")
                    
                    if (inThoughtBlock) {
                        inThoughtBlock = false
                        onToken("</thought>")
                        collectedGeminiText.append("</thought>")
                    }

                    if (geminiInputTokens > 0 || geminiOutputTokens > 0) {
                        onUsage?.invoke(TurnTokenUsage(geminiInputTokens, geminiOutputTokens, 0))
                    } else if (onUsage != null) {
                        val estimatedInput = (messages.sumOf { it.content.length } / 4).coerceAtLeast(10)
                        val estimatedOutput = (collectedGeminiText.length / 4).coerceAtLeast(1)
                        onUsage.invoke(TurnTokenUsage(estimatedInput, estimatedOutput, 0))
                    }
                    onComplete(collectedGeminiText.toString())
                }
            } catch (e: Throwable) {
                ai.deepcode.android.util.AppLogger.e("GeminiProvider", "Gemini stream failed", e)
                onError(e)
            }
        }
    }

    private fun convertSchemaObj(src: Map<*, *>?): JsonObject {
        val obj = JsonObject()
        if (src == null) return obj
        for ((k, v) in src) {
            val key = k.toString()
            if (key == "additionalProperties") continue
            when (v) {
                is String -> {
                    if (key.equals("type", ignoreCase = true)) {
                        obj.addProperty(key, v.uppercase())
                    } else {
                        obj.addProperty(key, v)
                    }
                }
                is Number -> obj.addProperty(key, v)
                is Boolean -> obj.addProperty(key, v)
                is Map<*, *> -> when (key) {
                    "items" -> obj.add(key, convertSchemaObj(v))
                    "properties" -> {
                        val props = JsonObject()
                        for ((pk, pv) in v) {
                            props.add(pk.toString(), convertSchemaObj(pv as? Map<*, *>))
                        }
                        obj.add("properties", props)
                    }
                    else -> obj.add(key, convertSchemaObj(v))
                }
                is List<*> -> {
                    val arr = JsonArray()
                    for (item in v) {
                        when (item) {
                            is String -> arr.add(item)
                            is Map<*, *> -> arr.add(convertSchemaObj(item))
                            else -> arr.add(item.toString())
                        }
                    }
                    obj.add(key, arr)
                }
                else -> obj.addProperty(key, v.toString())
            }
        }
        return obj
    }
}

// ==========================================
// 2. GROQ PROVIDER
// ==========================================
class GroqProvider : AIProvider {
    override val name = "Groq"
    override val isFree = false
    override val models = listOf(
        AIModel("llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", "Groq", true, "128k tokens", "Free"),
        AIModel("llama-3.1-8b-instant", "Llama 3.1 8B Instant (Fast LPU)", "Groq", true, "128k tokens", "Free"),
        AIModel("openai/gpt-oss-120b", "GPT OSS 120B", "Groq", true, "128k tokens", "Free"),
        AIModel("openai/gpt-oss-20b", "GPT OSS 20B", "Groq", true, "128k tokens", "Free"),
        AIModel("qwen/qwen3.6-27b", "Qwen 3.6 27B", "Groq", true, "128k tokens", "Free"),
        AIModel("deepseek-r1-distill-llama-70b", "DeepSeek R1 Distill 70B", "Groq", true, "128k tokens", "Free"),
        AIModel("groq/compound", "Groq Compound (Agentic)", "Groq", true, "128k tokens", "Free"),
        AIModel("gemma2-9b-it", "Gemma 2 9B", "Groq", true, "8k tokens", "Free")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        val effectiveModel = DecommissionedModels.sanitize(model)
        streamOpenAiCompatible(
            messages = messages,
            model = effectiveModel,
            tools = tools,
            apiKey = apiKey,
            baseUrl = resolveBaseUrl(customBaseUrl, "https://api.groq.com/openai/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 2.5. CEREBRUS PROVIDER
// ==========================================
class CerebrusProvider : AIProvider {
    override val name = "Cerebrus"
    override val isFree = false
    override val models = listOf(
        AIModel("llama-3.3-70b", "Llama 3.3 70B (Fast WSE)", "Cerebrus", true, "128k tokens", "Free"),
        AIModel("llama3.1-8b", "Llama 3.1 8B (Ultra Fast)", "Cerebrus", true, "8k tokens", "Free"),
        AIModel("qwen2.5-72b", "Qwen 2.5 72B (WSE)", "Cerebrus", true, "128k tokens", "Free"),
        AIModel("gpt-oss-120b", "GPT OSS 120B", "Cerebrus", true, "128k tokens", "Free"),
        AIModel("gemma-4-31b", "Gemma 4 31B", "Cerebrus", true, "128k tokens", "Free")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey.ifEmpty { "csk-free-dummy" },
            baseUrl = resolveBaseUrl(customBaseUrl, "https://api.cerebras.ai/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 3. OPENROUTER PROVIDER
// ==========================================
class OpenRouterProvider : AIProvider {
    override val name = "OpenRouter"
    override val isFree = false
    override val models = listOf(
        AIModel("google/gemma-2-9b-it:free", "Gemma 2 9B (Free)", "OpenRouter", true, "8k tokens", "Free"),
        AIModel("meta-llama/llama-3.1-8b-instruct:free", "Llama 3.1 8B (Free)", "OpenRouter", true, "128k tokens", "Free"),
        AIModel("qwen/qwen-2.5-72b-instruct:free", "Qwen 2.5 72B (Free)", "OpenRouter", true, "128k tokens", "Free"),
        AIModel("nousresearch/hermes-3-llama-3.1-405b:free", "Hermes 3 405B (Free)", "OpenRouter", true, "128k tokens", "Free")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey.ifEmpty { "sk-or-v1-free-dummy-key" },
            baseUrl = resolveBaseUrl(customBaseUrl, "https://openrouter.ai/api/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 3.5. OMNIROUTE PROVIDER
// ==========================================
const val OMNIROUTE_DEFAULT_API_KEY = "sk-e4b4f6093df00ab4-cae241-a03ed741"

class OmnirouteProvider : AIProvider {
    override val name = "Omniroute"
    override val isFree = false
    override val models = listOf(
        AIModel("gpt-4o", "GPT-4o", "Omniroute", false, "128k tokens", "Paid"),
        AIModel("gpt-4o-mini", "GPT-4o Mini", "Omniroute", false, "128k tokens", "Paid"),
        AIModel("claude-3.5-sonnet", "Claude 3.5 Sonnet", "Omniroute", false, "200k tokens", "Paid"),
        AIModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Omniroute", false, "1M tokens", "Paid")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey.ifEmpty { OMNIROUTE_DEFAULT_API_KEY },
            baseUrl = resolveBaseUrl(customBaseUrl, "http://10.0.2.2:20128/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 4. OPENAI PROVIDER
// ==========================================
class OpenAIProvider : AIProvider {
    override val name = "OpenAI"
    override val isFree = false
    override val models = listOf(
        AIModel("gpt-6-astra", "GPT 6 Astra", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("gpt-5.6-sol", "GPT 5.6 Sol", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("gpt-4o", "GPT-4o", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("gpt-4o-mini", "GPT-4o Mini", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("o1", "OpenAI o1", "OpenAI", false, "200k tokens", "Paid"),
        AIModel("o1-mini", "OpenAI o1 Mini", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("o3-mini", "OpenAI o3 Mini", "OpenAI", false, "200k tokens", "Paid"),
        AIModel("gpt-4.5-preview", "GPT-4.5 Preview", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("gpt-4-turbo", "GPT-4 Turbo", "OpenAI", false, "128k tokens", "Paid"),
        AIModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "OpenAI", false, "16k tokens", "Paid"),
        AIModel("dall-e-3", "DALL-E 3 (Image Generation)", "OpenAI", false, "1024x1024 Image", "Paid"),
        AIModel("dall-e-2", "DALL-E 2 (Image Generation)", "OpenAI", false, "1024x1024 Image", "Paid")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        if (model.startsWith("dall-e")) {
            val userPrompt = messages.lastOrNull { it.role == "user" }?.content ?: ""
            val executor = ai.deepcode.android.service.tools.ToolExecutor()
            val imgResult = executor.executeOpenAIDallE(userPrompt, apiKey, model)
            if (imgResult.isNotBlank()) {
                val tokenMarker = "[image:$imgResult]"
                onToken(tokenMarker)
                onComplete(tokenMarker)
            } else {
                onError(Exception("Failed to generate image via $model"))
            }
            return
        }

        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey,
            baseUrl = resolveBaseUrl(customBaseUrl, "https://api.openai.com/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 5. ANTHROPIC PROVIDER
// ==========================================
class AnthropicProvider : AIProvider {
    override val name = "Anthropic"
    override val isFree = false
    override val models = listOf(
        AIModel("claude-fable-5.1", "Claude Fable 5.1", "Anthropic", false, "1M tokens", "Paid"),
        AIModel("claude-opus-5", "Claude Opus 5", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-sonnet-5", "Claude Sonnet 5", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-3-7-sonnet-latest", "Claude 3.7 Sonnet (Hybrid)", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-3-5-haiku-latest", "Claude 3.5 Haiku", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-3-opus-latest", "Claude 3 Opus", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-4.5-sonnet", "Claude Sonnet 4.5", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-4.5-opus", "Claude Opus 4.5", "Anthropic", false, "200k tokens", "Paid"),
        AIModel("claude-4.5-haiku", "Claude Haiku 4.5", "Anthropic", false, "200k tokens", "Paid")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = resolveBaseUrl(customBaseUrl, "https://api.anthropic.com")
                val url = "$baseUrl/v1/messages"

                val msgArray = JsonArray()
                var systemPrompt = ""
                for (msg in messages) {
                    if (msg.role == "system") {
                        systemPrompt += msg.content + "\n"
                        continue
                    }
                    val m = JsonObject()
                    if (msg.role == "tool") {
                        m.addProperty("role", "user")
                        val contentArray = JsonArray()
                        val toolResultObj = JsonObject().apply {
                            addProperty("type", "tool_result")
                            val rawId = msg.toolCallsJson ?: ""
                            val toolId = try {
                                val parsed = JsonParser.parseString(rawId)
                                if (parsed.isJsonObject) parsed.asJsonObject.get("id")?.asString ?: rawId else rawId
                            } catch (_: Exception) { rawId }
                            addProperty("tool_use_id", toolId)
                            addProperty("content", msg.content)
                        }
                        contentArray.add(toolResultObj)
                        m.add("content", contentArray)
                        msgArray.add(m)
                    } else if (msg.role == "assistant" && msg.isToolCall && !msg.toolCallsJson.isNullOrEmpty()) {
                        m.addProperty("role", "assistant")
                        val contentArray = JsonArray()
                        if (msg.content.isNotEmpty()) {
                            val textObj = JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("content", msg.content)
                            }
                            contentArray.add(textObj)
                        }
                        try {
                            val tcArray = JsonParser.parseString(msg.toolCallsJson).asJsonArray
                            for (i in 0 until tcArray.size()) {
                                val tcObj = tcArray.get(i).asJsonObject
                                val toolUseObj = JsonObject().apply {
                                    addProperty("type", "tool_use")
                                    addProperty("id", tcObj.get("id")?.asString ?: "call_$i")
                                    addProperty("name", tcObj.get("name")?.asString ?: "")
                                    val argEl = tcObj.get("arguments")
                                    val argsStr = if (argEl != null && argEl.isJsonPrimitive) argEl.asString else argEl?.toString() ?: "{}"
                                    val argsJson = try { JsonParser.parseString(argsStr).asJsonObject } catch (_: Exception) { JsonObject() }
                                    add("input", argsJson)
                                }
                                contentArray.add(toolUseObj)
                            }
                        } catch (_: Exception) {}
                        m.add("content", contentArray)
                        msgArray.add(m)
                    } else {
                        m.addProperty("role", msg.role)
                        val contentArray = JsonArray()
                        val textContentObj = JsonObject()
                        textContentObj.addProperty("type", "text")
                        textContentObj.addProperty("text", msg.content)
                        contentArray.add(textContentObj)
                        m.add("content", contentArray)
                        msgArray.add(m)
                    }
                }

                val payload = JsonObject()
                payload.addProperty("model", model)
                payload.add("messages", msgArray)
                if (systemPrompt.isNotEmpty()) {
                    payload.addProperty("system", systemPrompt.trim())
                }
                payload.addProperty("stream", true)
                payload.addProperty("max_tokens", 4096)

                if (shouldIncludeTools(messages, tools)) {
                    val toolsArray = JsonArray()
                    for (tool in tools!!) {
                        val t = JsonObject()
                        t.addProperty("name", tool.name)
                        t.addProperty("description", tool.description)
                        
                        val params = JsonObject()
                        params.addProperty("type", "object")
                        val properties = JsonObject()
                        val required = JsonArray()
                        
                        val schemaProps = tool.inputSchema["properties"] as? Map<*, *>
                        schemaProps?.forEach { (k, v) ->
                            val propKey = k.toString()
                            val propVal = v as? Map<*, *>
                            val propObj = JsonObject()
                            propObj.addProperty("type", (propVal?.get("type") ?: "string").toString())
                            propObj.addProperty("description", (propVal?.get("description") ?: "").toString())
                            properties.add(propKey, propObj)
                            
                            val isReq = (tool.inputSchema["required"] as? List<*>)?.contains(propKey) ?: false
                            if (isReq) {
                                required.add(propKey)
                            }
                        }
                        params.add("properties", properties)
                        if (required.size() > 0) {
                            params.add("required", required)
                        }
                        t.add("input_schema", params)
                        toolsArray.add(t)
                    }
                    payload.add("tools", toolsArray)
                }

                val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("content-type", "application/json")
                    .build()
                val call = client.newCall(request)
                coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                    call.cancel()
                }
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string()?.take(1024) ?: ""
                        if (ApiKeyRotator.isRotatableError(null, response.code, errBody)) {
                            throw RateLimitException("Anthropic", response.code, "Anthropic API Error ${response.code}: $errBody")
                        }
                        throw Exception("Anthropic API Error ${response.code}: $errBody")
                    }
                    val body = response.body ?: throw Exception("Empty response body")
                    val source = body.source()
                    var line: String?
                    
                    var currentToolCallId = ""
                    var currentToolName = ""
                    val currentToolArgs = StringBuilder()
                    val collectedAnthropicText = StringBuilder()
                    var anthropicInputTokens = 0
                    var anthropicOutputTokens = 0

                    while (source.readUtf8Line().also { line = it } != null) {
                        val cleaned = line!!.trim()
                        if (cleaned.startsWith("data: ")) {
                            val dataStr = cleaned.substring(6).trim()
                            if (dataStr.isEmpty()) continue
                            try {
                                val chunk = gson.fromJson(dataStr, JsonObject::class.java)
                                val type = ai.deepcode.android.util.SafeJson.string(chunk, "type")
                                
                                when (type) {
                                    "message_start" -> {
                                        val msgObj = ai.deepcode.android.util.SafeJson.obj(chunk, "message")
                                        val usageObj = ai.deepcode.android.util.SafeJson.obj(msgObj, "usage")
                                        if (usageObj != null) {
                                            anthropicInputTokens = usageObj.get("input_tokens")?.asInt ?: 0
                                        }
                                    }
                                    "message_delta" -> {
                                        val usageObj = ai.deepcode.android.util.SafeJson.obj(chunk, "usage")
                                        if (usageObj != null) {
                                            anthropicOutputTokens = usageObj.get("output_tokens")?.asInt ?: 0
                                        }
                                    }
                                    "content_block_start" -> {
                                        val block = ai.deepcode.android.util.SafeJson.obj(chunk, "content_block") ?: continue
                                        if (ai.deepcode.android.util.SafeJson.string(block, "type") == "tool_use") {
                                            currentToolCallId = ai.deepcode.android.util.SafeJson.string(block, "id") ?: ""
                                            currentToolName = ai.deepcode.android.util.SafeJson.string(block, "name") ?: ""
                                            currentToolArgs.setLength(0)
                                        }
                                    }
                                    "content_block_delta" -> {
                                        val delta = ai.deepcode.android.util.SafeJson.obj(chunk, "delta") ?: continue
                                        val text = ai.deepcode.android.util.SafeJson.string(delta, "text")
                                        if (text != null) {
                                            collectedAnthropicText.append(text)
                                            onToken(text)
                                        } else {
                                            val partial = ai.deepcode.android.util.SafeJson.string(delta, "partial_json")
                                            if (partial != null) {
                                                currentToolArgs.append(partial)
                                            }
                                        }
                                    }
                                    "content_block_stop" -> {
                                        if (currentToolName.isNotEmpty()) {
                                            onToolCall(ToolCall(currentToolCallId, currentToolName, currentToolArgs.toString()))
                                            currentToolName = ""
                                            currentToolCallId = ""
                                            currentToolArgs.setLength(0)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    }
                    if (anthropicInputTokens > 0 || anthropicOutputTokens > 0) {
                        onUsage?.invoke(TurnTokenUsage(anthropicInputTokens, anthropicOutputTokens, 0))
                    } else if (onUsage != null) {
                        val estimatedInput = (messages.sumOf { it.content.length } / 4).coerceAtLeast(10)
                        val estimatedOutput = (collectedAnthropicText.length / 4).coerceAtLeast(1)
                        onUsage.invoke(TurnTokenUsage(estimatedInput, estimatedOutput, 0))
                    }
                    onComplete(collectedAnthropicText.toString())
                }
            } catch (e: Throwable) {
                ai.deepcode.android.util.AppLogger.e("AnthropicProvider", "Anthropic stream failed", e)
                onError(e)
            }
        }
    }
}

// ==========================================
// 6. MISTRAL PROVIDER
// ==========================================
class MistralProvider : AIProvider {
    override val name = "Mistral AI"
    override val isFree = false
    override val models = listOf(
        AIModel("mistral-large-latest", "Mistral Large", "Mistral AI", false, "128k tokens", "Paid"),
        AIModel("mistral-small-latest", "Mistral Small", "Mistral AI", false, "32k tokens", "Paid")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey,
            baseUrl = resolveBaseUrl(customBaseUrl, "https://api.mistral.ai/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

// ==========================================
// 7. OLLAMA PROVIDER
// ==========================================
class OllamaCloudProvider : AIProvider {
    override val name = "Ollama Cloud"
    override val isFree = false
    override val models = listOf(
        AIModel("gemma4", "Gemma 4 31B", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("glm-4.7", "GLM 4.7", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("gpt-oss:20b", "GPT-OSS 20B", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("gpt-oss:120b", "GPT-OSS 120B", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("minimax-m2.1", "MiniMax M2.1", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("minimax-m2.5", "MiniMax M2.5", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("minimax-m3", "MiniMax M3", "Ollama Cloud", true, "1M tokens", "Free"),
        AIModel("nemotron-3-super", "Nemotron 3 Super 120B", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("nemotron-3-ultra", "Nemotron 3 Ultra", "Ollama Cloud", true, "128k tokens", "Free"),
        AIModel("qwen3-coder:480b", "Qwen 3 Coder 480B", "Ollama Cloud", true, "128k tokens", "Free")
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) {
        streamOpenAiCompatible(
            messages = messages,
            model = model,
            tools = tools,
            apiKey = apiKey.ifEmpty { "ollama" },
            baseUrl = resolveBaseUrl(customBaseUrl, "https://ollama.com/v1"),
            onToken = onToken,
            onToolCall = onToolCall,
            onComplete = onComplete,
            onError = onError,
            onUsage = onUsage
        )
    }
}

private fun resolveBaseUrl(customBaseUrl: String?, defaultUrl: String): String {
    if (!customBaseUrl.isNullOrEmpty()) {
        val trimmed = customBaseUrl.trim()
        if (trimmed.contains("10.0.2.2:20128") && !defaultUrl.contains("10.0.2.2:20128")) {
            return defaultUrl
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.trimEnd('/')
        }
    }
    return defaultUrl
}

private fun resolveImageToDataUrl(pathOrUrl: String): String? {
    if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("data:image/")) {
        return pathOrUrl
    }
    val file = File(pathOrUrl.removePrefix("file://"))
    if (!file.exists() || file.length() == 0L) return null
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val maxDim = 1536
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (newW, newH) = if (ratio > 1f) {
                maxDim to (maxDim / ratio).toInt()
            } else {
                (maxDim * ratio).toInt() to maxDim
            }
            Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64"
    } catch (e: Exception) {
        null
    }
}

// ==========================================
// UTILITY TO NORMALIZE AND MERGE MESSAGES FOR API COMPLIANCE
// ==========================================
private fun normalizeMessagesForApi(messages: List<Message>): JsonArray {
    val messagesArray = JsonArray()
    var activeAssistant: JsonObject? = null
    var lastToolCallId = ""

    fun flushAssistant() {
        activeAssistant?.let {
            messagesArray.add(it)
            activeAssistant = null
        }
    }

    for (msg in messages) {
        val role = msg.role
        when (role) {
            "user", "system" -> {
                flushAssistant()
                val imageRegex = Regex("""\[image:([^\]]+)\]""")
                val imageMatches = if (role == "user") imageRegex.findAll(msg.content).toList() else emptyList()
                if (role == "user" && imageMatches.isNotEmpty()) {
                    val cleanText = msg.content.replace(imageRegex, "").trim()
                    val parts = JsonArray()
                    if (cleanText.isNotEmpty()) {
                        parts.add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", cleanText)
                        })
                    }
                    for (match in imageMatches) {
                        val imgRef = match.groupValues[1].trim()
                        val dataUrl = resolveImageToDataUrl(imgRef)
                        if (!dataUrl.isNullOrEmpty()) {
                            parts.add(JsonObject().apply {
                                addProperty("type", "image_url")
                                add("image_url", JsonObject().apply {
                                    addProperty("url", dataUrl)
                                })
                            })
                        }
                    }
                    val userObj = JsonObject().apply {
                        addProperty("role", role)
                        if (parts.size() > 0) {
                            add("content", parts)
                        } else {
                            addProperty("content", msg.content)
                        }
                    }
                    messagesArray.add(userObj)
                } else {
                    val userObj = JsonObject().apply {
                        addProperty("role", role)
                        addProperty("content", msg.content)
                    }
                    messagesArray.add(userObj)
                }
            }
            "tool" -> {
                flushAssistant()
                val toolObj = JsonObject().apply {
                    addProperty("role", "tool")
                    addProperty("content", msg.content)
                    // Extract tool_call_id: could be a plain ID string or a JSON object {"id":"...","name":"..."}
                    val rawId = if (!msg.toolCallsJson.isNullOrEmpty()) msg.toolCallsJson else lastToolCallId
                    val id = try {
                        val parsed = JsonParser.parseString(rawId)
                        if (parsed.isJsonObject) parsed.asJsonObject.get("id")?.asString ?: rawId
                        else rawId
                    } catch (_: Exception) { rawId }
                    addProperty("tool_call_id", id)
                }
                messagesArray.add(toolObj)
            }
            "assistant" -> {
                val targetObj = activeAssistant ?: JsonObject().apply {
                    addProperty("role", "assistant")
                    activeAssistant = this
                }
                if (msg.isToolCall && !msg.toolCallsJson.isNullOrEmpty()) {
                    val tcArray = try {
                        val trimmed = msg.toolCallsJson.trim()
                        if (trimmed.startsWith("[")) {
                            gson.fromJson(trimmed, JsonArray::class.java)
                        } else {
                            JsonArray().apply {
                                add(gson.fromJson(trimmed, JsonObject::class.java))
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                    if (tcArray != null && tcArray.size() > 0) {
                        val toolCallsArray = targetObj.getAsJsonArray("tool_calls") ?: JsonArray().apply {
                            targetObj.add("tool_calls", this)
                        }
                        for (i in 0 until tcArray.size()) {
                            val tcObj = tcArray.get(i)?.asJsonObject ?: continue
                            val toolCallId = tcObj.get("id")?.asString ?: continue
                            lastToolCallId = toolCallId
                            val toolCallItem = JsonObject().apply {
                                addProperty("id", toolCallId)
                                addProperty("type", "function")
                                val funcObj = JsonObject().apply {
                                    if (tcObj.has("name") && !tcObj.get("name").isJsonNull) {
                                        val nameEl = tcObj.get("name")
                                        addProperty("name", if (nameEl.isJsonPrimitive) nameEl.asString else nameEl.toString())
                                    }
                                    if (tcObj.has("arguments") && !tcObj.get("arguments").isJsonNull) {
                                        val argEl = tcObj.get("arguments")
                                        val argStr = if (argEl.isJsonPrimitive) argEl.asString else gson.toJson(argEl)
                                        addProperty("arguments", argStr)
                                    }
                                }
                                add("function", funcObj)
                            }
                            toolCallsArray.add(toolCallItem)
                        }
                    } else if (!targetObj.has("content")) {
                        targetObj.addProperty("content", msg.toolCallsJson)
                    }
                } else {
                    // This is a text assistant message
                    if (!msg.content.isNullOrEmpty()) {
                        val existingContent = if (targetObj.has("content") && !targetObj.get("content").isJsonNull) {
                            val cEl = targetObj.get("content")
                            if (cEl.isJsonPrimitive) cEl.asString else cEl.toString()
                        } else {
                            ""
                        }
                        val newContent = if (existingContent.isNotEmpty()) {
                            existingContent + "\n" + msg.content
                        } else {
                            msg.content
                        }
                        targetObj.addProperty("content", newContent)
                    }
                    val hasReasoning = !msg.toolCallsJson.isNullOrEmpty()
                    if (hasReasoning) {
                        targetObj.addProperty("reasoning_content", msg.toolCallsJson)
                    }
                }
            }
        }
    }
    flushAssistant()
    
    // Final pass to ensure strict OpenAI/Zen specification compliance:
    // Every assistant message with 'tool_calls' MUST be immediately followed by tool messages for each 'tool_call_id'.
    val cleanArray = JsonArray()
    var idx = 0
    while (idx < messagesArray.size()) {
        val currentObj = messagesArray.get(idx).asJsonObject
        val rEl = currentObj.get("role")
        val role = if (rEl != null && rEl.isJsonPrimitive) rEl.asString else rEl?.toString() ?: ""

        if (role == "assistant") {
            if (currentObj.has("tool_calls") && !currentObj.has("content")) {
                currentObj.add("content", com.google.gson.JsonNull.INSTANCE)
            }
        }

        cleanArray.add(currentObj)

        if (role == "assistant" && currentObj.has("tool_calls")) {
            val toolCalls = currentObj.getAsJsonArray("tool_calls")
            if (toolCalls != null && toolCalls.size() > 0) {
                val requiredIds = mutableSetOf<String>()
                for (k in 0 until toolCalls.size()) {
                    val tc = toolCalls.get(k)?.asJsonObject ?: continue
                    val idEl = tc.get("id")
                    val tcId = if (idEl != null && idEl.isJsonPrimitive) idEl.asString else idEl?.toString()
                    if (!tcId.isNullOrEmpty()) requiredIds.add(tcId)
                }

                var nextIdx = idx + 1
                while (nextIdx < messagesArray.size()) {
                    val nextObj = messagesArray.get(nextIdx).asJsonObject
                    val nrEl = nextObj.get("role")
                    val nextRole = if (nrEl != null && nrEl.isJsonPrimitive) nrEl.asString else nrEl?.toString() ?: ""
                    if (nextRole == "tool") {
                        val tIdEl = nextObj.get("tool_call_id")
                        val toolCallId = if (tIdEl != null && tIdEl.isJsonPrimitive) tIdEl.asString else tIdEl?.toString() ?: ""
                        requiredIds.remove(toolCallId)
                        cleanArray.add(nextObj)
                        nextIdx++
                    } else {
                        break
                    }
                }

                for (missingId in requiredIds) {
                    val syntheticToolMsg = JsonObject().apply {
                        addProperty("role", "tool")
                        addProperty("tool_call_id", missingId)
                        addProperty("content", "Tool execution completed.")
                    }
                    cleanArray.add(syntheticToolMsg)
                }

                idx = nextIdx - 1
            }
        }
        idx++
    }

    return cleanArray
}

// ==========================================
// GENERIC OPENAI SSE STREAMING UTILITY
// ==========================================
private suspend fun streamOpenAiCompatible(
    messages: List<Message>,
    model: String,
    tools: List<Tool>?,
    apiKey: String,
    baseUrl: String,
    onToken: (String) -> Unit,
    onToolCall: (ToolCall) -> Unit,
    onComplete: (String) -> Unit,
    onError: (Throwable) -> Unit,
    onUsage: ((TurnTokenUsage) -> Unit)? = null
) {
    withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/chat/completions"
            val effectiveModel = DecommissionedModels.sanitize(model)
            if (effectiveModel != model) {
                ai.deepcode.android.util.AppLogger.w("AIProvider", "Proactively mapped decommissioned model '$model' to '$effectiveModel'")
            }

            val messagesArray = normalizeMessagesForApi(messages)

            // Insert drive instruction at the BEGINNING, not end — appending after tool
            // results breaks the required assistant(tool_calls)→tool(tool_call_id) sequence.
            val driveInstr = JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", "IMPORTANT: By default, all files you create with write_file are stored to Telegram Drive (cloud). Only use storage='local' when the user explicitly asks to save to their device. When listing files, use .tgdrive path to see cloud-stored files. When a file is not found locally, it is automatically checked on Telegram Drive.")
            }
            val finalMessages = JsonArray()
            finalMessages.add(driveInstr)
            for (i in 0 until messagesArray.size()) finalMessages.add(messagesArray.get(i))

            val payload = JsonObject()
            payload.addProperty("model", effectiveModel)
            payload.add("messages", finalMessages)
            payload.addProperty("stream", true)

            if (shouldIncludeTools(messages, tools)) {
                val toolsArray = JsonArray()
                for (tool in tools!!) {
                    val tObj = JsonObject()
                    tObj.addProperty("type", "function")
                    
                    val funcObj = JsonObject()
                    funcObj.addProperty("name", tool.name)
                    funcObj.addProperty("description", tool.description)
                    
                    val params = JsonObject()
                    params.addProperty("type", "object")
                    val properties = JsonObject()
                    val required = JsonArray()
                    
                    val schemaProps = tool.inputSchema["properties"] as? Map<*, *>
                    schemaProps?.forEach { (k, v) ->
                        val propKey = k.toString()
                        val propVal = v as? Map<*, *>
                        val propObj = JsonObject()
                        propObj.addProperty("type", (propVal?.get("type") ?: "string").toString())
                        propObj.addProperty("description", (propVal?.get("description") ?: "").toString())
                        properties.add(propKey, propObj)
                        
                        val isReq = (tool.inputSchema["required"] as? List<*>)?.contains(propKey) ?: false
                        if (isReq) {
                            required.add(propKey)
                        }
                    }
                    params.add("properties", properties)
                    if (required.size() > 0) {
                        params.add("required", required)
                    }
                    
                    funcObj.add("parameters", params)
                    tObj.add("function", funcObj)
                    toolsArray.add(tObj)
                }
                payload.add("tools", toolsArray)
            }

            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .header("User-Agent", if (url.contains("agentrouter", ignoreCase = true)) "codex_cli_rs/0.1.0" else "opencode/1.0")
                .build()

            val accumulatedReasoning = StringBuilder()
            val accumulatedContent = StringBuilder()
            val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()

            val call = client.newCall(request)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                call.cancel()
            }
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()?.take(1024) ?: ""
                    if (ApiKeyRotator.isRotatableError(null, response.code, errBody)) {
                        throw RateLimitException(effectiveModel, response.code, "API Error ${response.code}: $errBody")
                    }
                    // Auto-recovery for model_decommissioned (e.g. Groq HTTP 400)
                    if (response.code == 400 && (errBody.contains("model_decommissioned", ignoreCase = true) || errBody.contains("decommissioned", ignoreCase = true))) {
                        val replacement = DecommissionedModels.sanitize(effectiveModel)
                        val finalReplacement = if (replacement != effectiveModel) {
                            replacement
                        } else {
                            if (baseUrl.contains("groq", ignoreCase = true)) "llama-3.3-70b-versatile" else null
                        }
                        if (finalReplacement != null && finalReplacement != effectiveModel) {
                            ai.deepcode.android.util.AppLogger.w("AIProvider", "Model '$effectiveModel' decommissioned (HTTP 400). Transparently retrying with '$finalReplacement'...")
                            streamOpenAiCompatible(
                                messages = messages,
                                model = finalReplacement,
                                tools = tools,
                                apiKey = apiKey,
                                baseUrl = baseUrl,
                                onToken = onToken,
                                onToolCall = onToolCall,
                                onComplete = onComplete,
                                onError = onError,
                                onUsage = onUsage
                            )
                            return@withContext
                        }
                    }
                    throw Exception("API Error ${response.code}: $errBody")
                }
                val contentType = response.header("Content-Type") ?: ""
                val body = response.body ?: throw Exception("Empty response body")
                if (!contentType.contains("text/event-stream") && !contentType.contains("application/x-ndjson") && !contentType.contains("application/stream+json")) {
                    val bodyString = body.string()
                    try {
                        val json = JsonParser.parseString(bodyString).asJsonObject
                        val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                        val msg = choice?.getAsJsonObject("message")
                        val text = msg?.get("content")?.asString ?: ""
                        if (text.isNotEmpty()) onToken(text)
                        val tcArray = msg?.getAsJsonArray("tool_calls")
                        if (tcArray != null) {
                            for (i in 0 until tcArray.size()) {
                                val tc = tcArray.get(i).asJsonObject
                                val id = tc.get("id")?.asString ?: UUID.randomUUID().toString()
                                val func = tc.getAsJsonObject("function")
                                val name = func?.get("name")?.asString ?: ""
                                val args = func?.get("arguments")?.asString ?: "{}"
                                onToolCall(ToolCall(id, name, args))
                            }
                        }
                        onComplete(text)
                        return@withContext
                    } catch (e: Exception) {
                        throw Exception("Expected event stream but got: $contentType\nResponse: ${bodyString.take(500)}")
                    }
                }
                val source = body.source()
                var line: String?
                var usageInput = 0
                var usageOutput = 0
                var usageReasoning = 0

                while (source.readUtf8Line().also { line = it } != null) {
                    val cleaned = line!!.trim()
                    if (cleaned.startsWith("data: ")) {
                        val dataVal = cleaned.substring(6).trim()
                        if (dataVal == "[DONE]") {
                            break
                        }
                        if (dataVal.isEmpty()) continue

                        try {
                            val chunk = gson.fromJson(dataVal, JsonObject::class.java)
                            if (chunk.has("usage") && !chunk.get("usage").isJsonNull) {
                                val u = chunk.getAsJsonObject("usage")
                                usageInput = u.get("prompt_tokens")?.asInt ?: 0
                                usageOutput = u.get("completion_tokens")?.asInt ?: 0
                                usageReasoning = u.get("reasoning_tokens")?.asInt ?: 0
                            }
                            val choices = chunk.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val choice = choices.get(0).asJsonObject
                                val delta = choice.getAsJsonObject("delta")
                                if (delta != null) {
                                    val reasoningField = when {
                                        delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull -> "reasoning_content"
                                        delta.has("reasoning") && !delta.get("reasoning").isJsonNull -> "reasoning"
                                        else -> null
                                    }
                                    if (reasoningField != null) {
                                        val rEl = delta.get(reasoningField)
                                        val reasoningToken = if (rEl.isJsonPrimitive) rEl.asString else rEl.toString()
                                        accumulatedReasoning.append(reasoningToken)
                                    }
                                    if (delta.has("content") && !delta.get("content").isJsonNull) {
                                        val cEl = delta.get("content")
                                        val contentToken = if (cEl.isJsonPrimitive) cEl.asString else cEl.toString()
                                        onToken(contentToken)
                                        accumulatedContent.append(contentToken)
                                    }
                                    
                                    if (delta.has("tool_calls") && !delta.get("tool_calls").isJsonNull) {
                                        val tcArray = delta.getAsJsonArray("tool_calls") ?: continue
                                        for (i in 0 until tcArray.size()) {
                                            val tcElement = try { tcArray.get(i).asJsonObject } catch (_: Exception) { continue }
                                            val index = try { tcElement.get("index")?.asInt ?: 0 } catch (_: Exception) { 0 }
                                            val builder = toolCallBuilders.getOrPut(index) { ToolCallBuilder() }
                                            
                                            if (tcElement.has("id") && !tcElement.get("id").isJsonNull) {
                                                val idEl = tcElement.get("id")
                                                builder.id = if (idEl.isJsonPrimitive) idEl.asString else idEl.toString()
                                            }
                                            val function = tcElement.getAsJsonObject("function")
                                            if (function != null) {
                                                if (function.has("name") && !function.get("name").isJsonNull) {
                                                    val nameEl = function.get("name")
                                                    builder.name = if (nameEl.isJsonPrimitive) nameEl.asString else nameEl.toString()
                                                }
                                                if (function.has("arguments") && !function.get("arguments").isJsonNull) {
                                                    val argEl = function.get("arguments")
                                                    val argChunk = if (argEl.isJsonPrimitive) argEl.asString else gson.toJson(argEl)
                                                    builder.arguments.append(argChunk)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
                
                toolCallBuilders.values.forEach { builder ->
                    if (builder.name.isNotEmpty()) {
                        onToolCall(ToolCall(builder.getValidId(), builder.name, builder.arguments.toString()))
                    }
                }
                if (usageInput > 0 || usageOutput > 0) {
                    onUsage?.invoke(TurnTokenUsage(usageInput, usageOutput, usageReasoning))
                } else {
                    val estimatedInput = messages.sumOf { it.content.length / 4 }
                    val accumulatedText = accumulatedContent.toString()
                    val estimatedOutput = (accumulatedText.length / 4).coerceAtLeast(1)
                    onUsage?.invoke(TurnTokenUsage(estimatedInput, estimatedOutput, 0))
                }
            }
            onComplete(accumulatedContent.toString())
        } catch (e: Throwable) {
            ai.deepcode.android.util.AppLogger.e("AIProvider", "streamOpenAiCompatible failed", e)
            onError(e)
        }
    }
}

// 11. ANTIGRAVITY PROVIDER (Google Cloud Code OAuth)
// ==========================================
class AntigravityProvider : AIProvider {
    override val name = "Antigravity"
    override val isFree = false
    override val models = listOf(
        // Claude models (served through Antigravity / Vertex AI)
        AIModel("claude-sonnet-5", "Claude Sonnet 5 (Thinking)", "Antigravity", false, "200k tokens", "Paid"),
        AIModel("claude-opus-4-6-thinking", "Claude Opus 4.6 (Thinking)", "Antigravity", false, "200k tokens", "Paid"),
        AIModel("claude-sonnet-4-6", "Claude Sonnet 4.6 (Thinking)", "Antigravity", false, "200k tokens", "Paid"),
        // Gemini models
        AIModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Antigravity", true, "1M tokens", "Free"),
        AIModel("gemini-3.8-flash-cyber", "Gemini 3.8 Flash Cyber", "Antigravity", false, "1M tokens", "Paid"),
        AIModel("gemini-3-flash-agent", "Gemini 3.5 Flash (High)", "Antigravity", true, "1M tokens", "Free"),
        AIModel("gemini-3.5-flash-low", "Gemini 3.5 Flash (Low)", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-3.5-flash-medium", "Gemini 3.5 Flash (Medium)", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-3-pro-preview", "Gemini 3.1 Pro", "Antigravity", false, "1M tokens", "Paid"),
        AIModel("gemini-3.1-pro-high", "Gemini 3.1 Pro (High)", "Antigravity", false, "1M tokens", "Paid"),
        AIModel("gemini-3.1-pro-low", "Gemini 3.1 Pro (Low)", "Antigravity", false, "1M tokens", "Paid"),
        AIModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-2.5-pro", "Gemini 2.5 Pro", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-2.5-flash-thinking", "Gemini 2.5 Flash Thinking", "Antigravity", false, "1M tokens", "Free"),
        AIModel("gemini-2.0-flash-exp", "Gemini 2.0 Flash (Exp)", "Antigravity", false, "1M tokens", "Free"),
        // OSS models
        AIModel("gpt-oss-120b-medium", "GPT-OSS 120B (Medium)", "Antigravity", false, "128k tokens", "Paid"),
    )

    override suspend fun streamCompletion(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        apiKey: String,
        customBaseUrl: String?,
        onToken: (String) -> Unit,
        onToolCall: (ToolCall) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onUsage: ((TurnTokenUsage) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        try {
            val baseUrl = resolveBaseUrl(customBaseUrl, "https://daily-cloudcode-pa.googleapis.com")
            val url = "$baseUrl/v1internal:streamGenerateContent?alt=sse"
            val parts2 = apiKey.split("||", limit = 2)
            val oauthToken = parts2[0]

            if (oauthToken.isBlank() || !oauthToken.startsWith("ya29.")) {
                onError(Exception("Antigravity token is invalid or missing. Please sign in again via Settings → API Keys. (token starts with: ${oauthToken.take(10)})"))
                return@withContext
            }

            val envelope = buildAntigravityEnvelope(messages, model, tools, oauthToken)
            val bodyJson = gson.toJson(envelope)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $oauthToken")
                .header("User-Agent", "Antigravity/4.2.0 (X11; Linux x86_64) Chrome/142.0.7444.175 Electron/39.2.3")
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    if (ApiKeyRotator.isRotatableError(null, response.code, errBody)) {
                        onError(RateLimitException("Antigravity", response.code, "Antigravity API error: HTTP ${response.code} $errBody"))
                    } else {
                        onError(Exception("Antigravity API error: HTTP ${response.code} $errBody"))
                    }
                    return@withContext
                }

                val bodyStream = response.body?.byteStream()
            if (bodyStream == null) {
                onError(Exception("Empty response body from Antigravity"))
                return@withContext
            }
            val reader = BufferedReader(InputStreamReader(bodyStream))
            var collectedText = StringBuilder()
            var hasError = false
            var debugLines = mutableListOf<String>()
            val maxDebugLines = 5
            var toolCallCounter = 0
            var hasFunctionCalls = false

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) return@forEachLine
                val payload = trimmed.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") return@forEachLine
                if (debugLines.size < maxDebugLines) debugLines.add(payload.take(300))
                try {
                    val json = gson.fromJson(payload, JsonObject::class.java)

                    if (json.has("error")) {
                        hasError = true
                        val err = json.getAsJsonObject("error")
                        val msg = err.get("message")?.asString ?: err.get("status")?.asString ?: "Antigravity API error"
                        onError(Exception(msg))
                        return@forEachLine
                    }

                    if (json.has("markdown")) {
                        val text = json.get("markdown").asString ?: ""
                        if (text.isNotEmpty()) {
                            onToken(text)
                            collectedText.append(text)
                        }
                        return@forEachLine
                    }

                    val resp = json.getAsJsonObject("response")
                    if (resp != null) {
                        val candidates = resp.getAsJsonArray("candidates")
                        if (candidates != null && candidates.size() > 0) {
                            val candidate = candidates[0].asJsonObject
                            val content = candidate.getAsJsonObject("content")
                            if (content != null) {
                                val parts = content.getAsJsonArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.size()) {
                                        val part = parts[i].asJsonObject
                                        if (part.has("text") && !part.has("thought") && !part.has("thoughtSignature")) {
                                            val text = part.get("text").asString ?: ""
                                            if (text.isNotEmpty()) {
                                                onToken(text)
                                                collectedText.append(text)
                                            }
                                        }
                                        if (part.has("functionCall")) {
                                            val fc = part.getAsJsonObject("functionCall")
                                            val name = fc.get("name")?.asString ?: ""
                                            val args = fc.get("args")?.asJsonObject
                                            if (name.isNotEmpty()) {
                                                hasFunctionCalls = true
                                                val argsStr = args?.toString() ?: "{}"
                                                onToolCall(ToolCall(id = "tc_${toolCallCounter++}", name = name, arguments = argsStr))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (resp.has("usageMetadata")) {
                            val u = resp.getAsJsonObject("usageMetadata")
                            onUsage?.invoke(TurnTokenUsage(
                                inputTokens = u.get("promptTokenCount")?.asInt ?: 0,
                                outputTokens = u.get("candidatesTokenCount")?.asInt ?: 0
                            ))
                        }
                    }
                } catch (e: Exception) {
                    if (!hasError) {
                        hasError = true
                        onError(Exception("Antigravity parse error: ${e.message}"))
                    }
                }
            }
            reader.close()
            if (!hasError) {
                if (collectedText.isEmpty() && !hasFunctionCalls && debugLines.isNotEmpty()) {
                    onError(Exception("Antigravity returned no text. Raw events:\n${debugLines.joinToString("\n")}"))
                } else {
                    onComplete(collectedText.toString())
                }
            }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun buildAntigravityEnvelope(
        messages: List<Message>,
        model: String,
        tools: List<Tool>?,
        oauthToken: String
    ): JsonObject {
        val envelope = JsonObject()
        envelope.addProperty("model", model)
        envelope.addProperty("userAgent", "Antigravity/4.2.0 (X11; Linux x86_64) Chrome/142.0.7444.175 Electron/39.2.3")
        envelope.addProperty("requestType", "agent")
        envelope.addProperty("requestId", UUID.randomUUID().toString())
        envelope.add("enabledCreditTypes", JsonArray().apply { add("GOOGLE_ONE_AI") })

        val requestObj = JsonObject()
        val contents = JsonArray()
        val systemText = StringBuilder()

        for (msg in messages) {
            when (msg.role) {
                "system" -> {
                    if (systemText.isNotEmpty()) systemText.append("\n")
                    systemText.append(msg.content)
                }
                "user" -> {
                    val c = JsonObject().apply {
                        addProperty("role", "user")
                        val parts = JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", msg.content) })
                        }
                        add("parts", parts)
                    }
                    contents.add(c)
                }
                "tool" -> {
                    val c = JsonObject().apply {
                        addProperty("role", "user")
                        val parts = JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", "Tool result: ${msg.content}") })
                        }
                        add("parts", parts)
                    }
                    contents.add(c)
                }
                "assistant" -> {
                    if (msg.isToolCall && !msg.toolCallsJson.isNullOrEmpty()) {
                        val toolCallsArray = try {
                            gson.fromJson(msg.toolCallsJson, JsonArray::class.java)
                        } catch (e: Exception) { null }
                        if (toolCallsArray != null && toolCallsArray.size() > 0) {
                            for (i in 0 until toolCallsArray.size()) {
                                val tcObj = toolCallsArray[i].asJsonObject
                                val id = tcObj.get("id")?.asString ?: ""
                                val name = tcObj.get("name")?.asString ?: ""
                                val argEl = tcObj.get("arguments")
                                val argsStr = if (argEl != null && argEl.isJsonPrimitive) argEl.asString else argEl?.toString() ?: "{}"
                                val args = try { gson.fromJson(argsStr, JsonObject::class.java) } catch (e: Exception) { JsonObject() }
                                val c = JsonObject().apply {
                                    addProperty("role", "model")
                                    val parts = JsonArray().apply {
                                        add(JsonObject().apply {
                                            add("functionCall", JsonObject().apply {
                                                addProperty("name", name)
                                                add("args", args)
                                            })
                                        })
                                    }
                                    add("parts", parts)
                                }
                                contents.add(c)
                            }
                        } else {
                            val c = JsonObject().apply {
                                addProperty("role", "model")
                                val parts = JsonArray().apply {
                                    add(JsonObject().apply { addProperty("text", msg.content) })
                                }
                                add("parts", parts)
                            }
                            contents.add(c)
                        }
                    } else {
                        val c = JsonObject().apply {
                            addProperty("role", "model")
                            val parts = JsonArray().apply {
                                add(JsonObject().apply { addProperty("text", msg.content) })
                            }
                            add("parts", parts)
                        }
                        contents.add(c)
                    }
                }
            }
        }

        if (shouldIncludeTools(messages, tools)) {
            val functionsArray = JsonArray()
            for (tool in tools!!) {
                val func = JsonObject()
                func.addProperty("name", tool.name)
                func.addProperty("description", tool.description)
                try {
                    val schemaJson = gson.toJsonTree(tool.inputSchema)
                    func.add("parameters", schemaJson)
                } catch (e: Exception) {
                    func.add("parameters", JsonObject())
                }
                functionsArray.add(func)
            }
            requestObj.add("tools", JsonArray().apply {
                add(JsonObject().apply {
                    add("functionDeclarations", functionsArray)
                })
            })
        }

        if (systemText.isNotEmpty()) {
            requestObj.add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemText.toString()) })
                })
            })
        }
        requestObj.add("contents", contents)

        val genConfig = JsonObject().apply {
            addProperty("temperature", 0.7)
            addProperty("maxOutputTokens", 16384)
        }
        requestObj.add("generationConfig", genConfig)

        val safetySettings = JsonArray().apply {
            for (cat in listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT")) {
                add(JsonObject().apply {
                    addProperty("category", cat)
                    addProperty("threshold", "BLOCK_NONE")
                })
            }
        }
        requestObj.add("safetySettings", safetySettings)
        envelope.add("request", requestObj)
        return envelope
    }
}

private class ToolCallBuilder(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder()
) {
    fun getValidId(): String = id.ifEmpty { "call_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12) }
}

val PROVIDER_BASE_URLS = mapOf(
    "Google Gemini" to "https://generativelanguage.googleapis.com",
    "Gemini" to "https://generativelanguage.googleapis.com",
    "Omniroute" to "http://10.0.2.2:20128/v1",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "Zen AI" to "https://opencode.ai/zen/v1",
    "Zen" to "https://opencode.ai/zen/v1",
    "Zen (Free)" to "https://opencode.ai/zen/v1",
    "Groq" to "https://api.groq.com/openai/v1",
    "Cerebrus" to "https://api.cerebrus.com/v1",
    "Cerebras" to "https://api.cerebras.ai/v1",
    "Mistral" to "https://api.mistral.ai/v1",
    "Antigravity" to "https://daily-cloudcode-pa.googleapis.com",
    "OpenAI" to "https://api.openai.com/v1",
    "Anthropic" to "https://api.anthropic.com/v1",
    "DeepSeek" to "https://api.deepseek.com/v1",
    "Together AI" to "https://api.together.xyz/v1",
    "Perplexity" to "https://api.perplexity.ai",
    "xAI" to "https://api.x.ai/v1",
    "Cohere" to "https://api.cohere.com/v1",
    "DeepInfra" to "https://api.deepinfra.com/v1/openai",
    "Fireworks AI" to "https://api.fireworks.ai/inference/v1",
    "NVIDIA NIM" to "https://integrate.api.nvidia.com/v1",
    "SambaNova" to "https://api.sambanova.ai/v1",
    "Hyperbolic" to "https://api.hyperbolic.xyz/v1",
    "GitHub Models" to "https://models.inference.ai.azure.com",
    "Novita AI" to "https://api.novita.ai/v1",
    "SiliconFlow" to "https://api.siliconflow.cn/v1",
    "Agent Router" to "https://agentrouter.org/v1",
    "GMI Cloud" to "https://api.gmi-serving.com/v1",
    "Ollama Cloud" to "https://ollama.com/v1",
    "Ollama" to "https://ollama.com/v1",
)

fun providerDefaultBaseUrl(providerName: String): String =
    PROVIDER_BASE_URLS[providerName] ?: "https://api.openai.com/v1"

fun providerStorageId(providerName: String): String = when (providerName) {
    "OpenAI" -> "openai"
    "Anthropic" -> "anthropic"
    "Zen AI" -> "zen"
    "Zen" -> "zen"
    "Zen (Free)" -> "zen"
    "Google Gemini" -> "gemini"
    "Gemini" -> "gemini"
    "Groq" -> "groq"
    "Cerebrus" -> "cerebrus"
    "OpenRouter" -> "openrouter"
    "Omniroute" -> "omniroute"
    "Mistral AI" -> "mistral"
    "Mistral" -> "mistral"
    "Ollama" -> "ollama"
    "OllamaCloud" -> "ollamacloud"
    "Antigravity" -> "antigravity"
    "DeepSeek" -> "deepseek"
    "Together AI" -> "together"
    "Perplexity" -> "perplexity"
    "xAI" -> "xai"
    "Cohere" -> "cohere"
    "DeepInfra" -> "deepinfra"
    "Fireworks AI" -> "fireworks"
    "NVIDIA NIM" -> "nvidia"
    "SambaNova" -> "sambanova"
    "Hyperbolic" -> "hyperbolic"
    "GitHub Models" -> "github-models"
    "Novita AI" -> "novita"
    "SiliconFlow" -> "siliconflow"
    "Agent Router" -> "agentrouter"
    "GMI Cloud" -> "gmi"
    else -> providerName.lowercase().replace(" ", "-")
}

object ModelCatalog {
    private val _models = MutableStateFlow<Map<String, List<AIModel>>>(emptyMap())
    val models: StateFlow<Map<String, List<AIModel>>> = _models
    private val gson = com.google.gson.Gson()

    fun filterValidModels(models: List<AIModel>?): List<AIModel> {
        return DecommissionedModels.filterValidModels(models)
    }

    fun loadFromPrefs(prefs: ai.deepcode.android.data.local.EncryptedPrefs) {
        try {
            val updated = _models.value.toMutableMap()
            var changed = false
            for (provider in AIProviderFactory.providers) {
                if (!updated.containsKey(provider.name)) {
                    val raw = prefs.getSetting("cached_models_${provider.name}", "")
                    if (raw.isNotEmpty()) {
                        val type = object : com.google.gson.reflect.TypeToken<List<AIModel>>() {}.type
                        val list: List<AIModel>? = gson.fromJson(raw, type)
                        val filtered = filterValidModels(list)
                        if (filtered.isNotEmpty()) {
                            updated[provider.name] = filtered
                            changed = true
                        }
                    }
                }
            }
            if (changed) {
                _models.value = updated
            }
        } catch (_: Exception) {}
    }

    fun setModels(providerName: String, fetchedModels: List<AIModel>, prefs: ai.deepcode.android.data.local.EncryptedPrefs? = null) {
        val filtered = filterValidModels(fetchedModels)
        _models.value = _models.value + (providerName to filtered)
        if (prefs != null && filtered.isNotEmpty()) {
            try {
                prefs.saveSetting("cached_models_$providerName", gson.toJson(filtered))
            } catch (_: Exception) {}
        }
    }

    fun getModelsForProvider(providerName: String, prefs: ai.deepcode.android.data.local.EncryptedPrefs? = null): List<AIModel> {
        val inMem = filterValidModels(_models.value[providerName])
        if (inMem.isNotEmpty()) return inMem
        if (prefs != null) {
            val raw = prefs.getSetting("cached_models_$providerName", "")
            if (raw.isNotEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<AIModel>>() {}.type
                    val list: List<AIModel>? = gson.fromJson(raw, type)
                    val filtered = filterValidModels(list)
                    if (filtered.isNotEmpty()) {
                        _models.value = _models.value + (providerName to filtered)
                        return filtered
                    }
                } catch (_: Exception) {}
            }
        }
        return emptyList()
    }

    fun clearProvider(providerName: String, prefs: ai.deepcode.android.data.local.EncryptedPrefs? = null) {
        _models.value = _models.value - providerName
        prefs?.saveSetting("cached_models_$providerName", "")
    }
}

fun formatModelTitle(rawId: String): String {
    val id = rawId.split("/").lastOrNull() ?: rawId
    val isFree = id.contains("free", ignoreCase = true)
    val base = id.removeSuffix("-free").removeSuffix(":free")
    val words = base.split("-", "_", ".").filter { it.isNotEmpty() }
    val formattedWords = words.map { word ->
        when (word.lowercase()) {
            "gpt" -> "GPT"
            "glm" -> "GLM"
            "qwen" -> "Qwen"
            "claude" -> "Claude"
            "gemini" -> "Gemini"
            "deepseek" -> "DeepSeek"
            "grok" -> "Grok"
            "kimi" -> "Kimi"
            "minimax" -> "MiniMax"
            "nemotron" -> "Nemotron"
            "mimo" -> "Mimo"
            "ling" -> "Ling"
            "muse" -> "Muse"
            "spark" -> "Spark"
            "laguna" -> "Laguna"
            "coder", "code" -> "Coder"
            "pro" -> "Pro"
            "flash" -> "Flash"
            "plus" -> "Plus"
            "max" -> "Max"
            "mini" -> "Mini"
            "nano" -> "Nano"
            "sol" -> "Sol"
            "terra" -> "Terra"
            "luna" -> "Luna"
            "contributor" -> "Contributor"
            "ultra" -> "Ultra"
            "lightning" -> "Lightning"
            "fin" -> "Fin"
            else -> word.replaceFirstChar { it.uppercase() }
        }
    }
    val title = formattedWords.joinToString(" ")
    return if (isFree) "$title (Free)" else title
}

suspend fun fetchModels(apiKey: String, baseUrl: String, providerName: String): List<AIModel> {
    return withContext(Dispatchers.IO) {
        try {
            val isGemini = providerName.contains("Gemini", ignoreCase = true) || baseUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)
            val isZen = providerName.contains("Zen", ignoreCase = true) || baseUrl.contains("opencode.ai/zen", ignoreCase = true)
            val url = if (isGemini) {
                "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            } else {
                baseUrl.trimEnd('/') + "/models"
            }

            val ua = if (providerName.contains("Agent Router", ignoreCase = true) || url.contains("agentrouter", ignoreCase = true)) "codex_cli_rs/0.1.0" else "opencode/1.0"
            val reqBuilder = Request.Builder().url(url).header("User-Agent", ua)
            if (!isGemini && (!isZen || (apiKey.isNotBlank() && apiKey != "zen-free"))) {
                reqBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            val request = reqBuilder.build()

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val response: Response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.w("ModelCatalog", "fetchModels $providerName: HTTP ${response.code} ${response.message}")
                return@withContext emptyList()
            }
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(bodyStr)
            val result = mutableListOf<AIModel>()

            if (isGemini) {
                val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()
                for (i in 0 until modelsArray.length()) {
                    val obj = modelsArray.getJSONObject(i)
                    val rawName = obj.optString("name", "")
                    val id = rawName.removePrefix("models/")
                    if (id.isBlank() || DecommissionedModels.isDecommissioned(id)) continue
                    val displayName = obj.optString("displayName", id)
                    val methods = obj.optJSONArray("supportedGenerationMethods")
                    var supportsGen = false
                    if (methods != null) {
                        for (m in 0 until methods.length()) {
                            if (methods.getString(m) == "generateContent") {
                                supportsGen = true
                                break
                            }
                        }
                    }
                    if (supportsGen) {
                        val isFree = id.contains("flash", ignoreCase = true)
                        val inputTokenLimit = obj.optInt("inputTokenLimit", 0)
                        val ctxStr = if (inputTokenLimit > 0) "${inputTokenLimit / 1000}k tokens" else "1M tokens"
                        result.add(AIModel(
                            id = id,
                            name = displayName,
                            provider = "Google Gemini",
                            isFree = isFree,
                            contextWindow = ctxStr,
                            badge = if (isFree) "Free" else "Paid"
                        ))
                    }
                }
            } else {
                val data = json.optJSONArray("data") ?: return@withContext emptyList()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isBlank()) continue
                    // Filter out decommissioned, deprecated, inactive, or shutdown models
                    if (DecommissionedModels.isJsonModelInactiveOrDecommissioned(obj)) {
                        AppLogger.d("ModelCatalog", "Skipping decommissioned/inactive model: $id for $providerName")
                        continue
                    }
                    val isFree = id.contains("free", ignoreCase = true)
                    val formattedName = formatModelTitle(id)
                    val ctxInt = obj.optInt("context_window", 0).let { if (it > 0) it else obj.optInt("context_length", 0) }
                    val ctxStr = if (ctxInt > 0) {
                        if (ctxInt >= 1000000) "${ctxInt / 1000000}M tokens" else "${ctxInt / 1000}k tokens"
                    } else {
                        obj.optString("context_length", "")
                    }
                    result.add(AIModel(
                        id = id,
                        name = formattedName,
                        provider = providerName,
                        isFree = isFree,
                        contextWindow = ctxStr,
                        badge = if (isFree) "Free" else "Paid"
                    ))
                }
            }
            AppLogger.i("ModelCatalog", "Fetched ${result.size} models for $providerName")
            result
        } catch (e: Exception) {
            AppLogger.w("ModelCatalog", "fetchModels $providerName error: ${e.message}")
            emptyList()
        }
    }
}

suspend fun fetchAntigravityModels(oauthToken: String): List<AIModel> = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("https://daily-cloudcode-pa.googleapis.com/v1internal:fetchAvailableModels")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $oauthToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext emptyList()
        val bodyStr = response.body?.string() ?: return@withContext emptyList()
        val json = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
        val modelsObj = json.getAsJsonObject("models") ?: return@withContext emptyList()
        val result = mutableListOf<AIModel>()
        for (entry in modelsObj.entrySet()) {
            val modelId = entry.key
            if (DecommissionedModels.isDecommissioned(modelId)) continue
            val info = entry.value.asJsonObject
            val displayName = info.get("displayName")?.asString ?: modelId
            val maxTokens = info.get("maxTokens")?.asInt ?: 0
            val ctxStr = if (maxTokens > 0) "${maxTokens / 1000}k tokens" else ""
            val supportsFree = info.get("quotaInfo")?.asJsonObject?.get("remainingFraction")?.asFloat ?: 0f > 0
            result.add(AIModel(
                id = modelId,
                name = displayName,
                provider = "Antigravity",
                isFree = supportsFree,
                contextWindow = ctxStr,
                badge = if (supportsFree) "Free" else "Paid"
            ))
        }
        result.sortedByDescending { it.isFree }
    } catch (e: Exception) {
        AppLogger.w("ModelCatalog", "fetchAntigravityModels error: ${e.message}")
        emptyList()
    }
}