package ai.deepcode.android.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import ai.deepcode.android.ui.theme.*

fun Modifier.gridBackground(
    gridSize: Dp = 22.dp,
    gridColor: Color
): Modifier = this.drawBehind {
    val sizePx = gridSize.toPx()
    val strokeWidth = 0.6.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = strokeWidth)
        x += sizePx
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = strokeWidth)
        y += sizePx
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "cardScale"
    )

    val mod = if (onClick != null) {
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else modifier

    Column(
        modifier = mod
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun OutlinedAppButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "btnScale"
    )

    val bg by animateColorAsState(
        targetValue = if (isPressed && enabled) color.copy(alpha = 0.15f) else Color.Transparent,
        label = "bg"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, if (enabled) color else color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) color else color.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) color else color.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FilledAppButton(
    onClick: () -> Unit,
    icon: ImageVector? = null,
    text: String,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "btnScale"
    )

    val bg by animateColorAsState(
        targetValue = if (isPressed && enabled) backgroundColor.copy(alpha = 0.8f) else backgroundColor,
        label = "bg"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else backgroundColor.copy(alpha = 0.4f))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "thumbOffset"
    )
    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onCheckedChange(!checked)
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary)
        )
    }
}

@Composable
fun StatusBadge(
    isConnected: Boolean,
    label: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant))
        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = if (isConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun IntegrationIcon(
    appId: String,
    appName: String,
    size: Dp = 36.dp,
    iconUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val domain = remember(appId) {
        when (appId.lowercase()) {
            "gmail" -> "gmail.com"
            "google_account", "google_calendar", "google_drive" -> "google.com"
            "github" -> "github.com"
            "notion" -> "notion.so"
            "slack" -> "slack.com"
            "telegram" -> "telegram.org"
            "whatsapp" -> "whatsapp.com"
            "spotify" -> "spotify.com"
            "twitter" -> "x.com"
            "linear" -> "linear.app"
            "jira" -> "atlassian.com"
            "stripe" -> "stripe.com"
            "shopify" -> "shopify.com"
            "discord" -> "discord.com"
            "dropbox" -> "dropbox.com"
            "trello" -> "trello.com"
            "asana" -> "asana.com"
            "hubspot" -> "hubspot.com"
            "airtable" -> "airtable.com"
            else -> "${appId.lowercase().replace("_", "")}.com"
        }
    }
    val finalIconUrl = if (!iconUrl.isNullOrBlank()) iconUrl else "https://logo.clearbit.com/$domain"
    val brandColors = remember(appId) {
        when (appId.lowercase()) {
            "gmail" -> Pair(Color(0xFFEA4335), Color(0xFFC5221F))
            "google_account", "google_calendar" -> Pair(Color(0xFF4285F4), Color(0xFF1A73E8))
            "google_drive" -> Pair(Color(0xFF34A853), Color(0xFF0F9D58))
            "github" -> Pair(Color(0xFF24292E), Color(0xFF000000))
            "notion" -> Pair(Color(0xFF2D3748), Color(0xFF1A202C))
            "slack" -> Pair(Color(0xFF4A154B), Color(0xFF3F0E40))
            "telegram" -> Pair(Color(0xFF2EA6DA), Color(0xFF1F8CB8))
            "whatsapp" -> Pair(Color(0xFF25D366), Color(0xFF128C7E))
            "spotify" -> Pair(Color(0xFF1DB954), Color(0xFF191414))
            "twitter" -> Pair(Color(0xFF1DA1F2), Color(0xFF0F8EC7))
            "linear" -> Pair(Color(0xFF5E6AD2), Color(0xFF4752B5))
            "jira" -> Pair(Color(0xFF0052CC), Color(0xFF0040A6))
            "stripe" -> Pair(Color(0xFF635BFF), Color(0xFF4F46E5))
            "shopify" -> Pair(Color(0xFF96BF48), Color(0xFF7A9E35))
            "discord" -> Pair(Color(0xFF5865F2), Color(0xFF404EED))
            "dropbox" -> Pair(Color(0xFF0061FF), Color(0xFF004AD6))
            "trello" -> Pair(Color(0xFF0079BF), Color(0xFF005E94))
            "asana" -> Pair(Color(0xFFF06A6A), Color(0xFFE24F4F))
            "hubspot" -> Pair(Color(0xFFFF7A59), Color(0xFFE25B37))
            "airtable" -> Pair(Color(0xFF18BFFF), Color(0xFF00A2E0))
            else -> Pair(Color(0xFF6B7280), Color(0xFF374151))
        }
    }
    val initials = remember(appName) {
        if (appName.isBlank()) "?"
        else {
            val words = appName.split(" ", "_", "-").filter { it.isNotBlank() }
            if (words.size >= 2) "${words[0].first().uppercase()}${words[1].first().uppercase()}"
            else appName.take(2).uppercase()
        }
    }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.matchParentSize()
                .background(brush = Brush.verticalGradient(
                    colors = listOf(brandColors.first, brandColors.second)
                )),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.3f).sp, fontFamily = FontFamily.Monospace)
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(finalIconUrl)
                .crossfade(true)
                .build(),
            contentDescription = appName,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun BottomNavBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        BottomNavTab("Dashboard", Icons.Default.Home, 0),
        BottomNavTab("Chat", Icons.AutoMirrored.Filled.Chat, 1),
        BottomNavTab("Automations", Icons.Default.Schedule, 2),
        BottomNavTab("Connections", Icons.Default.Link, 3),
        BottomNavTab("Settings", Icons.Default.Settings, 4)
    )
    Surface(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 0.5.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isActive = tab.index == activeTab
                val tintColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val scale = if (isActive) 1.15f else 1.0f

                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab.index) }
                        .padding(vertical = 8.dp)
                        .widthIn(min = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 28.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            )
                        }

                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tintColor,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = tintColor
                    )
                }
            }
        }
    }
}

