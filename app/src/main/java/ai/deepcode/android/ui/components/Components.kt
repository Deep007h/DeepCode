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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.window.DialogWindowProvider
import android.provider.MediaStore
import android.os.Environment
import android.os.Build
import android.content.ContentValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import ai.deepcode.android.ui.chat.StreamingActiveCursor

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
    val finalIconUrl = remember(appId, iconUrl, domain) {
        if (!iconUrl.isNullOrBlank()) iconUrl else "https://logo.clearbit.com/$domain"
    }
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
    val initials = remember(appName, appId) {
        if (appId.equals("github", ignoreCase = true) || appName.equals("github", ignoreCase = true)) "GH"
        else if (appName.isBlank()) "?"
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
        // Remembered request: previously rebuilt on EVERY recomposition, which
        // restarted Coil's async load and flickered the logo in scrolling lists.
        val context = LocalContext.current
        val imageRequest = remember(finalIconUrl) {
            ImageRequest.Builder(context)
                .data(finalIconUrl)
                .crossfade(true)
                // Clearbit 404s often; keep initials gradient underneath and
                // don't flash a blank frame on error.
                .allowHardware(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = appName,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
            // Null placeholder/error = keep initials visible, no flicker.
            placeholder = null,
            error = null,
            fallback = null
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
                // Animated (was instant 1.0f<->1.15f jump via graphicsLayer,
                // which read as a pop on every tab switch).
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1.15f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.75f,
                        stiffness = 350f
                    ),
                    label = "navScale_${tab.index}"
                )
                val pillAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isActive) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(180),
                    label = "navPill_${tab.index}"
                )

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
                        // Always occupy pill space (was if(isActive) Box) so
                        // activating a tab doesn't shift icon position by 28dp.
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 28.dp)
                                .graphicsLayer { alpha = pillAlpha }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        )

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

data class ParsedLine(
    val rawLine: String,
    val leadingSpaces: Int,
    val trimmedLine: String,
    val indentDp: Dp,
    val dotIdx: Int,
    val isNumberedList: Boolean,
    val headingLevel: Int,
    val styledText: AnnotatedString
)

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

private val RE_HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val RE_HEADING_ONLY = Regex("""^#{1,6}$""")

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains("-")) return false
    val stripped = trimmed.replace("|", "").replace(":", "").replace("-", "").replace(" ", "")
    return stripped.isEmpty() && trimmed.contains("|")
}

private fun parseTableRow(raw: String): List<String> {
    val trimmed = raw.trim()
    val content = trimmed.removePrefix("|").removeSuffix("|")
    return content.split(MD_PIPE_REGEX).map { it.trim() }
}

