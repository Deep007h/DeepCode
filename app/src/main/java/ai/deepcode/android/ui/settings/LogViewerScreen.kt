package ai.deepcode.android.ui.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.deepcode.android.data.repository.DeepCodeRepository
import ai.deepcode.android.domain.model.ChatSession
import ai.deepcode.android.domain.model.Message
import androidx.compose.foundation.BorderStroke

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.ui.components.gridBackground
import ai.deepcode.android.util.AppLogger
import ai.deepcode.android.util.LogEntry
import ai.deepcode.android.util.LogLevel
import ai.deepcode.android.util.LogcatReader
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.*
import java.io.File

// ── colour palette ────────────────────────────────────────────────────────────
private val LevelColors = mapOf(
    LogLevel.FATAL to Color(0xFF7F1D1D),
    LogLevel.ERROR to Color(0xFFEF4444),
    LogLevel.WARN  to Color(0xFFF59E0B),
    LogLevel.INFO  to Color(0xFF3B82F6),
    LogLevel.DEBUG to Color(0xFF6B7280)
)
private val LevelBgColors = mapOf(
    LogLevel.FATAL to Color(0xFF7F1D1D).copy(alpha = 0.12f),
    LogLevel.ERROR to Color(0xFFEF4444).copy(alpha = 0.06f),
    LogLevel.WARN  to Color(0xFFF59E0B).copy(alpha = 0.04f),
    LogLevel.INFO  to Color.Transparent,
    LogLevel.DEBUG to Color.Transparent
)

private val DarkBg      = Color(0xFF0A0A0A)
private val SurfaceBg   = Color(0xFF111111)
private val CardBg      = Color(0xFF1A1A1A)
private val CardBg2     = Color(0xFF161616)
private val BorderColor = Color(0xFF252525)
private val TextPrimary = Color(0xFFE5E5E5)
private val TextSecondary = Color(0xFF888888)
private val AccentBlue get() = AppPrimary
private val AccentGreen = Color(0xFF22C55E)
private val AccentPurple = Color(0xFFA855F7)
private val AccentOrange = Color(0xFFF97316)