private data class BottomNavTab(val label: String, val icon: ImageVector, val index: Int)

@Composable
fun NeoBrutalistCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shadowColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f),
    borderWidth: Dp = 1.dp,
    shadowOffset: Dp = 0.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(borderWidth, borderColor, shape),
        content = content
    )
}

@Composable
fun NeoBrutalistButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    shadowColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f),
    borderWidth: Dp = 1.dp,
    shadowOffset: Dp = 0.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.75f,
            stiffness = 350f
        ),
        label = "neoBtnScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .border(borderWidth, if (enabled) borderColor else borderColor.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun ToolCallCard(
    toolName: String,
    status: String,
    result: String,
    argsJson: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isFailed = status == "FAILED" || result.startsWith("Error") || result.contains("failed", ignoreCase = true)
    val statusColor = if (isFailed) Color(0xFFEF4444) else if (status == "RUNNING") Color(0xFFF59E0B) else Color(0xFF10B981)

    val toolIcon = when (toolName.lowercase()) {
        "web_search", "web_search_exa", "search_image" -> Icons.Default.Search
        "web_fetch", "read_url" -> Icons.Default.Language
        "tinyfish_agent" -> Icons.Default.AutoAwesome
        "create_pdf", "analyze_pdf", "list_reference_layouts", "get_layout_instructions" -> Icons.Default.Description
        "shell", "run_shell", "cmd" -> Icons.Default.Terminal
        "file_write", "file_read", "write_file", "read_file" -> Icons.Default.Folder
        "edge_tts" -> Icons.Default.VolumeUp
        else -> Icons.Default.Build
    }

    val title = remember(toolName, argsJson) { formatToolCallTitle(toolName, argsJson) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppCard,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = toolIcon,
                        contentDescription = toolName,
                        tint = AppPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFailed) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!argsJson.isNullOrBlank()) {
                        Text("Arguments:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppPrimary)
                        Text(argsJson, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (result.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Result:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Text(result.take(500), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun GroupedToolCallCard(
    tools: List<MessageContentPart.ToolCall>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val uniqueToolNames = remember(tools) { tools.map { it.name }.distinct() }
    val summaryTitle = remember(tools, uniqueToolNames) {
        if (uniqueToolNames.size == 1) {
            "Used ${tools.size} ${uniqueToolNames.first()} calls"
        } else {
            "Used ${tools.size} tools (${uniqueToolNames.joinToString(", ")})"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppCard,
        border = androidx.compose.foundation.BorderStroke(0.8.dp, AppBorder)
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Tools",
                        tint = AppPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = summaryTitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                    tools.forEach { tool ->
                        ToolCallCard(toolName = tool.name, status = "SUCCESS", result = tool.result)
                    }
                }
            }
        }
    }
}

private fun formatToolCallTitle(toolName: String, argsJson: String?): String {
    if (argsJson.isNullOrBlank()) return "Used $toolName"
    return try {
        val json = com.google.gson.JsonParser.parseString(argsJson).asJsonObject
        when (toolName.lowercase()) {
            "web_search", "web_search_exa" -> {
                val q = json.get("query")?.asString
                if (!q.isNullOrBlank()) "Searched web for \"$q\"" else "Used web_search"
            }
            "web_fetch" -> {
                val u = json.get("url")?.asString
                if (!u.isNullOrBlank()) "Fetched $u" else "Used web_fetch"
            }
            "tinyfish_agent" -> {
                val g = json.get("goal")?.asString
                if (!g.isNullOrBlank()) "Automated web task: \"$g\"" else "Used TinyFish Agent"
            }
            "create_pdf" -> {
                val t = json.get("title")?.asString
                if (!t.isNullOrBlank()) "Created PDF \"$t\"" else "Created PDF document"
            }
            "analyze_pdf" -> {
                val p = json.get("path")?.asString
                if (!p.isNullOrBlank()) "Analyzed PDF $p" else "Analyzed PDF"
            }
            "shell", "run_shell" -> {
                val c = json.get("command")?.asString ?: json.get("cmd")?.asString
                if (!c.isNullOrBlank()) "Ran command: $c" else "Ran shell command"
            }
            "file_write", "write_file" -> {
                val p = json.get("path")?.asString
                if (!p.isNullOrBlank()) "Wrote file $p" else "Wrote file"
            }
            "file_read", "read_file" -> {
                val p = json.get("path")?.asString
                if (!p.isNullOrBlank()) "Read file $p" else "Read file"
            }
            else -> "Used $toolName"
        }
    } catch (_: Exception) {
        "Used $toolName"
    }
}

private fun formatJsonPretty(jsonStr: String): String {
    return try {
        val element = com.google.gson.JsonParser.parseString(jsonStr)
        com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(element)
    } catch (_: Exception) {
        jsonStr
    }
}

@Composable
fun CodeBlock(code: String, language: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            try {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("Copied Code", code))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFFD1D5DB),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD1D5DB),
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE3E3E3)
                )
            }
        }
    }
}

