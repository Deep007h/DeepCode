package ai.deepcode.android.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.domain.model.Message
import ai.deepcode.android.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin


/**
 * High-performance, transitions.dev-inspired motion components for OpenCode's in-app chat:
 * - AnimatedThinkingPill (breathing ambient aura + sliding status swap + wave dots)
 * - TravelingWaveLoader (smooth sine-wave dot displacement)
 * - ReasoningAccordion (fluid height morph + spring chevron rotation)
 * - StreamingActiveCursor (energetic glowing pulse)
 */

/**
 * Context-aware dynamic thinking steps that change every 2 seconds.
 * Cycles through job-related engineering actions based on the active agent or engine.
 */
fun getDynamicThinkingSteps(engineBadge: String?): List<String> {
    val isDeepSeek = engineBadge?.contains("DeepSeek", ignoreCase = true) == true
    val isClaude = engineBadge?.contains("Claude", ignoreCase = true) == true
    val isAntigravity = engineBadge?.contains("Antigravity", ignoreCase = true) == true
    val isHermes = engineBadge?.contains("Hermes", ignoreCase = true) == true

    return when {
        isHermes -> listOf(
            "Analyzing prompt & repository directives...",
            "Consulting persistent memory & active skills...",
            "Formulating autonomous execution plan...",
            "Engaging Hermes toolchain in workspace...",
            "Synthesizing optimal code solution...",
            "Verifying output integrity & syntax..."
        )
        isDeepSeek -> listOf(
            "Analyzing prompt & repository context...",
            "Initializing DeepSeek Harness agent...",
            "Inspecting project structure & files...",
            "Formulating autonomous execution plan...",
            "Evaluating toolchain & commands...",
            "Synthesizing optimal code solution...",
            "Verifying syntax & best practices...",
            "Structuring finalized response..."
        )
        isClaude -> listOf(
            "Analyzing requirements & context...",
            "Initializing Claude Code agent...",
            "Inspecting workspace codebase...",
            "Formulating tool actions & edits...",
            "Synthesizing verified solution...",
            "Formatting finalized response..."
        )
        isAntigravity -> listOf(
            "Engaging Antigravity reasoning engine...",
            "Analyzing query intent & constraints...",
            "Exploring codebase dependencies...",
            "Constructing execution roadmap...",
            "Synthesizing optimal solution...",
            "Structuring finalized output..."
        )
        else -> listOf(
            "Analyzing requirements & intent...",
            "Inspecting project context...",
            "Formulating solution architecture...",
            "Evaluating edge cases & logic...",
            "Synthesizing code & explanation...",
            "Validating syntax & type safety...",
            "Refining implementation details...",
            "Structuring clean response..."
        )
    }
}

/**
 * Modern, dynamic thinking card for in-app chat.
 * Cycles through job-related engineering actions every 2 seconds during the thinking phase.
 * Perfectly integrates with DeepCode's UI design system (AppSurface, AppBorder, AppPrimary).
 */