private fun buildInlineStyledString(text: String, codeBg: Color, codeColor: Color, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val len = text.length
    var i = 0
    var isBold = false
    var isItalic = false
    var isCode = false
    var isStrike = false

    fun appendStyled(chunk: String) {
        if (chunk.isEmpty()) return
        if (!isBold && !isItalic && !isCode && !isStrike) {
            append(chunk)
        } else {
            val style = SpanStyle(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = if (isCode) FontFamily.Monospace else null,
                background = if (isCode) codeBg else Color.Unspecified,
                color = if (isCode) codeColor else Color.Unspecified,
                textDecoration = if (isStrike) TextDecoration.LineThrough else TextDecoration.None
            )
            withStyle(style) { append(chunk) }
        }
    }

    while (i < len) {
        when {
            // ***bold+italic***
            i + 2 < len && text[i] == '*' && text[i + 1] == '*' && text[i + 2] == '*' -> {
                isBold = !isBold; isItalic = !isItalic; i += 3
            }
            // **bold**
            i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                isBold = !isBold; i += 2
            }
            // __bold__
            i + 1 < len && text[i] == '_' && text[i + 1] == '_' -> {
                isBold = !isBold; i += 2
            }
            // ~~strike~~
            i + 1 < len && text[i] == '~' && text[i + 1] == '~' -> {
                isStrike = !isStrike; i += 2
            }
            // *italic* (skip if standalone asterisk surrounded by spaces)
            text[i] == '*' && !(i > 0 && text[i - 1] == ' ' && i + 1 < len && text[i + 1] == ' ') -> {
                isItalic = !isItalic; i += 1
            }
            // _italic_ (skip if inside variable_name like foo_bar)
            text[i] == '_' && !(i > 0 && text[i - 1].isLetterOrDigit() && i + 1 < len && text[i + 1].isLetterOrDigit()) -> {
                isItalic = !isItalic; i += 1
            }
            // `inline code`
            text[i] == '`' -> {
                isCode = !isCode; i += 1
            }
            // [link](url)
            text[i] == '[' -> {
                val rest = text.substring(i)
                val linkMatch = MD_LINK_REGEX.find(rest)
                if (linkMatch != null) {
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(linkMatch.groupValues[1])
                    }
                    i += linkMatch.value.length
                } else {
                    appendStyled("[")
                    i += 1
                }
            }
            else -> {
                val start = i
                // Consume at least 1 character to guarantee forward progress
                i++
                // Consume subsequent regular characters until the next potential delimiter
                while (i < len) {
                    val c = text[i]
                    if (c == '*' || c == '_' || c == '~' || c == '`' || c == '[') break
                    i++
                }
                appendStyled(text.substring(start, i))
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

    val headingMatch = RE_HEADING.find(trimmedLine)
    val isHeadingInProgress = RE_HEADING_ONLY.matches(trimmedLine)
    val headingLevel = when {
        headingMatch != null -> headingMatch.groupValues[1].length
        isHeadingInProgress -> trimmedLine.length
        else -> 0
    }

    val contentToStyle = when {
        trimmedLine.startsWith("> ") -> trimmedLine.substring(2)
        headingMatch != null -> headingMatch.groupValues[2]
        isHeadingInProgress -> ""
        trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("+ ") -> trimmedLine.substring(2)
        trimmedLine == "-" || trimmedLine == "*" || trimmedLine == "+" -> ""
        isNumberedList -> trimmedLine.substring(dotIdx + 2)
        else -> trimmedLine
    }
    val styled = buildInlineStyledString(contentToStyle, codeBg, codeColor, linkColor)
    return ParsedLine(rawLine, leadingSpaces, trimmedLine, indentDp, dotIdx, isNumberedList, headingLevel, styled)
}

private val RE_FILE_IDS = Regex("""(sediment://file[_-][a-zA-Z0-9_-]+|file-[a-zA-Z0-9_-]{8,}|file_[a-zA-Z0-9_-]{8,})""")
private val RE_DIRECT_IMAGE_URLS = Regex("""!\[.*?\]\(([^\)]+)\)""")
private val RE_TAG_IMAGE_URLS = Regex("""\[image:([^\]]+)\]""")
private val RE_STRIP_MD_IMAGE = Regex("""!\[.*?\]\([^\)]+\)\n?""")
private val RE_STRIP_TAG_IMAGE = Regex("""\[image:[^\]]+\]\n?""")
private val RE_STRIP_SEDIMENT = Regex("""sediment://file[_-][a-zA-Z0-9_-]+""")
private val RE_STRIP_FILE_DASH = Regex("""file-[a-zA-Z0-9_-]{8,}""")
private val RE_STRIP_FILE_UNDER = Regex("""file_[a-zA-Z0-9_-]{8,}""")

fun buildStreamingMarkdown(
    text: String,
    codeBg: Color,
    codeColor: Color,
    linkColor: Color = AppPrimary,
    textColor: Color = Color.White
): AnnotatedString = buildAnnotatedString {
    if (text.isEmpty()) return@buildAnnotatedString

    val clean = if (!text.contains("![") && !text.contains("[image:") && !text.contains("sediment") && !text.contains("file-") && !text.contains("file_")) {
        text
    } else {
        text.replace(RE_STRIP_MD_IMAGE, "")
            .replace(RE_STRIP_TAG_IMAGE, "")
            .replace(RE_STRIP_SEDIMENT, "")
            .replace(RE_STRIP_FILE_DASH, "")
            .replace(RE_STRIP_FILE_UNDER, "")
    }

    val lines = clean.lines()
    val totalLines = lines.size
    var inCodeFence = false

    for (lineIndex in 0 until totalLines) {
        val line = lines[lineIndex]
        val trimmed = line.trimStart()
        val isLast = lineIndex == totalLines - 1

        if (trimmed.startsWith("```")) {
            inCodeFence = !inCodeFence
            if (inCodeFence) {
                val lang = trimmed.removePrefix("```").trim()
                if (lang.isNotEmpty()) {
                    withStyle(SpanStyle(color = codeColor.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) {
                        append("[$lang]\n")
                    }
                }
            }
            continue
        }

        if (inCodeFence) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor, fontSize = 13.5.sp)) {
                append(line)
            }
            if (!isLast) append("\n")
            continue
        }

        val headingMatch = RE_HEADING.find(trimmed)
        val isHeadingInProgress = RE_HEADING_ONLY.matches(trimmed)
        if (isHeadingInProgress) {
            continue
        }
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2]
            val (headingSize, weight) = when (level) {
                1 -> 22.sp to FontWeight.Bold
                2 -> 19.sp to FontWeight.Bold
                3 -> 17.sp to FontWeight.Bold
                4 -> 15.5.sp to FontWeight.SemiBold
                else -> 14.5.sp to FontWeight.SemiBold
            }
            withStyle(SpanStyle(fontSize = headingSize, fontWeight = weight, color = textColor)) {
                append(buildInlineStyledString(title, codeBg, codeColor, linkColor))
            }
            if (!isLast) append("\n")
            continue
        }

        if (trimmed.matches(MD_DIVIDER_REGEX) && trimmed.filter { it != ' ' }.toSet().size == 1) {
            withStyle(SpanStyle(color = textColor.copy(alpha = 0.3f))) {
                append("──────────\n")
            }
            continue
        }

        if (isTableSeparator(trimmed) || trimmed == "|-") {
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 2) {
            val cols = parseTableRow(trimmed)
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp)) {
                cols.forEachIndexed { ci, col ->
                    append(col)
                    if (ci < cols.size - 1) append(" │ ")
                }
            }
            if (!isLast) append("\n")
            continue
        }

        if (trimmed.startsWith("> ")) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor.copy(alpha = 0.8f))) {
                append("▎ ")
                append(buildInlineStyledString(trimmed.substring(2), codeBg, codeColor, linkColor))
            }
            if (!isLast) append("\n")
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppPrimary)) {
                append("• ")
            }
            append(buildInlineStyledString(trimmed.substring(2), codeBg, codeColor, linkColor))
            if (!isLast) append("\n")
            continue
        }
        if (trimmed == "-" || trimmed == "*" || trimmed == "+") {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppPrimary)) {
                append("• ")
            }
            if (!isLast) append("\n")
            continue
        }

        val dotIdx = trimmed.indexOf(". ")
        val isNumberedList = dotIdx in 1..4 && trimmed.substring(0, dotIdx).all { it.isDigit() }
        if (isNumberedList) {
            val numPrefix = trimmed.substring(0, dotIdx + 2)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppPrimary)) {
                append(numPrefix)
            }
            append(buildInlineStyledString(trimmed.substring(dotIdx + 2), codeBg, codeColor, linkColor))
            if (!isLast) append("\n")
            continue
        }

        if (trimmed.startsWith("📌 ")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppPrimary)) {
                append(trimmed)
            }
            if (!isLast) append("\n")
            continue
        }

        val leadingSpaces = line.takeWhile { it == ' ' }.length
        if (leadingSpaces > 0) {
            append(" ".repeat(leadingSpaces))
        }
        append(buildInlineStyledString(trimmed, codeBg, codeColor, linkColor))
        if (!isLast) append("\n")
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    imageCache: Map<String, ImageBitmap>? = null,
    onSendSuggestion: ((String) -> Unit)? = null,
    isStreaming: Boolean = false
) {
    val context = LocalContext.current
    val cleanText = remember(text) { text }

    // 1. Extract raw file IDs and image URLs with fast-path check
    val allImages = remember(cleanText) {
        if (!cleanText.contains("image") && !cleanText.contains("http") && !cleanText.contains("![") && !cleanText.contains("[image:") && !cleanText.contains("file-") && !cleanText.contains("file_") && !cleanText.contains("sediment")) {
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
    // Hoisted: previously remember{} inside `if (allImages.isNotEmpty())`
    // reset preview state whenever the image list toggled 0<->N during
    // streaming and violates conditional-remember stability.
    var selectedPreviewUrl by remember(allImages) { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        // 3. Render all images matching chatgpt_app grid/stack layout (54.dp for multi, 260.dp for single)
        if (allImages.isNotEmpty()) {

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
                            maxDimension = 260.dp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (selectedPreviewUrl != null) {
                FullScreenImagePreviewDialog(
                    imageUrl = selectedPreviewUrl!!,
                    onDismiss = { selectedPreviewUrl = null },
                    onSendSuggestion = onSendSuggestion
                )
            }
        }

        // 4. Render clean text blocks (tables, code blocks, paragraphs)
        if (displayableText.isNotEmpty()) {
            var i = 0
            while (i < parsedLines.size) {
                val parsed = parsedLines[i]
                val trimmedLine = parsed.trimmedLine
                val isLastLine = i == parsedLines.size - 1

                if (trimmedLine.matches(MD_DIVIDER_REGEX) && trimmedLine.filter { it != ' ' }.toSet().size == 1) {
                    HorizontalDivider(
                        color = AppDivider,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    if (isStreaming && isLastLine) {
                        StreamingActiveCursor(color = AppPrimary)
                    }
                    i++
                    continue
                }

                if (trimmedLine.startsWith("|")) {
                    val tableLines = mutableListOf<String>()
                    var j = i
                    while (j < parsedLines.size && parsedLines[j].trimmedLine.startsWith("|")) {
                        val raw = lines[j].trimEnd()
                        if (raw != "|-" && !isTableSeparator(raw)) {
                            tableLines.add(raw)
                        }
                        j++
                    }
                    if (tableLines.isNotEmpty()) {
                        val rows = tableLines.map { row -> parseTableRow(row) }
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
                    val isTableAtEnd = j >= parsedLines.size
                    if (isStreaming && isTableAtEnd) {
                        Box(modifier = Modifier.padding(top = 4.dp)) {
                            StreamingActiveCursor(color = AppPrimary)
                        }
                    }
                    i = j
                } else {
                    val indentDp = parsed.indentDp
                    val isNumberedList = parsed.isNumberedList
                    val dotIdx = parsed.dotIdx
                    val styledText = parsed.styledText
                    val headingLevel = parsed.headingLevel

                    when {
                        headingLevel > 0 -> {
                            if (styledText.isNotEmpty()) {
                                val (fontSize, lineHeight, fontWeight) = when (headingLevel) {
                                    1 -> Triple(22.sp, 30.sp, FontWeight.Bold)
                                    2 -> Triple(19.sp, 26.sp, FontWeight.Bold)
                                    3 -> Triple(17.sp, 24.sp, FontWeight.Bold)
                                    4 -> Triple(15.5.sp, 22.sp, FontWeight.SemiBold)
                                    5 -> Triple(14.5.sp, 21.sp, FontWeight.SemiBold)
                                    else -> Triple(14.sp, 20.sp, FontWeight.SemiBold)
                                }
                                Row(
                                    modifier = Modifier
                                        .padding(start = indentDp)
                                        .padding(vertical = (8 - headingLevel).coerceAtLeast(3).dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        styledText,
                                        fontSize = fontSize,
                                        lineHeight = lineHeight,
                                        fontWeight = fontWeight,
                                        color = AppWhite
                                    )
                                    if (isStreaming && isLastLine) {
                                        Spacer(Modifier.width(4.dp))
                                        StreamingActiveCursor(color = AppPrimary)
                                    }
                                }
                            } else if (isStreaming && isLastLine) {
                                StreamingActiveCursor(color = AppPrimary)
                            }
                        }
                        trimmedLine.startsWith("> ") -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    styledText,
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = AppWhite.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(start = indentDp)
                                        .padding(vertical = 4.dp)
                                        .drawBehind {
                                            drawLine(color = AppPrimary, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 3.dp.toPx())
                                        }
                                        .padding(start = 12.dp)
                                )
                                if (isStreaming && isLastLine) {
                                    Spacer(Modifier.width(4.dp))
                                    StreamingActiveCursor(color = AppPrimary)
                                }
                            }
                        }
                        trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ") || trimmedLine.startsWith("+ ") || trimmedLine == "-" || trimmedLine == "*" || trimmedLine == "+" -> {
                            Row(
                                modifier = Modifier.padding(start = indentDp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("•  ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(styledText, fontSize = 15.sp, lineHeight = 23.sp, color = Color.White)
                                if (isStreaming && isLastLine) {
                                    Spacer(Modifier.width(4.dp))
                                    StreamingActiveCursor(color = AppPrimary)
                                }
                            }
                        }
                        isNumberedList -> {
                            Row(
                                modifier = Modifier.padding(start = indentDp).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(trimmedLine.substring(0, dotIdx + 2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(styledText, fontSize = 15.sp, lineHeight = 23.sp, color = Color.White)
                                if (isStreaming && isLastLine) {
                                    Spacer(Modifier.width(4.dp))
                                    StreamingActiveCursor(color = AppPrimary)
                                }
                            }
                        }
                        else -> {
                            var endJ = i
                            val batchStyled = buildAnnotatedString {
                                while (endJ < parsedLines.size) {
                                    val candidate = parsedLines[endJ]
                                    val cTrimmed = candidate.trimmedLine
                                    val isSpecial = candidate.headingLevel > 0 ||
                                            cTrimmed.startsWith("> ") ||
                                            cTrimmed.startsWith("- ") || cTrimmed.startsWith("* ") || cTrimmed.startsWith("+ ") ||
                                            cTrimmed == "-" || cTrimmed == "*" || cTrimmed == "+" ||
                                            candidate.isNumberedList ||
                                            cTrimmed.startsWith("|") ||
                                            (cTrimmed.matches(MD_DIVIDER_REGEX) && cTrimmed.filter { it != ' ' }.toSet().size == 1)
                                    if (isSpecial) break
                                    if (endJ > i) append("\n")
                                    if (candidate.leadingSpaces > 0) {
                                        append(" ".repeat(candidate.leadingSpaces))
                                    }
                                    append(candidate.styledText)
                                    endJ++
                                }
                            }
                            val isLastBatchLine = endJ >= parsedLines.size
                            if (batchStyled.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(start = indentDp).padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        batchStyled,
                                        fontSize = 15.sp,
                                        lineHeight = 23.sp,
                                        color = AppWhite
                                    )
                                    if (isStreaming && isLastBatchLine) {
                                        Spacer(Modifier.width(4.dp))
                                        StreamingActiveCursor(color = AppPrimary)
                                    }
                                }
                            } else {
                                if (isStreaming && isLastBatchLine) {
                                    StreamingActiveCursor(color = AppPrimary)
                                } else if (trimmedLine.isEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                            i = if (endJ > i) endJ else i + 1
                            continue
                        }
                    }
                    i++
                }
            }
        } else if (isStreaming) {
            StreamingActiveCursor(color = AppPrimary)
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
    if (rows.isEmpty() || maxCols <= 0) return

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
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val availableWidth = maxWidth

            val uriHandler = LocalUriHandler.current

            // 1. Calculate max character length per column across all rows (stripping markdown link URLs)
            val colMaxChars = remember(rows, maxCols) {
                (0 until maxCols).map { c ->
                    rows.maxOfOrNull { r ->
                        val raw = r.getOrNull(c) ?: ""
                        val stripped = MD_LINK_REGEX.replace(raw) { it.groupValues[1] }
                        stripped.length
                    } ?: 0
                }
            }

            // 2. Base width per column according to content length
            val baseColWidths = remember(colMaxChars, maxCols) {
                (0 until maxCols).map { c ->
                    val len = colMaxChars[c]
                    when {
                        len > 50 -> 240.dp
                        len > 30 -> 180.dp
                        len > 18 -> 140.dp
                        len > 8 -> 110.dp
                        else -> 80.dp
                    }
                }
            }

            val dividerWidthTotal = ((maxCols - 1).coerceAtLeast(0) * 1).dp
            val totalBaseWidth = baseColWidths.fold(0.dp) { acc, d -> acc + d } + dividerWidthTotal

            // 3. Proportional expansion if base widths fit inside availableWidth
            val colWidths = remember(totalBaseWidth, availableWidth, colMaxChars, maxCols) {
                if (totalBaseWidth <= availableWidth) {
                    val usableWidth = availableWidth - dividerWidthTotal
                    val totalWeight = colMaxChars.map { it.coerceIn(10, 80) }.sum().coerceAtLeast(1)
                    val calculated = (0 until maxCols).map { c ->
                        val weight = colMaxChars[c].coerceIn(10, 80)
                        val proportion = weight.toFloat() / totalWeight
                        (usableWidth * proportion).coerceAtLeast(baseColWidths[c])
                    }
                    val sumCalc = calculated.fold(0.dp) { acc, d -> acc + d }
                    val diff = usableWidth - sumCalc
                    calculated.mapIndexed { idx, d ->
                        if (idx == maxCols - 1) (d + diff).coerceAtLeast(60.dp) else d
                    }
                } else {
                    baseColWidths
                }
            }

            val tableWidth = colWidths.fold(0.dp) { acc, d -> acc + d } + dividerWidthTotal
            val isScrollable = tableWidth > availableWidth
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isScrollable) Modifier.horizontalScroll(scrollState) else Modifier)
            ) {
                Column(
                    modifier = Modifier
                        .width(tableWidth)
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    rows.forEachIndexed { rowIndex, row ->
                        val paddedRow = if (row.size < maxCols) {
                            row + List(maxCols - row.size) { "" }
                        } else {
                            row.take(maxCols)
                        }
                        val isHeader = rowIndex == 0
                        val rowBg = when {
                            isHeader -> AppSurfaceVariant
                            rowIndex % 2 == 1 -> AppCard
                            else -> AppSurface
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            paddedRow.forEachIndexed { colIndex, cell ->
                                val cellUrl = remember(cell) {
                                    val m = MD_LINK_REGEX.find(cell.trim()) ?: Regex("""\b(https?://[^\s)]+)""").find(cell.trim())
                                    if (m != null && m.groupValues.size > 2 && m.groupValues[2].isNotBlank()) m.groupValues[2]
                                    else if (m != null && m.groupValues.size > 1 && m.groupValues[1].startsWith("http")) m.groupValues[1]
                                    else null
                                }
                                Box(
                                    modifier = Modifier
                                        .width(colWidths[colIndex])
                                        .then(if (cellUrl != null && !isHeader) Modifier.clickable {
                                            try { uriHandler.openUri(cellUrl) } catch (_: Exception) {}
                                        } else Modifier)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = remember(cell, codeBg, codeColor, linkColor) {
                                            buildInlineStyledString(cell, codeBg, codeColor, linkColor)
                                        },
                                        fontSize = if (isHeader) 13.5.sp else 13.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isHeader) AppWhite else AppWhite.copy(alpha = 0.92f)
                                    )
                                }
                                if (colIndex < maxCols - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight()
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
private val THINK_OPEN_REGEX = Regex("""<\s*(?:think|thinking|reasoning|plan|reflection)\s*>""", RegexOption.IGNORE_CASE)
private val THINK_CLOSE_REGEX = Regex("""<\s*/\s*(?:think|thinking|reasoning|plan|reflection)\s*>""", RegexOption.IGNORE_CASE)
private val THINK_BRACKET_OPEN_REGEX = Regex("""\[\s*(?:thought|think|thinking|reasoning|plan)\s*\]""", RegexOption.IGNORE_CASE)
private val THINK_BRACKET_CLOSE_REGEX = Regex("""\[\s*/\s*(?:thought|think|thinking|reasoning|plan)\s*\]""", RegexOption.IGNORE_CASE)
private val INNER_THOUGHT_PREFIX_REGEX = Regex(
    """(?is)^(?:\s*(?:thought|thinking|reasoning|internal thoughts?|plan):\s*[^\n]*\n*|\s*(?:the\s+)?user\s+(?:is|wants|asked|said|just)\b[^.!?\n]*[.!?\n]*|\s*i\s+(?:should|will|need\s+to|must)\s+(?:respond|reply|answer|greet|help|ask)\b[^.!?\n]*[.!?\n]*|\s*no\s+tools\s+needed\b[^.!?\n]*[.!?\n]*)+""",
    RegexOption.MULTILINE
)

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

    if (!isUser) {
        remaining = ai.deepcode.android.ui.chat.stripThinkingProcess(remaining, isStreaming = false)
        if (remaining.startsWith("Error: JsonObject\n\n")) {
            remaining = remaining.removePrefix("Error: JsonObject\n\n").trim()
        } else if (remaining.startsWith("Error: JsonObject")) {
            remaining = remaining.removePrefix("Error: JsonObject").trim()
        }
    }

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
        val afterThoughtEnd = afterThought.substringAfter("</thought>", "")
        if (afterThoughtEnd.trim().isNotEmpty()) {
            parts.addAll(parseMessageContent(afterThoughtEnd, isUser))
        }
        return parts
    } else if (remaining.contains("</thought>")) {
        val afterThoughtEnd = remaining.substringAfter("</thought>")
        if (afterThoughtEnd.trim().isNotEmpty()) {
            parts.addAll(parseMessageContent(afterThoughtEnd, isUser))
        }
        return parts
    } else if (content.contains("<thought>") && !content.contains("</thought>")) {
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
    statusText: String = "Creating image"
) {
    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth < 130.dp || maxHeight < 130.dp

        if (isCompact) {
            val infiniteTransition = rememberInfiniteTransition(label = "compact_shimmer")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.75f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF232428).copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF6B6E76),
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            val displayText = if (statusText.isBlank() || statusText.startsWith("Generating image", ignoreCase = true) || statusText.startsWith("Creating image", ignoreCase = true)) {
                "Creating image"
            } else {
                statusText
            }

            val infiniteTransition = rememberInfiniteTransition(label = "image_loading")

            // Ultra-smooth easing curve for the horizontal progress indicator
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "progress"
            )

            // Very subtle ambient breathing for the artwork stack
            val ambientScale by infiniteTransition.animateFloat(
                initialValue = 0.985f,
                targetValue = 1.015f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ambientScale"
            )

            val ambientGlow by infiniteTransition.animateFloat(
                initialValue = 0.75f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ambientGlow"
            )

            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF232428))
                    .border(1.dp, Color(0xFF33353C), RoundedCornerShape(26.dp))
                    .padding(horizontal = 22.dp, vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header text
                    Text(
                        text = displayText,
                        color = Color(0xFFECEEF2),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Center graphic: Fanned photo card stack
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = ambientScale
                                    scaleY = ambientScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Farthest left card
                            Box(
                                modifier = Modifier
                                    .offset(x = (-16).dp)
                                    .size(136.dp, 108.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF26282E).copy(alpha = 0.28f))
                            )
                            // Farthest right card
                            Box(
                                modifier = Modifier
                                    .offset(x = 16.dp)
                                    .size(136.dp, 108.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF26282E).copy(alpha = 0.28f))
                            )
                            // Middle left card
                            Box(
                                modifier = Modifier
                                    .offset(x = (-8).dp)
                                    .size(140.dp, 113.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(Color(0xFF2A2C33).copy(alpha = 0.55f))
                            )
                            // Middle right card
                            Box(
                                modifier = Modifier
                                    .offset(x = 8.dp)
                                    .size(140.dp, 113.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(Color(0xFF2A2C33).copy(alpha = 0.55f))
                            )
                            // Front center photo card
                            Box(
                                modifier = Modifier
                                    .size(144.dp, 118.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF2E3037))
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // Sun circle in upper-left
                                    val sunRadius = w * 0.082f
                                    val sunCenter = Offset(w * 0.31f, h * 0.32f)
                                    drawCircle(
                                        color = Color(0xFF63666E).copy(alpha = ambientGlow),
                                        radius = sunRadius,
                                        center = sunCenter
                                    )

                                    // Left mountain (smaller / background peak)
                                    val leftPath = Path().apply {
                                        moveTo(-w * 0.05f, h)
                                        lineTo(w * 0.28f, h * 0.56f)
                                        quadraticTo(w * 0.32f, h * 0.52f, w * 0.36f, h * 0.56f)
                                        lineTo(w * 0.76f, h)
                                        close()
                                    }
                                    drawPath(leftPath, color = Color(0xFF3F4249))

                                    // Right mountain (taller / foreground peak)
                                    val rightPath = Path().apply {
                                        moveTo(w * 0.22f, h)
                                        lineTo(w * 0.60f, h * 0.44f)
                                        quadraticTo(w * 0.64f, h * 0.40f, w * 0.68f, h * 0.44f)
                                        lineTo(w * 1.05f, h)
                                        close()
                                    }
                                    drawPath(rightPath, color = Color(0xFF4C4F57))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Horizontal loading bar underneath
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val barWidth = 160.dp
                        val barHeight = 4.5.dp
                        val thumbWidth = 52.dp

                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(barHeight)
                                .clip(CircleShape)
                                .background(Color(0xFF383A41))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(thumbWidth)
                                    .offset(x = (barWidth - thumbWidth) * progress)
                                    .clip(CircleShape)
                                    .background(Color(0xFFD1D5DB).copy(alpha = 0.88f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
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
    var retryKey by remember(imageUrl) { mutableIntStateOf(0) }

    LaunchedEffect(imageUrl, retryKey) {
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

    val (widthDp, heightDp) = remember(bitmap, maxDimension, isError) {
        if (bitmap != null && bitmap!!.width > 0 && bitmap!!.height > 0) {
            val w = bitmap!!.width.toFloat()
            val h = bitmap!!.height.toFloat()
            if (w >= h) {
                maxDimension to (maxDimension * (h / w))
            } else {
                (maxDimension * (w / h)) to maxDimension
            }
        } else if (isError) {
            minOf(maxDimension, 220.dp) to 48.dp
        } else {
            maxDimension to minOf(maxDimension, 160.dp)
        }
    }

    Box(
        modifier = modifier
            .then(if (!fillContainer) Modifier.size(width = widthDp, height = heightDp) else Modifier)
            .then(if (!fillContainer) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier)
            .background(if (fillContainer) Color.Black else Color(0xFF1E202B)),
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
                    .then(if (!fillContainer) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier)
            )
        } else {
            Row(
                modifier = Modifier
                    .clickable { retryKey++ }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color(0xFFF5A623),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Image failed • Tap to retry",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun FullScreenImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit,
    onSendSuggestion: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var activeActionDialog by remember { mutableStateOf<String?>(null) }
    var commentText by remember { mutableStateOf("") }
    var eraseText by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
                window.setDimAmount(0f)
            }
            onDispose {}
        }

        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Centered Image with Fit scale
                AuthenticatedImageView(
                    imageUrl = imageUrl,
                    maxDimension = 400.dp,
                    fillContainer = true,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls: Close button (left) and Download button (right), with Floating Pill Toolbar placed little lower
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp, start = 14.dp, end = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var isDownloading by remember { mutableStateOf(false) }

                    // Row with Close button (left) and Download button (right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Preview",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (!isDownloading) {
                                    isDownloading = true
                                    Toast.makeText(context, "Downloading full resolution image...", Toast.LENGTH_SHORT).show()
                                    saveImageToDeviceGallery(context, imageUrl) { success ->
                                        isDownloading = false
                                        if (success) {
                                            Toast.makeText(context, "Saved full resolution image to Pictures/DeepCode", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Image",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Floating Pill Toolbar placed lower than both buttons
                    FullScreenImageActionPill(
                        onComment = { activeActionDialog = "comment" },
                        onRemoveBg = {
                            onSendSuggestion?.invoke("Remove background from this image [image:$imageUrl]")
                            Toast.makeText(context, "Requesting background removal with ChatGPT...", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        onErase = { activeActionDialog = "erase" },
                        onResize = { activeActionDialog = "resize" }
                    )
                }

                // Sub-dialogs
                if (activeActionDialog == "comment") {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        containerColor = Color(0xFF1C1C1E),
                        titleContentColor = Color.White,
                        textContentColor = Color.White.copy(alpha = 0.8f),
                        title = { Text("Comment on Image", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Describe any changes or additions you'd like ChatGPT to make to this image:",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    placeholder = { Text("e.g. Add party hat, make background sunset...", fontSize = 13.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    maxLines = 3
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (commentText.isNotBlank()) {
                                        onSendSuggestion?.invoke("For this image: ${commentText.trim()} [image:$imageUrl]")
                                        activeActionDialog = null
                                        onDismiss()
                                    }
                                }
                            ) {
                                Text("Send", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Cancel", color = Color.Gray)
                            }
                        }
                    )
                }

                if (activeActionDialog == "erase") {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        containerColor = Color(0xFF1C1C1E),
                        titleContentColor = Color.White,
                        textContentColor = Color.White.copy(alpha = 0.8f),
                        title = { Text("Erase Object", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "What item or part would you like to erase from this image?",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = eraseText,
                                    onValueChange = { eraseText = it },
                                    placeholder = { Text("e.g. leash, collar, person in background...", fontSize = 13.sp, color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                    ),
                                    maxLines = 2
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (eraseText.isNotBlank()) {
                                        onSendSuggestion?.invoke("Erase the ${eraseText.trim()} from this image [image:$imageUrl]")
                                        activeActionDialog = null
                                        onDismiss()
                                    }
                                }
                            ) {
                                Text("Erase", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Cancel", color = Color.Gray)
                            }
                        }
                    )
                }

                if (activeActionDialog == "resize") {
                    AlertDialog(
                        onDismissRequest = { activeActionDialog = null },
                        containerColor = Color(0xFF1C1C1E),
                        titleContentColor = Color.White,
                        textContentColor = Color.White.copy(alpha = 0.8f),
                        title = { Text("Resize Aspect Ratio", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val ratios = listOf(
                                    "1:1 (Square)" to "1:1 square",
                                    "16:9 (Landscape)" to "16:9 landscape",
                                    "9:16 (Portrait / Story)" to "9:16 vertical",
                                    "4:3 (Standard)" to "4:3 aspect ratio"
                                )
                                ratios.forEach { (label, ratioVal) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2C2C2E))
                                            .clickable {
                                                onSendSuggestion?.invoke("Resize and reframe this image into $ratioVal [image:$imageUrl]")
                                                activeActionDialog = null
                                                onDismiss()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(label, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { activeActionDialog = null }) {
                                Text("Cancel", color = Color.Gray)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreenImageActionPill(
    onComment: () -> Unit,
    onRemoveBg: () -> Unit,
    onErase: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xEB1C1C1E),
        border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillActionButton(
                icon = { CommentPillIcon() },
                label = "Comment",
                onClick = onComment
            )

            PillActionButton(
                icon = { RemoveBgPillIcon() },
                label = "Remove BG",
                onClick = onRemoveBg
            )

            PillActionButton(
                icon = { ErasePillIcon() },
                label = "Erase",
                onClick = onErase
            )

            PillActionButton(
                icon = { ResizePillIcon() },
                label = "Resize",
                onClick = onResize
            )
        }
    }
}

@Composable
private fun PillActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon()
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.SansSerif
        )
    }
}

@Composable
fun CommentPillIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        val r = w * 0.42f
        val cx = w * 0.46f
        val cy = h * 0.46f
        drawCircle(
            color = Color.White,
            radius = r,
            center = androidx.compose.ui.geometry.Offset(cx, cy),
            style = Stroke(width = stroke)
        )

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx + r * 0.6f, cy + r * 0.7f)
            lineTo(w * 0.95f, h * 0.95f)
            lineTo(cx + r * 0.85f, cy + r * 0.35f)
        }
        drawPath(path, color = Color.White, style = Stroke(width = stroke))

        val len = r * 0.52f
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(cx - len, cy),
            end = androidx.compose.ui.geometry.Offset(cx + len, cy),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(cx, cy - len),
            end = androidx.compose.ui.geometry.Offset(cx, cy + len),
            strokeWidth = stroke
        )
    }
}

@Composable
fun RemoveBgPillIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(w - stroke, h - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )

        drawCircle(
            color = Color.White,
            radius = w * 0.22f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke)
        )

        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.32f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.12f),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.12f),
            end = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.32f),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.68f),
            end = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.88f),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.88f),
            end = androidx.compose.ui.geometry.Offset(w * 0.88f, h * 0.68f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun ErasePillIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        withTransform({
            rotate(45f, pivot = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.5f))
        }) {
            drawRoundRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.12f),
                size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.76f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.58f),
                end = androidx.compose.ui.geometry.Offset(w * 0.76f, h * 0.58f),
                strokeWidth = stroke
            )
        }
    }
}

