package ai.deepcode.android.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    repository: DeepCodeRepository,
    profileManager: ai.deepcode.android.data.local.ProfileManager,
    onMenuClick: () -> Unit,
    onViewLogs: () -> Unit,
    onViewAgents: () -> Unit = {},
    onManagePersonas: () -> Unit = {},
    onManageTemplates: () -> Unit = {},
    onNavigateToVpn: () -> Unit = {},
    onNavigateToApiKeys: () -> Unit = {},
    onNavigateToCloudflare: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToThemesAndWallpapers: () -> Unit = {},
    onNavigateToVoiceModel: () -> Unit = {},
    onNavigateToLinuxSubsystem: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs

    val coroutineScope = rememberCoroutineScope()
    val personaEnabled by securePrefs.personaEnabledFlow.collectAsStateWithLifecycle()
    val activeCustomPersona by securePrefs.customPersonaFlow.collectAsStateWithLifecycle()
    val rootMode by securePrefs.rootModeFlow.collectAsStateWithLifecycle()

    val isRootAvailable by ai.deepcode.android.util.RootSystem.isRootAvailable.collectAsStateWithLifecycle()
    val isRootGranted by ai.deepcode.android.util.RootSystem.isRootGranted.collectAsStateWithLifecycle()
    val rootFlavor by ai.deepcode.android.util.RootSystem.rootFlavor.collectAsStateWithLifecycle()

    var showRootDialog by remember { mutableStateOf(false) }
    var isRequestingRoot by remember { mutableStateOf(false) }
    var rootTestOutput by remember { mutableStateOf("") }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTurnsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showConnectionDialog by remember { mutableStateOf(false) }

    val refreshRateMode by ai.deepcode.android.util.RefreshRateManager.currentMode.collectAsStateWithLifecycle()
    val appliedHz by ai.deepcode.android.util.RefreshRateManager.appliedRefreshRate.collectAsStateWithLifecycle()
    val supportedRates by ai.deepcode.android.util.RefreshRateManager.supportedRates.collectAsStateWithLifecycle()

    var profileName by remember { mutableStateOf(securePrefs.getSetting("profile_name", "Deep Patel")) }
    var profileEmail by remember { mutableStateOf(securePrefs.getSetting("profile_email", "deep@deepcode.ai")) }
    var profileRefreshKey by remember { mutableStateOf(0) }
    var maxTurns by remember { mutableStateOf(securePrefs.getSetting("max_history_turns", "8")) }

    fun refreshProfileFromManager() {
        val p = profileManager.getActiveProfile()
        if (p != null) {
            profileName = p.name
            profileEmail = profileManager.getProfileSetting(p.id, "email", "user@deepcode.ai")
        }
    }

    LaunchedEffect(profileRefreshKey) {
        refreshProfileFromManager()
    }

    fun saveProfileToManager(name: String, email: String) {
        val p = profileManager.getActiveProfile()
        if (p != null) {
            profileManager.updateProfile(p.copy(name = name))
            profileManager.saveProfileSetting(p.id, "email", email)
            securePrefs.saveSetting("profile_name", name)
            securePrefs.saveSetting("profile_email", email)
            profileRefreshKey++
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Settings Header
        item {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppWhite,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
        }

        // Profile Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 3.dp, isDark = isDarkThemeActive)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val p = remember(profileRefreshKey) { profileManager.getActiveProfile() }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AppPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (p != null) {
                            val bm by produceState<Bitmap?>(initialValue = profileManager.getAvatarBitmap(p.id), key1 = p.id, key2 = p.avatarPath, key3 = profileRefreshKey) {
                                if (value == null && p.avatarPath != null) {
                                    value = withContext(Dispatchers.IO) {
                                        try { android.graphics.BitmapFactory.decodeFile(p.avatarPath) } catch (_: Exception) { null }
                                    }
                                }
                            }
                            if (bm != null) {
                                Image(
                                    bitmap = bm!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Person, null, tint = AppPrimary, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Icon(Icons.Default.Person, null, tint = AppPrimary, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = profileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = AppWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = profileEmail,
                            fontSize = 12.sp,
                            color = AppMuted
                        )
                    }
                }

                // Edit Profile Button (Orange depth pill)
                Box(
                    modifier = Modifier
                        .depthPill(
                            shape = RoundedCornerShape(10.dp),
                            elevation = 2.dp,
                            isDark = isDarkThemeActive,
                            customGradient = listOf(AppPrimary.copy(alpha = 0.22f), AppPrimary.copy(alpha = 0.08f)),
                            customBorderColor = AppPrimary.copy(alpha = 0.6f)
                        )
                        .bouncyClickable(provideHaptic = true) {
                            refreshProfileFromManager()
                            showEditProfileDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Edit Profile",
                        color = AppPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Section: Appearance & Display
        item {
            SettingsSectionHeader(icon = Icons.Default.Palette, title = "Appearance & Display")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsNavRow(
                    icon = Icons.Default.Palette,
                    title = "Theme & Display",
                    subtitle = "Themes, accents, wallpapers, UI scale & 144Hz variable display rate",
                    onClick = onNavigateToThemesAndWallpapers
                )
            }
        }

        // Section: AI & Intelligence
        item {
            SettingsSectionHeader(icon = Icons.Default.Psychology, title = "AI & Intelligence")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                // Voice / Speech Model Row
                val ttsPriority = securePrefs.getSetting("tts_priority", "provider_first")
                val ttsProvider = securePrefs.getSetting("tts_provider", "Google Gemini")
                val ttsModel = securePrefs.getSetting("tts_model", "gemini-3.8-flash-tts")
                val voiceSubtitle = if (ttsPriority == "provider_first") {
                    "Priority: $ttsProvider ($ttsModel)"
                } else {
                    "Priority: Built-in Edge Neural TTS (Free)"
                }

                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "Voice / Speech Model",
                    subtitle = voiceSubtitle,
                    onClick = onNavigateToVoiceModel
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                val currentWorkflowMode by securePrefs.workflowModeFlow.collectAsState()
                val workflowSubtitle = when (currentWorkflowMode) {
                    ai.deepcode.android.data.local.WORKFLOW_DEEPSEEK_HARNESS -> "DeepSeek Harness (CLI)"
                    ai.deepcode.android.data.local.WORKFLOW_CLAUDE_CODE -> "Claude Code (CLI)"
                    ai.deepcode.android.data.local.WORKFLOW_ANTIGRAVITY -> "Google Antigravity (CLI)"
                    else -> "Direct In-App Engine"
                }

                SettingsNavRow(
                    icon = Icons.Default.Dns,
                    title = "Linux Subsystem & Runtimes",
                    subtitle = "$workflowSubtitle · Ubuntu 20.04 PRoot",
                    onClick = onNavigateToLinuxSubsystem
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Custom Persona Row
                val activePersonaName = if (personaEnabled && activeCustomPersona.isNotEmpty()) {
                    val custom = try {
                        val raw = securePrefs.getSetting("saved_personas", "[]")
                        val json = org.json.JSONArray(raw)
                        (0 until json.length()).map { i ->
                            val obj = json.getJSONObject(i)
                            obj.getString("name") to obj.getString("content")
                        }
                    } catch (_: Exception) { emptyList() }
                    val all = ai.deepcode.android.ui.settings.builtInPersonas.map { it.name to it.content } + custom
                    all.find { it.second == activeCustomPersona }?.first ?: "Custom"
                } else "Default Assistant"

                SettingsNavRow(
                    icon = Icons.Default.Face,
                    title = "Custom Persona & Tone",
                    subtitle = "Active: $activePersonaName",
                    onClick = onManagePersonas
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Root & Terminal Access Row
                SettingsNavRow(
                    icon = Icons.Default.Shield,
                    title = "Root & Terminal Access",
                    subtitle = when {
                        rootMode && isRootGranted -> "Active (${rootFlavor.displayName} - uid=0) • Native ADB & Shell"
                        isRootAvailable -> "${rootFlavor.displayName} detected • Tap to grant Superuser"
                        else -> "Standard terminal • Tap to check/request root"
                    },
                    onClick = { showRootDialog = true }
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Manage Agents Row
                SettingsNavRow(
                    icon = Icons.Default.Group,
                    title = "Manage Agents",
                    subtitle = "System instructions and custom autonomous agents",
                    onClick = onViewAgents
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Chat History Memory Limit Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTurnsDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AppPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Chat History Memory Limit",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Max conversation context turns preserved",
                                fontSize = 11.sp,
                                color = AppMuted
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (maxTurns) {
                                "-1" -> "Unlimited"
                                "0" -> "No History"
                                else -> "$maxTurns turns"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = AppMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Cross-Chat Memory Row
                SettingsNavRow(
                    icon = Icons.Default.Psychology,
                    title = "Cross-Chat Memory",
                    subtitle = "Manage shared long-term memory between in-app and Telegram chats",
                    onClick = onNavigateToMemory
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Manage Templates Row
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "Manage Templates",
                    subtitle = "Reusable prompt shortcuts and code starters",
                    onClick = onManageTemplates
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Plugins Row
                SettingsSubscreenRow(
                    icon = Icons.Default.Extension,
                    title = "Plugins",
                    subtitle = "${ai.deepcode.android.plugin.PluginRegistry.getEnabledCount()} of ${ai.deepcode.android.plugin.PluginRegistry.getTotalCount()} plugins enabled",
                    onClick = onNavigateToPlugins
                )
            }
        }

        // Section: Network & Security
        item {
            SettingsSectionHeader(icon = Icons.Default.Lock, title = "Network & Security")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsSubscreenRow(
                    icon = Icons.Default.Key,
                    title = "API Keys",
                    subtitle = "Manage your provider keys and credentials",
                    onClick = onNavigateToApiKeys
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Cloud,
                    title = "Cloudflare Settings",
                    subtitle = "Configure Cloudflare for AI image and video generation",
                    onClick = onNavigateToCloudflare
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Public,
                    title = "VPN Settings",
                    subtitle = "Manage VPN tunnel, proxy mode, and connection status",
                    onClick = onNavigateToVpn
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Shield,
                    title = "Security Settings",
                    subtitle = "App lock, safe execution guard & cache cleanup",
                    onClick = { showSecurityDialog = true }
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Link,
                    title = "Connection Settings",
                    subtitle = "Command timeout, offline mode & Telegram sync",
                    onClick = { showConnectionDialog = true }
                )
            }
        }

        // Section: App & System
        item {
            SettingsSectionHeader(icon = Icons.Default.Info, title = "App & System")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsSubscreenRow(
                    icon = Icons.Default.Terminal,
                    title = "View Logs",
                    subtitle = "Real-time system, network, and crash logs",
                    onClick = onViewLogs
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Info,
                    title = "About DeepCode",
                    subtitle = "v${ai.deepcode.android.BuildConfig.VERSION_NAME} (Build ${ai.deepcode.android.BuildConfig.VERSION_CODE})",
                    onClick = { Toast.makeText(context, "DeepCode v${ai.deepcode.android.BuildConfig.VERSION_NAME} (Build ${ai.deepcode.android.BuildConfig.VERSION_CODE})", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        // Section: Danger Zone
        item {
            SettingsSectionHeader(icon = Icons.Default.Warning, title = "Danger Zone")
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthPill(
                        shape = RoundedCornerShape(14.dp),
                        elevation = 2.5.dp,
                        isDark = isDarkThemeActive,
                        customGradient = listOf(Color(0xFFE53935).copy(alpha = 0.22f), Color(0xFFE53935).copy(alpha = 0.08f)),
                        customBorderColor = Color(0xFFE53935).copy(alpha = 0.6f)
                    )
                    .bouncyClickable(provideHaptic = true) { showDeleteAccountDialog = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account",
                        color = Color(0xFFE53935),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Turns Selection Dialog
    if (showTurnsDialog) {
        AlertDialog(
            onDismissRequest = { showTurnsDialog = false },
            title = { Text("Memory limit (turns)", color = AppWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val turnOptions = listOf(
                        "0" to "No History",
                        "4" to "4 turns",
                        "8" to "8 turns",
                        "16" to "16 turns",
                        "32" to "32 turns",
                        "-1" to "Unlimited"
                    )
                    turnOptions.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    maxTurns = value
                                    securePrefs.saveSetting("max_history_turns", value)
                                    showTurnsDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = AppWhite, fontSize = 14.sp)
                            if (maxTurns == value) {
                                Icon(Icons.Default.Check, null, tint = AppPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTurnsDialog = false }) {
                    Text("Cancel", color = AppMuted)
                }
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Profile Editor Dialog
    if (showEditProfileDialog) {
        EditProfileDialog(
            currentName = profileName,
            currentEmail = profileEmail,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, email ->
                saveProfileToManager(name, email)
                showEditProfileDialog = false
            }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account", color = AppWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you absolutely sure you want to delete your account? This action is permanent and will wipe all local logs, agents, keys, and session data.",
                    color = AppMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        Toast.makeText(context, "Account deletion requested", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete Forever", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = AppMuted)
                }
            },
            containerColor = AppSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showRootDialog) {
        RootAccessDialog(
            isRootAvailable = isRootAvailable,
            isRootGranted = isRootGranted,
            rootFlavor = rootFlavor,
            rootMode = rootMode,
            onToggleRootMode = { enabled ->
                securePrefs.saveBooleanSetting("root_mode", enabled)
            },
            onRequestRoot = {
                isRequestingRoot = true
                coroutineScope.launch {
                    val result = ai.deepcode.android.util.RootSystem.requestRootAccess(context)
                    isRequestingRoot = false
                    rootTestOutput = result.uidInfo.ifEmpty { result.message }
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                }
            },
            isRequesting = isRequestingRoot,
            onRunTest = { cmd ->
                coroutineScope.launch(Dispatchers.IO) {
                    val out = ai.deepcode.android.util.RootSystem.executeAsRoot(cmd)
                    withContext(Dispatchers.Main) {
                        rootTestOutput = "$ $cmd\n$out"
                    }
                }
            },
            testOutput = rootTestOutput,
            onDismiss = { showRootDialog = false }
        )
    }

    if (showRefreshRateDialog) {
        RefreshRateDialog(
            currentMode = refreshRateMode,
            appliedHz = appliedHz,
            supportedRates = supportedRates,
            onSelectMode = { mode ->
                ai.deepcode.android.util.RefreshRateManager.setMode(mode)
                val label = when (mode) {
                    "144" -> "144 Hz (Ultra High)"
                    "120" -> "120 Hz (High)"
                    "90" -> "90 Hz (Smooth)"
                    "60" -> "60 Hz (Standard)"
                    else -> "Variable 60 - 144 Hz (Adaptive)"
                }
                Toast.makeText(context, "Refresh rate set to $label", Toast.LENGTH_SHORT).show()
                showRefreshRateDialog = false
            },
            onDismiss = { showRefreshRateDialog = false }
        )
    }

    if (showSecurityDialog) {
        SecuritySettingsDialog(
            securePrefs = securePrefs,
            onDismiss = { showSecurityDialog = false }
        )
    }

    if (showConnectionDialog) {
        ConnectionSettingsDialog(
            securePrefs = securePrefs,
            onDismiss = { showConnectionDialog = false }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    control: @Composable () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "rowScale"
    )
    val rowModifier = if (onClick != null) {
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(rowModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                color = AppWhite
            )
        }
        control()
    }
}

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppWhite
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .background(AppPrimary, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun SettingsNavRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppWhite
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = AppMuted
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsSubscreenRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(provideHaptic = true) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = AppMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentEmail: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var email by remember { mutableStateOf(currentEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile", color = AppWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppWhite,
                        unfocusedTextColor = AppWhite,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppDarkGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Profile Email") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppWhite,
                        unfocusedTextColor = AppWhite,
                        focusedBorderColor = AppPrimary,
                        unfocusedBorderColor = AppDarkGray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledAppButton(
                onClick = {
                    if (name.isNotBlank() && email.isNotBlank()) {
                        onSave(name.trim(), email.trim())
                    }
                },
                text = "Save",
                backgroundColor = AppPrimary
            )
        },
        dismissButton = {
            OutlinedAppButton(
                onClick = onDismiss,
                text = "Cancel",
                color = AppMuted
            )
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun RefreshRateDialog(
    currentMode: String,
    appliedHz: Float,
    supportedRates: List<Float>,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        Triple(
            ai.deepcode.android.util.RefreshRateManager.MODE_DYNAMIC,
            "Variable (60 - 90 - 120 - 144 Hz)",
            "Adaptive: 144Hz during gestures & AI streaming, steps down to 60Hz when idle"
        ),
        Triple(
            ai.deepcode.android.util.RefreshRateManager.MODE_144,
            "Ultra High (144 Hz Max)",
            "Locks display to 144Hz max for ultra-high frame rate"
        ),
        Triple(
            ai.deepcode.android.util.RefreshRateManager.MODE_120,
            "High (120 Hz Max)",
            "Locks display to 120Hz smooth rate"
        ),
        Triple(
            ai.deepcode.android.util.RefreshRateManager.MODE_90,
            "Smooth (90 Hz Max)",
            "Balances responsiveness and power efficiency"
        ),
        Triple(
            ai.deepcode.android.util.RefreshRateManager.MODE_60,
            "Standard (60 Hz)",
            "Fixed 60Hz for maximum battery endurance"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = AppPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Display Refresh Rate",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppPrimary.copy(alpha = 0.2f))
                        .border(1.dp, AppPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "⚡ ${appliedHz.toInt()} Hz",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppPrimary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (supportedRates.isNotEmpty()) {
                    Text(
                        text = "Hardware modes detected: ${supportedRates.map { "${it.toInt()}Hz" }.distinct().joinToString(", ")}",
                        fontSize = 11.sp,
                        color = AppMuted
                    )
                    Spacer(Modifier.height(2.dp))
                }

                modes.forEach { (modeKey, title, subtitle) ->
                    val isSelected = currentMode == modeKey
                    val isDynamic = modeKey == ai.deepcode.android.util.RefreshRateManager.MODE_DYNAMIC
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .depthCard(
                                shape = RoundedCornerShape(12.dp),
                                elevation = if (isSelected) 3.dp else 1.dp,
                                customGradient = if (isSelected) listOf(
                                    AppPrimary.copy(alpha = 0.25f),
                                    AppPrimary.copy(alpha = 0.10f)
                                ) else null,
                                customBorderColor = if (isSelected) AppPrimary else AppBorder
                            )
                            .bouncyClickable(provideHaptic = true) {
                                onSelectMode(modeKey)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) AppPrimary else AppWhite
                                )
                                if (isDynamic) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AppSuccess.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "Adaptive",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AppSuccess
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = AppMuted,
                                lineHeight = 14.sp
                            )
                        }
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = AppPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledAppButton(
                onClick = onDismiss,
                text = "Close",
                backgroundColor = AppPrimary
            )
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun RootAccessDialog(
    isRootAvailable: Boolean,
    isRootGranted: Boolean,
    rootFlavor: ai.deepcode.android.util.RootFlavor,
    rootMode: Boolean,
    onToggleRootMode: (Boolean) -> Unit,
    onRequestRoot: () -> Unit,
    isRequesting: Boolean,
    onRunTest: (String) -> Unit,
    testOutput: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isRootGranted) AppPrimary else AppMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Root & Superuser",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppWhite
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRootGranted) AppSuccess.copy(alpha = 0.2f) else AppDarkGray.copy(alpha = 0.4f))
                        .border(1.dp, if (isRootGranted) AppSuccess.copy(alpha = 0.5f) else AppBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isRootGranted) "👑 Granted" else if (isRootAvailable) "⚠️ Detected" else "Not Rooted",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRootGranted) AppSuccess else if (isRootAvailable) Color(0xFFFFA000) else AppMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Root Environment:", fontSize = 12.sp, color = AppMuted)
                        Text(rootFlavor.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppWhite)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Superuser Status:", fontSize = 12.sp, color = AppMuted)
                        Text(
                            if (isRootGranted) "Granted (uid=0)" else if (isRootAvailable) "Pending Approval" else "No su binary found",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRootGranted) AppSuccess else if (isRootAvailable) Color(0xFFFFA000) else AppMuted
                        )
                    }
                }

                // Grant / Request Button
                FilledAppButton(
                    onClick = onRequestRoot,
                    text = if (isRequesting) "Requesting from ${rootFlavor.displayName}..." else if (isRootGranted) "Re-verify Superuser Access" else "Request Superuser Grant",
                    backgroundColor = if (isRootGranted) AppSurface else AppPrimary,
                    modifier = Modifier.fillMaxWidth()
                )

                // Root Mode Toggle for AI & Terminal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Root for AI & Terminal", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Executes shell and native ADB commands with uid=0", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = rootMode,
                        onCheckedChange = onToggleRootMode,
                        enabled = isRootGranted || isRootAvailable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }

                // Test Actions
                Text("Test Commands", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedAppButton(
                        onClick = { onRunTest("id") },
                        text = "Test 'id'",
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedAppButton(
                        onClick = { onRunTest("adb shell pm list features | head -n 5") },
                        text = "Test ADB Shell",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Terminal Output Viewer
                if (testOutput.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, AppBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Terminal Output:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppPrimary,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = testOutput,
                            fontSize = 11.sp,
                            color = Color(0xFF58A6FF),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            FilledAppButton(
                onClick = onDismiss,
                text = "Close",
                backgroundColor = AppPrimary
            )
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun SecuritySettingsDialog(
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var biometricLock by remember { mutableStateOf(securePrefs.getBooleanSetting("setting_biometric_lock", false)) }
    var safeMode by remember { mutableStateOf(securePrefs.getBooleanSetting("setting_safe_mode", false)) }
    var confirmRootCmds by remember { mutableStateOf(securePrefs.getBooleanSetting("setting_confirm_root_cmds", false)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Security & Protection",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Biometric Lock
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric App Lock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Require fingerprint or device PIN when opening DeepCode", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = biometricLock,
                        onCheckedChange = {
                            biometricLock = it
                            securePrefs.saveBooleanSetting("setting_biometric_lock", it)
                            Toast.makeText(context, if (it) "Biometric Lock enabled" else "Biometric Lock disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }

                // Safe Mode Execution
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safe Execution Guard", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Prevents destructive write/delete commands without explicit prompt", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = safeMode,
                        onCheckedChange = {
                            safeMode = it
                            securePrefs.saveBooleanSetting("setting_safe_mode", it)
                            Toast.makeText(context, if (it) "Safe Mode active" else "Safe Mode disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }

                // Confirm Root Commands
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Root Command Audit Log", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Log all native root/ADB commands with uid=0 execution details", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = confirmRootCmds,
                        onCheckedChange = {
                            confirmRootCmds = it
                            securePrefs.saveBooleanSetting("setting_confirm_root_cmds", it)
                            Toast.makeText(context, if (it) "Root command audit enabled" else "Root audit disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }

                // Purge Scratch & Cache
                FilledAppButton(
                    onClick = {
                        try {
                            val cacheDir = context.cacheDir
                            cacheDir.deleteRecursively()
                            Toast.makeText(context, "Temporary cache & scratch files purged successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error clearing cache: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    text = "Purge Scratch & Cache Files",
                    backgroundColor = AppField,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledAppButton(
                onClick = onDismiss,
                text = "Close",
                backgroundColor = AppPrimary
            )
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun ConnectionSettingsDialog(
    securePrefs: ai.deepcode.android.data.local.EncryptedPrefs,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var commandTimeout by remember { mutableStateOf(securePrefs.getSetting("setting_command_timeout", "45")) }
    var offlineMode by remember { mutableStateOf(securePrefs.getBooleanSetting("setting_offline_mode", false)) }
    var telegramSync by remember { mutableStateOf(securePrefs.getBooleanSetting("setting_tg_bg_sync", true)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Connection & Sync",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppWhite
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Command Execution Timeout
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(12.dp)
                ) {
                    Text("Command Execution Timeout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                    Spacer(Modifier.height(4.dp))
                    Text("Maximum seconds allowed for local terminal and ADB root commands", fontSize = 11.sp, color = AppMuted)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("15", "30", "45", "90").forEach { sec ->
                            val isSel = commandTimeout == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AppPrimary else AppDarkGray.copy(alpha = 0.5f))
                                    .clickable {
                                        commandTimeout = sec
                                        securePrefs.saveSetting("setting_command_timeout", sec)
                                        Toast.makeText(context, "Command timeout set to ${sec}s", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}s",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) AppWhite else AppMuted
                                )
                            }
                        }
                    }
                }

                // Offline-First Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Offline-First Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Blocks external network calls when local models or scripts are used", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = offlineMode,
                        onCheckedChange = {
                            offlineMode = it
                            securePrefs.saveBooleanSetting("setting_offline_mode", it)
                            Toast.makeText(context, if (it) "Offline-First enabled" else "Offline-First disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }

                // Telegram Sync
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppField)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Telegram Background Sync", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppWhite)
                        Spacer(Modifier.height(2.dp))
                        Text("Enable automatic polling for Telegram Drive and shared memory", fontSize = 11.sp, color = AppMuted)
                    }
                    Switch(
                        checked = telegramSync,
                        onCheckedChange = {
                            telegramSync = it
                            securePrefs.saveBooleanSetting("setting_tg_bg_sync", it)
                            Toast.makeText(context, if (it) "Telegram sync enabled" else "Telegram sync disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppWhite,
                            checkedTrackColor = AppPrimary,
                            uncheckedThumbColor = AppMuted,
                            uncheckedTrackColor = AppDarkGray
                        )
                    )
                }
            }
        },
        confirmButton = {
            FilledAppButton(
                onClick = onDismiss,
                text = "Close",
                backgroundColor = AppPrimary
            )
        },
        containerColor = AppSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