// ── tab definitions ───────────────────────────────────────────────────────────
private enum class LogTab(val label: String, val icon: @Composable () -> Unit, val categoryFilter: String?) {
    ALL      ("ALL",     { Icon(Icons.Default.List, null, modifier = Modifier.size(13.dp)) }, null),
    LOGCAT   ("LOGCAT",  { Icon(Icons.Default.Terminal, null, modifier = Modifier.size(13.dp)) }, "logcat"),
    NETWORK  ("NET",     { Icon(Icons.Default.Cloud, null, modifier = Modifier.size(13.dp)) }, "network"),
    SHELL    ("SHELL",   { Icon(Icons.Default.Code, null, modifier = Modifier.size(13.dp)) }, "shell"),
    ACTIONS  ("ACTIONS", { Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(13.dp)) }, "user_action"),
    ADB      ("ADB",     { Icon(Icons.Default.Cable, null, modifier = Modifier.size(13.dp)) }, "adb_bridge"),
    HISTORY  ("HISTORY", { Icon(Icons.Default.History, null, modifier = Modifier.size(13.dp)) }, "history"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val logEntries by AppLogger.logFlow.collectAsStateWithLifecycle()
    val stats by AppLogger.statsFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // ── state ─────────────────────────────────────────────────────────────────
    var selectedTab by remember { mutableStateOf(LogTab.ALL) }
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll  by remember { mutableStateOf(true) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showShareMenu  by remember { mutableStateOf(false) }
    var showTimeRangeMenu by remember { mutableStateOf(false) }
    var timeRangeHours by remember { mutableStateOf<Int?>(null) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var bookmarkedIds   by remember { mutableStateOf(setOf<Long>()) }
    var adbCommandInput by remember { mutableStateOf("") }
    var showAdbHelp by remember { mutableStateOf(false) }
    var logcatRunning by remember { mutableStateOf(LogcatReader.isRunning) }

    var filteredEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }

    // ── database session loading ──────────────────────────────────────────────
    val repository = remember { DeepCodeRepository.getInstance(context) }
    val sessions by repository.getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    // ── derived filters ───────────────────────────────────────────────────────
    LaunchedEffect(logEntries, selectedTab, filterLevel, searchQuery, timeRangeHours) {
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val tabCategory = selectedTab.categoryFilter
            val result = logEntries.filter { entry ->
                val matchesTab      = tabCategory == null || entry.category == tabCategory
                val matchesLevel    = filterLevel == null || entry.level == filterLevel
                val matchesSearch   = searchQuery.isEmpty() ||
                        entry.message.contains(searchQuery, ignoreCase = true) ||
                        entry.tag.contains(searchQuery, ignoreCase = true) ||
                        (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true)
                val rangeHours      = timeRangeHours
                val matchesTime     = rangeHours == null || (now - entry.timestamp) <= rangeHours * 3600_000L
                matchesTab && matchesLevel && matchesSearch && matchesTime
            }
            filteredEntries = result
        }
    }

    LaunchedEffect(filteredEntries.size, autoScroll) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    // ── ADB console entries (filter to adb_bridge category) ──────────────────
    val adbEntries = remember(logEntries) {
        logEntries.filter { it.category == "adb_bridge" }.takeLast(50)
    }

    // ── root layout ───────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .gridBackground(gridColor = Color.White.copy(alpha = 0.015f))
    ) {

        // ── top bar ───────────────────────────────────────────────────────────
        Surface(color = SurfaceBg, modifier = Modifier.fillMaxWidth()) {
            Column {
                // title row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "DeepCode Debug Console",
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace, color = TextPrimary
                                )
                                // Logcat live dot
                                val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
                                LaunchedEffect(logcatRunning) {
                                    if (logcatRunning) {
                                        while (true) {
                                            pulse.animateTo(0.3f, androidx.compose.animation.core.tween(600))
                                            pulse.animateTo(1f, androidx.compose.animation.core.tween(600))
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (logcatRunning) AccentGreen.copy(alpha = pulse.value)
                                            else TextSecondary.copy(alpha = 0.4f)
                                        )
                                )
                                Text(
                                    if (logcatRunning) "LIVE" else "PAUSED",
                                    fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                                    color = if (logcatRunning) AccentGreen else TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "${stats.totalEntries} entries · ${stats.errorCount} err · ${stats.warnCount} warn · ${stats.fileSizeKb}KB",
                                fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary
                            )
                        }
                    }

                    // action icons
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Logcat toggle
                        IconButton(
                            onClick = {
                                if (logcatRunning) { AppLogger.stopLogcat(); logcatRunning = false }
                                else { AppLogger.startLogcat(); logcatRunning = true }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (logcatRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Logcat toggle",
                                tint = if (logcatRunning) AccentGreen else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { autoScroll = !autoScroll }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                if (autoScroll) Icons.Default.KeyboardArrowDown else Icons.Default.Pause,
                                contentDescription = "Auto-scroll",
                                tint = if (autoScroll) AccentBlue else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { showShareMenu = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Share, "Share", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { AppLogger.clearLogs(); Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Clear", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // ── tab bar ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LogTab.entries.forEach { tab ->
                        val active = selectedTab == tab
                        val tabCount = when (tab) {
                            LogTab.ALL     -> stats.totalEntries
                            LogTab.LOGCAT  -> logEntries.count { it.category == "logcat" }
                            LogTab.NETWORK -> logEntries.count { it.category == "network" }
                            LogTab.SHELL   -> logEntries.count { it.category == "shell" }
                            LogTab.ACTIONS -> logEntries.count { it.category == "user_action" }
                            LogTab.ADB     -> logEntries.count { it.category == "adb_bridge" }
                            LogTab.HISTORY -> sessions.size
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) AccentBlue.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (active) AccentBlue.copy(alpha = 0.5f) else BorderColor, RoundedCornerShape(6.dp))
                                .clickable { selectedTab = tab; expandedEntryId = null }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompositionLocalProvider(LocalContentColor provides if (active) AccentBlue else TextSecondary) {
                                    tab.icon()
                                }
                                Text(
                                    tab.label, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                                    color = if (active) AccentBlue else TextSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                                if (tabCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (active) AccentBlue.copy(alpha = 0.3f) else BorderColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "$tabCount", fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                                            color = if (active) AccentBlue else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedTab != LogTab.HISTORY) {
                    // ── filter bar ────────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Search field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBg)
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Search logs…", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(AccentBlue),
                                modifier = Modifier.fillMaxSize()
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" },
                                    modifier = Modifier.align(Alignment.CenterEnd).size(18.dp)) {
                                    Icon(Icons.Default.Close, "Clear", tint = TextSecondary, modifier = Modifier.size(11.dp))
                                }
                            }
                        }

                        // Level filter chip
                        FilterChip(
                            selected = filterLevel != null,
                            onClick = { showFilterMenu = true },
                            label = { Text(filterLevel?.name ?: "ALL", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LevelColors[filterLevel]?.copy(alpha = 0.15f) ?: AccentBlue.copy(alpha = 0.15f),
                                selectedLabelColor = LevelColors[filterLevel] ?: AccentBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = BorderColor, selectedBorderColor = LevelColors[filterLevel] ?: AccentBlue,
                                enabled = true, selected = filterLevel != null
                            ),
                            modifier = Modifier.height(26.dp)
                        )

                        // Time range chip
                        FilterChip(
                            selected = timeRangeHours != null,
                            onClick = { showTimeRangeMenu = true },
                            label = {
                                Text(
                                    when (timeRangeHours) { null -> "ALL"; 1 -> "1H"; 6 -> "6H"; 24 -> "24H"; 168 -> "7D"; else -> "${timeRangeHours}H" },
                                    fontSize = 8.sp, fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentBlue.copy(alpha = 0.15f),
                                selectedLabelColor = AccentBlue
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = BorderColor, selectedBorderColor = AccentBlue,
                                enabled = true, selected = timeRangeHours != null
                            ),
                            modifier = Modifier.height(26.dp)
                        )
                    }

                    // stats bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LevelStatBadge(LogLevel.FATAL.name, stats.fatalCount, LevelColors[LogLevel.FATAL] ?: Color.Gray, stats.totalEntries)
                        LevelStatBadge(LogLevel.ERROR.name, stats.errorCount, LevelColors[LogLevel.ERROR] ?: Color.Gray, stats.totalEntries)
                        LevelStatBadge(LogLevel.WARN.name,  stats.warnCount,  LevelColors[LogLevel.WARN]  ?: Color.Gray, stats.totalEntries)
                        LevelStatBadge(LogLevel.INFO.name,  stats.infoCount,  LevelColors[LogLevel.INFO]  ?: Color.Gray, stats.totalEntries)
                        LevelStatBadge(LogLevel.DEBUG.name, stats.debugCount, LevelColors[LogLevel.DEBUG] ?: Color.Gray, stats.totalEntries)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${filteredEntries.size} shown",
                            fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor)
            }
        }

        // ── dropdown menus ────────────────────────────────────────────────────
        DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false },
            modifier = Modifier.background(SurfaceBg).border(1.dp, BorderColor)) {
            DropdownMenuItem(text = { Text("ALL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary) },
                onClick = { filterLevel = null; showFilterMenu = false })
            LogLevel.entries.reversed().forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.name, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = LevelColors[level] ?: TextPrimary) },
                    onClick = { filterLevel = level; showFilterMenu = false },
                    leadingIcon = { Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(LevelColors[level] ?: Color.Gray)) }
                )
            }
        }

        DropdownMenu(expanded = showTimeRangeMenu, onDismissRequest = { showTimeRangeMenu = false },
            modifier = Modifier.background(SurfaceBg).border(1.dp, BorderColor)) {
            listOf("ALL TIME" to null, "Last hour" to 1, "Last 6 hours" to 6, "Last 24 hours" to 24, "Last 7 days" to 168).forEach { (label, h) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) },
                    onClick = { timeRangeHours = h; showTimeRangeMenu = false }
                )
            }
        }

        DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false },
            modifier = Modifier.background(SurfaceBg).border(1.dp, BorderColor)) {
            DropdownMenuItem(
                text = { Text("Copy all to clipboard", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) },
                onClick = {
                    val text = filteredEntries.joinToString("\n") { "[${it.formattedTime}] [${it.level.tag}] [${it.tag}] ${it.message}" }
                    copyToClipboard(context, text); showShareMenu = false
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Share as text", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) },
                onClick = {
                    val text = filteredEntries.joinToString("\n") { "[${it.formattedTime}] [${it.level.tag}] [${it.tag}] ${it.message}" }
                    shareText(context, text); showShareMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Export to file", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) },
                onClick = {
                    val path = AppLogger.exportLogs()
                    Toast.makeText(context, if (path != null) "Exported: $path" else "Export failed", Toast.LENGTH_LONG).show()
                    showShareMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Save, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenuItem(
                text = { Text("Export as JSON", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextPrimary) },
                onClick = {
                    copyToClipboard(context, AppLogger.exportLogsAsJson())
                    Toast.makeText(context, "JSON copied", Toast.LENGTH_SHORT).show()
                    showShareMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Code, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
            )
        }

        // ── main content: log list or history ─────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == LogTab.HISTORY) {
                SessionHistoryTab(
                    repository = repository,
                    selectedSessionId = selectedSessionId,
                    onSelectSession = { selectedSessionId = it },
                    scope = scope,
                    context = context
                )
            } else if (filteredEntries.isEmpty()) {
                EmptyState(selectedTab, searchQuery, filterLevel)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        LogEntryRow(
                            entry = entry,
                            isExpanded = expandedEntryId == entry.id,
                            searchQuery = searchQuery,
                            isBookmarked = entry.id in bookmarkedIds,
                            onToggleExpand = { expandedEntryId = if (expandedEntryId == entry.id) null else entry.id },
                            onBookmark = {
                                bookmarkedIds = if (entry.id in bookmarkedIds) bookmarkedIds - entry.id else bookmarkedIds + entry.id
                            },
                            onCopy   = { copyEntryToClipboard(context, entry) },
                            onShareEntry = { shareEntry(context, entry) }
                        )
                    }
                }
            }
        }

        // ── ADB console panel ─────────────────────────────────────────────────
        AdbConsolePanel(
            adbEntries = adbEntries,
            commandInput = adbCommandInput,
            onCommandChange = { adbCommandInput = it },
            showHelp = showAdbHelp,
            onToggleHelp = { showAdbHelp = !showAdbHelp },
            onSendCommand = { cmd ->
                // Simulate local inject — the real power comes via adb shell am broadcast
                AppLogger.logUserAction("ADB Console", "User typed: $cmd")
                adbCommandInput = ""
                Toast.makeText(context, "Use: adb shell am broadcast -a ai.deepcode.DEBUG_CMD --es cmd \"$cmd\" --es req_id \"r1\"",
                    Toast.LENGTH_LONG).show()
            }
        )
    }
}