@Composable
fun AnimatedThinkingCard(
    engineBadge: String? = null,
    explicitStatus: String? = null,
    accentColor: Color = AppPrimary,
    modifier: Modifier = Modifier
) {
    val dynamicSteps = remember(engineBadge) {
        getDynamicThinkingSteps(engineBadge)
    }

    var stepIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(dynamicSteps, explicitStatus) {
        if (explicitStatus == null) {
            stepIndex = 0
            while (isActive) {
                delay(2000L)
                stepIndex = (stepIndex + 1) % dynamicSteps.size
            }
        }
    }

    val displayStatus = explicitStatus ?: dynamicSteps[stepIndex % dynamicSteps.size]

    val effectiveAccent = remember(engineBadge, accentColor) {
        when {
            engineBadge?.contains("Hermes", ignoreCase = true) == true -> Color(0xFF10B981)
            engineBadge?.contains("DeepSeek", ignoreCase = true) == true -> Color(0xFFFF6D00)
            engineBadge?.contains("Claude", ignoreCase = true) == true -> Color(0xFFD97706)
            engineBadge?.contains("Antigravity", ignoreCase = true) == true -> Color(0xFF3B82F6)
            engineBadge?.contains("Fallback", ignoreCase = true) == true -> Color(0xFFEF4444)
            else -> accentColor
        }
    }

    val headerLabel = remember(engineBadge, explicitStatus) {
        when {
            explicitStatus != null -> "PROCESSING TASK"
            engineBadge?.contains("Hermes", ignoreCase = true) == true -> "THINKING · HERMES AGENT"
            engineBadge?.contains("DeepSeek", ignoreCase = true) == true -> "THINKING · DEEPSEEK HARNESS"
            engineBadge?.contains("Claude", ignoreCase = true) == true -> "THINKING · CLAUDE CODE"
            engineBadge?.contains("Antigravity", ignoreCase = true) == true -> "THINKING · ANTIGRAVITY"
            engineBadge?.contains("Direct Engine", ignoreCase = true) == true -> "THINKING · DIRECT ENGINE"
            !engineBadge.isNullOrBlank() -> "THINKING · ${engineBadge.substringBefore("·").trim().uppercase()}"
            else -> "THINKING"
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingCardTransition")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = MotionTokens.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LiveDotAlpha"
    )

    val isDark = isDarkThemeActive
    val cardBg = if (isDark) AppSurface else Color.White
    val borderBrush = remember(glowAlpha, effectiveAccent, isDark) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    effectiveAccent.copy(alpha = glowAlpha * 0.40f),
                    AppBorder.copy(alpha = 0.75f),
                    effectiveAccent.copy(alpha = glowAlpha * 0.15f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    effectiveAccent.copy(alpha = glowAlpha * 0.50f),
                    AppBorder,
                    effectiveAccent.copy(alpha = glowAlpha * 0.20f)
                )
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 1.5.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color(0x12000000),
                ambientColor = Color(0x06000000)
            ),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderBrush)
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            effectiveAccent.copy(alpha = if (isDark) 0.08f else 0.04f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Leading animated sparkle icon container
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(effectiveAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                BreathingSparkleIcon(tint = effectiveAccent, modifier = Modifier.size(17.dp))
            }

            // Center content: Micro-header with pulsing dot + Dynamic job step
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(effectiveAccent.copy(alpha = liveDotAlpha))
                    )
                    Text(
                        text = headerLabel,
                        color = effectiveAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                // Smooth vertical slide & fade text swap between job steps
                AnimatedContent(
                    targetState = displayStatus,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EaseOutCubic)
                        ) { height -> height / 2 } + fadeIn(
                            animationSpec = tween(MotionTokens.DurationFast)
                        )).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(MotionTokens.DurationMicro, easing = MotionTokens.EaseInOut)
                            ) { height -> -height / 2 } + fadeOut(
                                animationSpec = tween(MotionTokens.DurationMicro)
                            )
                        )
                    },
                    label = "ThinkingStepSwap"
                ) { targetText ->
                    Text(
                        text = targetText,
                        color = if (isDark) AppWhite.copy(alpha = 0.95f) else Color(0xFF1F2937),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing animated wave indicator
            TravelingWaveLoader(dotColor = effectiveAccent)
        }
    }
}

/**
 * Backwards compatibility wrapper for AnimatedThinkingPill,
 * delegating to AnimatedThinkingCard.
 */
@Composable
fun AnimatedThinkingPill(
    statusText: String,
    accentColor: Color = AppPrimary,
    modifier: Modifier = Modifier
) {
    AnimatedThinkingCard(
        explicitStatus = statusText,
        accentColor = accentColor,
        modifier = modifier
    )
}

/**
 * Organic breathing sparkle / brain icon with subtle scale and micro-rotation.
 */
@Composable
fun BreathingSparkleIcon(
    tint: Color = AppPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SparkleBreath")

    val scale = infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleScale"
    )

    val rotation = infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = MotionTokens.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleRotate"
    )

    Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = "Thinking",
        tint = tint,
        modifier = modifier
            .size(18.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            }
    )
}

/**
 * Traveling sine-wave 3-dot loader (transitions.dev P33 Matrix dot loader principle).
 * Calculates continuous smooth vertical wave displacements using a phase-delayed sine function.
 */
