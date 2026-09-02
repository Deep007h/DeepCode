package ai.deepcode.android.ui.dashboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.ui.components.AppCard
import ai.deepcode.android.ui.components.IntegrationIcon
import ai.deepcode.android.ui.components.StatusBadge
import ai.deepcode.android.ui.components.AppToggle
import ai.deepcode.android.ui.components.OutlinedAppButton
import ai.deepcode.android.ui.theme.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import ai.deepcode.android.ui.connections.IntegrationEntity
import ai.deepcode.android.domain.model.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    activeConnections: List<IntegrationEntity>,
    integrationsCount: Int,
    onTabSelect: (Int) -> Unit,
    repository: ai.deepcode.android.data.repository.DeepCodeRepository? = null,
    onShowTokenUsage: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val sessions by (repository?.getAllSessions() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val messageCount by (repository?.getMessageCount() ?: kotlinx.coroutines.flow.flowOf(0))
        .collectAsStateWithLifecycle(initialValue = 0)

    val messagesSynced = if (messageCount > 0) {
        if (messageCount >= 1000) "${messageCount / 1000}k+" else messageCount.toString()
    } else if (sessions.isNotEmpty()) {
        "${sessions.size * 3}+"
    } else "0"

    val recentSessions = remember(sessions) { sessions.sortedByDescending { it.createdAt }.take(4) }
    val now = System.currentTimeMillis()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val profileName = repository?.securePrefs?.getSetting("profile_name", "Deep Patel") ?: "Deep Patel"
                    val firstName = profileName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: "User"

                    val greeting = remember {
                        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                        when {
                            hour < 12 -> "Good morning"
                            hour < 17 -> "Good afternoon"
                            else -> "Good evening"
                        }
                    }

                    Text(
                        text = "$greeting, $firstName",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your AI workspace is ready",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                var vpnMenuExpanded by remember { mutableStateOf(false) }
                val vpnEnabled by ai.deepcode.android.util.VpnManager.vpnEnabled.collectAsStateWithLifecycle()
                val activeServer by ai.deepcode.android.util.VpnManager.activeServer.collectAsStateWithLifecycle()
                val servers by ai.deepcode.android.util.VpnManager.servers.collectAsStateWithLifecycle()
                val vpnMode by ai.deepcode.android.util.VpnManager.vpnMode.collectAsStateWithLifecycle()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { vpnMenuExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (vpnEnabled) Icons.Default.VpnLock else Icons.Default.VpnKey,
                                contentDescription = "VPN Status",
                                tint = if (vpnEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = vpnMenuExpanded,
                            onDismissRequest = { vpnMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("VPN Connection", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (vpnEnabled) "Enabled" else "Disabled",
                                            color = if (vpnEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                        AppToggle(
                                            checked = vpnEnabled,
                                            onCheckedChange = { 
                                                ai.deepcode.android.util.VpnManager.setVpnEnabled(it)
                                            }
                                        )
                                    }
                                },
                                onClick = { 
                                    ai.deepcode.android.util.VpnManager.setVpnEnabled(!vpnEnabled)
                                }
                            )
                            
                            if (vpnMode == ai.deepcode.android.util.VpnMode.AUTO) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text("Select Server:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) },
                                    onClick = {},
                                    enabled = false
                                )
                                servers.take(6).forEach { server ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${server.country} (${server.ip})",
                                                    color = if (activeServer?.ip == server.ip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.sp
                                                )
                                                if (server.pingMs > 0) {
                                                    Text(
                                                        text = "${server.pingMs}ms",
                                                        color = if (server.pingMs < 80) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            ai.deepcode.android.util.VpnManager.selectServer(server)
                                            vpnMenuExpanded = false
                                        }
                                    )
                                }
                            } else {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
                                DropdownMenuItem(
                                    text = { Text("Custom Proxy Active", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) },
                                    onClick = { vpnMenuExpanded = false }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { Toast.makeText(context, "Notifications", Toast.LENGTH_SHORT).show() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        modifier = Modifier.weight(1f),
                        label = "Active Connections",
                        value = "${activeConnections.size}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        icon = Icons.Default.Link
                    )
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                    StatItem(
                        modifier = Modifier.weight(1f),
                        label = "Integrations",
                        value = "$integrationsCount",
                        valueColor = AppIntegrationPurple,
                        icon = Icons.Default.Extension
                    )
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                    StatItem(
                        modifier = Modifier.weight(1f),
                        label = "Messages",
                        value = messagesSynced,
                        valueColor = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Default.Chat
                    )
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                    StatItem(
                        modifier = Modifier.weight(1f),
                        label = "Sessions",
                        value = "${sessions.size}",
                        valueColor = AppDestructive,
                        icon = Icons.Default.Warning
                    )
                }
            }
        }

        item {
            val lifetimeTotals by (repository?.tokenRepository?.observeLifetimeTotals()
                ?: kotlinx.coroutines.flow.flowOf(null))
                .collectAsStateWithLifecycle(initialValue = null)

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onShowTokenUsage?.invoke() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Σ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text("Token Usage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (lifetimeTotals != null && lifetimeTotals!!.totalTokens > 0) {
                                Text(
                                    "${formatCompactNumber(lifetimeTotals!!.totalTokens)} tokens  ·  ${lifetimeTotals!!.formattedCost()}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Text("No data yet — starts tracking after first AI response", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Today's Activity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { }
                ) {
                    Text(
                        text = "View all",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val primaryColor = MaterialTheme.colorScheme.primary
            val activities = remember(activeConnections, recentSessions, tertiaryColor, primaryColor) {
                val list = mutableListOf<Triple<ImageVector, Color, String>>()
                activeConnections.take(2).forEach { conn ->
                    val icon = if (conn.displayName.contains("Telegram", ignoreCase = true)) Icons.Default.Send else Icons.Default.Link
                    list.add(Triple(icon, tertiaryColor, "${conn.displayName} connected"))
                }
                recentSessions.take(2).forEach { session ->
                    list.add(Triple(Icons.Default.Chat, primaryColor, "Session: ${session.title}"))
                }
                list.add(Triple(Icons.Default.Bolt, AppIntegrationPurple, "Automation executed"))
                list
            }

            AppCard {
                if (activities.isEmpty()) {
                    Text(
                        text = "No recent activity yet. Start a chat or connect a service.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        activities.forEachIndexed { index, act ->
                            TimelineActivityItem(
                                icon = act.first,
                                iconBg = act.second.copy(alpha = 0.12f),
                                title = act.third,
                                time = if (index == 0) "2m ago" else if (index == 1) "2m ago" else "15m ago",
                                isLast = index == activities.size - 1
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Quick Connect",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(quickConnectApps, key = { it.id }) { app ->
                    Column(
                        modifier = Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { Toast.makeText(context, "Connecting ${app.name}...", Toast.LENGTH_SHORT).show() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IntegrationIcon(
                            appId = app.id,
                            appName = app.name,
                            size = 36.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                
                item {
                    Column(
                        modifier = Modifier
                            .width(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .clickable { Toast.makeText(context, "More integrations...", Toast.LENGTH_SHORT).show() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "More",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Recent Sessions",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTabSelect(1) }
                ) {
                    Text(
                        text = "View all",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                if (recentSessions.isEmpty()) {
                    Text(
                        text = "No recent sessions yet. Start a new chat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentSessions.take(3).forEachIndexed { index, session ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                            }
                            
                            val icon = when {
                                session.title.contains("Telegram", ignoreCase = true) -> Icons.Default.Send
                                session.title.contains("Crypto", ignoreCase = true) -> Icons.Default.MonetizationOn
                                else -> Icons.Default.Chat
                            }
                            val iconColor = when {
                                session.title.contains("Telegram", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                session.title.contains("Crypto", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTabSelect(1) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(iconColor.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                    Text(
                        text = session.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Local • " + formatTimeAgo(now, session.createdAt),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color,
    icon: ImageVector
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                valueColor.copy(alpha = 0.2f),
                                valueColor.copy(alpha = 0.08f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun TimelineActivityItem(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    time: String,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = time,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

private data class QuickConnectApp(
    val id: String,
    val name: String
)

private fun formatTimeAgo(now: Long, then: Long): String {
    if (then <= 0) return "never"
    val diff = now - then
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(then))
    }
}

private fun formatCompactNumber(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K"
    else -> "$count"
}

private val quickConnectApps = listOf(
    QuickConnectApp("telegram", "Telegram"),
    QuickConnectApp("whatsapp", "WhatsApp"),
    QuickConnectApp("airtable", "Airtable"),
    QuickConnectApp("github", "GitHub"),
    QuickConnectApp("slack", "Slack"),
    QuickConnectApp("notion", "Notion")
)
