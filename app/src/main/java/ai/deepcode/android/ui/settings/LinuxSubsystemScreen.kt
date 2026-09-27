package ai.deepcode.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.local.WORKFLOW_ANTIGRAVITY
import ai.deepcode.android.data.local.WORKFLOW_CLAUDE_CODE
import ai.deepcode.android.data.local.WORKFLOW_DEEPSEEK_HARNESS
import ai.deepcode.android.data.local.WORKFLOW_DIRECT
import ai.deepcode.android.data.local.WORKFLOW_HERMES
import ai.deepcode.android.ui.theme.*
import com.jarves.mh.data.AppPreferences
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.runtime.RuntimeInstaller
import com.jarves.mh.runtime.RuntimeInstallProgress
import com.jarves.mh.data.ApiKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppWorkflowProvider(
    val id: String,
    val name: String,
    val providerKind: ProviderKind,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val dshApi: String,
    val storageKeys: List<String>
)

val APP_WORKFLOW_PROVIDERS = listOf(
    AppWorkflowProvider(
        id = "zen",
        name = "Zen AI (Free)",
        providerKind = ProviderKind.OPENCODE_ZEN,
        defaultBaseUrl = "https://opencode.ai/zen/v1",
        defaultModel = "mimo-v2.5-free",
        dshApi = "openai-completions",
        storageKeys = listOf("zen", "opencode-zen", "opencode", "zenmux")
    ),
    AppWorkflowProvider(
        id = "deepseek",
        name = "DeepSeek",
        providerKind = ProviderKind.DEEPSEEK,
        defaultBaseUrl = "https://api.deepseek.com/anthropic",
        defaultModel = "deepseek-v4-flash",
        dshApi = "anthropic-messages",
        storageKeys = listOf("deepseek")
    ),
    AppWorkflowProvider(
        id = "openrouter",
        name = "OpenRouter",
        providerKind = ProviderKind.LLM_ROUTER,
        defaultBaseUrl = "https://openrouter.ai/api",
        defaultModel = "~anthropic/claude-sonnet-latest",
        dshApi = "anthropic-messages",
        storageKeys = listOf("openrouter")
    ),
    AppWorkflowProvider(
        id = "openai",
        name = "OpenAI",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        dshApi = "openai-completions",
        storageKeys = listOf("openai")
    ),
    AppWorkflowProvider(
        id = "anthropic",
        name = "Anthropic",
        providerKind = ProviderKind.ANTHROPIC,
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-sonnet-4-6",
        dshApi = "anthropic-messages",
        storageKeys = listOf("anthropic")
    ),
    AppWorkflowProvider(
        id = "gemini",
        name = "Google Gemini",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        defaultModel = "gemini-2.5-flash",
        dshApi = "openai-completions",
        storageKeys = listOf("gemini", "google gemini", "google-gemini", "google")
    ),
    AppWorkflowProvider(
        id = "groq",
        name = "Groq",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        dshApi = "openai-completions",
        storageKeys = listOf("groq")
    ),
    AppWorkflowProvider(
        id = "nvidia",
        name = "NVIDIA NIM",
        providerKind = ProviderKind.NVIDIA_NIM,
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "qwen/qwen2.5-coder-32b-instruct",
        dshApi = "openai-completions",
        storageKeys = listOf("nvidia", "nvidia-nim")
    ),
    AppWorkflowProvider(
        id = "together",
        name = "Together AI",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://api.together.xyz/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        dshApi = "openai-completions",
        storageKeys = listOf("together", "together-ai")
    ),
    AppWorkflowProvider(
        id = "mistral",
        name = "Mistral AI",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://api.mistral.ai/v1",
        defaultModel = "codestral-latest",
        dshApi = "openai-completions",
        storageKeys = listOf("mistral")
    ),
    AppWorkflowProvider(
        id = "cerebras",
        name = "Cerebras",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "qwen-3.8-27b",
        dshApi = "openai-completions",
        storageKeys = listOf("cerebras", "cerebrus")
    ),
    AppWorkflowProvider(
        id = "kimi",
        name = "Moonshot Kimi",
        providerKind = ProviderKind.KIMI,
        defaultBaseUrl = "https://api.moonshot.ai/anthropic",
        defaultModel = "kimi-k2.6",
        dshApi = "anthropic-messages",
        storageKeys = listOf("kimi")
    ),
    AppWorkflowProvider(
        id = "custom",
        name = "Custom Endpoint",
        providerKind = ProviderKind.CUSTOM,
        defaultBaseUrl = "",
        defaultModel = "",
        dshApi = "openai-completions",
        storageKeys = listOf("custom")
    )
)

