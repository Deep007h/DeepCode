package ai.deepcode.android.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable depth styling tokens and modifiers based on YouTube tactile dark-mode action pills.
 * Features:
 * - Subtle vertical gradients (lighter on top, deeper on bottom) simulating physical curvature
 * - Top-edge specular bevel highlight (bright rim at top, fading downwards)
 * - Soft ambient and spot drop shadows
 * - Responsive spring press feedback
 */
object DepthTokens {
    // ── Neutral Pill Colors (Dark Theme) ──
    val PillGradientTopDark = Color(0xFF333338)
    val PillGradientBottomDark = Color(0xFF1E1E22)
    val PillHighlightTopDark = Color.White.copy(alpha = 0.22f)
    val PillHighlightBottomDark = Color.White.copy(alpha = 0.04f)

    // ── Card Surface Colors (Dark Theme) ──
    val CardGradientTopDark = Color(0xFF26262B)
    val CardGradientBottomDark = Color(0xFF19191D)
    val CardHighlightTopDark = Color.White.copy(alpha = 0.18f)
    val CardHighlightBottomDark = Color.White.copy(alpha = 0.03f)

    // ── Input Bar Colors (Dark Theme) ──
    val InputGradientTopDark = Color(0xFF2A2A30)
    val InputGradientBottomDark = Color(0xFF19191C)
    val InputHighlightTopDark = Color.White.copy(alpha = 0.25f)
    val InputHighlightBottomDark = Color.White.copy(alpha = 0.05f)

    // ── Divider Color between segmented buttons ──
    val SegmentDividerDark = Color.White.copy(alpha = 0.16f)

    // ── Light Theme Equivalents ──
    val PillGradientTopLight = Color(0xFFFFFFFF)
    val PillGradientBottomLight = Color(0xFFEDEDF0)
    val PillHighlightTopLight = Color.White
    val PillHighlightBottomLight = Color(0x1F000000)

    val CardGradientTopLight = Color(0xFFFFFFFF)
    val CardGradientBottomLight = Color(0xFFF7F7F9)
    val CardHighlightTopLight = Color.White
    val CardHighlightBottomLight = Color(0x14000000)
}

/**
 * Applies YouTube-style tactile depth to a pill or button.
 * Includes soft drop shadow, convex vertical gradient, and top-edge specular highlight.
 */
fun Modifier.depthPill(
    shape: Shape = CircleShape,
    elevation: Dp = 3.dp,
    customGradient: List<Color>? = null,
    highlightAlpha: Float = 0.22f,
    isDark: Boolean = true
): Modifier {
    val gradient = customGradient ?: if (isDark) {
        listOf(DepthTokens.PillGradientTopDark, DepthTokens.PillGradientBottomDark)
    } else {
        listOf(DepthTokens.PillGradientTopLight, DepthTokens.PillGradientBottomLight)
    }

    val topHighlight = if (isDark) {
        Color.White.copy(alpha = highlightAlpha)
    } else {
        Color.White
    }
    val bottomHighlight = if (isDark) {
        Color.White.copy(alpha = 0.04f)
    } else {
        Color(0x1F000000)
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            spotColor = if (isDark) Color(0x80000000) else Color(0x1F000000),
            ambientColor = if (isDark) Color(0x40000000) else Color(0x0F000000)
        )
        .clip(shape)
        .background(Brush.verticalGradient(gradient))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(topHighlight, bottomHighlight)),
            shape = shape
        )
}

/**
 * Applies tactile depth to a card surface (e.g. Dashboard cards, dialogs, panels).
 */
fun Modifier.depthCard(
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 4.dp,
    customGradient: List<Color>? = null,
    isDark: Boolean = true
): Modifier {
    val gradient = customGradient ?: if (isDark) {
        listOf(DepthTokens.CardGradientTopDark, DepthTokens.CardGradientBottomDark)
    } else {
        listOf(DepthTokens.CardGradientTopLight, DepthTokens.CardGradientBottomLight)
    }

    val topHighlight = if (isDark) DepthTokens.CardHighlightTopDark else DepthTokens.CardHighlightTopLight
    val bottomHighlight = if (isDark) DepthTokens.CardHighlightBottomDark else DepthTokens.CardHighlightBottomLight

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            spotColor = if (isDark) Color(0x99000000) else Color(0x1A000000),
            ambientColor = if (isDark) Color(0x4D000000) else Color(0x0D000000)
        )
        .clip(shape)
        .background(Brush.verticalGradient(gradient))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(topHighlight, bottomHighlight)),
            shape = shape
        )
}

/**
 * Applies tactile depth to an input bar or search capsule.
 */
fun Modifier.depthInputBar(
    shape: Shape = RoundedCornerShape(32.dp),
    elevation: Dp = 5.dp,
    isDark: Boolean = true
): Modifier {
    val gradient = if (isDark) {
        listOf(DepthTokens.InputGradientTopDark, DepthTokens.InputGradientBottomDark)
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFF2F2F5))
    }

    val topHighlight = if (isDark) DepthTokens.InputHighlightTopDark else Color.White
    val bottomHighlight = if (isDark) DepthTokens.InputHighlightBottomDark else Color(0x22000000)

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            spotColor = if (isDark) Color(0xA6000000) else Color(0x1A000000),
            ambientColor = if (isDark) Color(0x59000000) else Color(0x0D000000)
        )
        .clip(shape)
        .background(Brush.verticalGradient(gradient))
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(listOf(topHighlight, bottomHighlight)),
            shape = shape
        )
}

/**
 * Standalone YouTube-style depth pill button.
 */
@Composable
fun DepthPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    text: String? = null,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    contentColor: Color = Color.White,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
) {
    val primaryColors = listOf(AppPrimary, AppPrimaryGradientEnd)
    val customGradient = if (isPrimary) primaryColors else null

    Box(
        modifier = modifier
            .depthPill(
                shape = shape,
                elevation = if (isPrimary) 4.dp else 2.5.dp,
                customGradient = customGradient,
                highlightAlpha = if (isPrimary) 0.35f else 0.22f
            )
            .bouncyClickable(enabled = enabled, provideHaptic = true, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                icon()
            }
            if (!text.isNullOrBlank()) {
                Text(
                    text = text,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Circular or squircle depth icon button.
 */
@Composable
fun DepthIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    content: @Composable () -> Unit
) {
    val customGradient = if (isPrimary) listOf(AppPrimary, AppPrimaryGradientEnd) else null

    Box(
        modifier = modifier
            .size(size)
            .depthPill(
                shape = shape,
                elevation = 3.dp,
                customGradient = customGradient,
                highlightAlpha = if (isPrimary) 0.35f else 0.22f
            )
            .bouncyClickable(enabled = enabled, provideHaptic = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Compound segmented pill button with vertical divider (like Like/Dislike in YouTube).
 */
@Composable
fun DepthSegmentedPill(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .depthPill(shape = shape, elevation = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

/**
 * Vertical divider inside a segmented pill button.
 */
@Composable
fun DepthSegmentDivider(
    height: Dp = 18.dp,
    color: Color = DepthTokens.SegmentDividerDark
) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height)
            .background(color)
    )
}