data class ParsedLine(val rawLine: String, val leadingSpaces: Int, val trimmedLine: String, val indentDp: Dp, val dotIdx: Int, val isNumberedList: Boolean, val styledText: AnnotatedString)

private val MD_LINK_REGEX = Regex("^\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
private val MD_IMAGE_REGEX = Regex("""!\[(.*?)\]\((.*?)\)""")
private val MD_IMAGE_TAG_REGEX = Regex("""\[image:([^\]]+)\]""")
private val MD_PLAIN_URL_REGEX = Regex("""^https?://\S+(?:\.(?:jpg|jpeg|png|gif|webp|bmp)|image\.pollinations\.ai/prompt/|imagen)(\?\S*)?$""", RegexOption.IGNORE_CASE)
private val MD_DIVIDER_REGEX = Regex("^[-*_ ]{3,}$")
private val MD_TABLE_SEP_REGEX = Regex("^\\|[-:| ]+\\|$")
private val MD_PIPE_REGEX = Regex("\\|")
private val MD_AUDIO_REGEX = Regex("""\[audio:([^\]]+)\]""")
private val MD_AUDIO_LINK_REGEX = Regex("""\[(.*?)\]\((file:///[^\)]+\.(mp3|wav|ogg|m4a|aac|flac))\)""", RegexOption.IGNORE_CASE)
private val MD_IMAGE_URL_REGEX = Regex("""\[image:([^\]]+)\]""")
private val MD_VIDEO_REGEX = Regex("""\[video:([^\]]+)\]""")
private val MD_VIDEO_URL_REGEX = Regex("""^https?://\S+\.(mp4|webm|avi|mov|mkv|3gp)(\?\S*)?$""", RegexOption.IGNORE_CASE)
private val MD_FILE_REGEX = Regex("""\[file:([^\]]+)\]""")
private val MD_LAYOUT_SELECTOR_REGEX = Regex("""\[layout_selector\]""")

private fun buildInlineStyledString(text: String, codeBg: Color, codeColor: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val len = text.length; var i = 0; var isBold = false; var isItalic = false; var isCode = false
    while (i < len) {
        when {
            i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> { isBold = !isBold; i += 2 }
            text[i] == '*' && (i + 1 >= len || text[i + 1] != '*') -> { isItalic = !isItalic; i += 1 }
            text[i] == '`' -> { isCode = !isCode; i += 1 }
            text[i] == '[' -> {
                val rest = text.substring(i)
                val linkMatch = MD_LINK_REGEX.find(rest)
                if (linkMatch != null) {
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(linkMatch.groupValues[1]) }
                    i += linkMatch.value.length
                } else { append('['); i += 1 }
            }
            else -> {
                val start = i
                while (i < len) { if (text[i] == '*' || text[i] == '`' || text[i] == '[') break; i++ }
                val chunk = text.substring(start, i)
                val style = when {
                    isBold && isCode -> SpanStyle(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, background = codeBg)
                    isBold && isItalic -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    isItalic && isCode -> SpanStyle(fontStyle = FontStyle.Italic, fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor)
                    isBold -> SpanStyle(fontWeight = FontWeight.Bold)
                    isItalic -> SpanStyle(fontStyle = FontStyle.Italic)
                    isCode -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor)
                    else -> SpanStyle()
                }
                withStyle(style) { append(chunk) }
            }
        }
    }
}

private fun parseSingleMarkdownLine(rawLine: String, codeBg: Color, codeColor: Color, linkColor: Color = AppPrimary): ParsedLine {
    val leadingSpaces = rawLine.takeWhile { it == ' ' }.length
    val trimmedLine = rawLine.trimStart()
    val indentDp = (leadingSpaces * 6).dp
    val dotIdx = trimmedLine.indexOf(". ")
    val isNumberedList = dotIdx in 1..4 && trimmedLine.substring(0, dotIdx).all { it.isDigit() }
    val contentToStyle = when {
        trimmedLine.startsWith("> ") -> trimmedLine.substring(2)
        trimmedLine.startsWith("### ") -> trimmedLine.substring(4)
        trimmedLine.startsWith("## ") -> trimmedLine.substring(3)
        trimmedLine.startsWith("# ") -> trimmedLine.substring(2)
        trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> trimmedLine.substring(2)
        isNumberedList -> trimmedLine.substring(dotIdx + 2)
        else -> trimmedLine
    }
    val styled = buildInlineStyledString(contentToStyle, codeBg, codeColor, linkColor)
    return ParsedLine(rawLine, leadingSpaces, trimmedLine, indentDp, dotIdx, isNumberedList, styled)
}

