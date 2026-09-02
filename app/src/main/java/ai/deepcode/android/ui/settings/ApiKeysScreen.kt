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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import android.net.Uri
import ai.deepcode.android.data.remote.ModelCatalog
import ai.deepcode.android.data.remote.fetchModels
import ai.deepcode.android.data.remote.providerDefaultBaseUrl
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
            .background(Color.Transparent)
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppSurface)
                    .border(1.dp, AppDarkGray, RoundedCornerShape(10.dp))
                    .clickable { onBack() },
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppSurface)
                    .border(1.dp, AppDarkGray, RoundedCornerShape(8.dp))
                    .clickable { filePickerLauncher.launch("text/plain") }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) chipColor.copy(alpha = 0.15f)
                            else AppSurface
                        )
                        .border(
                            1.dp,
                            if (isSelected) chipColor.copy(alpha = 0.5f)
                            else AppDarkGray,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = category }
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
            items(filteredProviders, key = { "${it.id}_$refreshVersion" }) { provider ->
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
        // Keep the Antigravity OAuth client permanently — don't restore the old one.
        // The old fallback client (623008392016-...) is only used for device-level
        // AccountManager tokens (Gmail/Calendar), not for this OAuth code flow.
        if (prevClientId != null && providerId != "antigravity") {
            securePrefs.saveSetting("google_client_id", prevClientId)
            securePrefs.saveSetting("google_client_secret", "")
        }
    }
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
    val savedFilter = remember(provider.id) { securePrefs.getSetting("model_filter_${provider.id}", "all") }

    // Mutable state for each slot
    val keys = remember(provider.id) {
        initialKeys.map { mutableStateOf(it) }
    }
    val keyVisibilities = remember(provider.id) {
        (1..maxSlots).map { mutableStateOf(false) }
    }
    var selectedFilter by remember(provider.id) { mutableStateOf(savedFilter) }

    // How many slots to show: filled count + 1 (for next empty), clamped to max
    val filledCount = keys.count { it.value.isNotEmpty() }
    var visibleSlots by remember(provider.id) { mutableStateOf((filledCount + 1).coerceIn(1, maxSlots)) }

    val hasKey = keys[0].value.isNotEmpty()
    val hasChanged = keys.indices.any { keys[it].value != initialKeys[it] } || selectedFilter != savedFilter

    val categoryColor = CATEGORY_COLORS[provider.category] ?: AppPrimary

    ProviderCardFrame(
        provider = provider,
        isExpanded = isExpanded,
        onToggle = onToggle,
        categoryColor = categoryColor,
        hasKey = hasKey,
        keyPreview = if (hasKey) {
            val k = keys[0].value
            val keyCount = keys.count { it.value.isNotEmpty() }
            val preview = if (k.length > 16) "${k.take(6)}...${k.takeLast(4)}" else "\u2022".repeat(k.length.coerceAtMost(12))
            if (keyCount > 1) "$preview (+${keyCount - 1})" else preview
        } else null
    ) {
        HorizontalDivider(color = AppDarkGray, thickness = 1.dp)

        // Render key fields for visible slots
        for (i in 0 until visibleSlots) {
            val slotLabel = if (i == 0) "API Key" else "Key ${i + 1}"
            ApiKeyField(
                label = slotLabel,
                value = keys[i].value,
                onValueChange = { keys[i].value = it },
                visible = keyVisibilities[i].value,
                onToggleVisibility = { keyVisibilities[i].value = !keyVisibilities[i].value },
                onClear = { keys[i].value = "" }
            )
        }

        // "Add Key" button if there are more slots available
        if (visibleSlots < maxSlots) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.1f))
                        .border(1.dp, categoryColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .clickable { visibleSlots = (visibleSlots + 1).coerceAtMost(maxSlots) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Key",
                            tint = categoryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Key (${visibleSlots}/$maxSlots)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = categoryColor
                        )
                    }
                }
            }
        }

        ModelFilterRow(selectedFilter = selectedFilter, onSelect = { selectedFilter = it }, categoryColor = categoryColor)

        ActionRow(
            hasChanged = hasChanged,
            onCancel = {
                for (i in keys.indices) {
                    keys[i].value = initialKeys[i]
                }
                selectedFilter = savedFilter
                visibleSlots = (initialKeys.count { it.isNotEmpty() } + 1).coerceIn(1, maxSlots)
            },
            onSave = {
                for (i in keys.indices) {
                    securePrefs.saveApiKeySlot(provider.id, i + 1, keys[i].value.trim())
                }
                securePrefs.saveSetting("model_filter_${provider.id}", selectedFilter)
                if (keys[0].value.trim().isNotEmpty()) {
                    scope.launch {
                        val baseUrl = providerDefaultBaseUrl(provider.name)
                        val models = fetchModels(keys[0].value.trim(), baseUrl, provider.name)
                        if (models.isNotEmpty()) {
                            ModelCatalog.setModels(provider.name, models)
                        }
                    }
                }
                Toast.makeText(context, "${provider.name} keys saved", Toast.LENGTH_SHORT).show()
            }
        )
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
    keyPreview: String?,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface)
            .border(
                1.dp,
                if (isExpanded) categoryColor.copy(alpha = 0.3f) else AppDarkGray,
                RoundedCornerShape(12.dp)
            )
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderIcon(provider.id, provider.name)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CategoryBadge(provider.category, categoryColor)
                    if (provider.authType == "oauth") {
                        Spacer(modifier = Modifier.width(4.dp))
                        OAuthBadge()
                    } else if (provider.authType == "webcookie") {
                        Spacer(modifier = Modifier.width(4.dp))
                        WebBadge()
                    }
                }
                Text(
                    text = keyPreview ?: "No key set",
                    fontSize = 11.sp,
                    color = if (hasKey) Color(0xFF10B981) else AppMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (hasKey) Color(0xFF10B981) else AppMuted.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AppMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onClear),
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) categoryColor else AppDivider)
                        .clickable { onSelect(value) }
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
            OutlinedButton(
                onClick = onCancel,
                enabled = hasChanged,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppWhite),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) { Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = AppScreenBg),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = hasChanged
            ) { Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ── Badges ─────────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = category, fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold)
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
    val logoUrl = when (id) {
        "openai" -> "https://logo.clearbit.com/openai.com"
        "anthropic" -> "https://logo.clearbit.com/anthropic.com"
        "gemini" -> "https://logo.clearbit.com/google.com"
        "groq" -> "https://logo.clearbit.com/groq.com"
        "mistral" -> "https://logo.clearbit.com/mistral.ai"
        "cerebras" -> "https://logo.clearbit.com/cerebras.ai"
        "deepseek" -> "https://logo.clearbit.com/deepseek.com"
        "openrouter" -> "https://logo.clearbit.com/openrouter.ai"
        "claude" -> "https://logo.clearbit.com/anthropic.com"
        "cohere" -> "https://logo.clearbit.com/cohere.com"
        "together" -> "https://logo.clearbit.com/together.ai"
        "nvidia" -> "https://logo.clearbit.com/nvidia.com"
        "perplexity" -> "https://logo.clearbit.com/perplexity.ai"
        "huggingface" -> "https://logo.clearbit.com/huggingface.co"
        "ai21" -> "https://logo.clearbit.com/ai21.com"
        "alibaba" -> "https://logo.clearbit.com/alibaba.com"
        "baidu" -> "https://logo.clearbit.com/baidu.com"
        "tencent" -> "https://logo.clearbit.com/tencent.com"
        "github" -> "https://logo.clearbit.com/github.com"
        "cursor" -> "https://logo.clearbit.com/cursor.com"
        "windsurf" -> "https://logo.clearbit.com/codeium.com"
        "gitlab-duo" -> "https://logo.clearbit.com/gitlab.com"
        "bedrock" -> "https://logo.clearbit.com/aws.amazon.com"
        "vertex" -> "https://logo.clearbit.com/cloud.google.com"
        "azure-openai" -> "https://logo.clearbit.com/azure.microsoft.com"
        "cloudflare-ai" -> "https://logo.clearbit.com/cloudflare.com"
        "antigravity" -> "https://logo.clearbit.com/antigravity.com"
        "suno" -> "https://logo.clearbit.com/suno.ai"
        "leonardo" -> "https://logo.clearbit.com/leonardo.ai"
        "fireworks" -> "https://logo.clearbit.com/fireworks.ai"
        "deepinfra" -> "https://logo.clearbit.com/deepinfra.com"
        "hyperbolic" -> "https://logo.clearbit.com/hyperbolic.xyz"
        "reka" -> "https://logo.clearbit.com/reka.ai"
        "sambanova" -> "https://logo.clearbit.com/sambanova.ai"
        "nebius" -> "https://logo.clearbit.com/nebius.com"
        "modal" -> "https://logo.clearbit.com/modal.com"
        "databricks" -> "https://logo.clearbit.com/databricks.com"
        "snowflake" -> "https://logo.clearbit.com/snowflake.com"
        "heroku" -> "https://logo.clearbit.com/heroku.com"
        "baseten" -> "https://logo.clearbit.com/baseten.co"
        "ollama-cloud" -> "https://logo.clearbit.com/ollama.com"
        "opencode", "opencode-zen", "opencode-go" -> "https://logo.clearbit.com/opencode.ai"
        "zen", "zenmux", "zenmux-free" -> "https://logo.clearbit.com/zen.ly"
        "github-models" -> "https://logo.clearbit.com/github.com"
        "pollinations" -> "https://logo.clearbit.com/pollinations.ai"
        "duckduckgo-web" -> "https://logo.clearbit.com/duckduckgo.com"
        "claude-web" -> "https://logo.clearbit.com/anthropic.com"
        "gemini-web" -> "https://logo.clearbit.com/google.com"
        "grok-web" -> "https://logo.clearbit.com/x.com"
        "perplexity-web" -> "https://logo.clearbit.com/perplexity.ai"
        "deepseek-web" -> "https://logo.clearbit.com/deepseek.com"
        "kimi" -> "https://logo.clearbit.com/kimi.com"
        "qwen" -> "https://logo.clearbit.com/alibaba.com"
        "minimax" -> "https://logo.clearbit.com/minimax.com"
        "moonshot" -> "https://logo.clearbit.com/moonshot.com"
        "yi" -> "https://logo.clearbit.com/lingyiwanwu.com"
        "baichuan" -> "https://logo.clearbit.com/baichuan.com"
        "stepfun" -> "https://logo.clearbit.com/stepfun.com"
        "doubao" -> "https://logo.clearbit.com/doubao.com"
        "volcengine" -> "https://logo.clearbit.com/volcengine.com"
        "coze" -> "https://logo.clearbit.com/coze.com"
        "kiro" -> "https://logo.clearbit.com/kiro.com"
        "codex" -> "https://logo.clearbit.com/codex.com"
        "qoder" -> "https://logo.clearbit.com/qoder.com"
        "zed-hosted" -> "https://logo.clearbit.com/zed.com"
        "trae" -> "https://logo.clearbit.com/trae.com"
        "devin-cli" -> "https://logo.clearbit.com/devin.ai"
        "v0-vercel" -> "https://logo.clearbit.com/vercel.com"
        "v0-vercel-web" -> "https://logo.clearbit.com/vercel.com"
        "vercel-ai-gateway" -> "https://logo.clearbit.com/vercel.com"
        "copilot-web" -> "https://logo.clearbit.com/github.com"
        "copilot-m365-web" -> "https://logo.clearbit.com/microsoft.com"
        "huggingchat" -> "https://logo.clearbit.com/huggingface.co"
        "glm" -> "https://logo.clearbit.com/zhipu.com"
        "puter" -> "https://logo.clearbit.com/puter.com"
        "nlpcloud" -> "https://logo.clearbit.com/nlpcloud.com"
        "auggie" -> "https://logo.clearbit.com/auggie.com"
        "chipotle" -> "https://logo.clearbit.com/chipotle.com"
        "lmarena" -> "https://logo.clearbit.com/lmsys.org"
        "grok-cli" -> "https://logo.clearbit.com/x.com"
        "xai" -> "https://logo.clearbit.com/x.ai"
        "ideogram" -> "https://logo.clearbit.com/ideogram.ai"
        "haiper" -> "https://logo.clearbit.com/haiper.ai"
        "veo" -> "https://logo.clearbit.com/veo.ai"
        "udio" -> "https://logo.clearbit.com/udio.com"
        "dify" -> "https://logo.clearbit.com/dify.ai"
        "ovhcloud" -> "https://logo.clearbit.com/ovhcloud.com"
        "codestral" -> "https://logo.clearbit.com/mistral.ai"
        "wandb" -> "https://logo.clearbit.com/wandb.com"
        "xiaomi-mimo" -> "https://logo.clearbit.com/xiaomi.com"
        "upstage" -> "https://logo.clearbit.com/upstage.ai"
        "scaleway" -> "https://logo.clearbit.com/scaleway.com"
        "novita" -> "https://logo.clearbit.com/novita.ai"
        "siliconflow" -> "https://logo.clearbit.com/siliconflow.com"
        "liquid" -> "https://logo.clearbit.com/liquid.ai"
        "modelscope" -> "https://logo.clearbit.com/modelscope.cn"
        "lambda-ai" -> "https://logo.clearbit.com/lambda.ai"
        "hackclub" -> "https://logo.clearbit.com/hackclub.com"
        "blackbox" -> "https://logo.clearbit.com/blackbox.ai"
        "blackbox-web" -> "https://logo.clearbit.com/blackbox.ai"
        "preditbase" -> "https://logo.clearbit.com/predibase.com"
        "gemini-business" -> "https://logo.clearbit.com/google.com"
        "requesty" -> "https://logo.clearbit.com/requesty.ai"
        "digitalocean" -> "https://logo.clearbit.com/digitalocean.com"
        "meta-llama" -> "https://logo.clearbit.com/meta.com"
        "qianfan" -> "https://logo.clearbit.com/baidu.com"
        "venice" -> "https://logo.clearbit.com/venice.ai"
        "poe-web" -> "https://logo.clearbit.com/poe.com"
        "kimi-coding" -> "https://logo.clearbit.com/kimi.com"
        "kimi-coding-apikey" -> "https://logo.clearbit.com/kimi.com"
        "kimi-web" -> "https://logo.clearbit.com/kimi.com"
        "doubao-web" -> "https://logo.clearbit.com/doubao.com"
        "qwen-web" -> "https://logo.clearbit.com/alibaba.com"
        "yuanbao-web" -> ""
        "bailian-coding-plan" -> "https://logo.clearbit.com/alibaba.com"
        "glm-cn" -> "https://logo.clearbit.com/zhipu.com"
        "glmt" -> "https://logo.clearbit.com/zhipu.com"
        "minimax-cn" -> "https://logo.clearbit.com/minimax.com"
        "alibaba-cn" -> "https://logo.clearbit.com/alibaba.com"
        "cline" -> "https://logo.clearbit.com/cline.com"
        "vertex-partner" -> "https://logo.clearbit.com/cloud.google.com"
        "command-code" -> "https://logo.clearbit.com/cohere.com"
        "mimocode" -> ""
        else -> null
    }

    var imageError by remember(id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppDivider),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null && logoUrl.isNotEmpty() && !imageError) {
            coil.compose.AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                onError = { imageError = true }
            )
        } else {
            Text(
                text = name.take(2).uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppPrimary
            )
        }
    }
}