// ── ADB Console Panel ─────────────────────────────────────────────────────────
@Composable
private fun AdbConsolePanel(
    adbEntries: List<LogEntry>,
    commandInput: String,
    onCommandChange: (String) -> Unit,
    showHelp: Boolean,
    onToggleHelp: () -> Unit,
    onSendCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D))
            .drawBehind {
                drawLine(Color(0xFF2A2A2A), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }
    ) {
        // Panel header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Cable, null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                Text("ADB Bridge", fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = AccentPurple, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(AccentGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text("READY", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
                }
            }
            IconButton(onClick = onToggleHelp, modifier = Modifier.size(22.dp)) {
                Icon(
                    if (showHelp) Icons.Default.ExpandMore else Icons.Default.HelpOutline,
                    "Help", tint = TextSecondary, modifier = Modifier.size(14.dp)
                )
            }
        }

        // Help panel (collapsed by default)
        AnimatedVisibility(visible = showHelp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF080808))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("── ADB COMMAND REFERENCE ──", fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace, color = AccentPurple, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))

                Text("▸ DEBUG", fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                    color = TextSecondary, fontWeight = FontWeight.Bold)
                val debugCmds = listOf(
                    "ping" to "Check bridge alive + uptime",
                    "run_shell [cmd]" to "Execute root shell command",
                    "read_logs [LEVEL]" to "Dump last 100 entries",
                    "get_stats" to "JSON stats (entries, errors, logcat)",
                    "inject_log L|T|M" to "Insert synthetic log entry",
                    "export_logs" to "Write logs to file",
                    "logcat_start/stop" to "Toggle live logcat capture"
                )
                debugCmds.forEach { (cmd, desc) ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("  $cmd", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace,
                            color = AccentOrange, modifier = Modifier.width(150.dp))
                        Text(desc, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))

                Text("▸ AI CHAT", fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                    color = TextSecondary, fontWeight = FontWeight.Bold)
                val aiCmds = listOf(
                    "ai_chat [msg]" to "Send to AI → stream to DEEPCODE_AI",
                    "ai_chat_in SID|||msg" to "Chat in a specific session",
                    "ai_get_model" to "Current provider/model/session",
                    "ai_set_model P|||M" to "Set provider + model",
                    "ai_list_models" to "All providers + models JSON"
                )
                aiCmds.forEach { (cmd, desc) ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("  $cmd", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace,
                            color = AccentGreen, modifier = Modifier.width(150.dp))
                        Text(desc, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))

                Text("▸ SESSIONS / HISTORY", fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                    color = TextSecondary, fontWeight = FontWeight.Bold)
                val sessionCmds = listOf(
                    "ai_list_sessions" to "List all sessions as JSON",
                    "ai_new_session [title]" to "Create + switch to new session",
                    "ai_switch_session SID" to "Switch ADB bridge to session",
                    "ai_read_session SID" to "All messages in session as JSON",
                    "ai_last_messages SID|||N" to "Last N messages (default 5)",
                    "ai_search_history [q]" to "Search across all sessions",
                    "ai_delete_session SID" to "Delete session + messages"
                )
                sessionCmds.forEach { (cmd, desc) ->
                    Row(modifier = Modifier.padding(vertical = 1.dp)) {
                        Text("  $cmd", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace,
                            color = AccentBlue, modifier = Modifier.width(150.dp))
                        Text(desc, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("── HOW TO USE ──", fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace, color = AccentPurple, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                AdbCodeLine("# Send message to AI")
                AdbCodeLine("adb shell am broadcast -a ai.deepcode.DEBUG_CMD \\")
                AdbCodeLine("  --es cmd \"ai_chat\" --es args \"Hello AI!\" --es req_id \"r1\"")
                Spacer(Modifier.height(3.dp))
                AdbCodeLine("adb logcat -s DEEPCODE_AI      # live token stream")
                AdbCodeLine("adb logcat -s DEEPCODE_AGENT -d # final response")
                Spacer(Modifier.height(3.dp))
                AdbCodeLine("# Read chat history")
                AdbCodeLine("adb shell am broadcast -a ai.deepcode.DEBUG_CMD \\")
                AdbCodeLine("  --es cmd \"ai_list_sessions\" --es req_id \"r2\"")
                AdbCodeLine("adb shell am broadcast -a ai.deepcode.DEBUG_CMD \\")
                AdbCodeLine("  --es cmd \"ai_read_session\" --es args \"SESSION_ID\" --es req_id \"r3\"")
            }
        }

        // Recent ADB entries (last 5)
        if (adbEntries.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                adbEntries.takeLast(5).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        val isCmd = entry.message.startsWith("←")
                        val isRsp = entry.message.startsWith("→")
                        Text(
                            if (isCmd) "IN " else if (isRsp) "OUT" else "   ",
                            fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                            color = if (isCmd) AccentOrange else if (isRsp) AccentGreen else TextSecondary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            entry.message, fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                            color = TextSecondary, maxLines = 1
                        )
                    }
                }
            }
        }

        // Command input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg2)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("$", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = AccentGreen)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (commandInput.isEmpty()) {
                    Text("adb cmd (e.g. ping, run_shell id, get_stats)…",
                        fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.5f))
                }
                BasicTextField(
                    value = commandInput,
                    onValueChange = onCommandChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 9.sp, color = AccentGreen, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(AccentGreen),
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = { if (commandInput.isNotBlank()) onSendCommand(commandInput) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Send, "Send", tint = AccentPurple, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun AdbCodeLine(code: String) {
    Text(
        code, fontSize = 7.5.sp, fontFamily = FontFamily.Monospace,
        color = Color(0xFF6EE7B7),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0A2A1A).copy(alpha = 0.5f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyState(tab: LogTab, query: String, filterLevel: LogLevel?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Info, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    query.isNotEmpty() -> "No matches for \"$query\""
                    filterLevel != null -> "No ${filterLevel.name} entries"
                    tab != LogTab.ALL -> "No ${tab.label.lowercase()} entries yet"
                    else -> "No logs yet"
                },
                fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.6f)
            )
        }
    }
}

