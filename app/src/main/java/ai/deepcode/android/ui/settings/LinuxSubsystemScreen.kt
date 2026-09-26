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
    var dshProviderKind by remember { mutableStateOf(initialProfile.kind) }
    var dshBaseUrl by remember { mutableStateOf(initialProfile.baseUrl) }
    var dshModel by remember { mutableStateOf(initialProfile.model) }
    var dshApiKey by remember { mutableStateOf(vault.getSecret(initialProfile.kind.name) ?: securePrefs.getApiKey(initialProfile.kind.name.lowercase())) }
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
                title = {
                    Text("Linux Subsystem & Runtimes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AppWhite)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        },
        containerColor = AppScreenBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Workflow Selection Card
            item {
                Text("Agent Execution Workflow", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Select how prompts and autonomous agents are executed:", color = AppMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))

                        val modes = listOf(
                            Triple(WORKFLOW_DIRECT, "Direct In-App Engine", "Native Android streaming agent loop"),
                            Triple(WORKFLOW_DEEPSEEK_HARNESS, "DeepSeek Harness (DSH)", "Autonomous CLI in PRoot with multi-provider routing"),
                            Triple(WORKFLOW_CLAUDE_CODE, "Claude Code CLI", "Anthropic autonomous terminal agent in PRoot"),
                            Triple(WORKFLOW_ANTIGRAVITY, "Google Antigravity CLI", "Official Google agent in PRoot with reasoning effort")
                        )

                        modes.forEach { (modeId, modeTitle, modeDesc) ->
                            val isSelected = workflowMode == modeId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        workflowMode = modeId
                                        securePrefs.setWorkflowMode(modeId)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(modeTitle, fontWeight = FontWeight.SemiBold, color = AppWhite, fontSize = 13.sp)
                                    Text(modeDesc, color = AppMuted, fontSize = 11.sp)
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        workflowMode = modeId
                                        securePrefs.setWorkflowMode(modeId)
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AppPrimary)
                                )
                            }
                            if (modeId != modes.last().first) {
                                HorizontalDivider(color = AppBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            // DeepSeek Harness Provider Configuration Card
            item {
                Text("DeepSeek Harness (DSH) Provider", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Configure the API provider and model used by DeepSeek Harness:", color = AppMuted, fontSize = 12.sp)

                        val dshProviders = listOf(
                            ProviderKind.DEEPSEEK,
                            ProviderKind.LLM_ROUTER,
                            ProviderKind.OPENCODE_ZEN,
                            ProviderKind.NVIDIA_NIM,
                            ProviderKind.KIMI,
                            ProviderKind.ANTHROPIC,
                            ProviderKind.CUSTOM
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dshProviders) { p ->
                                val isSelected = p == dshProviderKind
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        dshProviderKind = p
                                        dshBaseUrl = p.defaultBaseUrl
                                        dshModel = p.defaultModel
                                    },
                                    label = { Text(p.title, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = AppPrimary,
                                        containerColor = AppSurfaceVariant,
                                        labelColor = AppMuted
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if (isSelected) AppPrimary else AppBorder
                                    )
                                )
                            }
                        }

                        OutlinedTextField(
                            value = dshBaseUrl,
                            onValueChange = { dshBaseUrl = it },
                            label = { Text("Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
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
                            label = { Text("API Key (${dshProviderKind.title})") },
                            placeholder = { Text("sk-...") },
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
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurfaceVariant,
                                unfocusedContainerColor = AppSurfaceVariant,
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite
                            )
                        )

                        Button(
                            onClick = {
                                val prof = ProviderProfile(
                                    kind = dshProviderKind,
                                    baseUrl = dshBaseUrl,
                                    model = dshModel,
                                    hasSecret = dshApiKey.isNotBlank()
                                )
                                appPrefs.saveProvider(prof, AgentKind.DEEPSEEK_HARNESS)
                                if (dshApiKey.isNotBlank()) {
                                    vault.putSecret(dshProviderKind.name, dshApiKey.trim())
                                    securePrefs.saveApiKey(dshProviderKind.name.lowercase(), dshApiKey.trim())
                                    securePrefs.saveApiKey("deepseek", dshApiKey.trim())
                                }
                                Toast.makeText(context, "Saved DeepSeek Harness provider settings", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save DSH Provider Settings")
                        }
                    }
                }
            }

            // Environment Status Banner
            item {
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isInstalled) Color(0xFF22C55E).copy(alpha = 0.5f) else AppBorder)
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
                        Button(
                            onClick = runSetup,
                            enabled = !isInstalling,
                            colors = ButtonDefaults.buttonColors(containerColor = AppPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isInstalled) Icons.Default.Refresh else Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isInstalled) "Repair / Update Runtime" else "Bootstrap Linux Subsystem")
                        }
                    }
                }
            }

            // Installed Coding Agents Card
            item {
                Text("Autonomous Coding Agents", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        AgentRow(
                            name = "Google Antigravity CLI",
                            version = installedAgents[AgentKind.ANTIGRAVITY] ?: (if (isInstalled) "Ready" else "Bundled in APK"),
                            status = if (isInstalled) "Active" else "Bundled",
                            icon = Icons.Default.AutoAwesome
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder)
                        AgentRow(
                            name = "Claude Code CLI",
                            version = installedAgents[AgentKind.CLAUDE_CODE] ?: "On-demand download",
                            status = if (installedAgents.containsKey(AgentKind.CLAUDE_CODE)) "Installed" else "Available",
                            icon = Icons.Default.Code
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder)
                        AgentRow(
                            name = "DeepSeek Harness (DSH)",
                            version = installedAgents[AgentKind.DEEPSEEK_HARNESS] ?: "On-demand download",
                            status = if (installedAgents.containsKey(AgentKind.DEEPSEEK_HARNESS)) "Installed" else "Available",
                            icon = Icons.Default.Terminal
                        )
                    }
                }
            }

            // Toolchains Card
            item {
                Text("Development Toolchains", fontWeight = FontWeight.Bold, color = AppWhite, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AppSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        ToolchainRow(name = "Node.js & npm", desc = "Core runtime JavaScript/TypeScript engine", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder)
                        ToolchainRow(name = "Git Version Control", desc = "Repository cloning, commits & diffs", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder)
                        ToolchainRow(name = "Python 3 & pip", desc = "Python scripting and machine learning", available = isInstalled)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = AppBorder)
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AppSurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(status, color = AppWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