@Composable
fun ResizePillIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.3.dp.toPx()

        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )

        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.65f, stroke / 2),
            end = androidx.compose.ui.geometry.Offset(w - stroke / 2, stroke / 2),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w - stroke / 2, stroke / 2),
            end = androidx.compose.ui.geometry.Offset(w - stroke / 2, h * 0.35f),
            strokeWidth = stroke
        )

        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(stroke / 2, h * 0.65f),
            end = androidx.compose.ui.geometry.Offset(stroke / 2, h - stroke / 2),
            strokeWidth = stroke
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(stroke / 2, h - stroke / 2),
            end = androidx.compose.ui.geometry.Offset(w * 0.35f, h - stroke / 2),
            strokeWidth = stroke
        )
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
            val req = okhttp3.Request.Builder()
                .url(imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
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

fun saveImageToDeviceGallery(context: Context, imageUrl: String, onComplete: (Boolean) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        var success = false
        try {
            val pair: Pair<ByteArray?, String> = when {
                imageUrl.startsWith("file://") -> {
                    val f = java.io.File(imageUrl.removePrefix("file://"))
                    if (f.exists()) f.readBytes() to (f.extension.ifBlank { "png" }) else null to "png"
                }
                imageUrl.startsWith("/") -> {
                    val f = java.io.File(imageUrl)
                    if (f.exists()) f.readBytes() to (f.extension.ifBlank { "png" }) else null to "png"
                }
                imageUrl.startsWith("data:image/") -> {
                    val base64Data = imageUrl.substringAfter("base64,")
                    val decoded = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    val mime = imageUrl.substringAfter("data:image/").substringBefore(";")
                    decoded to (mime.ifBlank { "png" })
                }
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                    val req = okhttp3.Request.Builder()
                        .url(imageUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                        .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .build()
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                    val data = client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.bytes() else null
                    }
                    val ext = imageUrl.substringAfterLast(".", "png").substringBefore("?").takeIf { it.length in 3..5 } ?: "png"
                    data to ext
                }
                else -> null to "png"
            }

            val (bytes, ext) = pair
            if (bytes != null && bytes.isNotEmpty()) {
                val uri = insertImageBytesToMediaStore(context, bytes, ext)
                success = uri != null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        }
        withContext(Dispatchers.Main) {
            onComplete(success)
        }
    }
}

private fun insertImageBytesToMediaStore(context: Context, bytes: ByteArray, ext: String): String? {
    val safeExt = if (ext.startsWith(".")) ext else ".$ext"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "DeepCode_${System.currentTimeMillis()}$safeExt")
        put(MediaStore.Images.Media.MIME_TYPE, "image/${ext.lowercase().replace("jpg", "jpeg")}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DeepCode")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DeepCode")
            if (!dir.exists()) dir.mkdirs()
            put(MediaStore.Images.Media.DATA, java.io.File(dir, "DeepCode_${System.currentTimeMillis()}$safeExt").absolutePath)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
        uri.toString()
    } catch (e: Exception) {
        context.contentResolver.delete(uri, null, null)
        null
    }
}
