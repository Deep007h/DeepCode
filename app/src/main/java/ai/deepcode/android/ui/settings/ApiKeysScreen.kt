package ai.deepcode.android.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.deepcode.android.data.remote.AIProviderFactory
import ai.deepcode.android.data.remote.ModelCatalog
import ai.deepcode.android.data.remote.OPENAI_PROVIDERS
import ai.deepcode.android.data.remote.fetchModels
import ai.deepcode.android.data.remote.providerDefaultBaseUrl
import ai.deepcode.android.data.remote.providerStorageId
import ai.deepcode.android.domain.model.AIModel
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.service.google.GoogleAuthService
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ProviderDef(
    val id: String,
    val name: String,
    val category: String,
    val authType: String = "apikey"
)

private val ALL_PROVIDERS = listOf(
    // ── Popular ──
    ProviderDef("zen", "Zen AI", "Popular", "apikey"),
    ProviderDef("openai", "OpenAI", "Popular", "apikey"),
    ProviderDef("anthropic", "Anthropic", "Popular", "apikey"),
    ProviderDef("gemini", "Google Gemini", "Popular", "apikey"),
    ProviderDef("groq", "Groq", "Popular", "apikey"),
    ProviderDef("mistral", "Mistral AI", "Popular", "apikey"),
    ProviderDef("cerebras", "Cerebras", "Popular", "apikey"),
    ProviderDef("deepseek", "DeepSeek", "Popular", "apikey"),
    ProviderDef("openrouter", "OpenRouter", "Popular", "apikey"),
    ProviderDef("claude", "Claude", "Popular", "apikey"),
    ProviderDef("cohere", "Cohere", "Popular", "apikey"),
    ProviderDef("tinyfish", "TinyFish (Search & Fetch)", "Popular", "apikey"),

    // ── OpenAI-Compatible (API Key) ──
    ProviderDef("aimlapi", "AIML API", "OpenAI", "apikey"),
    ProviderDef("together", "Together AI", "OpenAI", "apikey"),
    ProviderDef("deepinfra", "DeepInfra", "OpenAI", "apikey"),
    ProviderDef("fireworks", "Fireworks AI", "OpenAI", "apikey"),
    ProviderDef("nvidia", "NVIDIA NIM", "OpenAI", "apikey"),
    ProviderDef("nebius", "Nebius AI", "OpenAI", "apikey"),
    ProviderDef("sambanova", "SambaNova", "OpenAI", "apikey"),
    ProviderDef("hyperbolic", "Hyperbolic", "OpenAI", "apikey"),
    ProviderDef("synthetic", "Synthetic", "OpenAI", "apikey"),
    ProviderDef("friendliai", "Friendli AI", "OpenAI", "apikey"),
    ProviderDef("featherless-ai", "Featherless AI", "OpenAI", "apikey"),
    ProviderDef("liquid", "Liquid AI", "OpenAI", "apikey"),
    ProviderDef("siliconflow", "SiliconFlow", "OpenAI", "apikey"),
    ProviderDef("novita", "Novita AI", "OpenAI", "apikey"),
    ProviderDef("scaleway", "Scaleway", "OpenAI", "apikey"),
    ProviderDef("perplexity", "Perplexity", "OpenAI", "apikey"),
    ProviderDef("monsterapi", "Monster API", "OpenAI", "apikey"),
    ProviderDef("modelscope", "ModelScope", "OpenAI", "apikey"),
    ProviderDef("sensenova", "SenseNova", "OpenAI", "apikey"),
    ProviderDef("lambda-ai", "Lambda AI", "OpenAI", "apikey"),
    ProviderDef("kluster", "Kluster AI", "OpenAI", "apikey"),
    ProviderDef("galadriel", "Galadriel", "OpenAI", "apikey"),
    ProviderDef("qianfan", "Qianfan", "OpenAI", "apikey"),
    ProviderDef("meta-llama", "Meta Llama", "OpenAI", "apikey"),
    ProviderDef("nous-research", "Nous Research", "OpenAI", "apikey"),
    ProviderDef("alibaba", "Alibaba", "OpenAI", "apikey"),
    ProviderDef("ai21", "AI21 Labs", "OpenAI", "apikey"),
    ProviderDef("huggingface", "HuggingFace", "OpenAI", "apikey"),
    ProviderDef("freeaiapikey", "Free AI API Key", "OpenAI", "apikey"),
    ProviderDef("publicai", "Public AI", "OpenAI", "apikey"),
    ProviderDef("venice", "Venice AI", "OpenAI", "apikey"),
    ProviderDef("tokenrouter", "TokenRouter", "OpenAI", "apikey"),
    ProviderDef("requesty", "Requesty", "OpenAI", "apikey"),
    ProviderDef("digitalocean", "DigitalOcean", "OpenAI", "apikey"),
    ProviderDef("hcnsec", "HCNSec", "OpenAI", "apikey"),
    ProviderDef("openadapter", "OpenAdapter", "OpenAI", "apikey"),
    ProviderDef("agentrouter", "AgentRouter", "OpenAI", "apikey"),
    ProviderDef("gmi", "GMI Cloud", "OpenAI", "apikey"),
    ProviderDef("baseten", "Baseten", "OpenAI", "apikey"),
    ProviderDef("heroku", "Heroku", "OpenAI", "apikey"),
    ProviderDef("maritalk", "MariTalk", "OpenAI", "apikey"),
    ProviderDef("bluesminds", "BlueSminds", "OpenAI", "apikey"),
    ProviderDef("snowflake", "Snowflake", "OpenAI", "apikey"),
    ProviderDef("databricks", "Databricks", "OpenAI", "apikey"),
    ProviderDef("reka", "Reka AI", "OpenAI", "apikey"),
    ProviderDef("modal", "Modal", "OpenAI", "apikey"),
    ProviderDef("chutes", "Chutes", "OpenAI", "apikey"),
    ProviderDef("factory", "Factory AI", "OpenAI", "apikey"),
    ProviderDef("x5lab", "X5 Lab", "OpenAI", "apikey"),
    ProviderDef("kenari", "Kenari", "OpenAI", "apikey"),
    ProviderDef("pioneer", "Pioneer", "OpenAI", "apikey"),
    ProviderDef("sumopod", "SumoPod", "OpenAI", "apikey"),
    ProviderDef("wafer", "Wafer", "OpenAI", "apikey"),
    ProviderDef("dit", "DIT", "OpenAI", "apikey"),
    ProviderDef("morph", "Morph AI", "OpenAI", "apikey"),
    ProviderDef("nanogpt", "NanoGPT", "OpenAI", "apikey"),
    ProviderDef("zai", "ZAI", "OpenAI", "apikey"),
    ProviderDef("inclusionai", "Inclusion AI", "OpenAI", "apikey"),
    ProviderDef("llamagate", "LlamaGate", "OpenAI", "apikey"),
    ProviderDef("inference-net", "Inference.net", "OpenAI", "apikey"),
    ProviderDef("llm7", "LLM7", "OpenAI", "apikey"),
    ProviderDef("charm-hyper", "Charm Hyper", "OpenAI", "apikey"),
    ProviderDef("nube", "Nube", "OpenAI", "apikey"),
    ProviderDef("sparkdesk", "SparkDesk", "OpenAI", "apikey"),
    ProviderDef("api-airforce", "API AirForce", "OpenAI", "apikey"),
    ProviderDef("hackclub", "Hack Club", "OpenAI", "apikey"),
    ProviderDef("tencent", "Tencent", "OpenAI", "apikey"),
    ProviderDef("coze", "Coze", "OpenAI", "apikey"),
    ProviderDef("yi", "Yi", "OpenAI", "apikey"),
    ProviderDef("baichuan", "Baichuan", "OpenAI", "apikey"),
    ProviderDef("qwen", "Qwen", "OpenAI", "apikey"),
    ProviderDef("minimax", "MiniMax", "OpenAI", "apikey"),
    ProviderDef("moonshot", "Moonshot", "OpenAI", "apikey"),
    ProviderDef("stepfun", "StepFun", "OpenAI", "apikey"),
    ProviderDef("doubao", "DouBao", "OpenAI", "apikey"),
    ProviderDef("bailian-coding-plan", "Bailian Coding", "OpenAI", "apikey"),
    ProviderDef("volcengine", "VolcEngine", "OpenAI", "apikey"),
    ProviderDef("baidu", "Baidu", "OpenAI", "apikey"),
    ProviderDef("gigachat", "GigaChat", "OpenAI", "apikey"),
    ProviderDef("uncloseai", "Unclose AI", "OpenAI", "apikey"),
    ProviderDef("nscale", "Nscale", "OpenAI", "apikey"),
    ProviderDef("glhf", "GLHF", "OpenAI", "apikey"),
    ProviderDef("orcarouter", "OrcaRouter", "OpenAI", "apikey"),
    ProviderDef("freemodel-dev", "FreeModel.dev", "OpenAI", "apikey"),
    ProviderDef("gitlawb", "GitLawB", "OpenAI", "apikey"),
    ProviderDef("agy", "AGY", "OpenAI", "apikey"),
    ProviderDef("longcat", "LongCat", "OpenAI", "apikey"),
    ProviderDef("preditbase", "Predibase", "OpenAI", "apikey"),
    ProviderDef("codestral", "Codestral", "OpenAI", "apikey"),
    ProviderDef("wandb", "Weights & Biases", "OpenAI", "apikey"),
    ProviderDef("xiaomi-mimo", "Xiaomi MiMo", "OpenAI", "apikey"),
    ProviderDef("ovhcloud", "OVHcloud", "OpenAI", "apikey"),
    ProviderDef("dify", "Dify", "OpenAI", "apikey"),
    ProviderDef("kilocode", "KiloCode", "OpenAI", "apikey"),
    ProviderDef("upstage", "Upstage", "OpenAI", "apikey"),
    ProviderDef("bazaarlink", "BazaarLink", "OpenAI", "apikey"),
    ProviderDef("bytez", "Bytez", "OpenAI", "apikey"),
    ProviderDef("blackbox", "Blackbox", "OpenAI", "apikey"),
    ProviderDef("kimi", "Kimi", "OpenAI", "apikey"),
    ProviderDef("chipotle", "Chipotle AI", "OpenAI", "apikey"),
    ProviderDef("theoldllm", "The Old LLM", "OpenAI", "apikey"),
    ProviderDef("nlpcloud", "NLP Cloud", "OpenAI", "apikey"),
    ProviderDef("auggie", "Auggie", "OpenAI", "apikey"),
    ProviderDef("grok-cli", "Grok CLI", "OpenAI", "apikey"),
    ProviderDef("codebuddy-cn", "CodeBuddy CN", "OpenAI", "apikey"),
    ProviderDef("zenmux-free", "ZenMux Free", "OpenAI", "apikey"),
    ProviderDef("xai", "xAI", "OpenAI", "apikey"),
    ProviderDef("mimocode", "MiMo Code", "OpenAI", "apikey"),

    // ── Free Tier ──
    ProviderDef("opencode", "OpenCode Free", "Free", "apikey"),
    ProviderDef("opencode-zen", "OpenCode Zen", "Free", "apikey"),
    ProviderDef("opencode-go", "OpenCode Go", "Free", "apikey"),
    ProviderDef("zenmux", "ZenMux", "Free", "apikey"),
    ProviderDef("ollama-cloud", "Ollama Cloud", "Free", "apikey"),
    ProviderDef("github-models", "GitHub Models", "Free", "apikey"),
    ProviderDef("pollinations", "Pollinations", "Free", "nokey"),

    // ── OAuth (Google) ──
    ProviderDef("antigravity", "Antigravity", "OAuth", "oauth"),
    ProviderDef("gemini-business", "Gemini Business", "OAuth", "oauth"),

    // ── OAuth (Device Code) ──
    ProviderDef("cursor", "Cursor", "OAuth", "oauth"),
    ProviderDef("windsurf", "Windsurf", "OAuth", "oauth"),
    ProviderDef("github", "GitHub Copilot", "OAuth", "oauth"),
    ProviderDef("kiro", "Kiro", "OAuth", "oauth"),
    ProviderDef("codex", "Codex CLI", "OAuth", "oauth"),
    ProviderDef("qoder", "Qoder", "OAuth", "oauth"),
    ProviderDef("zed-hosted", "Zed Hosted", "OAuth", "oauth"),
    ProviderDef("trae", "Trae", "OAuth", "oauth"),
    ProviderDef("gitlab-duo", "GitLab Duo", "OAuth", "oauth"),
    ProviderDef("devin-cli", "Devin CLI", "OAuth", "oauth"),
    ProviderDef("copilot-m365-web", "Copilot M365", "OAuth", "oauth"),

    // ── Web / Cookie ──
    ProviderDef("claude-web", "Claude Web", "Web", "webcookie"),
    ProviderDef("gemini-web", "Gemini Web", "Web", "webcookie"),
    ProviderDef("grok-web", "Grok Web", "Web", "webcookie"),
    ProviderDef("perplexity-web", "Perplexity Web", "Web", "webcookie"),
    ProviderDef("deepseek-web", "DeepSeek Web", "Web", "webcookie"),
    ProviderDef("kimi-web", "Kimi Web", "Web", "webcookie"),
    ProviderDef("doubao-web", "DouBao Web", "Web", "webcookie"),
    ProviderDef("qwen-web", "Qwen Web", "Web", "webcookie"),
    ProviderDef("duckduckgo-web", "DuckDuckGo Web", "Web", "nokey"),
    ProviderDef("t3-web", "T3 Web", "Web", "nokey"),
    ProviderDef("veo", "Veo AI", "Media", "veo"),
    ProviderDef("yuanbao-web", "YuanBao Web", "Web", "webcookie"),
    ProviderDef("huggingchat", "HuggingChat", "Web", "webcookie"),
    ProviderDef("blackbox-web", "Blackbox Web", "Web", "webcookie"),
    ProviderDef("muse-spark-web", "Muse Spark Web", "Web", "webcookie"),
    ProviderDef("adapta-web", "Adapta Web", "Web", "webcookie"),
    ProviderDef("poe-web", "Poe Web", "Web", "webcookie"),
    ProviderDef("inner-ai", "Inner AI", "Web", "webcookie"),
    ProviderDef("copilot-web", "Copilot Web", "Web", "webcookie"),
    ProviderDef("v0-vercel-web", "v0 by Vercel", "Web", "webcookie"),

    // ── Specialized / Cloud ──
    ProviderDef("bedrock", "AWS Bedrock", "Special", "apikey"),
    ProviderDef("vertex", "Google Vertex AI", "Special", "apikey"),
    ProviderDef("vertex-partner", "Vertex Partner", "Special", "apikey"),
    ProviderDef("azure-openai", "Azure OpenAI", "Special", "apikey"),
    ProviderDef("cloudflare-ai", "Cloudflare AI", "Special", "apikey"),
    ProviderDef("glm", "GLM (Zhipu)", "Special", "apikey"),
    ProviderDef("glmt", "GLM Thinking", "Special", "apikey"),
    ProviderDef("glm-cn", "GLM China", "Special", "apikey"),
    ProviderDef("puter", "Puter", "Special", "apikey"),
    ProviderDef("cliproxyapi", "CLI Proxy API", "Special", "apikey"),
    ProviderDef("ninerouter", "9Router", "Special", "apikey"),
    ProviderDef("kie", "KIE", "Special", "apikey"),
    ProviderDef("lmarena", "LM Arena", "Special", "apikey"),
    ProviderDef("command-code", "Command Code", "Special", "apikey"),
    ProviderDef("vercel-ai-gateway", "Vercel AI Gateway", "Special", "apikey"),
    ProviderDef("kilo-gateway", "Kilo Gateway", "Special", "apikey"),
    ProviderDef("cline", "Cline", "Special", "apikey"),
    ProviderDef("clinepass", "ClinePass", "Special", "apikey"),
    ProviderDef("alibaba-cn", "Alibaba CN", "Special", "apikey"),
    ProviderDef("minimax-cn", "MiniMax CN", "Special", "apikey"),
    ProviderDef("kimi-coding", "Kimi Coding", "Special", "apikey"),
    ProviderDef("kimi-coding-apikey", "Kimi Coding API", "Special", "apikey"),
    ProviderDef("bailian-coding-plan", "Bailian Plan", "Special", "apikey"),

    // ── Image / Media ──
    ProviderDef("leonardo", "Leonardo AI", "Media", "apikey"),
    ProviderDef("haiper", "Haiper", "Media", "apikey"),
    ProviderDef("suno", "Suno", "Media", "apikey"),
    ProviderDef("udio", "Udio", "Media", "apikey"),
    ProviderDef("ideogram", "Ideogram", "Media", "apikey"),
)

