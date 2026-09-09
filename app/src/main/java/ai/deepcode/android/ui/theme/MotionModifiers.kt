package ai.deepcode.android.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Reusable motion modifiers based on transitions.dev micro-interactions,
 * optimized to execute in Jetpack Compose's Draw phase for 60/120fps smoothness.
 */

/**
 * Tactile bouncy press interaction (transitions.dev button scale).
 * Compresses slightly to 0.95f on down-press and springs back smoothly on release.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    provideHaptic: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = MotionTokens.SnappySpring,
        label = "BouncyScale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && provideHaptic) {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Error state shake animation (transitions.dev P12).
 * Shakes horizontally using a damped sine wave keyframe sequence whenever [trigger] becomes true.
 */
fun Modifier.shakeOnError(trigger: Boolean): Modifier = composed {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger) {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 380
                    0f at 0
                    (-12f) at 50
                    12f at 100
                    (-8f) at 160
                    8f at 220
                    (-4f) at 280
                    4f at 340
                    0f at 380
                }
            )
        }
    }

    this.graphicsLayer {
        translationX = shakeOffset.value
    }
}

/**
 * High-performance GPU-accelerated gradient shimmer placeholder (transitions.dev P14/P15).
 * Only renders when [visible] is true, drawing a traveling light sweep across the surface.
 */
fun Modifier.shimmerPlaceholder(
    visible: Boolean,
    baseColor: Color = Color.Unspecified,
    highlightColor: Color = Color.Unspecified
): Modifier = composed {
    if (!visible) return@composed this

    val defaultBase = if (baseColor != Color.Unspecified) baseColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val defaultHighlight = if (highlightColor != Color.Unspecified) highlightColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val brush = Brush.linearGradient(
        colors = listOf(defaultBase, defaultHighlight, defaultBase),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 400f, 0f)
    )

    this.background(brush)
}

/**
 * Pop-in scale and alpha entrance with overshoot physics (transitions.dev P1).
 */
fun Modifier.popIn(
    visible: Boolean,
    initialScale: Float = 0.3f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (visible) 1.0f else initialScale,
        animationSpec = MotionTokens.BouncySpring,
        label = "PopInScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1.0f else 0.0f,
        animationSpec = tween(MotionTokens.DurationFast, easing = MotionTokens.EaseOutCubic),
        label = "PopInAlpha"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