@Composable
fun TravelingWaveLoader(
    dotColor: Color = AppPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhase")
    val phase = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SineWaveProgress"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .graphicsLayer {
                        val currentPhase = phase.value
                        val waveOffset = sin(currentPhase - (i * Math.PI / 2.5)).toFloat()
                        this.translationY = -3.5f * ((waveOffset + 1f) / 2f)
                        this.alpha = 0.40f + (0.60f * ((waveOffset + 1f) / 2f))
                    }
                    .background(dotColor, CircleShape)
            )
        }
    }
}

/**
 * Energetic glowing trailing cursor for streaming responses (transitions.dev P30 Streaming text).
 * Remains steadily illuminated with a warm aura while active, with subtle breathing rhythm.
 */
@Composable
fun StreamingActiveCursor(
    color: Color = Color(0xFFFF6D00),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "StreamingCursorPulse")

    val auraScale = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    val cursorAlpha = infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CursorAlpha"
    )

    Box(
        modifier = modifier
            .padding(start = 3.dp, bottom = 3.dp)
            .size(width = 8.dp, height = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Soft outer glow halo
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 16.dp)
                .graphicsLayer {
                    val s = auraScale.value
                    val a = cursorAlpha.value
                    scaleX = s * 1.6f
                    scaleY = s * 1.3f
                    alpha = a * 0.35f
                }
                .background(color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        )
        // Solid core cursor line
        Box(
            modifier = Modifier
                .width(2.2.dp)
                .height(16.dp)
                .graphicsLayer {
                    alpha = cursorAlpha.value
                }
                .background(color, RoundedCornerShape(1.dp))
        )
    }
}

/**
 * Smooth Reasoning Stream Accordion (transitions.dev P21 Accordion & P28 Reasoning stream).
 * Features butter-smooth height interpolation and spring-rotated disclosure chevron.
 */
@Composable
fun ReasoningAccordion(
    thought: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isLiveStreaming: Boolean = false,
    accentColor: Color = Color(0xFFFF6D00)
) {
    val chevronRotation = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MotionTokens.SnappySpring,
        label = "AccordionChevron"
    )

    // Only run a pulse loop while actually live-streaming. Reading is deferred
    // to draw phase so composition does not thrash at 60/120fps.
    val railAlphaState = if (isLiveStreaming) {
        rememberInfiniteTransition(label = "RailPulse").animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = MotionTokens.EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LiveRailAlpha"
        )
    } else {
        null
    }

    val isDark = isDarkThemeActive
    val accordionBg = if (isDark) {
        if (isLiveStreaming) Color(0xFF13151A) else Color(0xFF16181F)
    } else {
        if (isLiveStreaming) AppCard else AppSurfaceVariant.copy(alpha = 0.7f)
    }
    val accordionBorder = if (isLiveStreaming) {
        accentColor.copy(alpha = if (isDark) 0.4f else 0.7f)
    } else {
        if (isDark) Color(0xFF262933) else AppBorder
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = if (isDark) 0.dp else 1.5.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = Color(0x14000000),
                ambientColor = Color(0x06000000)
            )
            .border(
                width = if (isLiveStreaming) 1.dp else 0.5.dp,
                color = accordionBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = MotionTokens.DurationNormal,
                    easing = MotionTokens.EaseInOut
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = accordionBg
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isLiveStreaming) "Live Thought Process" else "Thought Process",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isLiveStreaming) accentColor else AppMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                if (isLiveStreaming) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = railAlphaState?.value ?: 1f }
                            .background(accentColor, CircleShape)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand thought",
                    tint = AppMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = chevronRotation.value
                        }
                )
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left vertical intelligence rail
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .graphicsLayer { alpha = railAlphaState?.value ?: 1f }
                            .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = thought.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFD1D1D6) else AppWhite.copy(alpha = 0.85f),
                        fontStyle = FontStyle.Italic,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

/**
 * ════════════════════════════════════════════════════════════════
 * Telegram-Style Swipe-to-Reply & Reply Threading Components
 * ════════════════════════════════════════════════════════════════
 */

