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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.painterResource
import ai.deepcode.android.R

@Composable
fun DashboardScreen(
    activeConnections: List<IntegrationEntity>,
    integrationsCount: Int,
    onTabSelect: (Int) -> Unit,
    repository: ai.deepcode.android.data.repository.DeepCodeRepository? = null,
    onShowTokenUsage: (() -> Unit)? = null,
    onSessionSelect: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    val sessions by (repository?.getAllSessions() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val messageCount by (repository?.getMessageCount() ?: kotlinx.coroutines.flow.flowOf(0))
        .collectAsStateWithLifecycle(initialValue = 0)
    val allTokenSessions by (repository?.tokenRepository?.observeAllSessions() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val lifetimeTotals by (repository?.tokenRepository?.observeLifetimeTotals() ?: kotlinx.coroutines.flow.flowOf(null))
        .collectAsStateWithLifecycle(initialValue = null)

    val profileName = repository?.securePrefs?.getSetting("profile_name", "Deep Patel") ?: "Deep Patel"

    LaunchedEffect(profileName) {
        ai.deepcode.android.util.DailyGreetingManager.initialize(context, profileName)
    }
    // Token backfill is IO-heavy: run once per repository instance, not on
    // every message/session count change (previously keyed on
    // profileName + sessions.size + messageCount, re-running sync on each
    // new message and retriggering recomposition).
    LaunchedEffect(repository) {
        repository?.syncAndBackfillTokenUsage()
    }

    val dailyGreeting by ai.deepcode.android.util.DailyGreetingManager.greetingState.collectAsStateWithLifecycle()

    // Show the real message count; fall back to 0 when there are none.
    // Previously fabricated an estimate (sessions.size * 3) that was
    // presented as an authoritative synced count.
    val messagesSynced = if (messageCount > 0) {
        if (messageCount >= 1000) "${messageCount / 1000}k+" else messageCount.toString()
    } else "0"

    val recentSessions = remember(sessions) { sessions.sortedByDescending { it.createdAt }.take(5) }
    val now = System.currentTimeMillis()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppScreenBg)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
    ) {
        // ── TOP BAR WITH DAILY GREETING & STATUS ───────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dailyGreeting.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
                            .clickable { Toast.makeText(context, "Notifications synced", Toast.LENGTH_SHORT).show() },
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

        // ── DAILY AI BRIEFING & GREETING CARD (UPDATES ONCE DAILY) ───────────────
        item {
            var refreshRotation by remember { mutableFloatStateOf(0f) }
            val animatedRotation by androidx.compose.animation.core.animateFloatAsState(
                targetValue = refreshRotation,
                animationSpec = androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "refreshGreetingRotation"
            )

            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(provideHaptic = true) {
                        refreshRotation += 360f
                        ai.deepcode.android.util.DailyGreetingManager.refreshDailyGreeting(context, profileName, forceRefresh = true)
                        Toast.makeText(context, "Refreshed daily AI insight", Toast.LENGTH_SHORT).show()
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✨", fontSize = 12.sp)
                            }
                            Text(
                                text = dailyGreeting.tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (dailyGreeting.isAiGenerated) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AI GENERATED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Greeting",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = animatedRotation }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = dailyGreeting.subtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // ── TOP CORE METRICS ───────────────────────────────────────────────────────
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        modifier = Modifier.weight(1f),
                        label = "Connections",
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
                        valueColor = Color(0xFF38BDF8),
                        icon = Icons.Default.History
                    )
                }
            }
        }

        // ── REAL DATA ANALYSIS & TOKEN INTELLIGENCE CARD ──────────────────────────
        item {
            val totalTokens = lifetimeTotals?.totalTokens ?: 0L
            val totalInput = lifetimeTotals?.totalInput ?: 0L
            val totalOutput = lifetimeTotals?.totalOutput ?: 0L
            val totalReasoning = lifetimeTotals?.totalReasoning ?: 0L
            val totalTurns = lifetimeTotals?.totalTurns ?: 0
            val costStr = lifetimeTotals?.formattedCost() ?: "$0.00"

            val inputPercent = if (totalTokens > 0) ((totalInput.toDouble() / totalTokens) * 100).toInt() else 60
            val outputPercent = if (totalTokens > 0) ((totalOutput.toDouble() / totalTokens) * 100).toInt() else 35
            // Only render a reasoning slice when reasoning tokens actually exist.
            // Truncating input/output percents leaves a residue that would
            // otherwise draw a phantom purple "Reason" slice with no legend entry.
            val reasoningPercent = if (totalReasoning > 0) (100 - inputPercent - outputPercent).coerceAtLeast(0) else 0

            AppCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onShowTokenUsage?.invoke() }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
                                Text("Σ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column {
                                Text(
                                    text = "Real-Time Token Analysis",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (totalTokens > 0) "${formatCompactNumber(totalTokens)} total tokens  ·  $costStr  ·  $totalTurns turns"
                                           else "Real-time tracker ready",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (totalTokens > 0) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Visual Distribution Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(inputPercent.coerceAtLeast(1).toFloat())
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(1.dp))
                            Box(
                                modifier = Modifier
                                    .weight(outputPercent.coerceAtLeast(1).toFloat())
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                            if (reasoningPercent > 0) {
                                Spacer(modifier = Modifier.width(1.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(reasoningPercent.toFloat())
                                        .fillMaxHeight()
                                        .background(AppIntegrationPurple)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Breakdown Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In: ${formatCompactNumber(totalInput)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Out: ${formatCompactNumber(totalOutput)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (totalReasoning > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AppIntegrationPurple))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reason: ${formatCompactNumber(totalReasoning)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
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
                            .bouncyClickable(provideHaptic = true) { onTabSelect(3) }
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
                            .bouncyClickable(provideHaptic = true) { onTabSelect(3) }
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
                            
                            val isGptSession = session.title.contains("ChatGPT", ignoreCase = true)
                            val icon = when {
                                session.title.contains("Telegram", ignoreCase = true) -> Icons.Default.Send
                                session.title.contains("Crypto", ignoreCase = true) -> Icons.Default.MonetizationOn
                                else -> Icons.Default.Chat
                            }
                            val iconColor = when {
                                isGptSession -> Color(0xFF10A37F)
                                session.title.contains("Telegram", ignoreCase = true) -> MaterialTheme.colorScheme.tertiary
                                session.title.contains("Crypto", ignoreCase = true) -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .bouncyClickable(provideHaptic = true) {
                                        onSessionSelect?.invoke(session.id)
                                        onTabSelect(1)
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                        if (isGptSession) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_chatgpt),
                                                contentDescription = "ChatGPT",
                                                tint = Color(0xFF10A37F),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = iconColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
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
                                            text = (if (isGptSession) "ChatGPT • " else "Local • ") + formatTimeAgo(now, session.createdAt),
                                            color = if (isGptSession) Color(0xFF10A37F).copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
