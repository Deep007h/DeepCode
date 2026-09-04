package ai.deepcode.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntSize

/**
 * Design motion tokens converted from transitions.dev into native Jetpack Compose specifications.
 * Provides high-performance, hardware-accelerated easing curves, spring physics, and durations.
 */
object MotionTokens {

    // ── Canonical Easing Curves (from transitions.dev) ────────────
    /** Smooth deceleration curve for menus, panels, and card entrances. */
    val EaseOutCubic = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

    /** Overshoot spring-like cubic bezier for pop-in badges, buttons, and alert highlights. */
    val EaseOutSpring = CubicBezierEasing(0.34f, 1.36f, 0.64f, 1.0f)

    /** Symmetric acceleration/deceleration for accordion expand/collapse and size transitions. */
    val EaseInOut = CubicBezierEasing(0.40f, 0.0f, 0.20f, 1.0f)

    /** Subtle anticipation curve for tactile press feedback. */
    val EaseOutBack = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

    // ── Standard Durations (ms) ──────────────────────────────────
    const val DurationMicro = 150
    const val DurationFast = 250
    const val DurationNormal = 350
    const val DurationSlow = 500
    const val DurationBreathing = 1600

    // ── Native Spring Specifications (60/120fps RenderThread) ───
    /** Medium bouncy spring with subtle overshoot for badges and indicators. */
    val BouncySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Low bounce, snappy tactile spring for button clicks and small micro-interactions. */
    val SnappySpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /** Critically damped gentle spring for smooth content layout / size morphs without jitter. */
    val GentleSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** Spring spec for IntSize layout morphing (accordions, bubble expansion). */
    val LayoutSpring: SpringSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