/**
 * Cleans thinking processes, tool tags, and formatting from message content
 * to produce a crisp single-line snippet for reply preview and quote headers.
 */
fun cleanSnippetForReply(content: String): String {
    var text = content.trim()
    while (text.startsWith("[reply", ignoreCase = true)) {
        val next = text.replace(Regex("""^\[reply[\s\S]*?\[/reply\]\s*""", RegexOption.IGNORE_CASE), "").trim()
        if (next == text) break
        text = next
    }
    text = stripThinkingProcess(text, isStreaming = false)
    if (text.contains("[image:")) {
        val count = Regex("""\[image:[^\]]+\]""").findAll(text).count()
        text = text.replace(Regex("""\[image:[^\]]+\]"""), "").trim()
        if (text.isBlank()) return if (count > 1) "📷 $count Photos" else "📷 Photo"
    }
    if (text.contains("[file:")) {
        val fileName = Regex("""\[file:([^\]]+)\]""").find(text)?.groupValues?.getOrNull(1)?.substringAfterLast("/") ?: "Document"
        text = text.replace(Regex("""\[file:[^\]]+\]"""), "").trim()
        if (text.isBlank()) return "📎 $fileName"
    }
    if (text.contains("[audio:")) {
        return "🎵 Audio message"
    }
    if (text.contains("[video:")) {
        return "🎬 Video"
    }
    // Remove markdown code fences
    text = text.replace(Regex("""```[a-zA-Z0-9]*\n?"""), "").replace("```", "")
    // Neutralize any nested reply closing tags and quotes so it cannot corrupt outer tags
    text = text.replace("[/reply]", "[reply]").replace("\"", "'")
    // Normalize spaces and newlines
    text = text.replace(Regex("""\s+"""), " ").trim()
    return if (text.length > 100) text.take(100) + "…" else text
}

data class ReplyHeaderInfo(
    val author: String,
    val targetMessageId: String?,
    val snippet: String,
    val cleanBody: String
)

private val RE_REPLY_TAG = Regex("""^\[reply\s+author="([^"]*)"(?:\s+id="([^"]*)")?\]([\s\S]*?)\[/reply\]\s*""", RegexOption.IGNORE_CASE)

fun parseReplyHeader(content: String): ReplyHeaderInfo? {
    val match = RE_REPLY_TAG.find(content) ?: return null
    val author = match.groupValues[1].ifEmpty { "Message" }
    val id = match.groupValues[2].takeIf { it.isNotEmpty() }
    val snippet = match.groupValues[3].trim()
    val cleanBody = content.substring(match.range.last + 1).trimStart()
    return ReplyHeaderInfo(author, id, snippet, cleanBody)
}

/**
 * Container that detects a Telegram-style swipe-left gesture on a message.
 * When swiped past threshold (~60dp), vibrates with haptic feedback and triggers onReply upon release.
 */
@Composable
fun SwipeToReplyContainer(
    message: Message,
    enabled: Boolean = true,
    onReply: (Message) -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled || message.isToolCall || message.role == "tool") {
        content()
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val thresholdDp = 60.dp
    val maxDragDp = 90.dp
    val thresholdPx = with(density) { thresholdDp.toPx() }
    val maxDragPx = with(density) { maxDragDp.toPx() }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var hasHapticFired by remember { mutableStateOf(false) }

    val isDark = isDarkThemeActive
    val progress = (-dragOffsetX / thresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        hasHapticFired = false
                    },
                    onDragEnd = {
                        val triggered = dragOffsetX <= -thresholdPx
                        val startVal = dragOffsetX
                        coroutineScope.launch {
                            if (triggered) {
                                onReply(message)
                            }
                            Animatable(startVal).animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) {
                                dragOffsetX = value
                            }
                        }
                    },
                    onDragCancel = {
                        val startVal = dragOffsetX
                        coroutineScope.launch {
                            Animatable(startVal).animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) {
                                dragOffsetX = value
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // Only swipe to the left: dragAmount < 0
                        val next = (dragOffsetX + dragAmount).coerceIn(-maxDragPx, 0f)
                        if (next != dragOffsetX) {
                            change.consume()
                            dragOffsetX = next
                            if (next <= -thresholdPx && !hasHapticFired) {
                                hasHapticFired = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (next > -thresholdPx && hasHapticFired) {
                                hasHapticFired = false
                            }
                        }
                    }
                )
            }
    ) {
        // Behind: Telegram-style animated reply arrow indicator
        if (dragOffsetX < -2f) {
            val iconScale = 0.4f + 0.6f * progress
            val bgAlpha = if (progress >= 1f) 1f else 0.45f * progress
            val circleSize = (36 + 4 * progress).dp
            val rightPadding = (12 + (maxDragPx + dragOffsetX) / 8).coerceAtLeast(8f).dp

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = rightPadding)
                    .size(circleSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        alpha = progress.coerceIn(0.25f, 1f)
                    }
                    .clip(CircleShape)
                    .background(
                        if (progress >= 1f) AppPrimary
                        else if (isDark) AppPrimary.copy(alpha = 0.55f * bgAlpha)
                        else AppPrimary.copy(alpha = 0.35f * bgAlpha)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Foreground: the message bubble sliding horizontally via GPU layer (no relayout jitter)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragOffsetX }
        ) {
            content()
        }
    }
}