private val RE_FILE_IDS = Regex("""(sediment://file[_-][a-zA-Z0-9_-]+|file-[a-zA-Z0-9_-]{8,}|file_[a-zA-Z0-9_-]{8,})""")
private val RE_DIRECT_IMAGE_URLS = Regex("""!\[.*?\]\((https?://[^\)]+)\)""")
private val RE_TAG_IMAGE_URLS = Regex("""\[image:([^\]]+)\]""")
private val RE_STRIP_MD_IMAGE = Regex("""!\[.*?\]\((?:sediment://|file[_-]|https?://).*?\)\n?""")
private val RE_STRIP_TAG_IMAGE = Regex("""\[image:[^\]]+\\]\n?""")
private val RE_STRIP_SEDIMENT = Regex("""sediment://file[_-][a-zA-Z0-9_-]+""")
private val RE_STRIP_FILE_DASH = Regex("""file-[a-zA-Z0-9_-]{8,}""")
private val RE_STRIP_FILE_UNDER = Regex("""file_[a-zA-Z0-9_-]{8,}""")

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    imageCache: Map<String, ImageBitmap>? = null,
    onSendSuggestion: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val cleanText = remember(text) { text }

    // 1. Extract raw file IDs and image URLs with fast-path check
    val allImages = remember(cleanText) {
        if (!cleanText.contains("image") && !cleanText.contains("http") && !cleanText.contains("file-") && !cleanText.contains("file_") && !cleanText.contains("sediment")) {
            emptyList()
        } else {
            val fileIds = RE_FILE_IDS.findAll(cleanText).map { it.groupValues[1] }.toList()
            val directMatches = RE_DIRECT_IMAGE_URLS.findAll(cleanText).map { it.groupValues[1] }.toList()
            val directImageUrls = if (directMatches.isNotEmpty()) directMatches else if (cleanText.startsWith("https://") && (cleanText.endsWith(".png") || cleanText.endsWith(".jpg") || cleanText.contains("oaiusercontent.com"))) listOf(cleanText.trim()) else emptyList()
            val tagImageUrls = RE_TAG_IMAGE_URLS.findAll(cleanText).map { it.groupValues[1] }.toList()
            (fileIds + directImageUrls + tagImageUrls).distinct()
        }
    }

    // 2. Strip raw image tags and file ID debris from text so display text is clean
    val displayableText = remember(cleanText) {
        if (!cleanText.contains("![") && !cleanText.contains("[image:") && !cleanText.contains("sediment") && !cleanText.contains("file-") && !cleanText.contains("file_")) {
            cleanText.trim()
        } else {
            cleanText.replace(RE_STRIP_MD_IMAGE, "")
                .replace(RE_STRIP_TAG_IMAGE, "")
                .replace(RE_STRIP_SEDIMENT, "")
                .replace(RE_STRIP_FILE_DASH, "")
                .replace(RE_STRIP_FILE_UNDER, "")
                .trim()
        }
    }

    val codeBg = if (isDarkThemeActive) Color(0xFF232530) else Color(0xFFEFF0F4)
    val codeColor = AppPrimary
    val linkColor = Color(0xFF3B82F6)
    val lines = remember(displayableText) { displayableText.lines() }
    val parsedLines = remember(displayableText, codeBg, codeColor, linkColor) { lines.map { parseSingleMarkdownLine(it, codeBg, codeColor, linkColor) } }

    Column(modifier = modifier) {
        // 3. Render all images matching chatgpt_app grid/stack layout (54.dp for multi, 110.dp for single)
        if (allImages.isNotEmpty()) {
            var selectedPreviewUrl by remember { mutableStateOf<String?>(null) }

            if (allImages.size > 1) {
                val imageRows = remember(allImages) { allImages.chunked(3) }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    for (rowImages in imageRows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (imgUrl in rowImages) {
                                Box(modifier = Modifier.clickable { selectedPreviewUrl = imgUrl }) {
                                    AuthenticatedImageView(
                                        imageUrl = imgUrl,
                                        maxDimension = 54.dp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                for (imgUrl in allImages) {
                    Box(
                        modifier = Modifier
                            .clickable { selectedPreviewUrl = imgUrl }
                            .padding(vertical = 4.dp)
                    ) {
                        AuthenticatedImageView(
                            imageUrl = imgUrl,
                            maxDimension = 110.dp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (selectedPreviewUrl != null) {
                FullScreenImagePreviewDialog(
                    imageUrl = selectedPreviewUrl!!,
                    onDismiss = { selectedPreviewUrl = null }
                )
            }
        }

        // 4. Render clean text blocks (tables, code blocks, paragraphs)
        if (displayableText.isNotEmpty()) {
            var i = 0
            while (i < parsedLines.size) {
                val parsed = parsedLines[i]
                val trimmedLine = parsed.trimmedLine

                if (trimmedLine.matches(MD_DIVIDER_REGEX) && trimmedLine.filter { it != ' ' }.toSet().size == 1) {
                HorizontalDivider(
                    color = AppDivider,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                i++
                continue
            }

            if (trimmedLine.startsWith("|")) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < parsedLines.size && parsedLines[j].trimmedLine.startsWith("|")) {
                    val raw = lines[j].trimEnd()
                    if (raw != "|-" && !raw.matches(MD_TABLE_SEP_REGEX)) {
                        tableLines.add(raw)
                    }
                    j++
                }
                if (tableLines.size >= 1) {
                    val rows = tableLines.map { row ->
                        row.split(MD_PIPE_REGEX).drop(1).dropLastWhile { it.isBlank() }.map { it.trim() }
                    }
                    if (rows.isNotEmpty()) {
                        val maxCols = rows.maxOfOrNull { it.size } ?: 0
                        TableCard(
                            rows = rows,
                            maxCols = maxCols,
                            codeBg = codeBg,
                            codeColor = codeColor,
                            linkColor = linkColor
                        )
                    }
                }
                i = j
            } else {
                val indentDp = parsed.indentDp; val isNumberedList = parsed.isNumberedList; val dotIdx = parsed.dotIdx; val styledText = parsed.styledText
                when {
                    trimmedLine.startsWith("> ") -> {
                        Text(
                            styledText,
                            fontSize = 16.sp,
                            lineHeight = 25.sp,
                            color = AppWhite.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(start = indentDp)
                                .padding(vertical = 5.dp)
                                .drawBehind {
                                    drawLine(color = AppPrimary, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 3.dp.toPx())
                                }
                                .padding(start = 12.dp)
                        )
                    }
                    trimmedLine.startsWith("### ") -> Text(styledText, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = AppWhite, modifier = Modifier.padding(start = indentDp).padding(vertical = 8.dp))
                    trimmedLine.startsWith("## ") -> Text(styledText, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = AppWhite, modifier = Modifier.padding(start = indentDp).padding(vertical = 10.dp))
                    trimmedLine.startsWith("# ") -> Text(styledText, fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = AppWhite, modifier = Modifier.padding(start = indentDp).padding(vertical = 12.dp))
                    trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") -> Row(modifier = Modifier.padding(start = indentDp).padding(vertical = 5.dp)) {
                        Text("•  ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(styledText, fontSize = 16.sp, lineHeight = 25.sp, color = Color.White)
                    }
                    isNumberedList -> Row(modifier = Modifier.padding(start = indentDp).padding(vertical = 5.dp)) {
                        Text(trimmedLine.substring(0, dotIdx + 2), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(styledText, fontSize = 16.sp, lineHeight = 25.sp, color = Color.White)
                    }
                    else -> {
                        if (trimmedLine.isNotEmpty()) {
                            Text(
                                styledText,
                                fontSize = 16.sp,
                                lineHeight = 25.sp,
                                color = AppWhite,
                                modifier = Modifier.padding(start = indentDp).padding(vertical = 5.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                }
                i++
            }
        }
    }
}

@Composable
private fun TableCard(
    rows: List<List<String>>,
    maxCols: Int,
    codeBg: Color = Color(0xFF232530),
    codeColor: Color = Color(0xFFF5A623),
    linkColor: Color = Color(0xFF3B82F6)
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, AppBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(
            containerColor = AppCard
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            Column {
                rows.forEachIndexed { rowIndex, row ->
                    val paddedRow = if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
                    val isHeader = rowIndex == 0
                    val rowBg = when {
                        isHeader -> AppSurfaceVariant
                        rowIndex % 2 == 1 -> AppCard
                        else -> AppSurface
                    }

                    Row(
                        modifier = Modifier.background(rowBg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        paddedRow.forEachIndexed { colIndex, cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 130.dp, max = 300.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = remember(cell, codeBg, codeColor, linkColor) { buildInlineStyledString(cell, codeBg, codeColor, linkColor) },
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    color = AppWhite
                                )
                            }
                            if (colIndex < maxCols - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(AppDivider)
                                )
                            }
                        }
                    }
                    if (rowIndex < rows.size - 1) {
                        HorizontalDivider(
                            color = AppDivider,
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

sealed class MessageContentPart {
    data class Attachment(val filename: String) : MessageContentPart()
    data class ToolCall(val name: String, val result: String) : MessageContentPart()
    data class Code(val code: String, val language: String) : MessageContentPart()
    data class Markdown(val text: String) : MessageContentPart()
    data class PlainText(val text: String) : MessageContentPart()
    data class Thought(val text: String) : MessageContentPart()
    data class Audio(val filePath: String) : MessageContentPart()
    data class Video(val url: String) : MessageContentPart()
    data class FileAttachment(val filePath: String) : MessageContentPart()
    data class LayoutSelector(val layouts: List<Pair<String, String>>) : MessageContentPart() // (name, description)
}

private val messageParseCache = linkedMapOf<String, List<MessageContentPart>>()
private const val MAX_PARSE_CACHE = 400

private val DSML_TOOL_CALLS_REGEX = Regex("""<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?tool_calls?[\s\S]*?<\s*/\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?tool_calls?\s*>""", RegexOption.IGNORE_CASE)
private val DSML_INVOKE_REGEX = Regex("""<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?invoke[\s\S]*?<\s*/\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?invoke\s*>""", RegexOption.IGNORE_CASE)
private val DSML_PARAM_REGEX = Regex("""<\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?parameter[\s\S]*?<\s*/\s*(?:\|{1,2}\s*DSML\s*\|{1,2}\s*)?parameter\s*>""", RegexOption.IGNORE_CASE)
private val THINK_OPEN_REGEX = Regex("""<\s*(?:think|thinking|reasoning)\s*>""", RegexOption.IGNORE_CASE)
private val THINK_CLOSE_REGEX = Regex("""<\s*/\s*(?:think|thinking|reasoning)\s*>""", RegexOption.IGNORE_CASE)
private val THINK_BRACKET_OPEN_REGEX = Regex("""\[\s*(?:thought|think|thinking|reasoning)\s*\]""", RegexOption.IGNORE_CASE)
private val THINK_BRACKET_CLOSE_REGEX = Regex("""\[\s*/\s*(?:thought|think|thinking|reasoning)\s*\]""", RegexOption.IGNORE_CASE)

fun parseMessageContent(content: String, isUser: Boolean): List<MessageContentPart> {
    val key = (if (isUser) "U:" else "A:") + content
    synchronized(messageParseCache) {
        messageParseCache[key]?.let { return it }
        val result = parseMessageContentInternal(content, isUser)
        if (messageParseCache.size > MAX_PARSE_CACHE) messageParseCache.clear()
        messageParseCache[key] = result
        return result
    }
}

fun parseMessageContentInternal(content: String, isUser: Boolean): List<MessageContentPart> {
    val parts = mutableListOf<MessageContentPart>()
    var remaining = content
    // Clean raw DSML markup if present using pre-compiled regexes
    remaining = remaining.replace(DSML_TOOL_CALLS_REGEX, "")
    remaining = remaining.replace(DSML_INVOKE_REGEX, "")
    remaining = remaining.replace(DSML_PARAM_REGEX, "")

    // Normalize all thinking/reasoning tag variations using pre-compiled regexes
    remaining = remaining
        .replace(THINK_OPEN_REGEX, "<thought>")
        .replace(THINK_CLOSE_REGEX, "</thought>")
        .replace(THINK_BRACKET_OPEN_REGEX, "<thought>")
        .replace(THINK_BRACKET_CLOSE_REGEX, "</thought>")

    if (remaining.startsWith("📌 ")) {
        val filename = remaining.lines().firstOrNull()?.substring(2)?.trim() ?: "file"
        parts.add(MessageContentPart.Attachment(filename))
        remaining = if (remaining.contains("]\n\n")) remaining.substringAfter("]\n\n").trim() else remaining.substringAfter("\n\n").trim()
    }
    if (remaining.isEmpty()) return parts

    if (remaining.contains("<thought>")) {
        val beforeThought = remaining.substringBefore("<thought>")
        if (beforeThought.trim().isNotEmpty()) {
            parts.addAll(parseMessageContent(beforeThought, isUser))
        }
        val afterThought = remaining.substringAfter("<thought>")
        val thoughtContent = afterThought.substringBefore("</thought>")
        parts.add(MessageContentPart.Thought(thoughtContent))

        val afterThoughtEnd = afterThought.substringAfter("</thought>", "")
        if (afterThoughtEnd.trim().isNotEmpty()) {
            parts.addAll(parseMessageContent(afterThoughtEnd, isUser))
        }
        return parts
    } else if (remaining.contains("</thought>")) {
        val thoughtContent = remaining.substringBefore("</thought>")
        parts.add(MessageContentPart.Thought(thoughtContent))
        val afterThoughtEnd = remaining.substringAfter("</thought>")
        if (afterThoughtEnd.trim().isNotEmpty()) {
            parts.addAll(parseMessageContent(afterThoughtEnd, isUser))
        }
        return parts
    } else if (content.contains("<thought>") && !content.contains("</thought>")) {
        val thoughtContent = remaining.substringAfter("<thought>")
        parts.add(MessageContentPart.Thought(thoughtContent))
        return parts
    }

    val audioMatch = MD_AUDIO_REGEX.find(remaining)
    val audioLinkMatch = if (audioMatch == null) MD_AUDIO_LINK_REGEX.find(remaining) else null
    val targetAudioMatch = audioMatch ?: audioLinkMatch
    if (targetAudioMatch != null) {
        val before = remaining.substringBefore(targetAudioMatch.value)
        if (before.trim().isNotEmpty()) parts.addAll(parseMessageContent(before, isUser))
        val rawPath = if (audioLinkMatch != null) audioLinkMatch.groupValues[2] else targetAudioMatch.groupValues[1]
        val cleanPath = rawPath.removePrefix("file://").trim()
        parts.add(MessageContentPart.Audio(cleanPath))
        val after = remaining.substringAfter(targetAudioMatch.value)
        if (after.trim().isNotEmpty()) parts.addAll(parseMessageContent(after, isUser))
        return parts
    }

    val imageUrlMatch = MD_IMAGE_URL_REGEX.find(remaining)
    if (imageUrlMatch != null) {
        val before = remaining.substringBefore(imageUrlMatch.value)
        if (before.trim().isNotEmpty()) parts.addAll(parseMessageContent(before, isUser))
        parts.add(MessageContentPart.Markdown("![image](${imageUrlMatch.groupValues[1]})"))
        val after = remaining.substringAfter(imageUrlMatch.value)
        if (after.trim().isNotEmpty()) parts.addAll(parseMessageContent(after, isUser))
        return parts
    }

    val videoMatch = MD_VIDEO_REGEX.find(remaining)
    if (videoMatch != null) {
        val before = remaining.substringBefore(videoMatch.value)
        if (before.trim().isNotEmpty()) parts.addAll(parseMessageContent(before, isUser))
        parts.add(MessageContentPart.Video(videoMatch.groupValues[1]))
        val after = remaining.substringAfter(videoMatch.value)
        if (after.trim().isNotEmpty()) parts.addAll(parseMessageContent(after, isUser))
        return parts
    }

    val fileMatch = MD_FILE_REGEX.find(remaining)
    if (fileMatch != null) {
        val before = remaining.substringBefore(fileMatch.value)
        if (before.trim().isNotEmpty()) parts.addAll(parseMessageContent(before, isUser))
        parts.add(MessageContentPart.FileAttachment(fileMatch.groupValues[1]))
        val after = remaining.substringAfter(fileMatch.value)
        if (after.trim().isNotEmpty()) parts.addAll(parseMessageContent(after, isUser))
        return parts
    }

    val layoutMatch = MD_LAYOUT_SELECTOR_REGEX.find(remaining)
    if (layoutMatch != null) {
        val before = remaining.substringBefore(layoutMatch.value)
        if (before.trim().isNotEmpty()) parts.addAll(parseMessageContent(before, isUser))
        parts.add(MessageContentPart.LayoutSelector(emptyList()))
        val after = remaining.substringAfter(layoutMatch.value)
        if (after.trim().isNotEmpty()) parts.addAll(parseMessageContent(after, isUser))
        return parts
    }

    if (remaining.contains("TOOL:")) {
        val contentBefore = remaining.substringBefore("TOOL:")
        val toolCallStr = "TOOL:" + remaining.substringAfter("TOOL:")
        if (contentBefore.trim().isNotEmpty()) parts.add(MessageContentPart.Markdown(contentBefore))
        parts.add(MessageContentPart.ToolCall(toolCallStr.substringAfter("TOOL:").substringBefore(",").trim(), toolCallStr.substringAfter("OBSERVATION:").trim()))
    } else if (remaining.contains("```")) {
        remaining.split("```").forEachIndexed { index, part ->
            if (index % 2 == 1) {
                val trimmed = part.trim()
                val lines = trimmed.lines()
                if (lines.size <= 1) {
                    parts.add(MessageContentPart.Code(trimmed, "code"))
                } else {
                    parts.add(MessageContentPart.Code(lines.drop(1).joinToString("\n"), lines.first().trim()))
                }
            } else if (part.trim().isNotEmpty()) {
                parts.add(MessageContentPart.Markdown(part))
            }
        }
    } else parts.add(if (isUser) MessageContentPart.PlainText(remaining) else MessageContentPart.Markdown(remaining))
    return parts
}

@Composable
fun WelcomeSuggestionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color> = listOf(MaterialTheme.colorScheme.primary, Color(0xFFD4880F))
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "suggestionScale"
    )

    Card(
        modifier = modifier
            .width(160.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    gradient.first().copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                gradient.first().copy(alpha = 0.2f),
                                gradient.last().copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = gradient.first(),
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "chipScale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun VoiceInputButton(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "voiceScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFFEF4444) else MaterialTheme.colorScheme.surface,
        label = "voiceBg"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onStartRecording
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPressed) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = "Voice input",
            tint = if (isPressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun FileCard(filePath: String) {
    val context = LocalContext.current
    val file = remember(filePath) { java.io.File(filePath) }
    val fileName = remember(filePath) { file.name }
    val fileSize = remember(filePath) {
        if (file.exists()) {
            val bytes = file.length()
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        } else "—"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AppDivider, RoundedCornerShape(10.dp))
            .border(1.dp, AppBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = AppPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = fileSize,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
        // Open button
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = "Open",
            tint = AppPrimary,
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        )
        Spacer(modifier = Modifier.width(12.dp))
        // Share button
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            context.packageName + ".provider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = context.contentResolver.getType(uri) ?: "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share file"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
    }
}

data class ReferenceLayoutOption(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val name: String,
    val description: String,
    val id: String
)

val referenceLayoutOptions = listOf(
    ReferenceLayoutOption(Icons.Default.Edit, "Field Notes", "Spiral notebook style — for study notes", "field-notes"),
    ReferenceLayoutOption(Icons.Default.Article, "Editorial Journal", "Scholarly two-column format", "editorial-journal"),
    ReferenceLayoutOption(Icons.Default.Assessment, "Executive Briefing", "Slide-deck style for reports", "executive-briefing"),
    ReferenceLayoutOption(Icons.Default.Build, "Blueprint", "Technical blueprint style", "blueprint"),
    ReferenceLayoutOption(Icons.Default.PlayArrow, "Quickstart Guide", "Step-by-step tutorial layout", "quickstart-guide"),
    ReferenceLayoutOption(Icons.Default.ViewAgenda, "Poster", "Large-format single-page layout", "poster"),
    ReferenceLayoutOption(Icons.Default.Person, "Resume", "ATS-friendly resume format", "resume"),
    ReferenceLayoutOption(Icons.Default.Dashboard, "Dashboard", "Data-heavy layout with charts", "dashboard"),
)

@Composable
fun LayoutSelectorCard(onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "Choose a layout:",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        referenceLayoutOptions.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { layout ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(layout.name) },
                        colors = CardDefaults.cardColors(
                            containerColor = AppDivider
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(layout.icon, null, tint = AppPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                layout.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                layout.description,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 9.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowItems.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val videoViewRef = remember { mutableStateOf<android.widget.VideoView?>(null) }

    DisposableEffect(videoUrl) {
        onDispose {
            videoViewRef.value?.stopPlayback()
            videoViewRef.value = null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).also { vv ->
                    vv.setVideoPath(videoUrl)
                    vv.setOnPreparedListener { mp ->
                        mp.isLooping = false
                    }
                    vv.setOnCompletionListener { isPlaying = false }
                    vv.setOnErrorListener { _, _, _ -> isPlaying = false; true }
                    videoViewRef.value = vv
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            TextButton(onClick = {
                val vv = videoViewRef.value
                if (vv != null) {
                    if (isPlaying) {
                        vv.pause()
                        isPlaying = false
                    } else {
                        vv.start()
                        isPlaying = true
                    }
                }
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isPlaying) "Pause" else "Play Video",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MediaProcessingOverlay(
    mediaType: String,
    prompt: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val icon = when (mediaType) {
        "video" -> Icons.Default.Movie
        else -> Icons.Default.Panorama
    }
    val label = when (mediaType) {
        "video" -> "Generating video..."
        else -> "Creating image..."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppField)
            .border(1.dp, AppBorder, RoundedCornerShape(16.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .alpha(pulseAnim.coerceIn(0f, 1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                prompt,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Processing...",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun ImageGenerationSkeleton(
    modifier: Modifier = Modifier,
    statusText: String = "Generating image..."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161822))
            .border(1.dp, Color(0xFF2B2E3D), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8B5CF6).copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎨", fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "This might take a few seconds...",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AuthenticatedImageView(
    imageUrl: String,
    modifier: Modifier = Modifier,
    maxDimension: Dp = 110.dp,
    fillContainer: Boolean = false
) {
    val context = LocalContext.current
    var bitmap by remember(imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var isError by remember(imageUrl) { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        isLoading = true
        isError = false
        val fetchedBitmap = loadImageBitmapFromUrl(imageUrl)
        if (fetchedBitmap != null) {
            bitmap = fetchedBitmap
        } else {
            isError = true
        }
        isLoading = false
    }

    val (widthDp, heightDp) = remember(bitmap, maxDimension) {
        if (bitmap != null && bitmap!!.width > 0 && bitmap!!.height > 0) {
            val w = bitmap!!.width.toFloat()
            val h = bitmap!!.height.toFloat()
            if (w >= h) {
                maxDimension to (maxDimension * (h / w))
            } else {
                (maxDimension * (w / h)) to maxDimension
            }
        } else {
            maxDimension to maxDimension
        }
    }

    Box(
        modifier = modifier
            .then(if (!fillContainer) Modifier.size(width = widthDp, height = heightDp) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E202B)),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            ImageGenerationSkeleton(statusText = "")
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Generated Image",
                contentScale = if (fillContainer) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Image Error",
                tint = Color.Red.copy(alpha = 0.7f),
                modifier = Modifier.size(if (maxDimension <= 35.dp) 14.dp else 20.dp)
            )
        }
    }
}

@Composable
fun FullScreenImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AuthenticatedImageView(
                    imageUrl = imageUrl,
                    maxDimension = 360.dp,
                    fillContainer = true,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E202B).copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Preview",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Image URL", imageUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Saved image URL to clipboard", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E202B).copy(alpha = 0.7f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Save Image",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private suspend fun loadImageBitmapFromUrl(imageUrl: String): android.graphics.Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        if (imageUrl.startsWith("file://")) {
            val file = java.io.File(imageUrl.removePrefix("file://"))
            if (file.exists()) return@withContext android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } else if (imageUrl.startsWith("/")) {
            val file = java.io.File(imageUrl)
            if (file.exists()) return@withContext android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        } else if (imageUrl.startsWith("data:image/")) {
            val base64Data = imageUrl.substringAfter("base64,")
            val decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            return@withContext android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            val req = okhttp3.Request.Builder().url(imageUrl).build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    return@withContext android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        }
    } catch (_: Exception) {}
    null
}
