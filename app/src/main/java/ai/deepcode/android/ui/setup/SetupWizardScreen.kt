package ai.deepcode.android.ui.setup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.local.EncryptedPrefs
import ai.deepcode.android.data.local.Profile
import ai.deepcode.android.data.local.ProfileManager
import ai.deepcode.android.data.local.WORKFLOW_ANTIGRAVITY
import ai.deepcode.android.data.local.WORKFLOW_CLAUDE_CODE
import ai.deepcode.android.data.local.WORKFLOW_DEEPSEEK_HARNESS
import ai.deepcode.android.data.local.WORKFLOW_DIRECT
import ai.deepcode.android.service.tools.ToolExecutor
import ai.deepcode.android.ui.theme.*
import com.jarves.mh.data.ApiKeyVault
import com.jarves.mh.data.AppPreferences
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.DevStack
import com.jarves.mh.model.ProviderKind
import com.jarves.mh.model.ProviderProfile
import com.jarves.mh.model.defaultDshApiForProvider
import com.jarves.mh.runtime.RuntimeInstallProgress
import com.jarves.mh.runtime.RuntimeInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val RoleChips = listOf("Developer", "AI Engineer", "Fullstack", "Builder", "Researcher", "Student", "Hacker")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    profileManager: ProfileManager,
    isFirstRun: Boolean,
    onBack: (() -> Unit)?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { EncryptedPrefs.getInstance(context) }
    val appPrefs = remember { AppPreferences(context) }
    val vault = remember { ApiKeyVault(context) }
    val installer = remember { RuntimeInstaller(context) }

    val totalSteps = 5
    var currentStep by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (currentStep > 0) {
            currentStep--
        } else {
            onBack?.invoke()
        }
    }

    // Step 0: Profile & Identity
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("Developer") }
    var selectedAccentId by remember { mutableStateOf(ActiveAccent.id) }
    var avatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Step 1: Workflow Engine Selection
    var workflowMode by remember { mutableStateOf(WORKFLOW_DIRECT) }

    // Step 2: Provider Configuration
    var dshProviderKind by remember { mutableStateOf(ProviderKind.DEEPSEEK) }
    var dshBaseUrl by remember { mutableStateOf(ProviderKind.DEEPSEEK.defaultBaseUrl) }
    var dshModel by remember { mutableStateOf(ProviderKind.DEEPSEEK.defaultModel) }
    var dshApiKey by remember { mutableStateOf("") }
    var showDshKey by remember { mutableStateOf(false) }

    // Direct Mode Provider Configuration
    var directProviderName by remember { mutableStateOf("Google Gemini") }
    var directApiKey by remember { mutableStateOf("") }
    var showDirectKey by remember { mutableStateOf(false) }

    // Step 3: Voice / TTS Configuration
    var ttsEnabled by remember { mutableStateOf(true) }
    var ttsEngine by remember { mutableStateOf("edge_tts") }
    var ttsVoice by remember { mutableStateOf("en-US-JennyNeural") }
    var isTestingAudio by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }
    var activeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Step 4: Subsystem Setup
    var isSubsystemInstalled by remember { mutableStateOf(installer.isInstalled()) }
    var isInstallingSubsystem by remember { mutableStateOf(false) }
    var subsystemProgress by remember { mutableStateOf<RuntimeInstallProgress?>(null) }
    var subsystemError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeMediaPlayer?.stop()
                activeMediaPlayer?.reset()
                activeMediaPlayer?.release()
            } catch (_: Exception) {}
            activeMediaPlayer = null
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { s ->
                    avatarBitmap = BitmapFactory.decodeStream(s)
                }
            } catch (_: Exception) {}
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (currentStep + 1) / totalSteps.toFloat(),
        animationSpec = tween(durationMillis = 350),
        label = "setupProgress"
    )

    fun completeSetup() {
        // 1. Create and persist profile
        val finalName = name.trim().ifBlank { "Developer" }
        val newProfile = Profile(
            name = finalName,
            role = role.trim().ifBlank { null },
            accentId = selectedAccentId,
            pinLength = 4,
            useBiometric = false
        )
        val saved = profileManager.addProfile(newProfile)
        avatarBitmap?.let { bm ->
            val path = profileManager.saveAvatar(saved.id, bm)
            profileManager.updateProfile(saved.copy(avatarPath = path))
        }
        profileManager.setActiveProfile(saved.id)

        // 2. Save workflow mode
        securePrefs.setWorkflowMode(workflowMode)
        appPrefs.onboardingComplete = true

        // 3. Save provider configuration
        if (workflowMode == WORKFLOW_DEEPSEEK_HARNESS) {
            val agent = AgentKind.DEEPSEEK_HARNESS
            appPrefs.agentKind = agent.stableId
            val matchedProv = ai.deepcode.android.ui.settings.APP_WORKFLOW_PROVIDERS.find {
                it.providerKind == dshProviderKind && (it.defaultBaseUrl.isBlank() || it.defaultBaseUrl == dshBaseUrl)
            }
            val profile = ProviderProfile(
                kind = dshProviderKind,
                baseUrl = dshBaseUrl,
                model = dshModel,
                hasSecret = dshApiKey.isNotBlank(),
                dshApi = matchedProv?.dshApi ?: defaultDshApiForProvider(dshProviderKind)
            )
            appPrefs.saveProvider(profile, agent)
            matchedProv?.let {
                appPrefs.preferences.edit().putString("provider_dsh_app_id", it.id).apply()
            }
            if (dshApiKey.isNotBlank()) {
                vault.putSecret(dshProviderKind.name, dshApiKey.trim())
                matchedProv?.let { vault.putSecret(it.id, dshApiKey.trim()) }
                matchedProv?.storageKeys?.forEach { k ->
                    securePrefs.saveApiKey(k, dshApiKey.trim())
                }
            }
        } else if (workflowMode == WORKFLOW_CLAUDE_CODE) {
            appPrefs.agentKind = AgentKind.CLAUDE_CODE.stableId
            if (directApiKey.isNotBlank()) {
                vault.putSecret(ProviderKind.ANTHROPIC.name, directApiKey.trim())
                securePrefs.saveApiKey("anthropic", directApiKey.trim())
            }
        } else if (workflowMode == WORKFLOW_ANTIGRAVITY) {
            appPrefs.agentKind = AgentKind.ANTIGRAVITY.stableId
            if (directApiKey.isNotBlank()) {
                securePrefs.saveApiKey("gemini", directApiKey.trim())
            }
        } else {
            // Direct native engine
            if (directApiKey.isNotBlank()) {
                val pKey = when (directProviderName) {
                    "Google Gemini" -> "gemini"
                    "OpenAI" -> "openai"
                    "Anthropic" -> "anthropic"
                    "DeepSeek" -> "deepseek"
                    "OpenRouter" -> "openrouter"
                    "Groq" -> "groq"
                    else -> directProviderName.lowercase()
                }
                securePrefs.saveApiKey(pKey, directApiKey.trim())
            }
        }

        // 4. Save TTS preferences
        securePrefs.saveSetting("tts_enabled", if (ttsEnabled) "true" else "false")
        if (ttsEnabled) {
            when (ttsEngine) {
                "gemini" -> {
                    securePrefs.saveSetting("tts_priority", "provider_first")
                    securePrefs.saveSetting("tts_provider", "Google Gemini")
                    securePrefs.saveSetting("tts_model", "gemini-3.8-flash-tts")
                    securePrefs.saveSetting("tts_gemini_voice", ttsVoice)
                }
                "openai" -> {
                    securePrefs.saveSetting("tts_priority", "provider_first")
                    securePrefs.saveSetting("tts_provider", "OpenAI")
                    securePrefs.saveSetting("tts_model", "tts-1")
                    securePrefs.saveSetting("tts_openai_voice", ttsVoice)
                }
                "android" -> {
                    securePrefs.saveSetting("tts_priority", "default_first")
                    securePrefs.saveSetting("tts_provider", "Default")
                    securePrefs.saveSetting("tts_model", "android")
                }
                else -> {
                    securePrefs.saveSetting("tts_priority", "default_first")
                    securePrefs.saveSetting("tts_provider", "Default")
                    securePrefs.saveSetting("tts_model", "edge_tts")
                }
            }
        }

        onDone()
    }

    Scaffold(
        containerColor = AppScreenBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isFirstRun) "Welcome to DeepCode" else "Create Profile",
                            color = AppWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Step ${currentStep + 1} of $totalSteps: " + when (currentStep) {
                                0 -> "Identity"
                                1 -> "Workflow"
                                2 -> "AI Provider"
                                3 -> "Voice & Audio"
                                4 -> "Subsystem & Launch"
                                else -> ""
                            },
                            color = AppMuted,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    if (currentStep > 0 || onBack != null) {
                        IconButton(onClick = {
                            if (currentStep > 0) currentStep--
                            else onBack?.invoke()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppWhite)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        },
        bottomBar = {
            Surface(
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AppBorder)
                        ) {
                            Text("Back", color = AppWhite)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < totalSteps - 1) {
                                currentStep++
                            } else {
                                completeSetup()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveAccent.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentStep < totalSteps - 1) "Continue" else "Launch DeepCode",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (currentStep < totalSteps - 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Material 3 Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = ActiveAccent.primary,
                trackColor = AppBorder.copy(alpha = 0.4f)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (currentStep) {
                    0 -> StepIdentity(
                        name = name,
                        onNameChange = { name = it },
                        role = role,
                        onRoleChange = { role = it },
                        accentId = selectedAccentId,
                        onAccentChange = { selectedAccentId = it },
                        avatarBitmap = avatarBitmap,
                        onPickAvatar = { avatarPicker.launch("image/*") }
                    )
                    1 -> StepWorkflow(
                        selectedMode = workflowMode,
                        onSelectMode = { workflowMode = it }
                    )
                    2 -> StepProvider(
                        workflowMode = workflowMode,
                        dshProviderKind = dshProviderKind,
                        onDshProviderChange = { kind ->
                            dshProviderKind = kind
                            dshBaseUrl = kind.defaultBaseUrl
                            dshModel = kind.defaultModel
                        },
                        dshBaseUrl = dshBaseUrl,
                        onDshBaseUrlChange = { dshBaseUrl = it },
                        dshModel = dshModel,
                        onDshModelChange = { dshModel = it },
                        dshApiKey = dshApiKey,
                        onDshApiKeyChange = { dshApiKey = it },
                        showDshKey = showDshKey,
                        onToggleShowDshKey = { showDshKey = !showDshKey },
                        directProvider = directProviderName,
                        onDirectProviderChange = { directProviderName = it },
                        directApiKey = directApiKey,
                        onDirectApiKeyChange = { directApiKey = it },
                        showDirectKey = showDirectKey,
                        onToggleShowDirectKey = { showDirectKey = !showDirectKey }
                    )
                    3 -> StepVoice(
                        context = context,
                        scope = scope,
                        ttsEnabled = ttsEnabled,
                        onTtsEnabledChange = { ttsEnabled = it },
                        ttsEngine = ttsEngine,
                        onTtsEngineChange = { engine ->
                            ttsEngine = engine
                            ttsVoice = when (engine) {
                                "gemini" -> "Puck"
                                "openai" -> "alloy"
                                else -> "en-US-JennyNeural"
                            }
                        },
                        ttsVoice = ttsVoice,
                        onTtsVoiceChange = { ttsVoice = it },
                        isTestingAudio = isTestingAudio,
                        setIsTestingAudio = { isTestingAudio = it },
                        isPlayingAudio = isPlayingAudio,
                        setIsPlayingAudio = { isPlayingAudio = it },
                        activeMediaPlayer = activeMediaPlayer,
                        setActiveMediaPlayer = { activeMediaPlayer = it }
                    )
                    4 -> StepSubsystemReview(
                        context = context,
                        scope = scope,
                        workflowMode = workflowMode,
                        installer = installer,
                        isSubsystemInstalled = isSubsystemInstalled,
                        onSubsystemInstalled = { isSubsystemInstalled = true },
                        isInstallingSubsystem = isInstallingSubsystem,
                        setIsInstallingSubsystem = { isInstallingSubsystem = it },
                        subsystemProgress = subsystemProgress,
                        setSubsystemProgress = { subsystemProgress = it },
                        subsystemError = subsystemError,
                        setSubsystemError = { subsystemError = it },
                        userName = name.ifBlank { "Developer" },
                        role = role,
                        ttsEngine = if (ttsEnabled) ttsEngine else "Disabled"
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// STEP 0: Identity
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun StepIdentity(
    name: String,
    onNameChange: (String) -> Unit,
    role: String,
    onRoleChange: (String) -> Unit,
    accentId: String,
    onAccentChange: (String) -> Unit,
    avatarBitmap: Bitmap?,
    onPickAvatar: () -> Unit
) {
    Text(
        text = "Personalize Your Workspace",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppWhite
    )
    Text(
        text = "Set up your avatar, profile name, role, and UI accent theme.",
        fontSize = 13.sp,
        color = AppMuted
    )

    // Avatar Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(AppSurfaceVariant)
                .clickable(onClick = onPickAvatar),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = "Avatar",
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(Icons.Default.AccountCircle, null, tint = ActiveAccent.primary, modifier = Modifier.size(54.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Button(
                onClick = onPickAvatar,
                colors = ButtonDefaults.buttonColors(containerColor = AppSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp), tint = AppWhite)
                Spacer(Modifier.width(8.dp))
                Text(if (avatarBitmap != null) "Change Photo" else "Upload Photo", color = AppWhite, fontSize = 12.sp)
            }
            Text("Optional profile picture", fontSize = 11.sp, color = AppMuted)
        }
    }

    // Name Input
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Display Name") },
        placeholder = { Text("e.g. Alex, Satoshi, or DeepDev") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ActiveAccent.primary,
            unfocusedBorderColor = AppBorder,
            focusedContainerColor = AppSurface,
            unfocusedContainerColor = AppSurface,
            focusedTextColor = AppWhite,
            unfocusedTextColor = AppWhite
        )
    )

    // Role Chips
    Text("Your Primary Role", fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(RoleChips) { chipRole ->
            val isSelected = role.equals(chipRole, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onRoleChange(chipRole) },
                label = { Text(chipRole, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ActiveAccent.primary.copy(alpha = 0.2f),
                    selectedLabelColor = ActiveAccent.primary,
                    containerColor = AppSurface,
                    labelColor = AppMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isSelected) ActiveAccent.primary else AppBorder
                )
            )
        }
    }

    // Accent Theme Swatches
    Text("Theme Accent Color", fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(AccentThemes) { theme ->
            val isSelected = theme.id == accentId
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(theme.primary)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onAccentChange(theme.id) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// STEP 1: Workflow Engine Selection
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun StepWorkflow(
    selectedMode: String,
    onSelectMode: (String) -> Unit
) {
    Text(
        text = "Select Execution Workflow",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppWhite
    )
    Text(
        text = "Choose how you want DeepCode to execute coding agents and tool pipelines.",
        fontSize = 13.sp,
        color = AppMuted
    )

    WorkflowOptionCard(
        title = "Direct In-App Engine",
        badge = "Recommended · Native",
        desc = "Fast, lightweight in-app agent runner. Streams multi-model chat, executes local file operations, terminal commands, and opens live web preview.",
        icon = Icons.Default.Bolt,
        isSelected = selectedMode == WORKFLOW_DIRECT,
        onClick = { onSelectMode(WORKFLOW_DIRECT) }
    )

    WorkflowOptionCard(
        title = "DeepSeek Harness (DSH)",
        badge = "Autonomous CLI · Multi-Provider",
        desc = "Official DeepSeek coding agent running inside the private rootless Ubuntu 20.04 LTS subsystem. Supports DeepSeek, OpenRouter, OpenCode Zen, and Custom endpoints.",
        icon = Icons.Default.Terminal,
        isSelected = selectedMode == WORKFLOW_DEEPSEEK_HARNESS,
        onClick = { onSelectMode(WORKFLOW_DEEPSEEK_HARNESS) }
    )

    WorkflowOptionCard(
        title = "Claude Code CLI",
        badge = "Anthropic Terminal Agent",
        desc = "Anthropic's autonomous coding CLI agent running inside Ubuntu userspace with full workspace toolchain execution.",
        icon = Icons.Default.Code,
        isSelected = selectedMode == WORKFLOW_CLAUDE_CODE,
        onClick = { onSelectMode(WORKFLOW_CLAUDE_CODE) }
    )

    WorkflowOptionCard(
        title = "Google Antigravity CLI",
        badge = "Official Google Agent",
        desc = "Google DeepMind's official autonomous coding agent running inside Ubuntu PRoot with reasoning effort control.",
        icon = Icons.Default.AutoAwesome,
        isSelected = selectedMode == WORKFLOW_ANTIGRAVITY,
        onClick = { onSelectMode(WORKFLOW_ANTIGRAVITY) }
    )
}

@Composable
private fun WorkflowOptionCard(
    title: String,
    badge: String,
    desc: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ActiveAccent.primary.copy(alpha = 0.12f) else AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ActiveAccent.primary else AppBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) ActiveAccent.primary else AppSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else AppPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 15.sp)
                        Text(badge, color = ActiveAccent.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = ActiveAccent.primary)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(desc, color = AppMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// STEP 2: Provider Configuration
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun StepProvider(
    workflowMode: String,
    dshProviderKind: ProviderKind,
    onDshProviderChange: (ProviderKind) -> Unit,
    dshBaseUrl: String,
    onDshBaseUrlChange: (String) -> Unit,
    dshModel: String,
    onDshModelChange: (String) -> Unit,
    dshApiKey: String,
    onDshApiKeyChange: (String) -> Unit,
    showDshKey: Boolean,
    onToggleShowDshKey: () -> Unit,
    directProvider: String,
    onDirectProviderChange: (String) -> Unit,
    directApiKey: String,
    onDirectApiKeyChange: (String) -> Unit,
    showDirectKey: Boolean,
    onToggleShowDirectKey: () -> Unit
) {
    if (workflowMode == WORKFLOW_DEEPSEEK_HARNESS) {
        Text(
            text = "Configure DeepSeek Harness Provider",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppWhite
        )
        Text(
            text = "Select which API provider DeepSeek Harness will use to execute reasoning and tool turns.",
            fontSize = 13.sp,
            color = AppMuted
        )

        Text("Select API Provider", fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ai.deepcode.android.ui.settings.APP_WORKFLOW_PROVIDERS) { prov ->
                val isSelected = prov.providerKind == dshProviderKind && (prov.defaultBaseUrl.isBlank() || prov.defaultBaseUrl == dshBaseUrl)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onDshProviderChange(prov.providerKind)
                        onDshBaseUrlChange(prov.defaultBaseUrl)
                        onDshModelChange(prov.defaultModel)
                    },
                    label = { Text(prov.name, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ActiveAccent.primary.copy(alpha = 0.2f),
                        selectedLabelColor = ActiveAccent.primary,
                        containerColor = AppSurface,
                        labelColor = AppMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) ActiveAccent.primary else AppBorder
                    )
                )
            }
        }

        OutlinedTextField(
            value = dshBaseUrl,
            onValueChange = onDshBaseUrlChange,
            label = { Text("Base Endpoint URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ActiveAccent.primary,
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedTextColor = AppWhite,
                unfocusedTextColor = AppWhite
            )
        )

        OutlinedTextField(
            value = dshModel,
            onValueChange = onDshModelChange,
            label = { Text("Model Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ActiveAccent.primary,
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedTextColor = AppWhite,
                unfocusedTextColor = AppWhite
            )
        )

        OutlinedTextField(
            value = dshApiKey,
            onValueChange = onDshApiKeyChange,
            label = { Text("API Key (${dshProviderKind.title})") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (showDshKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShowDshKey) {
                    Icon(
                        imageVector = if (showDshKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle key",
                        tint = AppMuted
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ActiveAccent.primary,
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedTextColor = AppWhite,
                unfocusedTextColor = AppWhite
            )
        )
    } else {
        // Direct Native or Claude / Antigravity Mode
        Text(
            text = "AI Model & API Provider",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppWhite
        )
        Text(
            text = "Configure your AI provider credentials. You can also skip this and configure it anytime in Settings.",
            fontSize = 13.sp,
            color = AppMuted
        )

        val directProviders = listOf("Google Gemini", "DeepSeek", "OpenAI", "Anthropic", "OpenRouter", "Groq")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(directProviders) { p ->
                val isSelected = p == directProvider
                FilterChip(
                    selected = isSelected,
                    onClick = { onDirectProviderChange(p) },
                    label = { Text(p, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ActiveAccent.primary.copy(alpha = 0.2f),
                        selectedLabelColor = ActiveAccent.primary,
                        containerColor = AppSurface,
                        labelColor = AppMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) ActiveAccent.primary else AppBorder
                    )
                )
            }
        }

        OutlinedTextField(
            value = directApiKey,
            onValueChange = onDirectApiKeyChange,
            label = { Text("$directProvider API Key") },
            placeholder = { Text("Paste your API key here (or leave empty)") },
            singleLine = true,
            visualTransformation = if (showDirectKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleShowDirectKey) {
                    Icon(
                        imageVector = if (showDirectKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle key",
                        tint = AppMuted
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ActiveAccent.primary,
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface,
                focusedTextColor = AppWhite,
                unfocusedTextColor = AppWhite
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// STEP 3: Voice / Text-to-Speech (TTS) Configuration
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun StepVoice(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    ttsEnabled: Boolean,
    onTtsEnabledChange: (Boolean) -> Unit,
    ttsEngine: String,
    onTtsEngineChange: (String) -> Unit,
    ttsVoice: String,
    onTtsVoiceChange: (String) -> Unit,
    isTestingAudio: Boolean,
    setIsTestingAudio: (Boolean) -> Unit,
    isPlayingAudio: Boolean,
    setIsPlayingAudio: (Boolean) -> Unit,
    activeMediaPlayer: MediaPlayer?,
    setActiveMediaPlayer: (MediaPlayer?) -> Unit
) {
    Text(
        text = "Voice & Speech Synthesis",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppWhite
    )
    Text(
        text = "Configure neural speech output so DeepCode can read code explanations, stories, or responses aloud.",
        fontSize = 13.sp,
        color = AppMuted
    )

    // Switch row
    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Enable Speech Synthesis", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Text("Allow generating and playing audio voice replies", color = AppMuted, fontSize = 11.sp)
            }
            Switch(
                checked = ttsEnabled,
                onCheckedChange = onTtsEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = ActiveAccent.primary)
            )
        }
    }

    if (ttsEnabled) {
        Text("TTS Synthesis Engine", fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)

        val engines = listOf(
            Triple("edge_tts", "Microsoft Edge Neural", "Free · Built-in · 400+ voices · 0 key"),
            Triple("gemini", "Google Gemini Expressive", "Dynamic emotion sensing · Puck, Charon"),
            Triple("openai", "OpenAI Neural Voice", "Studio prosody · Alloy, Echo, Nova"),
            Triple("android", "Android System TTS", "100% offline on-device speech engine")
        )

        engines.forEach { (engId, engTitle, engDesc) ->
            val isSelected = ttsEngine == engId
            Surface(
                color = if (isSelected) ActiveAccent.primary.copy(alpha = 0.12f) else AppSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ActiveAccent.primary else AppBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTtsEngineChange(engId) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(engTitle, fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                        Text(engDesc, color = AppMuted, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = { onTtsEngineChange(engId) },
                        colors = RadioButtonDefaults.colors(selectedColor = ActiveAccent.primary)
                    )
                }
            }
        }

        // Voice Picker Chips
        val sampleVoices = when (ttsEngine) {
            "gemini" -> listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
            "openai" -> listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
            else -> listOf("en-US-JennyNeural", "en-US-GuyNeural", "en-GB-SoniaNeural")
        }

        Text("Select Voice Cadence", fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sampleVoices) { v ->
                val isSelected = ttsVoice.equals(v, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onTtsVoiceChange(v) },
                    label = { Text(v, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ActiveAccent.primary.copy(alpha = 0.2f),
                        selectedLabelColor = ActiveAccent.primary,
                        containerColor = AppSurface,
                        labelColor = AppMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) ActiveAccent.primary else AppBorder
                    )
                )
            }
        }

        // Test Voice Button
        Button(
            onClick = {
                if (isPlayingAudio) {
                    try {
                        activeMediaPlayer?.stop()
                        activeMediaPlayer?.reset()
                        activeMediaPlayer?.release()
                    } catch (_: Exception) {}
                    setActiveMediaPlayer(null)
                    setIsPlayingAudio(false)
                } else {
                    setIsTestingAudio(true)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val executor = ToolExecutor(context)
                            val prov = when (ttsEngine) {
                                "gemini" -> "Google Gemini"
                                "openai" -> "OpenAI"
                                else -> "Default"
                            }
                            val model = when (ttsEngine) {
                                "gemini" -> "gemini-3.8-flash-tts"
                                "openai" -> "tts-1"
                                "android" -> "android"
                                else -> "edge_tts"
                            }
                            val testSentence = "Hello! This is a test of your text to speech voice in DeepCode."
                            val result = executor.synthesizeSpeechWithResult(
                                text = testSentence,
                                preferredProvider = prov,
                                preferredModel = model,
                                verbatim = true
                            )
                            withContext(Dispatchers.Main) {
                                setIsTestingAudio(false)
                                if (!result.audioPath.isNullOrBlank()) {
                                    val f = File(result.audioPath)
                                    if (f.exists() && f.length() > 0) {
                                        val player = MediaPlayer().apply {
                                            setAudioAttributes(
                                                android.media.AudioAttributes.Builder()
                                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                                    .build()
                                            )
                                            java.io.FileInputStream(f).use { fis -> setDataSource(fis.fd) }
                                            setOnPreparedListener { mp ->
                                                mp.start()
                                                setIsPlayingAudio(true)
                                            }
                                            setOnCompletionListener {
                                                setIsPlayingAudio(false)
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                setIsPlayingAudio(false)
                                                true
                                            }
                                            prepareAsync()
                                        }
                                        setActiveMediaPlayer(player)
                                    }
                                } else {
                                    Toast.makeText(context, "Voice test: ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                setIsTestingAudio(false)
                                Toast.makeText(context, "TTS Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = AppSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (isPlayingAudio) Color(0xFFEF4444) else ActiveAccent.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isTestingAudio) "Synthesizing test audio..."
                else if (isPlayingAudio) "Stop playback"
                else "Test Voice Preview",
                color = AppWhite,
                fontSize = 13.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// STEP 4: Linux Subsystem & Final Review
// ─────────────────────────────────────────────────────────────────────
@Composable
private fun StepSubsystemReview(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    workflowMode: String,
    installer: RuntimeInstaller,
    isSubsystemInstalled: Boolean,
    onSubsystemInstalled: () -> Unit,
    isInstallingSubsystem: Boolean,
    setIsInstallingSubsystem: (Boolean) -> Unit,
    subsystemProgress: RuntimeInstallProgress?,
    setSubsystemProgress: (RuntimeInstallProgress?) -> Unit,
    subsystemError: String?,
    setSubsystemError: (String?) -> Unit,
    userName: String,
    role: String,
    ttsEngine: String
) {
    Text(
        text = "Subsystem & Ready to Launch",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppWhite
    )
    Text(
        text = "Review your configuration and prepare your environment.",
        fontSize = 13.sp,
        color = AppMuted
    )

    // Subsystem Card
    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isSubsystemInstalled) Color(0xFF22C55E).copy(alpha = 0.5f) else AppBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isSubsystemInstalled) Color(0xFF22C55E) else Color(0xFFEAB308))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isSubsystemInstalled) "Ubuntu 20.04 LTS (Ready)" else "Ubuntu Subsystem (Optional)",
                        fontWeight = FontWeight.Bold,
                        color = AppWhite,
                        fontSize = 14.sp
                    )
                }
                Text("PRoot ARM64", fontSize = 11.sp, color = AppMuted, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isSubsystemInstalled)
                    "Rootless Linux subsystem is ready. PRoot ARM64 binaries and CLI tools can be executed inside the app sandbox."
                else
                    "Provides an isolated rootless Ubuntu 20.04 LTS sandbox for Node.js, Python, Git, and autonomous CLI agents.",
                fontSize = 12.sp,
                color = AppMuted,
                lineHeight = 17.sp
            )

            if (isInstallingSubsystem) {
                Spacer(Modifier.height(12.dp))
                subsystemProgress?.let { prog ->
                    Text("${prog.message} (${(prog.fraction * 100).toInt()}%)", color = ActiveAccent.primary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { prog.fraction }, modifier = Modifier.fillMaxWidth(), color = ActiveAccent.primary)
                } ?: run {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ActiveAccent.primary)
                }
            }

            if (subsystemError != null) {
                Spacer(Modifier.height(8.dp))
                Text("Error: $subsystemError", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            if (!isSubsystemInstalled) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (!isInstallingSubsystem) {
                            setIsInstallingSubsystem(true)
                            setSubsystemError(null)
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val agent = when (workflowMode) {
                                        WORKFLOW_DEEPSEEK_HARNESS -> AgentKind.DEEPSEEK_HARNESS
                                        WORKFLOW_CLAUDE_CODE -> AgentKind.CLAUDE_CODE
                                        else -> AgentKind.ANTIGRAVITY
                                    }
                                    installer.ensureInstalled(
                                        selectedStacks = setOf(DevStack.PYTHON, DevStack.ANDROID),
                                        agent = agent
                                    ) { p ->
                                        withContext(Dispatchers.Main) { setSubsystemProgress(p) }
                                    }
                                    withContext(Dispatchers.Main) {
                                        setIsInstallingSubsystem(false)
                                        setSubsystemProgress(null)
                                        onSubsystemInstalled()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        setIsInstallingSubsystem(false)
                                        setSubsystemError(e.message ?: "Install failed")
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isInstallingSubsystem,
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveAccent.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isInstallingSubsystem) "Bootstrapping..." else "Install Linux Subsystem Now")
                }
            }
        }
    }

    // Summary Card
    Surface(
        color = AppSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Configuration Overview", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
            ReviewRow("User Profile", "$userName ($role)")
            ReviewRow("Execution Workflow", when (workflowMode) {
                WORKFLOW_DEEPSEEK_HARNESS -> "DeepSeek Harness (Autonomous PRoot CLI)"
                WORKFLOW_CLAUDE_CODE -> "Claude Code CLI"
                WORKFLOW_ANTIGRAVITY -> "Google Antigravity CLI"
                else -> "Direct In-App Engine (Native Android)"
            })
            ReviewRow("Voice / TTS Engine", ttsEngine)
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppMuted, fontSize = 12.sp)
        Text(value, color = AppWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
