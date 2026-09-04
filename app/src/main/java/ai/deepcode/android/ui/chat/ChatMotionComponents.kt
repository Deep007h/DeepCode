package ai.deepcode.android.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.ui.theme.MotionTokens
import ai.deepcode.android.ui.theme.bouncyClickable
import kotlin.math.sin

/**
 * High-performance, transitions.dev-inspired motion components for OpenCode's in-app chat:
 * - AnimatedThinkingPill (breathing ambient aura + sliding status swap + wave dots)
 * - TravelingWaveLoader (smooth sine-wave dot displacement)
 * - ReasoningAccordion (fluid height morph + spring chevron rotation)
 * - StreamingActiveCursor (energetic glowing pulse)
 */

/**
 * Ambient breathing thinking pill (transitions.dev P28 Thinking states).
 * Renders an illuminated breathing border with smooth text state morphing.
 */
@Composable
fun AnimatedThinkingPill(
    statusText: String,
    accentColor: Color = Color(0xFFFF6D00),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThinkingPillAura")

    // Ambient glow pulse between 0.35f and 0.85f.
    // NOTE: single infinite animation only. The previous second sweepOffset
    // animation (600px linear sweep) forced a Brush.linearGradient recreation
    // on every frame -> recomposition at 60fps + shader recompile = jank.
    // A static alpha-pulsed border is visually identical and ~free.
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MotionTokens.DurationBreathing, easing = MotionTokens.EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val borderBrush = remember(glowAlpha, accentColor) {
        Brush.linearGradient(
            colors = listOf(
                accentColor.copy(alpha = glowAlpha * 0.4f),
                accentColor.copy(alpha = glowAlpha),
                accentColor.copy(alpha = glowAlpha * 0.2f)
            )
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13151A))
            .border(1.2.dp, borderBrush, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BreathingSparkleIcon(tint = accentColor)

        // Smooth vertical slide & fade text swap between states
        AnimatedContent(
            targetState = statusText,
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
            label = "ThinkingTextSwap"
        ) { targetText ->
            Text(
                text = targetText,
                color = accentColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }

        Spacer(Modifier.width(2.dp))
        TravelingWaveLoader(dotColor = accentColor)
    }
}

/**
 * Organic breathing sparkle / brain icon with subtle scale and micro-rotation.
 */
@Composable
fun BreathingSparkleIcon(
    tint: Color = Color(0xFFFF6D00),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SparkleBreath")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleScale"
    )

    val rotation by infiniteTransition.animateFloat(
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
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
    )
}

/**
 * Traveling sine-wave 3-dot loader (transitions.dev P33 Matrix dot loader principle).
 * Calculates continuous smooth vertical wave displacements using a phase-delayed sine function.
 */
@Composable
fun TravelingWaveLoader(
    dotColor: Color = Color(0xFFFF6D00),
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WavePhase")
    val phase by infiniteTransition.animateFloat(
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
            val waveOffset = sin(phase - (i * Math.PI / 2.5)).toFloat()
            val normalizedAlpha = 0.40f + (0.60f * ((waveOffset + 1f) / 2f))
            val translationY = -3.5f * ((waveOffset + 1f) / 2f)

            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .graphicsLayer {
                        this.translationY = translationY
                        this.alpha = normalizedAlpha
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

    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    val cursorAlpha by infiniteTransition.animateFloat(
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
                    scaleX = auraScale * 1.6f
                    scaleY = auraScale * 1.3f
                    alpha = cursorAlpha * 0.35f
                }
                .background(color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        )
        // Solid core cursor line
        Box(
            modifier = Modifier
                .width(2.2.dp)
                .height(16.dp)
                .graphicsLayer {
                    alpha = cursorAlpha
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
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = MotionTokens.SnappySpring,
        label = "AccordionChevron"
    )

    // Only run a pulse loop while actually live-streaming. Previously this
    // rememberInfiniteTransition ran unconditionally (even with identical
    // 1.0f->1.0f bounds), keeping a choreographer callback alive for every
    // collapsed thought card in history.
    val railAlpha: Float = if (isLiveStreaming) {
        rememberInfiniteTransition(label = "RailPulse").animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = MotionTokens.EaseInOut),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LiveRailAlpha"
        ).value
    } else {
        1f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (isLiveStreaming) 1.dp else 0.5.dp,
                color = if (isLiveStreaming) accentColor.copy(alpha = 0.4f) else Color(0xFF262933),
                shape = RoundedCornerShape(12.dp)
            )
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = MotionTokens.DurationNormal,
                    easing = MotionTokens.EaseInOut
                )
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLiveStreaming) Color(0xFF13151A) else Color(0xFF16181F)
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
                    color = if (isLiveStreaming) accentColor else Color(0xFF9E9EA7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                if (isLiveStreaming) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer { alpha = railAlpha }
                            .background(accentColor, CircleShape)
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand thought",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            rotationZ = chevronRotation
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
                            .graphicsLayer { alpha = railAlpha }
                            .background(accentColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = thought.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD1D1D6),
                        fontStyle = FontStyle.Italic,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}