/**
 * Floating Telegram-style Reply Preview Bar placed directly above the bottom input bar.
 */
@Composable
fun ReplyPreviewBar(
    replyMessage: Message,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkThemeActive
    val author = if (replyMessage.role == "user") "You" else "DeepCode"
    val snippet = remember(replyMessage.content) { cleanSnippetForReply(replyMessage.content) }

    val bg = if (isDark) Color(0xFF1E2028) else Color(0xFFF2F3F7)
    val border = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppPrimary)
        )

        Spacer(Modifier.width(10.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = AppPrimary,
            modifier = Modifier.size(18.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Replying to $author",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppPrimary,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = snippet,
                fontSize = 14.sp,
                color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel reply",
                tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Quoted Reply Header rendered inside the message bubble, linking back to the original message.
 */
@Composable
fun QuotedReplyHeader(
    author: String,
    snippet: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkThemeActive
    val bg = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 5.dp, horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(AppPrimary)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    tint = AppPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = author,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppPrimary,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = snippet,
                fontSize = 13.5.sp,
                color = if (isDark) AppWhite.copy(alpha = 0.85f) else Color(0xFF1F2937),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Engine indicator badge showing which autonomous harness agent or engine generated an assistant reply.
 * Distinguishes PRoot autonomous agents (DeepSeek Harness, Claude Code, Antigravity) from Direct In-App Engine
 * and highlights any fallback transitions.
 */
@Composable
fun EngineIndicatorBadge(
    engineName: String,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkThemeActive
    val isPRoot = engineName.contains("PRoot", ignoreCase = true) ||
            engineName.contains("DeepSeek", ignoreCase = true) ||
            engineName.contains("Claude", ignoreCase = true) ||
            engineName.contains("Antigravity", ignoreCase = true) ||
            engineName.contains("Hermes", ignoreCase = true)
    val isFallback = engineName.contains("Fallback", ignoreCase = true)

    val (icon, tint) = when {
        isFallback -> "⚠️" to Color(0xFFEF4444)
        engineName.contains("Hermes", ignoreCase = true) -> "🪽" to Color(0xFF10B981)
        engineName.contains("DeepSeek", ignoreCase = true) -> "⚡" to Color(0xFFFF6D00)
        engineName.contains("Claude", ignoreCase = true) -> "✳️" to Color(0xFFD97706)
        engineName.contains("Antigravity", ignoreCase = true) -> "🚀" to Color(0xFF3B82F6)
        else -> "🤖" to AppPrimary
    }

    val bgColor = if (isDark) {
        tint.copy(alpha = 0.12f)
    } else {
        tint.copy(alpha = 0.08f)
    }
    val borderColor = tint.copy(alpha = 0.35f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(0.8.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = icon,
                fontSize = 11.sp
            )
            Text(
                text = engineName,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) tint.copy(alpha = 0.95f) else tint,
                letterSpacing = 0.2.sp
            )
            if (isPRoot && !isFallback) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            } else if (isFallback) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }
    }
}

