package ai.deepcode.android.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.ui.theme.*
import com.jarves.mh.model.AgentKind
import com.jarves.mh.model.DevStack
import com.jarves.mh.runtime.RuntimeInstaller
import com.jarves.mh.runtime.RuntimeInstallProgress
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