private val CATEGORIES = listOf(
    "All", "Popular", "OpenAI", "Free", "OAuth", "Web", "Special", "Media"
)

private val CATEGORY_COLORS = mapOf(
    "All" to AppPrimary,
    "Popular" to Color(0xFFF59E0B),
    "OpenAI" to Color(0xFF10B981),
    "Free" to Color(0xFF3B82F6),
    "OAuth" to Color(0xFF8B5CF6),
    "Web" to Color(0xFFEC4899),
    "Special" to Color(0xFFF97316),
    "Media" to Color(0xFF14B8A6)
)

private fun parseAndImportApiKeys(
    context: android.content.Context,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    fileContent: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (Int) -> Unit,
    onFailure: (String) -> Unit
) {
    try {
        var importedCount = 0
        val lines = fileContent.split('\n', '\r')
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//") || trimmedLine.startsWith(";")) {
                continue
            }
            val parts = trimmedLine.split('=', limit = 2)
            if (parts.size != 2) continue
            val fullKey = parts[0].trim()
            val value = parts[1].trim()
            if (value.isEmpty()) continue

            when {
                // Multi-key slot suffixes (_2 through _6)
                fullKey.matches(Regex(".*_[2-6]$")) -> {
                    val lastUnderscore = fullKey.lastIndexOf('_')
                    val rawProviderId = fullKey.substring(0, lastUnderscore).trim().lowercase()
                    val slot = fullKey.substring(lastUnderscore + 1).toIntOrNull() ?: 2
                    val provider = ALL_PROVIDERS.find { p ->
                        p.id == rawProviderId || p.id.lowercase() == rawProviderId ||
                        p.name.lowercase().replace(" ", "").replace("-", "") == rawProviderId.replace(" ", "").replace("-", "")
                    }
                    val providerId = provider?.id ?: rawProviderId
                    securePrefs.saveApiKeySlot(providerId, slot, value)
                    importedCount++
                }
                // Cookie suffix
                fullKey.endsWith("_cookie") -> {
                    val providerId = fullKey.substring(0, fullKey.length - 7).trim().lowercase()
                    securePrefs.saveSetting("web_cookie_${providerId}", value)
                    importedCount++
                }
                // Token suffix
                fullKey.endsWith("_token") -> {
                    val providerId = fullKey.substring(0, fullKey.length - 6).trim().lowercase()
                    securePrefs.saveSetting("oauth_token_${providerId}", value)
                    importedCount++
                }
                // Project ID suffix
                fullKey.endsWith("_project") -> {
                    val providerId = fullKey.substring(0, fullKey.length - 8).trim().lowercase()
                    securePrefs.saveSetting("oauth_project_${providerId}", value)
                    importedCount++
                }
                // Endpoint suffix
                fullKey.endsWith("_endpoint") -> {
                    val providerId = fullKey.substring(0, fullKey.length - 9).trim().lowercase()
                    securePrefs.saveSetting("${providerId}_endpoint_url", value)
                    importedCount++
                }
                // Cloudflare special keys
                fullKey == "cloudflare" || fullKey == "cloudflare_token" || fullKey == "cloudflare-ai" -> {
                    securePrefs.saveApiKey("cloudflare", value)
                    importedCount++
                }
                fullKey == "cloudflare_account_id" -> {
                    securePrefs.saveSetting("cloudflare_account_id", value)
                    importedCount++
                }
                // Standard case: just providerId
                else -> {
                    val providerId = fullKey.lowercase()
                    val provider = ALL_PROVIDERS.find { it.id == providerId || it.id.lowercase() == providerId }
                    if (provider != null) {
                        when (provider.authType) {
                            "webcookie" -> securePrefs.saveSetting("web_cookie_${provider.id}", value)
                            "oauth" -> securePrefs.saveSetting("oauth_token_${provider.id}", value)
                            else -> {
                                securePrefs.saveApiKey(provider.id, value)
                                scope.launch {
                                    val baseUrl = providerDefaultBaseUrl(provider.name)
                                    val models = fetchModels(value, baseUrl, provider.name)
                                    if (models.isNotEmpty()) {
                                        ModelCatalog.setModels(provider.name, models)
                                    }
                                }
                            }
                        }
                        importedCount++
                    }
                }
            }
        }
        onSuccess(importedCount)
    } catch (e: Exception) {
        onFailure(e.message ?: "Unknown error occurred during parsing")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var refreshVersion by remember { mutableStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val fileContent = context.contentResolver.openInputStream(it)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                    
                    if (fileContent.isNotBlank()) {
                        parseAndImportApiKeys(
                            context = context,
                            securePrefs = securePrefs,
                            fileContent = fileContent,
                            scope = scope,
                            onSuccess = { count ->
                                scope.launch(Dispatchers.Main) {
                                    refreshVersion++
                                    Toast.makeText(context, "Successfully imported $count API key(s)!", Toast.LENGTH_LONG).show()
                                }
                            },
                            onFailure = { error ->
                                scope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, "Error importing keys: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Selected file is empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var expandedProviderId by remember { mutableStateOf<String?>(null) }
    var oauthRefreshVersion by remember { mutableStateOf(0) }
    val configuredIds = ALL_PROVIDERS.filter { p ->
        when (p.authType) {
            "nokey" -> true
            "oauth" -> securePrefs.getSetting("oauth_token_${p.id}", "").isNotEmpty()
            "webcookie" -> securePrefs.getSetting("cookie_${p.id}", "").isNotEmpty()
            else -> securePrefs.getApiKey(p.id).isNotEmpty()
        }
    }.map { it.id }.toSet()

    val filteredProviders = remember(searchQuery, selectedCategory, configuredIds, refreshVersion) {
        ALL_PROVIDERS.filter { p ->
            val matchesSearch = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.id.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "All" || p.category == selectedCategory
            matchesSearch && matchesCategory
        }.sortedBy { p ->
            if (p.id in configuredIds) 0 else 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppScreenBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .depthPill(shape = RoundedCornerShape(10.dp), elevation = 2.dp, isDark = true)
                    .bouncyClickable(provideHaptic = true) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "API Keys",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .depthPill(shape = RoundedCornerShape(10.dp), elevation = 2.dp, isDark = true)
                    .bouncyClickable(provideHaptic = true) { filePickerLauncher.launch("text/plain") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = "Import",
                        tint = AppPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Import",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${ALL_PROVIDERS.size} providers",
                fontSize = 11.sp,
                color = AppMuted,
                fontWeight = FontWeight.Medium
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search providers...", fontSize = 13.sp, color = AppMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppMuted, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "Clear", tint = AppMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppWhite,
                unfocusedTextColor = AppWhite,
                focusedBorderColor = AppPrimary,
                unfocusedBorderColor = AppDarkGray,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CATEGORIES) { category ->
                val isSelected = selectedCategory == category
                val chipColor = CATEGORY_COLORS[category] ?: AppPrimary
                Box(
                    modifier = Modifier
                        .depthPill(
                            shape = RoundedCornerShape(10.dp),
                            elevation = if (isSelected) 2.dp else 1.dp,
                            isDark = true,
                            customGradient = if (isSelected) listOf(
                                chipColor.copy(alpha = 0.28f), chipColor.copy(alpha = 0.12f)
                            ) else null,
                            customBorderColor = if (isSelected) chipColor.copy(alpha = 0.7f) else null
                        )
                        .bouncyClickable(provideHaptic = true) { selectedCategory = category }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) chipColor else AppMuted
                    )
                }
            }
        }

        Text(
            text = "${filteredProviders.size} provider${if (filteredProviders.size != 1) "s" else ""}",
            fontSize = 11.sp,
            color = AppMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredProviders, key = { it.id }) { provider ->
                when (provider.authType) {
                    "oauth" -> OAuthProviderCard(
                        provider = provider,
                        isExpanded = expandedProviderId == provider.id,
                        onToggle = {
                            expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                        },
                        securePrefs = securePrefs,
                        context = context,
                        scope = scope,
                        onRequestGoogleSignIn = { pId ->
                            scope.launch {
                                val extraScopes = if (pId == "antigravity")
                                    listOf("https://www.googleapis.com/auth/cloud-platform") else emptyList()
                                val result = performBrowserOAuth(context, securePrefs, pId, extraScopes)
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        oauthRefreshVersion++
                                        Toast.makeText(context, "Connected via Google", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val msg = result.exceptionOrNull()?.message ?: "OAuth failed"
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        oauthRefreshVersion = oauthRefreshVersion
                    )
                    "webcookie" -> WebCookieProviderCard(
                        provider = provider,
                        isExpanded = expandedProviderId == provider.id,
                        onToggle = {
                            expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                        },
                        securePrefs = securePrefs,
                        context = context
                    )
                    "nokey" -> NoKeyProviderCard(
                        provider = provider,
                        isExpanded = expandedProviderId == provider.id,
                        onToggle = {
                            expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                        }
                    )
                    "veo" -> VeoKeyProviderCard(
                        provider = provider,
                        isExpanded = expandedProviderId == provider.id,
                        onToggle = {
                            expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                        },
                        securePrefs = securePrefs,
                        context = context,
                        scope = scope
                    )
                    else -> ApiKeyProviderCard(
                        provider = provider,
                        isExpanded = expandedProviderId == provider.id,
                        onToggle = {
                            expandedProviderId = if (expandedProviderId == provider.id) null else provider.id
                        },
                        securePrefs = securePrefs,
                        context = context,
                        scope = scope
                    )
                }
            }
        }
    }
}

private suspend fun performBrowserOAuth(
    context: android.content.Context,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    providerId: String,
    extraScopes: List<String> = emptyList()
): Result<String> = withContext(Dispatchers.IO) {
    var serverSocket: java.net.ServerSocket? = null
    try {
        val googleAuth = GoogleAuthService(context)

        serverSocket = java.net.ServerSocket(0)
        val port = serverSocket.localPort
        val redirectUri = "http://127.0.0.1:$port"
        val state = java.util.UUID.randomUUID().toString()

        val authUrl = googleAuth.buildAuthUrl(
            scopes = GoogleAuthService.IDENTITY_SCOPES + extraScopes,
            state = state,
            customRedirectUri = redirectUri
        )

        withContext(Dispatchers.Main) {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                // no browser available
            }
        }

        serverSocket.soTimeout = 180000
        val client = serverSocket.accept()
        client.soTimeout = 30000

        val reader = java.io.BufferedReader(java.io.InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: return@withContext Result.failure(Exception("No request received"))
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) break
        }

        val requestUri = requestLine.split(" ").getOrNull(1) ?: ""
        val params = android.net.Uri.parse("http://127.0.0.1$requestUri")
        val code = params.getQueryParameter("code") ?: return@withContext Result.failure(Exception("No authorization code in redirect"))
        val returnedState = params.getQueryParameter("state") ?: ""
        if (returnedState != state) {
            return@withContext Result.failure(Exception("OAuth state mismatch"))
        }

        val successHtml = "<html><body><p>Authentication complete. You can close this tab.</p><script>window.close()</script></body></html>"
        val httpResponse = "HTTP/1.1 200 OK\r\nContent-Length: ${successHtml.length}\r\nContent-Type: text/html\r\n\r\n$successHtml"
        client.getOutputStream().write(httpResponse.toByteArray(Charsets.UTF_8))
        client.close()

        val tokensResult = googleAuth.exchangeCodeForTokens(code, redirectUri)
        if (tokensResult.isSuccess) {
            val tokens = tokensResult.getOrThrow()
            securePrefs.saveSetting("oauth_token_$providerId", tokens.accessToken)
            securePrefs.saveSetting("oauth_refresh_$providerId", tokens.refreshToken)

            // For Antigravity, auto-discover the GCP project ID via loadCodeAssist
            if (providerId == "antigravity") {
                val accessToken = tokens.accessToken
                try {
                    val assistUrl = java.net.URL("https://daily-cloudcode-pa.googleapis.com/v1internal:loadCodeAssist")
                    val conn = assistUrl.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    conn.setRequestProperty("User-Agent", "vscode/1.X.X (Antigravity/4.2.0)")
                    conn.doOutput = true
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val bodyBytes = """{"metadata":{"ideType":"ANTIGRAVITY"}}""".toByteArray(Charsets.UTF_8)
                    conn.outputStream.write(bodyBytes)
                    conn.outputStream.flush()
                    val httpStatus = conn.responseCode
                    val responseBody = if (httpStatus == 200) {
                        conn.inputStream.bufferedReader().readText()
                    } else {
                        val errorStream = conn.errorStream
                        if (errorStream != null) "HTTP $httpStatus: ${errorStream.bufferedReader().readText()}" else "HTTP $httpStatus"
                    }
                    conn.disconnect()
                    if (httpStatus == 200) {
                        val json = com.google.gson.JsonParser.parseString(responseBody).asJsonObject
                        val raw = json.get("cloudaicompanionProject")
                        if (raw != null && !raw.isJsonNull) {
                            val projectId = if (raw.isJsonPrimitive && raw.asJsonPrimitive.isString) {
                                raw.asString.trim()
                            } else if (raw.isJsonObject) {
                                raw.asJsonObject.get("id")?.asString?.trim() ?: ""
                            } else ""
                            if (projectId.isNotEmpty()) {
                                securePrefs.saveSetting("oauth_project_$providerId", projectId)
                            }
                        }
                        // If still no project ID, save the raw response for debugging
                        if (securePrefs.getSetting("oauth_project_$providerId", "").isEmpty()) {
                            securePrefs.saveSetting("oauth_project_antigravity_debug", responseBody.take(500))
                        }
                    } else {
                        securePrefs.saveSetting("oauth_project_antigravity_debug", "loadCodeAssist failed: $responseBody")
                    }
                } catch (e: Exception) {
                    securePrefs.saveSetting("oauth_project_antigravity_debug", "loadCodeAssist error: ${e.message}")
                }
            }

            Result.success(tokens.accessToken)
        } else {
            Result.failure(tokensResult.exceptionOrNull() ?: Exception("Token exchange failed"))
        }
    } catch (e: java.net.SocketTimeoutException) {
        Result.failure(Exception("Sign-in timed out. Please try again."))
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}

// ── Cube Icon & Helpers ───────────────────────────────────────────────────

@Composable
fun CubeIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val pTop = Offset(cx, h * 0.12f)
        val pTR = Offset(w * 0.88f, h * 0.32f)
        val pBR = Offset(w * 0.88f, h * 0.72f)
        val pBot = Offset(cx, h * 0.92f)
        val pBL = Offset(w * 0.12f, h * 0.72f)
        val pTL = Offset(w * 0.12f, h * 0.32f)
        val pCenter = Offset(cx, h * 0.52f)

        val stroke = Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        val outerPath = Path().apply {
            moveTo(pTop.x, pTop.y)
            lineTo(pTR.x, pTR.y)
            lineTo(pBR.x, pBR.y)
            lineTo(pBot.x, pBot.y)
            lineTo(pBL.x, pBL.y)
            lineTo(pTL.x, pTL.y)
            close()
        }
        drawPath(outerPath, tint, style = stroke)

        drawLine(tint, pCenter, pTop, strokeWidth = stroke.width)
        drawLine(tint, pCenter, pBL, strokeWidth = stroke.width)
        drawLine(tint, pCenter, pBR, strokeWidth = stroke.width)
    }
}

private fun getProviderDescription(id: String, name: String): String = when (id) {
    "groq" -> "Ultra-fast inference for open models."
    "gmi" -> "High-performance GPU cluster inference."
    "zen" -> "Free & pro developer models with fast streaming."
    "openai" -> "Industry-leading reasoning, multimodal & GPT models."
    "anthropic" -> "Claude models with nuanced reasoning & vision."
    "gemini" -> "Google Gemini multimodality and massive context."
    "mistral" -> "Open and frontier multilingual models."
    "cerebras" -> "World-record speed Llama and open models."
    "deepseek" -> "High intelligence reasoning & code completion."
    "openrouter" -> "Unified gateway to 100+ AI models."
    "together" -> "Fast, cost-effective open-source AI cloud."
    "fireworks" -> "Ultra-fast inference and function calling platform."
    "deepinfra" -> "Scalable pay-as-you-go inference for open models."
    "nvidia" -> "NVIDIA accelerated enterprise models & NIM."
    "perplexity" -> "Real-time web search and cited answer engines."
    "xai" -> "Grok models with real-time insight and reasoning."
    "cohere" -> "Enterprise enterprise-grade command models."
    "sambanova" -> "Ultra-fast inference on specialized chips."
    "hyperbolic" -> "Decentralized GPU cloud for open models."
    "github-models" -> "Azure AI and GitHub hosted model endpoints."
    "novita" -> "Cost-effective GPU cloud inference."
    "siliconflow" -> "High throughput inference in Asia-Pacific."
    "agentrouter" -> "Decentralized routing and agent execution."
    "ollama", "ollamacloud" -> "Local and private open source LLMs."
    else -> "Fast and secure API inference for $name."
}

private fun getProviderConsoleUrl(id: String): String = when (id) {
    "groq" -> "https://console.groq.com"
    "gmi" -> "https://console.gmicloud.ai"
    "zen" -> "https://opencode.ai"
    "openai" -> "https://platform.openai.com"
    "anthropic" -> "https://console.anthropic.com"
    "gemini" -> "https://aistudio.google.com"
    "mistral" -> "https://console.mistral.ai"
    "cerebras" -> "https://cloud.cerebras.ai"
    "deepseek" -> "https://platform.deepseek.com"
    "openrouter" -> "https://openrouter.ai"
    "together" -> "https://api.together.ai"
    "fireworks" -> "https://fireworks.ai"
    "deepinfra" -> "https://deepinfra.com"
    "nvidia" -> "https://build.nvidia.com"
    "perplexity" -> "https://www.perplexity.ai"
    "xai" -> "https://console.x.ai"
    "cohere" -> "https://dashboard.cohere.com"
    "sambanova" -> "https://cloud.sambanova.ai"
    "hyperbolic" -> "https://app.hyperbolic.xyz"
    "github-models" -> "https://github.com/marketplace/models"
    "novita" -> "https://novita.ai"
    "siliconflow" -> "https://cloud.siliconflow.cn"
    "agentrouter" -> "https://agentrouter.org"
    "ollama", "ollamacloud" -> "https://ollama.com"
    else -> "https://console.$id.com"
}

private fun formatContextTag(contextWindow: String, id: String): String {
    val lower = contextWindow.lowercase()
    return when {
        lower.contains("1m") -> "1M"
        lower.contains("2m") -> "2M"
        lower.contains("128k") -> "128K"
        lower.contains("200k") -> "200K"
        lower.contains("256k") -> "256K"
        lower.contains("64k") -> "64K"
        lower.contains("32k") -> "32K"
        lower.contains("16k") -> "16K"
        lower.contains("8k") -> "8K"
        lower.contains("4k") -> "4K"
        id.contains("128k", true) -> "128K"
        id.contains("32k", true) -> "32K"
        id.contains("64k", true) -> "64K"
        id.contains("8k", true) -> "8K"
        id.contains("1m", true) -> "1M"
        id.contains("2m", true) -> "2M"
        id.contains("70b", true) -> "128K"
        id.contains("8b", true) -> "128K"
        else -> "128K"
    }
}

private fun detectModalityTag(name: String, id: String): String {
    val combined = "$name $id".lowercase()
    return when {
        combined.contains("vision") || combined.contains("-vl") || combined.contains("omni") || combined.contains("4o") -> "Vision"
        combined.contains("coder") || combined.contains("code") -> "Code"
        combined.contains("image") || combined.contains("imagen") -> "Image"
        combined.contains("audio") || combined.contains("voice") -> "Audio"
        else -> "Text"
    }
}

private fun getAvailableModelsForProvider(provider: ProviderDef, securePrefs: ai.deepcode.android.data.local.EncryptedPrefs): List<AIModel> {
    val catalog = ModelCatalog.getModelsForProvider(provider.name, securePrefs)
    if (catalog.isNotEmpty()) return catalog
    val factoryProvider = AIProviderFactory.providers.find {
        it.name.equals(provider.name, ignoreCase = true) ||
        it.name.contains(provider.name, ignoreCase = true) ||
        providerStorageId(it.name) == provider.id
    }
    if (factoryProvider != null && factoryProvider.models.isNotEmpty()) {
        return factoryProvider.models
    }
    val openAiConf = OPENAI_PROVIDERS.find {
        it.name.equals(provider.name, ignoreCase = true) ||
        it.name.contains(provider.name, ignoreCase = true)
    }
    if (openAiConf != null && openAiConf.models.isNotEmpty()) {
        return openAiConf.models
    }
    return emptyList()
}

// ── API Key Card ───────────────────────────────────────────────────────────

@Composable
private fun ApiKeyProviderCard(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val maxSlots = ai.deepcode.android.data.local.EncryptedPrefs.MAX_API_KEYS_PER_PROVIDER

    // Load initial values for all slots
    val initialKeys = remember(provider.id) {
        (1..maxSlots).map { slot ->
            securePrefs.getApiKeySlot(provider.id, slot)
        }
    }

    val keys = remember(provider.id) {
        initialKeys.map { mutableStateOf(it) }
    }
    var activeSlot by remember(provider.id) { mutableStateOf(1) }
    var keyVisible by remember(provider.id) { mutableStateOf(false) }

    val hasKey = keys.any { it.value.isNotEmpty() }
    val filledCount = keys.count { it.value.isNotEmpty() }

    // Drawer state
    var showModelsDrawer by remember(provider.id) { mutableStateOf(false) }
    var modelSearchQuery by remember(provider.id) { mutableStateOf("") }
    var modelFilter by remember(provider.id) { mutableStateOf("All") }

    // Load models
    val catalogModels by ModelCatalog.models.collectAsStateWithLifecycle()
    val availableModels = remember(provider.id, catalogModels) {
        getAvailableModelsForProvider(provider, securePrefs)
    }

    // Selected models for chat persistence
    val savedSelectedStr = remember(provider.id) {
        securePrefs.getSetting("selected_models_${provider.id}", "")
    }
    var selectedModelIds by remember(provider.id) {
        mutableStateOf(
            if (savedSelectedStr.isNotBlank()) {
                savedSelectedStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }
        )
    }

    // Default active model for provider
    val savedDefaultModelId = remember(provider.id) {
        securePrefs.getSetting("default_model_${provider.id}", "")
    }
    var currentDefaultModelId by remember(provider.id) {
        mutableStateOf(
            if (savedDefaultModelId.isNotBlank()) savedDefaultModelId
            else selectedModelIds.firstOrNull() ?: availableModels.firstOrNull()?.id ?: ""
        )
    }

    val selectedModelName = remember(currentDefaultModelId, availableModels) {
        availableModels.find { it.id == currentDefaultModelId }?.name
            ?: availableModels.firstOrNull()?.name
            ?: "Select Model"
    }

    var isFetchingModels by remember(provider.id) { mutableStateOf(false) }

    // Trigger fetchModels in background if key exists and live models are not yet cached
    LaunchedEffect(provider.id, keys.map { it.value }.firstOrNull { it.isNotBlank() }) {
        val keyVal = keys.firstOrNull { it.value.isNotBlank() }?.value?.trim() ?: ""
        val hasCached = securePrefs.getSetting("cached_models_${provider.name}", "").isNotBlank()
        val inCatalog = !ModelCatalog.models.value[provider.name].isNullOrEmpty()
        if (keyVal.isNotEmpty() && (!hasCached || !inCatalog)) {
            val baseUrl = providerDefaultBaseUrl(provider.name)
            val fetched = fetchModels(keyVal, baseUrl, provider.name)
            if (fetched.isNotEmpty()) {
                ModelCatalog.setModels(provider.name, fetched, securePrefs)
            }
        }
    }

    val categoryColor = CATEGORY_COLORS[provider.category] ?: Color(0xFFF59E0B)

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = hasKey
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        // ── 1. Top Row: Two Columns (Select Model & API Key) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Column: Select Model
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Model",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "View All >",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showModelsDrawer = !showModelsDrawer }
                            .padding(vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(8.dp))
                        .clickable { showModelsDrawer = !showModelsDrawer }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CubeIcon(tint = Color(0xFFA1A1AA), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedModelName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand models",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Right Column: API Key
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeSlot > 1) "API Key ($activeSlot)" else "API Key",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (filledCount > 1) {
                        Text(
                            text = "Slot $activeSlot/$filledCount",
                            color = Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    activeSlot = (activeSlot % filledCount) + 1
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentKeyValue = keys[activeSlot - 1].value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF18181B))
                            .border(1.dp, Color(0xFF2E2E32), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (currentKeyValue.isEmpty()) {
                                    Text(
                                        text = "Enter API Key...",
                                        color = Color(0xFF52525B),
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = currentKeyValue,
                                    onValueChange = { newVal ->
                                        keys[activeSlot - 1].value = newVal
                                        securePrefs.saveApiKeySlot(provider.id, activeSlot, newVal.trim())
                                        if (newVal.trim().isNotEmpty()) {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val baseUrl = providerDefaultBaseUrl(provider.name)
                                                    val fetched = fetchModels(newVal.trim(), baseUrl, provider.name)
                                                    if (fetched.isNotEmpty()) {
                                                        ModelCatalog.setModels(provider.name, fetched, securePrefs)
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation('•'),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(Color(0xFFF59E0B)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(
                                onClick = { keyVisible = !keyVisible },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle key visibility",
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Red Delete Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF7F1D1D).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                keys[activeSlot - 1].value = ""
                                securePrefs.saveApiKeySlot(provider.id, activeSlot, "")
                                Toast.makeText(context, "${provider.name} key cleared", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete key",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── 2. "Models by [Provider]" Sub-Card ──
        AnimatedVisibility(visible = showModelsDrawer) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101012))
                    .border(1.dp, Color(0xFF242428), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CubeIcon(tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Models by ${provider.name}",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222226))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${availableModels.size} models",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF6366F1),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (hasKey) {
                            IconButton(
                                onClick = {
                                    val keyToUse = keys.firstOrNull { it.value.isNotBlank() }?.value?.trim() ?: ""
                                    if (keyToUse.isNotEmpty()) {
                                        scope.launch(Dispatchers.IO) {
                                            isFetchingModels = true
                                            try {
                                                val baseUrl = providerDefaultBaseUrl(provider.name)
                                                val fetched = fetchModels(keyToUse, baseUrl, provider.name)
                                                if (fetched.isNotEmpty()) {
                                                    ModelCatalog.setModels(provider.name, fetched, securePrefs)
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Loaded ${fetched.size} models", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "No models returned", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Fetch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            } finally {
                                                isFetchingModels = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh models",
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        IconButton(
                            onClick = { showModelsDrawer = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close models drawer",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0xFF2A2A2E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (modelSearchQuery.isEmpty()) {
                                Text(
                                    text = "Search models...",
                                    color = Color(0xFF71717A),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = modelSearchQuery,
                                onValueChange = { modelSearchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFFF59E0B)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (modelSearchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF71717A),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { modelSearchQuery = "" }
                            )
                        }
                    }
                }

                // Filter Tabs (All, Configured, Free, Paid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        "All" to "All (${availableModels.size})",
                        "Configured" to "Configured",
                        "Free" to "Free",
                        "Paid" to "Paid"
                    )
                    for ((key, label) in tabs) {
                        val isSelected = modelFilter.equals(key, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFFF59E0B) else Color(0xFF1F1F23))
                                .clickable { modelFilter = key }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color(0xFFA1A1AA)
                            )
                        }
                    }
                }

                // Filtered List
                val displayedModels = remember(availableModels, modelSearchQuery, modelFilter, selectedModelIds) {
                    availableModels.filter { model ->
                        val matchesSearch = modelSearchQuery.isBlank() ||
                            model.name.contains(modelSearchQuery, ignoreCase = true) ||
                            model.id.contains(modelSearchQuery, ignoreCase = true)
                        val matchesFilter = when (modelFilter.lowercase()) {
                            "configured" -> model.id in selectedModelIds
                            "free" -> model.isFree || model.badge.equals("free", ignoreCase = true)
                            "paid" -> !model.isFree && !model.badge.equals("free", ignoreCase = true)
                            else -> true
                        }
                        matchesSearch && matchesFilter
                    }
                }

                if (displayedModels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (modelFilter == "Configured") "No models configured yet. Select models below to show in chat." else "No models matching criteria",
                            color = Color(0xFF71717A),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayedModels.take(50).forEach { model ->
                            val isModelSelected = model.id in selectedModelIds
                            val isDefaultModel = model.id == currentDefaultModelId

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val newIds = if (isModelSelected) {
                                            selectedModelIds - model.id
                                        } else {
                                            selectedModelIds + model.id
                                        }
                                        selectedModelIds = newIds
                                        securePrefs.saveSetting(
                                            "selected_models_${provider.id}",
                                            newIds.joinToString(",")
                                        )
                                        currentDefaultModelId = model.id
                                        securePrefs.saveSetting("default_model_${provider.id}", model.id)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CubeIcon(tint = Color(0xFFA1A1AA), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = model.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isModelSelected || isDefaultModel) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Modality Pill
                                val modality = detectModalityTag(model.name, model.id)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF222226))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = modality,
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))

                                // Context Window Pill
                                val ctxTag = formatContextTag(model.contextWindow, model.id)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF222226))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = ctxTag,
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                // Selection Circle
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isModelSelected) Color(0xFFF59E0B) else Color(0xFF52525B),
                                            shape = CircleShape
                                        )
                                        .background(
                                            color = if (isModelSelected) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isModelSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(0xFFF59E0B), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Footer Row (+ Add Another Key & Encrypted Note) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add Key Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = 0.06f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable {
                        val nextSlot = (filledCount + 1).coerceAtMost(maxSlots)
                        activeSlot = if (activeSlot < filledCount) activeSlot + 1 else nextSlot
                        Toast.makeText(context, "Switched to Key Slot $activeSlot", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Key",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Another Key (${filledCount.coerceAtLeast(1)}/$maxSlots)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF59E0B)
                    )
                }
            }

            // Encrypted note
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Keys are encrypted and stored locally.",
                    fontSize = 11.sp,
                    color = Color(0xFF71717A)
                )
            }
        }
    }
}

