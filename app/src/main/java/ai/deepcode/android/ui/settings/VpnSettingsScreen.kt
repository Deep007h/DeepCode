package ai.deepcode.android.ui.settings
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.ui.components.*
import ai.deepcode.android.ui.theme.*
import ai.deepcode.android.util.VpnManager
import ai.deepcode.android.util.VpnMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnSettingsScreen(
    repository: DeepCodeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vpnEnabled by VpnManager.vpnEnabled.collectAsStateWithLifecycle()
    val vpnStatus by VpnManager.vpnStatus.collectAsStateWithLifecycle()
    val vpnMode by VpnManager.vpnMode.collectAsStateWithLifecycle()
    val activeServer by VpnManager.activeServer.collectAsStateWithLifecycle()
    val servers by VpnManager.servers.collectAsStateWithLifecycle()
    val isRefreshing by VpnManager.isRefreshing.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnLock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("VPN Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRow(
                            icon = Icons.Default.VpnLock,
                            label = "Enable VPN Tunnel",
                            control = {
                                AppToggle(
                                    checked = vpnEnabled,
                                    onCheckedChange = { VpnManager.setVpnEnabled(it) }
                                )
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Status", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Text(
                                text = vpnStatus,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vpnEnabled && !vpnStatus.contains("Failed") && !vpnStatus.contains("Error"))
                                    MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("VPN Mode", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedAppButton(
                                    onClick = { VpnManager.setVpnMode(VpnMode.AUTO) },
                                    text = "Auto-Proxy",
                                    color = if (vpnMode == VpnMode.AUTO) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.heightIn(min = 28.dp)
                                )
                                OutlinedAppButton(
                                    onClick = { VpnManager.setVpnMode(VpnMode.CUSTOM) },
                                    text = "ProtonVPN/Custom",
                                    color = if (vpnMode == VpnMode.CUSTOM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.heightIn(min = 28.dp)
                                )
                            }
                        }

                        if (vpnMode == VpnMode.CUSTOM) {
                            CustomProxySection()
                        } else {
                            ServerSelectionSection(
                                servers = servers,
                                activeServer = activeServer,
                                isRefreshing = isRefreshing
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomProxySection() {
    var showCustomProxyDialog by remember { mutableStateOf(false) }
    val customHost by VpnManager.customHost.collectAsStateWithLifecycle()
    val customPort by VpnManager.customPort.collectAsStateWithLifecycle()
    val customType by VpnManager.customType.collectAsStateWithLifecycle()
    val customUser by VpnManager.customUser.collectAsStateWithLifecycle()
    val customPass by VpnManager.customPass.collectAsStateWithLifecycle()

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

    SettingsRow(
        icon = Icons.Default.Edit,
        label = "Configure Proxy Connection",
        control = {
            Text(
                text = if (customHost.isNotEmpty()) "$customHost:$customPort" else "Not Set",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { showCustomProxyDialog = true }
            )
        },
        onClick = { showCustomProxyDialog = true }
    )

    if (showCustomProxyDialog) {
        var hostInput by remember { mutableStateOf(customHost) }
        var portInput by remember { mutableStateOf(customPort.toString()) }
        var userInput by remember { mutableStateOf(customUser) }
        var passInput by remember { mutableStateOf(customPass) }
        var typeInput by remember { mutableStateOf(customType) }

        AlertDialog(
            onDismissRequest = { showCustomProxyDialog = false },
            title = { Text("Proxy Settings (e.g. ProtonVPN)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your proxy details. This is fully compatible with ProtonVPN's local proxy port or any SOCKS5/HTTP tunnel.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Proxy Server IP/Hostname") },
                        placeholder = { Text("e.g. 127.0.0.1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Port") },
                            placeholder = { Text("1080") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        OutlinedTextField(
                            value = typeInput,
                            onValueChange = { typeInput = it },
                            label = { Text("Type (SOCKS5/HTTP)") },
                            placeholder = { Text("SOCKS5") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("Username (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text("Password (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedPort = portInput.toIntOrNull() ?: 1080
                        val normalizedType = if (typeInput.uppercase().contains("HTTP")) "HTTP" else "SOCKS5"
                        VpnManager.saveCustomProxy(
                            hostInput.trim(),
                            parsedPort,
                            normalizedType,
                            userInput.trim(),
                            passInput.trim()
                        )
                        showCustomProxyDialog = false
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomProxyDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ServerSelectionSection(
    servers: List<ai.deepcode.android.util.VpnServer>,
    activeServer: ai.deepcode.android.util.VpnServer?,
    isRefreshing: Boolean
) {
    var serversExpanded by remember { mutableStateOf(false) }

    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { serversExpanded = !serversExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = activeServer?.let { "${it.country} (${it.ip})" } ?: "Select Server",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeServer != null && activeServer.pingMs > 0) {
                    Text(
                        text = "${activeServer.pingMs}ms",
                        color = if (activeServer.pingMs < 100) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (serversExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (serversExpanded) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                servers.forEach { server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeServer?.ip == server.ip) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .border(
                                width = 0.5.dp,
                                color = if (activeServer?.ip == server.ip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                VpnManager.selectServer(server)
                                serversExpanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = server.countryCode,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = server.country,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${server.ip})",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${server.pingMs}ms",
                            fontSize = 11.sp,
                            color = if (server.pingMs < 80) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedAppButton(
                    onClick = { VpnManager.refreshServers() },
                    text = if (isRefreshing) "Refreshing..." else "Refresh Speed Test",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