// ── stats badge ───────────────────────────────────────────────────────────────
@Composable
private fun LevelStatBadge(name: String, count: Int, color: Color, total: Int) {
    if (count == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(3.dp))
        Text("$name:$count", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = color.copy(alpha = 0.8f))
    }
}

// ── log entry row ─────────────────────────────────────────────────────────────
@Composable
private fun LogEntryRow(
    entry: LogEntry,
    isExpanded: Boolean,
    searchQuery: String,
    isBookmarked: Boolean,
    onToggleExpand: () -> Unit,
    onBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShareEntry: () -> Unit
) {
    val levelColor = LevelColors[entry.level] ?: Color.Gray
    val bgColor    = LevelBgColors[entry.level] ?: Color.Transparent
    val hasStack   = entry.stackTrace != null

    // Category accent colour
    val catColor = when (entry.category) {
        "logcat"      -> Color(0xFF7C3AED)
        "network"     -> Color(0xFF0EA5E9)
        "shell"       -> Color(0xFF10B981)
        "user_action" -> Color(0xFFF59E0B)
        "adb_bridge"  -> AccentPurple
        "lifecycle"   -> Color(0xFF6B7280)
        "touch"       -> Color(0xFFF97316)
        "database"    -> Color(0xFF84CC16)
        else          -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isBookmarked) AppPrimary.copy(alpha = 0.06f)
                else if (isExpanded) CardBg else bgColor
            )
            .border(
                width = if (isExpanded || isBookmarked) 0.5.dp else 0.dp,
                color = if (isBookmarked) AppPrimary.copy(alpha = 0.3f) else BorderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { if (hasStack) onToggleExpand() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Level bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (hasStack) 32.dp else 22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(levelColor)
            )
            Spacer(Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        entry.formattedTime.substringAfter(" "),
                        fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.5f)
                    )
                    Box(
                        Modifier.clip(RoundedCornerShape(2.dp))
                            .background(levelColor.copy(alpha = 0.2f))
                            .padding(horizontal = 3.dp)
                    ) {
                        Text(entry.level.tag, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = levelColor)
                    }
                    Text("[${entry.tag}]", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = AccentBlue.copy(alpha = 0.7f))
                    if (catColor != null && entry.category != null) {
                        Box(
                            Modifier.clip(RoundedCornerShape(2.dp))
                                .background(catColor.copy(alpha = 0.15f))
                                .padding(horizontal = 3.dp)
                        ) {
                            Text(entry.category!!, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = catColor)
                        }
                    }
                    if (entry.durationMs != null) {
                        Text("${entry.durationMs}ms", fontSize = 7.sp, fontFamily = FontFamily.Monospace,
                            color = if (entry.durationMs > 1000) Color(0xFFEF4444) else TextSecondary)
                    }
                }

                Spacer(Modifier.height(1.dp))

                if (searchQuery.isNotEmpty()) {
                    HighlightedText(text = entry.message, query = searchQuery,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2, fontSize = 10.sp)
                } else {
                    Text(
                        entry.message, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        color = when (entry.level) {
                            LogLevel.FATAL, LogLevel.ERROR -> Color(0xFFFCA5A5)
                            else -> TextPrimary
                        },
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    IconButton(onClick = onBookmark, modifier = Modifier.size(18.dp)) {
                        Icon(
                            if (isBookmarked) Icons.Default.Star else Icons.Default.StarOutline,
                            "Bookmark",
                            tint = if (isBookmarked) AppPrimary else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (hasStack) {
                        IconButton(onClick = onToggleExpand, modifier = Modifier.size(18.dp)) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                "Expand", tint = levelColor.copy(alpha = 0.6f), modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(10.dp))
                    }
                    IconButton(onClick = onShareEntry, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Share, "Share", tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(10.dp))
                    }
                }
            }
        }

        // Stack trace
        if (isExpanded && hasStack) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CardBg)
                    .border(0.5.dp, BorderColor, RoundedCornerShape(4.dp))
                    .padding(6.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(entry.stackTrace ?: "", fontSize = 8.sp, fontFamily = FontFamily.Monospace,
                    color = levelColor.copy(alpha = 0.7f), lineHeight = 11.sp)
            }
        }

        // Thread / source info when expanded
        if (isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🧵 ${entry.threadName}", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.4f))
                if (entry.sourceClass != null) {
                    Text("📍 ${entry.sourceClass}.${entry.sourceMethod ?: "?"}", fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.4f))
                }
                if (entry.activityName != null) {
                    Text("📱 ${entry.activityName}", fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ── highlighted search text ───────────────────────────────────────────────────
@Composable
private fun HighlightedText(text: String, query: String, maxLines: Int, fontSize: androidx.compose.ui.unit.TextUnit) {
    val annotated = remember(text, query) {
        val lowerText  = text.lowercase()
        val lowerQuery = query.lowercase()
        buildAnnotatedString {
            var current = 0
            while (true) {
                val idx = lowerText.indexOf(lowerQuery, current)
                if (idx == -1 || query.isEmpty()) { append(text.substring(current)); break }
                append(text.substring(current, idx))
                withStyle(SpanStyle(color = AppPrimary, fontWeight = FontWeight.Bold, background = AppPrimary.copy(alpha = 0.2f))) {
                    append(text.substring(idx, idx + query.length))
                }
                current = idx + query.length
            }
        }
    }
    Text(text = annotated, fontSize = fontSize, fontFamily = FontFamily.Monospace, maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

// ── helpers ───────────────────────────────────────────────────────────────────
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("log", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun copyEntryToClipboard(context: Context, entry: LogEntry) {
    val text = "[${entry.formattedTime}] [${entry.level.tag}] [${entry.tag}] ${entry.message}" +
            (if (entry.stackTrace != null) "\n${entry.stackTrace}" else "")
    copyToClipboard(context, text)
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Share logs"))
}

private fun shareEntry(context: Context, entry: LogEntry) {
    val text = "[${entry.formattedTime}] [${entry.level.tag}] [${entry.tag}] ${entry.message}" +
            (if (entry.stackTrace != null) "\n${entry.stackTrace}" else "")
    shareText(context, text)
}

// ── Session History Tab Composable ────────────────────────────────────────────
@Composable
private fun SessionHistoryTab(
    repository: DeepCodeRepository,
    selectedSessionId: String?,
    onSelectSession: (String?) -> Unit,
    scope: CoroutineScope,
    context: Context
) {
    val sessions by repository.getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    val messages by remember(selectedSessionId) {
        if (selectedSessionId != null) {
            repository.getMessagesForSession(selectedSessionId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    if (selectedSessionId == null) {
        // Session List
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No chat sessions found", fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = TextSecondary.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(session.id) },
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    session.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "ID: ${session.id}",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(session.createdAt)),
                                    color = TextSecondary.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repository.deleteSession(session.id)
                                        Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, "Delete session", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Detailed messages view
        val activeSession = sessions.find { it.id == selectedSessionId }
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBg2)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSelectSession(null) }) {
                    Icon(Icons.Default.ArrowBack, "Back to sessions", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        activeSession?.title ?: "Session Details",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        selectedSessionId,
                        color = TextSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = BorderColor)

            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No messages in this session", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isUser = message.role == "user"
                        val isError = message.role == "assistant" && (
                            message.content.startsWith("Error:", ignoreCase = true) ||
                            message.content.contains("Exception:", ignoreCase = true) ||
                            message.content.contains("failed to configure", ignoreCase = true) ||
                            message.content.contains("not available", ignoreCase = true)
                        )

                        val bubbleBg = when {
                            isUser -> AccentBlue.copy(alpha = 0.1f)
                            isError -> Color.Red.copy(alpha = 0.08f)
                            message.role == "tool" -> AccentPurple.copy(alpha = 0.05f)
                            else -> CardBg
                        }

                        val bubbleBorder = when {
                            isUser -> AccentBlue.copy(alpha = 0.3f)
                            isError -> Color.Red.copy(alpha = 0.4f)
                            message.role == "tool" -> AccentPurple.copy(alpha = 0.2f)
                            else -> BorderColor
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(bubbleBg)
                                .border(1.dp, bubbleBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val icon = when {
                                        isUser -> Icons.Default.Person
                                        isError -> Icons.Default.Warning
                                        message.role == "tool" -> Icons.Default.Build
                                        else -> Icons.Default.Android
                                    }
                                    val iconColor = when {
                                        isUser -> AccentBlue
                                        isError -> Color.Red
                                        message.role == "tool" -> AccentPurple
                                        else -> AccentGreen
                                    }
                                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(12.dp))
                                    Text(
                                        message.role.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = iconColor
                                    )
                                }
                                Text(
                                    java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date(message.timestamp)),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(Modifier.height(6.dp))

                            Text(
                                message.content,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isError) Color(0xFFFCA5A5) else TextPrimary
                            )

                            if (message.toolCallsJson != null && message.toolCallsJson.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .border(0.5.dp, BorderColor, RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        "🛠️ Tool Call:\n${message.toolCallsJson}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentOrange
                                    )
                                }
                            }

                            if (message.toolResultsJson != null && message.toolResultsJson.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .border(0.5.dp, BorderColor, RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        "📥 Tool Result:\n${message.toolResultsJson}",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AccentGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
