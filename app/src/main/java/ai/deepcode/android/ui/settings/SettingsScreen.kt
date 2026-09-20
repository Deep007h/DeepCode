package ai.deepcode.android.ui.settings

import android.graphics.Bitmap
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val securePrefs = repository.securePrefs

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTurnsDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showRefreshRateDialog by remember { mutableStateOf(false) }

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
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Settings Header
        item {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppWhite,
                modifier = Modifier.padding(vertical = 8.dp)
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

        // Section: Appearance (Separate dedicated page tile)
        item {
            SettingsSectionHeader(icon = Icons.Default.Palette, title = "Appearance")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsNavRow(
                    icon = Icons.Default.Palette,
                    title = "Themes & Wallpapers",
                    subtitle = "Accent colors, dark mode, and chat wallpaper choice",
                    onClick = onNavigateToThemesAndWallpapers
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsNavRow(
                    icon = Icons.Default.Speed,
                    title = "Display & Refresh Rate",
                    subtitle = when (refreshRateMode) {
                        "144" -> "Locked 144 Hz (Ultra High)"
                        "120" -> "Locked 120 Hz (High)"
                        "90" -> "Locked 90 Hz (Smooth)"
                        "60" -> "Standard 60 Hz"
                        else -> "Variable 60 - 144 Hz (Adaptive)"
                    },
                    onClick = { showRefreshRateDialog = true }
                )
            }
        }

        // Card group 1: Manage Agents, Chat History Memory Limit, Manage Templates
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                // Manage Agents Row
                SettingsNavRow(
                    icon = Icons.Default.Group,
                    title = "Manage Agents",
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
                        Text(
                            text = "Chat History Memory Limit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppWhite
                        )
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
                            color = AppMuted
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

                // Manage Templates Row
                SettingsNavRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "Manage Templates",
                    onClick = onManageTemplates
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                // Custom Persona Row
                SettingsNavRow(
                    icon = Icons.Default.Face,
                    title = "Custom Persona",
                    onClick = onManagePersonas
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)

                SettingsSubscreenRow(
                    icon = Icons.Default.Extension,
                    title = "Plugins",
                    subtitle = "${ai.deepcode.android.plugin.PluginRegistry.getEnabledCount()} of ${ai.deepcode.android.plugin.PluginRegistry.getTotalCount()} plugins enabled",
                    onClick = onNavigateToPlugins
                )
            }
        }

        // Section: Security
        item {
            SettingsSectionHeader(icon = Icons.Default.Lock, title = "Security")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsSubscreenRow(
                    icon = Icons.Default.Shield,
                    title = "Security Settings",
                    subtitle = "Manage authentication, API keys, and security preferences.",
                    onClick = { Toast.makeText(context, "Security settings sub-page", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Link,
                    title = "Connection Settings",
                    subtitle = "Configure sync, bridge behavior, and external connections.",
                    onClick = { Toast.makeText(context, "Connection settings sub-page", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Public,
                    title = "VPN Settings",
                    subtitle = "Manage VPN tunnel, mode, and connection status.",
                    onClick = onNavigateToVpn
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Key,
                    title = "API Keys",
                    subtitle = "Manage your API keys and external service credentials.",
                    onClick = onNavigateToApiKeys
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Cloud,
                    title = "Cloudflare Settings",
                    subtitle = "Configure Cloudflare for image generation.",
                    onClick = onNavigateToCloudflare
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.Psychology,
                    title = "Cross-Chat Memory",
                    subtitle = "Manage shared long-term memory between in-app and Telegram chats.",
                    onClick = onNavigateToMemory
                )
                HorizontalDivider(color = AppDivider, thickness = 1.dp)
                SettingsSubscreenRow(
                    icon = Icons.Default.SettingsSuggest,
                    title = "Google API Settings",
                    subtitle = "Manage your Google API credentials and services.",
                    onClick = { Toast.makeText(context, "Google API credentials settings sub-page", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        // Section: About
        item {
            SettingsSectionHeader(icon = Icons.Default.Info, title = "About")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .depthCard(shape = RoundedCornerShape(16.dp), elevation = 2.dp, isDark = isDarkThemeActive)
            ) {
                SettingsSubscreenRow(
                    icon = Icons.Default.Info,
                    title = "About",
                    subtitle = "App information, version, policies, and legal.",
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