// ── OAuth Card ─────────────────────────────────────────────────────────────

@Composable
private fun OAuthProviderCard(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onRequestGoogleSignIn: (String) -> Unit,
    oauthRefreshVersion: Int = 0
) {
    val accessTokenKey = "oauth_token_${provider.id}"
    val refreshTokenKey = "oauth_refresh_${provider.id}"

    var localSaveVersion by remember(provider.id) { mutableStateOf(0) }
    val savedAccessToken = remember(provider.id, oauthRefreshVersion, localSaveVersion) { securePrefs.getSetting(accessTokenKey, "") }
    val savedRefreshToken = remember(provider.id, oauthRefreshVersion, localSaveVersion) { securePrefs.getSetting(refreshTokenKey, "") }

    val isConnected = savedAccessToken.isNotEmpty()
    val categoryColor = CATEGORY_COLORS[provider.category] ?: AppPrimary

    var showManual by remember(provider.id) { mutableStateOf(false) }
    var manualToken by remember(provider.id) { mutableStateOf("") }
    val projectKey = "oauth_project_${provider.id}"
    val savedProjectId = remember(provider.id, oauthRefreshVersion, localSaveVersion) { securePrefs.getSetting(projectKey, "") }
    var projectIdInput by remember(provider.id, oauthRefreshVersion, localSaveVersion) { mutableStateOf(savedProjectId) }

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = isConnected,
        keyPreview = if (isConnected) "Connected via OAuth" else null
    ) {
        HorizontalDivider(color = AppDarkGray, thickness = 1.dp)

        if (isConnected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connected via Google",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .clickable {
                            securePrefs.saveSetting(accessTokenKey, "")
                            securePrefs.saveSetting(refreshTokenKey, "")
                            Toast.makeText(context, "${provider.name} disconnected", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Disconnect",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE53935)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This provider uses OAuth authentication. Connect your Google account to get started.",
                    fontSize = 12.sp,
                    color = AppMuted,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = { onRequestGoogleSignIn(provider.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Or paste an access token manually:",
                    fontSize = 11.sp,
                    color = AppMuted,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        placeholder = { Text("Paste OAuth token...", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            cursorColor = AppPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppDarkGray,
                            focusedContainerColor = AppCard,
                            unfocusedContainerColor = AppCard
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Button(
                        onClick = {
                            if (manualToken.isNotBlank()) {
                                securePrefs.saveSetting(accessTokenKey, manualToken.trim())
                                securePrefs.saveSetting(refreshTokenKey, "")
                                localSaveVersion++
                                manualToken = ""
                                Toast.makeText(context, "${provider.name} connected", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = manualToken.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppPrimary,
                            contentColor = AppScreenBg
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (provider.id == "antigravity") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = AppDarkGray, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "GCP Project ID (optional):",
                    fontSize = 11.sp,
                    color = AppMuted,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = projectIdInput,
                        onValueChange = { projectIdInput = it },
                        placeholder = { Text("my-gcp-project-id", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            cursorColor = AppPrimary,
                            focusedBorderColor = AppPrimary,
                            unfocusedBorderColor = AppDarkGray,
                            focusedContainerColor = AppCard,
                            unfocusedContainerColor = AppCard
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Button(
                        onClick = {
                            if (projectIdInput.isNotBlank()) {
                                securePrefs.saveSetting(projectKey, projectIdInput.trim())
                                localSaveVersion++
                                Toast.makeText(context, "Project ID saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = projectIdInput.isNotBlank() && projectIdInput != savedProjectId,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppPrimary,
                            contentColor = AppScreenBg
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Web/Cookie Card ────────────────────────────────────────────────────────

@Composable
private fun WebCookieProviderCard(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    context: android.content.Context
) {
    val cookieKey = "web_cookie_${provider.id}"
    val initialCookie = remember(provider.id) { securePrefs.getSetting(cookieKey, "") }

    var cookieValue by remember(provider.id) { mutableStateOf(initialCookie) }
    var cookieVisible by remember(provider.id) { mutableStateOf(false) }

    val hasCookie = cookieValue.isNotEmpty()
    val hasChanged = cookieValue != initialCookie
    val categoryColor = CATEGORY_COLORS[provider.category] ?: AppPrimary

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = hasCookie,
        keyPreview = if (hasCookie) "Session cookie saved" else null
    ) {
        HorizontalDivider(color = AppDarkGray, thickness = 1.dp)

        Column {
            Text(
                text = "Session Cookie / Token",
                fontSize = 11.sp,
                color = AppMuted,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Paste the session token from your browser's developer tools.",
                fontSize = 10.sp,
                color = AppMuted.copy(alpha = 0.7f),
                lineHeight = 13.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = cookieValue,
                    onValueChange = { cookieValue = it },
                    placeholder = { Text("session_token=...", fontSize = 12.sp) },
                    singleLine = true,
                    visualTransformation = if (cookieVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { cookieVisible = !cookieVisible }) {
                            Icon(
                                imageVector = if (cookieVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AppMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppWhite,
                        unfocusedTextColor = AppWhite,
                        cursorColor = AppPrimary,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppDarkGray,
                        focusedContainerColor = AppCard,
                        unfocusedContainerColor = AppCard
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                if (hasCookie) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { cookieValue = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        ActionRow(
            hasChanged = hasChanged,
            onCancel = { cookieValue = initialCookie },
            onSave = {
                securePrefs.saveSetting(cookieKey, cookieValue.trim())
                Toast.makeText(context, "${provider.name} cookie saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ── No-Key Card ────────────────────────────────────────────────────────────

@Composable
private fun NoKeyProviderCard(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val categoryColor = CATEGORY_COLORS[provider.category] ?: AppPrimary

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = true,
        keyPreview = "No auth required"
    ) {
        HorizontalDivider(color = AppDarkGray, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "This provider works without any authentication.",
                fontSize = 12.sp,
                color = AppMuted
            )
        }
    }
}

// ── Veo Key Card ───────────────────────────────────────────────────────────

@Composable
private fun VeoKeyProviderCard(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val initialKey = remember(provider.id) { securePrefs.getApiKey(provider.id) }
    val initialEndpoint = remember(provider.id) { securePrefs.getSetting("veo_endpoint_url", "") }

    var key by remember(provider.id) { mutableStateOf(initialKey) }
    var endpoint by remember(provider.id) { mutableStateOf(initialEndpoint) }
    var keyVisible by remember(provider.id) { mutableStateOf(false) }

    val hasKey = key.isNotEmpty()
    val hasChanged = key != initialKey || endpoint != initialEndpoint
    val categoryColor = CATEGORY_COLORS[provider.category] ?: AppPrimary

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = hasKey,
        keyPreview = if (hasKey) {
            val k = key
            if (k.length > 16) "${k.take(6)}...${k.takeLast(4)}" else "•".repeat(k.length.coerceAtMost(12))
        } else null
    ) {
        HorizontalDivider(color = AppDarkGray, thickness = 1.dp)

        ApiKeyField(
            label = "API Key",
            value = key,
            onValueChange = { key = it },
            visible = keyVisible,
            onToggleVisibility = { keyVisible = !keyVisible },
            onClear = { key = "" }
        )

        Column {
            Text(text = "API Endpoint URL", fontSize = 11.sp, color = AppMuted, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                placeholder = { Text("https://us-central1-aiplatform.googleapis.com/...", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppWhite,
                    unfocusedTextColor = AppWhite,
                    cursorColor = AppPrimary,
                    focusedBorderColor = AppPrimary,
                    unfocusedBorderColor = AppDarkGray,
                    focusedContainerColor = AppCard,
                    unfocusedContainerColor = AppCard
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
        }

        ActionRow(
            hasChanged = hasChanged,
            onCancel = {
                key = initialKey
                endpoint = initialEndpoint
            },
            onSave = {
                securePrefs.saveApiKey(provider.id, key.trim())
                securePrefs.saveSetting("veo_endpoint_url", endpoint.trim())
                Toast.makeText(context, "${provider.name} key saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ── Shared Card Frame ──────────────────────────────────────────────────────

@Composable
private fun ProviderCardFrame(
    provider: ProviderDef,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    categoryColor: Color,
    hasKey: Boolean,
    keyPreview: String? = null,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .depthCard(
                shape = RoundedCornerShape(14.dp),
                elevation = if (isExpanded) 3.5.dp else 1.5.dp,
                isDark = true,
                customBorderColor = if (isExpanded) categoryColor.copy(alpha = 0.55f) else null
            )
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderIcon(provider.id, provider.name)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = provider.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CategoryBadge(provider.category, categoryColor)
                    if (provider.authType == "oauth") {
                        OAuthBadge()
                    } else if (provider.authType == "webcookie") {
                        WebBadge()
                    }
                }

                // Provider description
                val description = getProviderDescription(provider.id, provider.name)
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    color = Color(0xFFA1A1AA),
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Console link with ↗ icon
                val consoleUrl = getProviderConsoleUrl(provider.id)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(consoleUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open $consoleUrl", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = consoleUrl,
                        fontSize = 11.sp,
                        color = Color(0xFF71717A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open console",
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(11.dp)
                    )
                }

                if (keyPreview != null) {
                    Text(
                        text = keyPreview,
                        fontSize = 11.sp,
                        color = if (hasKey) Color(0xFF10B981) else AppMuted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (hasKey) Color(0xFF10B981) else Color(0xFF3F3F46))
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF71717A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = expandedContent
            )
        }
    }
}

// ── Shared Sub-Components ──────────────────────────────────────────────────

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Text(text = label, fontSize = 11.sp, color = AppMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Paste your key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = AppMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppWhite,
                    unfocusedTextColor = AppWhite,
                    cursorColor = AppPrimary,
                    focusedBorderColor = AppPrimary,
                    unfocusedBorderColor = AppDarkGray,
                    focusedContainerColor = AppCard,
                    unfocusedContainerColor = AppCard
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .depthPill(
                            shape = RoundedCornerShape(8.dp),
                            elevation = 1.5.dp,
                            isDark = true,
                            customGradient = listOf(Color(0xFFE53935).copy(alpha = 0.25f), Color(0xFFE53935).copy(alpha = 0.10f)),
                            customBorderColor = Color(0xFFE53935).copy(alpha = 0.5f)
                        )
                        .bouncyClickable(provideHaptic = true, onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelFilterRow(
    selectedFilter: String,
    onSelect: (String) -> Unit,
    categoryColor: Color
) {
    Column {
        Text(text = "Model Filter", fontSize = 11.sp, color = AppMuted, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val filterOptions = listOf("all", "free", "paid")
            val filterLabels = listOf("All", "Free", "Paid")
            filterOptions.forEachIndexed { index, value ->
                val isSelected = selectedFilter == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .depthPill(
                            shape = RoundedCornerShape(8.dp),
                            elevation = if (isSelected) 2.dp else 1.dp,
                            isDark = true,
                            customGradient = if (isSelected) listOf(
                                categoryColor.copy(alpha = 0.9f), categoryColor.copy(alpha = 0.65f)
                            ) else null,
                            customBorderColor = if (isSelected) categoryColor else null
                        )
                        .bouncyClickable(provideHaptic = true) { onSelect(value) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filterLabels[index],
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else AppMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    hasChanged: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Encrypted at rest", fontSize = 9.sp, color = AppMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .depthPill(
                        shape = RoundedCornerShape(8.dp),
                        elevation = if (hasChanged) 1.5.dp else 0.dp,
                        isDark = true
                    )
                    .bouncyClickable(enabled = hasChanged, provideHaptic = true, onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Cancel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasChanged) AppWhite else AppMuted
                )
            }
            Box(
                modifier = Modifier
                    .depthPill(
                        shape = RoundedCornerShape(8.dp),
                        elevation = if (hasChanged) 2.5.dp else 0.dp,
                        isDark = true,
                        customGradient = if (hasChanged) listOf(
                            AppPrimary, AppPrimary.copy(alpha = 0.8f)
                        ) else listOf(
                            AppMuted.copy(alpha = 0.25f), AppMuted.copy(alpha = 0.12f)
                        ),
                        customBorderColor = if (hasChanged) Color.White.copy(alpha = 0.4f) else null
                    )
                    .bouncyClickable(enabled = hasChanged, provideHaptic = true, onClick = onSave)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Save",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasChanged) Color.Black else AppMuted
                )
            }
        }
    }
}

// ── Badges ─────────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: String, color: Color) {
    val displayCategory = when (category.lowercase()) {
        "official" -> "Popular"
        else -> category.replaceFirstChar { it.uppercase() }
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = displayCategory, fontSize = 10.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OAuthBadge() {
    Box(
        modifier = Modifier
            .background(Color(0xFF4285F4).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFF4285F4).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = "OAuth", fontSize = 8.sp, color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WebBadge() {
    Box(
        modifier = Modifier
            .background(Color(0xFFEC4899).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFFEC4899).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = "Web", fontSize = 8.sp, color = Color(0xFFEC4899), fontWeight = FontWeight.Bold)
    }
}

// ── Provider Icon ──────────────────────────────────────────────────────────

@Composable
private fun ProviderIcon(id: String, name: String) {
    val initials = when (id.lowercase()) {
        "groq" -> "GR"
        "gmi" -> "GM"
        "openai" -> "OA"
        "anthropic" -> "AN"
        "gemini" -> "GM"
        "mistral" -> "MI"
        "deepseek" -> "DS"
        "cerebras" -> "CB"
        "openrouter" -> "OR"
        "together" -> "TG"
        "fireworks" -> "FW"
        "deepinfra" -> "DI"
        "nvidia" -> "NV"
        "perplexity" -> "PP"
        "cohere" -> "CO"
        "xai" -> "XA"
        "sambanova" -> "SN"
        "hyperbolic" -> "HB"
        "github-models", "github" -> "GH"
        "zen", "zenmux", "zenmux-free", "opencode", "opencode-zen" -> "ZN"
        else -> name.take(2).uppercase()
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF202024))
            .border(1.dp, Color(0xFF2E2E34), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF59E0B),
            fontFamily = FontFamily.Monospace
        )
    }
}