fun getSavedKeyForWorkflowProvider(provider: AppWorkflowProvider, securePrefs: EncryptedPrefs, vault: ApiKeyVault): String {
    vault.getSecret(provider.providerKind.name)?.takeIf { it.isNotBlank() }?.let { return it }
    vault.getSecret(provider.id)?.takeIf { it.isNotBlank() }?.let { return it }
    for (k in provider.storageKeys) {
        val key = securePrefs.getApiKey(k)
        if (key.isNotBlank()) return key
        val slotKey = securePrefs.getApiKeySlot(k, 1)
        if (slotKey.isNotBlank()) return slotKey
    }
    return ""
}

fun isWorkflowProviderConfigured(provider: AppWorkflowProvider, securePrefs: EncryptedPrefs, vault: ApiKeyVault): Boolean {
    if (provider.id == "zen") return true
    return getSavedKeyForWorkflowProvider(provider, securePrefs, vault).isNotBlank()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxSubsystemScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installer = remember { RuntimeInstaller(context) }
    val securePrefs = remember { EncryptedPrefs.getInstance(context) }
    val appPrefs = remember { AppPreferences(context) }
    val vault = remember { ApiKeyVault(context) }

    var workflowMode by remember { mutableStateOf(securePrefs.getWorkflowMode()) }
    val initialProfile = remember { appPrefs.loadProvider(vault, AgentKind.DEEPSEEK_HARNESS) }
    val savedAppId = remember { appPrefs.preferences.getString("provider_dsh_app_id", null) }
    val initialSelectedProvider = remember {
        APP_WORKFLOW_PROVIDERS.find { it.id == savedAppId }
            ?: APP_WORKFLOW_PROVIDERS.find { it.providerKind == initialProfile.kind }
            ?: APP_WORKFLOW_PROVIDERS.first()
    }
    var selectedWorkflowProvider by remember { mutableStateOf(initialSelectedProvider) }
    var dshProviderKind by remember { mutableStateOf(initialProfile.kind) }
    var dshDshApi by remember { mutableStateOf(initialProfile.dshApi.ifBlank { initialSelectedProvider.dshApi }) }
    var dshBaseUrl by remember { mutableStateOf(initialProfile.baseUrl) }
    var dshModel by remember { mutableStateOf(initialProfile.model) }
    var dshApiKey by remember {
        mutableStateOf(
            getSavedKeyForWorkflowProvider(initialSelectedProvider, securePrefs, vault)
                .ifBlank { vault.getSecret(initialProfile.kind.name) ?: securePrefs.getApiKey(initialProfile.kind.name.lowercase()) }
        )
    }
    var showDshKey by remember { mutableStateOf(false) }

    var isInstalled by remember { mutableStateOf(installer.isInstalled()) }
    var installedAgents by remember { mutableStateOf<Map<AgentKind, String>>(emptyMap()) }
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf<RuntimeInstallProgress?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isInstalled = installer.isInstalled()
            if (isInstalled) {
                installedAgents = installer.installedAgentVersions()
            }
        }
    }

    val runSetup: () -> Unit = {
        if (!isInstalling) {
            isInstalling = true
            installError = null
            scope.launch(Dispatchers.IO) {
                try {
                    installer.ensureInstalled(
                        selectedStacks = setOf(DevStack.PYTHON, DevStack.ANDROID),
                        agent = AgentKind.ANTIGRAVITY
                    ) { progress ->
                        withContext(Dispatchers.Main) {
                            installProgress = progress
                        }
                    }
                    withContext(Dispatchers.Main) {
                        isInstalled = installer.isInstalled()
                        installedAgents = installer.installedAgentVersions()
                        isInstalling = false
                        installProgress = null
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        installError = e.message ?: "Installation failed"
                        isInstalling = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Text("Linux Subsystem & Runtimes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppWhite)
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(38.dp)
                            .depthPill(shape = CircleShape, elevation = 2.dp, isDark = isDarkThemeActive)
                            .bouncyClickable(provideHaptic = true, onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppWhite, modifier = Modifier.size(18.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppScreenBg)
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = AppScreenBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Workflow Selection Card
            item {
                Text("Agent Execution Workflow", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select how prompts and autonomous agents are executed:", color = AppMuted, fontSize = 12.sp)

                        val modes = listOf(
                            Triple(WORKFLOW_DIRECT, "Direct In-App Engine", "Native Android streaming agent loop"),
                            Triple(WORKFLOW_HERMES, "Hermes Agent", "Nous Research autonomous agent · self-improving skills & persistent memory"),
                            Triple(WORKFLOW_DEEPSEEK_HARNESS, "DeepSeek Harness (DSH)", "Autonomous CLI in PRoot with multi-provider routing"),
                            Triple(WORKFLOW_CLAUDE_CODE, "Claude Code CLI", "Anthropic autonomous terminal agent in PRoot"),
                            Triple(WORKFLOW_ANTIGRAVITY, "Google Antigravity CLI", "Official Google agent in PRoot with reasoning effort")
                        )

                        modes.forEach { (modeId, modeTitle, modeDesc) ->
                            val isSelected = workflowMode == modeId
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .depthPill(
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = if (isSelected) 2.5.dp else 1.dp,
                                        customGradient = if (isSelected) {
                                            if (isDarkThemeActive) listOf(Color(0xFF1E2838), Color(0xFF131A26))
                                            else listOf(Color(0xFFE8F1FC), Color(0xFFD6E6F8))
                                        } else null,
                                        customBorderColor = if (isSelected) AppPrimary else null,
                                        highlightAlpha = if (isSelected) 0.35f else 0.08f,
                                        isDark = isDarkThemeActive
                                    )
                                    .bouncyClickable(provideHaptic = true) {
                                        workflowMode = modeId
                                        securePrefs.setWorkflowMode(modeId)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            modeTitle,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) AppPrimary else AppWhite,
                                            fontSize = 13.5.sp
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(modeDesc, color = AppMuted, fontSize = 11.5.sp)
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            workflowMode = modeId
                                            securePrefs.setWorkflowMode(modeId)
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = AppPrimary, unselectedColor = AppBorder)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // DeepSeek Harness Provider Configuration Card
            item {
                Text("Workflow API Provider (DeepSeek Harness)", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Select from API providers configured in DeepCode to power autonomous agents:", color = AppMuted, fontSize = 12.sp)

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(APP_WORKFLOW_PROVIDERS) { p ->
                                val isSelected = p.id == selectedWorkflowProvider.id
                                val isConfigured = isWorkflowProviderConfigured(p, securePrefs, vault)
                                Box(
                                    modifier = Modifier
                                        .depthPill(
                                            shape = RoundedCornerShape(20.dp),
                                            elevation = if (isSelected) 3.dp else 1.5.dp,
                                            customGradient = if (isSelected) {
                                                listOf(AppPrimary.copy(alpha = 0.28f), AppPrimaryGradientEnd.copy(alpha = 0.18f))
                                            } else null,
                                            customBorderColor = if (isSelected) AppPrimary else if (isConfigured) Color(0xFF22C55E).copy(alpha = 0.5f) else null,
                                            highlightAlpha = if (isSelected) 0.35f else 0.12f,
                                            isDark = isDarkThemeActive
                                        )
                                        .bouncyClickable(provideHaptic = true) {
                                            selectedWorkflowProvider = p
                                            dshProviderKind = p.providerKind
                                            dshDshApi = p.dshApi
                                            dshBaseUrl = p.defaultBaseUrl
                                            dshModel = p.defaultModel
                                            val saved = getSavedKeyForWorkflowProvider(p, securePrefs, vault)
                                            if (saved.isNotBlank()) {
                                                dshApiKey = saved
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isConfigured) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                        }
                                        Text(
                                            text = p.name,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) AppPrimary else if (isConfigured) AppWhite else AppMuted
                                        )
                                    }
                                }
                            }
                        }

                        if (isWorkflowProviderConfigured(selectedWorkflowProvider, securePrefs, vault) && selectedWorkflowProvider.id != "custom") {
                            Surface(
                                color = Color(0xFF22C55E).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Using API credentials already saved in DeepCode", fontSize = 11.5.sp, color = Color(0xFF22C55E))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = dshBaseUrl,
                            onValueChange = { dshBaseUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurfaceVariant,
                                unfocusedContainerColor = AppSurfaceVariant,
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite
                            )
                        )

                        OutlinedTextField(
                            value = dshModel,
                            onValueChange = { dshModel = it },
                            label = { Text("Model Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurfaceVariant,
                                unfocusedContainerColor = AppSurfaceVariant,
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite
                            )
                        )

                        OutlinedTextField(
                            value = dshApiKey,
                            onValueChange = { dshApiKey = it },
                            label = { Text("API Key (${selectedWorkflowProvider.name})") },
                            placeholder = { Text(if (selectedWorkflowProvider.id == "zen") "Free (no key required)" else "sk-...") },
                            singleLine = true,
                            visualTransformation = if (showDshKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showDshKey = !showDshKey }) {
                                    Icon(
                                        imageVector = if (showDshKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key",
                                        tint = AppMuted
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurfaceVariant,
                                unfocusedContainerColor = AppSurfaceVariant,
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite
                            )
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .depthPill(
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = 3.dp,
                                    customGradient = listOf(AppPrimary, AppPrimaryGradientEnd),
                                    highlightAlpha = 0.35f,
                                    isDark = isDarkThemeActive
                                )
                                .bouncyClickable(provideHaptic = true) {
                                    val prof = ProviderProfile(
                                        kind = dshProviderKind,
                                        baseUrl = dshBaseUrl,
                                        model = dshModel,
                                        hasSecret = dshApiKey.isNotBlank(),
                                        dshApi = dshDshApi
                                    )
                                    appPrefs.saveProvider(prof, AgentKind.DEEPSEEK_HARNESS)
                                    appPrefs.preferences.edit().putString("provider_dsh_app_id", selectedWorkflowProvider.id).apply()
                                    if (dshApiKey.isNotBlank()) {
                                        vault.putSecret(dshProviderKind.name, dshApiKey.trim())
                                        vault.putSecret(selectedWorkflowProvider.id, dshApiKey.trim())
                                        selectedWorkflowProvider.storageKeys.forEach { k ->
                                            securePrefs.saveApiKey(k, dshApiKey.trim())
                                        }
                                    }
                                    Toast.makeText(context, "Saved ${selectedWorkflowProvider.name} for autonomous workflows", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save Workflow Provider Settings", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                            }
                        }
                    }
                }
            }

            // Environment Status Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(
                            shape = RoundedCornerShape(18.dp),
                            elevation = 3.dp,
                            customBorderColor = if (isInstalled) Color(0xFF22C55E).copy(alpha = 0.5f) else null
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isInstalled) Color(0xFF22C55E) else Color(0xFFEAB308))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (isInstalled) "Ubuntu 20.04 LTS (Ready)" else "Environment Not Configured",
                                    fontWeight = FontWeight.Bold,
                                    color = AppWhite,
                                    fontSize = 15.sp
                                )
                            }
                            Text(
                                text = "PRoot ARM64",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AppMuted
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isInstalled)
                                "A rootless userspace Linux subsystem is running inside your app sandbox. It executes commands, runs toolchains (Git, Node.js, Python), and drives local coding agents."
                            else
                                "Install the self-contained Ubuntu 20.04 LTS subsystem to run real Linux binaries, Node.js, Git, Python, and local agent CLI runners without root access.",
                            fontSize = 12.sp,
                            color = AppMuted,
                            lineHeight = 18.sp
                        )

                        if (isInstalling) {
                            Spacer(Modifier.height(14.dp))
                            installProgress?.let { prog ->
                                Text(
                                    text = "${prog.message} (${(prog.fraction * 100).toInt()}%)",
                                    fontSize = 12.sp,
                                    color = AppPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { prog.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = AppPrimary
                                )
                            } ?: run {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppPrimary)
                            }
                        }

                        if (installError != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Error: $installError",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .depthPill(
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = 3.dp,
                                    customGradient = if (isInstalled) {
                                        listOf(DepthTokens.PillGradientTopDark, DepthTokens.PillGradientBottomDark)
                                    } else {
                                        listOf(AppPrimary, AppPrimaryGradientEnd)
                                    },
                                    customBorderColor = if (isInstalled) AppBorder else null,
                                    highlightAlpha = if (isInstalled) 0.15f else 0.35f,
                                    isDark = isDarkThemeActive
                                )
                                .bouncyClickable(enabled = !isInstalling, provideHaptic = true) {
                                    runSetup()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isInstalled) Icons.Default.Refresh else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = if (isInstalled) AppPrimary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isInstalled) "Repair / Update Runtime" else "Bootstrap Linux Subsystem",
                                    color = if (isInstalled) AppPrimary else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Installed Coding Agents Card
            item {
                Text("Autonomous Coding Agents", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        AgentRow(
                            name = "Google Antigravity CLI",
                            version = installedAgents[AgentKind.ANTIGRAVITY] ?: (if (isInstalled) "Ready" else "Bundled in APK"),
                            status = if (isInstalled) "Active" else "Bundled",
                            icon = Icons.Default.AutoAwesome
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        AgentRow(
                            name = "Claude Code CLI",
                            version = installedAgents[AgentKind.CLAUDE_CODE] ?: "On-demand download",
                            status = if (installedAgents.containsKey(AgentKind.CLAUDE_CODE)) "Installed" else "Available",
                            icon = Icons.Default.Code
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        AgentRow(
                            name = "DeepSeek Harness (DSH)",
                            version = installedAgents[AgentKind.DEEPSEEK_HARNESS] ?: "On-demand download",
                            status = if (installedAgents.containsKey(AgentKind.DEEPSEEK_HARNESS)) "Installed" else "Available",
                            icon = Icons.Default.Terminal
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        AgentRow(
                            name = "Hermes Agent",
                            version = installedAgents[AgentKind.HERMES] ?: (if (isInstalled) "Ready" else "Autonomous PRoot"),
                            status = if (installedAgents.containsKey(AgentKind.HERMES) || isInstalled) "Active" else "Available",
                            icon = Icons.Default.Psychology
                        )
                    }
                }
            }

            // Toolchains Card
            item {
                Text("Development Toolchains", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .depthCard(shape = RoundedCornerShape(18.dp), elevation = 3.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        ToolchainRow(name = "Node.js & npm", desc = "Core runtime JavaScript/TypeScript engine", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        ToolchainRow(name = "Git Version Control", desc = "Repository cloning, commits & diffs", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        ToolchainRow(name = "Python 3 & pip", desc = "Python scripting and machine learning", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder.copy(alpha = 0.5f))
                        ToolchainRow(name = "Android SDK & aapt2", desc = "On-device APK compilation without ADB", available = isInstalled)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(
    name: String,
    version: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AppPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(name, fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
                Text(version, color = AppMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        val isActive = status.contains("Active", ignoreCase = true) || status.contains("Ready", ignoreCase = true)
        Box(
            modifier = Modifier
                .depthPill(
                    shape = RoundedCornerShape(8.dp),
                    elevation = 1.dp,
                    customGradient = if (isActive) {
                        listOf(Color(0xFF166534).copy(alpha = 0.45f), Color(0xFF14532D).copy(alpha = 0.35f))
                    } else null,
                    customBorderColor = if (isActive) Color(0xFF22C55E).copy(alpha = 0.45f) else null,
                    highlightAlpha = if (isActive) 0.25f else 0.1f,
                    isDark = isDarkThemeActive
                )
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Text(
                text = status,
                color = if (isActive) Color(0xFF4ADE80) else AppWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ToolchainRow(
    name: String,
    desc: String,
    available: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(name, fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
            Text(desc, color = AppMuted, fontSize = 11.sp)
        }
        Icon(
            imageVector = if (available) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (available) Color(0xFF22C55E) else AppMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}
