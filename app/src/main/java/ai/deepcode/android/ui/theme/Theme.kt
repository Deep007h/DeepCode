package ai.deepcode.android.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
//  THEME SYSTEM
//  - Accent themes: multiple UI accent colors selectable in Settings.
//  - Theme mode: system / light / dark.
//  - Global reactive palette: the vals below read the active theme,
//    so every reference across the app updates live when it changes.
// ─────────────────────────────────────────────────────────────

/** A user-selectable accent color theme. */
data class AccentTheme(
    val id: String,
    val displayName: String,
    val primary: Color,
    val primaryGradientEnd: Color
)

/** Catalog of available accent themes (Amber is the classic OpenCode accent). */
val AccentThemes = listOf(
    AccentTheme("amber", "Amber", Color(0xFFF5A623), Color(0xFFD97706)),
    AccentTheme("blue", "Ocean Blue", Color(0xFF0A84FF), Color(0xFF0061FE)),
    AccentTheme("purple", "Violet", Color(0xFFBF5AF2), Color(0xFF8E24AA)),
    AccentTheme("green", "Emerald", Color(0xFF30D158), Color(0xFF059669)),
    AccentTheme("red", "Crimson", Color(0xFFFF453A), Color(0xFFC81E1E)),
    AccentTheme("pink", "Rose", Color(0xFFFF375F), Color(0xFFE01E5A)),
    AccentTheme("teal", "Teal", Color(0xFF40C8E0), Color(0xFF0E7C8C)),
    AccentTheme("orange", "Orange", Color(0xFFFF9F0A), Color(0xFFD4880F))
)

// ── Global reactive theme state (single source of truth) ──
private var _themeMode by mutableStateOf("system")
private var _accentId by mutableStateOf("amber")
private var _darkActive by mutableStateOf(true)

/** Current theme mode: "system" | "light" | "dark". */
var AppThemeMode: String
    get() = _themeMode
    set(value) {
        if (_themeMode != value) _themeMode = value
    }

/** Id of the active accent theme (see [AccentThemes]). */
var AppAccentId: String
    get() = _accentId
    set(value) {
        if (_accentId != value) _accentId = value
    }

/** True when the current palette is the dark one. */
val isDarkThemeActive: Boolean get() = _darkActive

/** The active accent theme. */
val ActiveAccent: AccentTheme
    get() = AccentThemes.firstOrNull { it.id == _accentId } ?: AccentThemes.first()

// ─────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────
//  SEMANTIC / STATUS COLORS (fixed across themes)
// ─────────────────────────────────────────────────────────────
val AppSuccess = Color(0xFF4CAF50)
val AppDestructive = Color(0xFFE53935)
val AppIntegrationPurple = Color(0xFF7C3AED)

// Legacy light palette constants (kept for compatibility)
val AppSurfaceLight = Color(0xFFF5F5F0)
val AppBackgroundLight = Color(0xFFFAFAF5)

// ─────────────────────────────────────────────────────────────
//  DYNAMIC PALETTE (driven by mode + accent)
//  Each getter evaluates the active theme so Compose tracks it.
// ─────────────────────────────────────────────────────────────
val AppBackground: Color get() = if (isDarkThemeActive) Color(0xFF0D0D0D) else Color(0xFFFAFAF5)
val AppSurface: Color get() = if (isDarkThemeActive) Color(0xFF1A1A1A) else Color.White
val AppSurfaceVariant: Color get() = if (isDarkThemeActive) Color(0xFF3A3A3A) else Color(0xFFE8E8E3)
val AppMuted: Color get() = if (isDarkThemeActive) Color(0xFF9E9E9E) else Color(0xFF6B7280)
val AppWhite: Color get() = if (isDarkThemeActive) Color.White else Color(0xFF1C1C1E)
val AppDarkGray: Color get() = if (isDarkThemeActive) Color(0xFF3A3A3A) else Color(0xFFD4D4D0)

/** Full-screen background (screens that use their own darker canvas). */
val AppScreenBg: Color get() = if (isDarkThemeActive) Color(0xFF0D0D0D) else Color(0xFFFAFAF5)

/** Card / grouped-surface background. */
val AppCard: Color get() = if (isDarkThemeActive) Color(0xFF1A1A1A) else Color.White

/** Subtle divider line color. */
val AppDivider: Color get() = if (isDarkThemeActive) Color(0xFF2E2E2E) else Color(0xFFE8E8E3)

/** Border / outline color. */
val AppBorder: Color get() = if (isDarkThemeActive) Color(0xFF3A3A3A) else Color(0xFFD4D4D0)

/** Input / field background color. */
val AppField: Color get() = if (isDarkThemeActive) Color(0xFF0D0D0D) else Color(0xFFF5F5F0)

/** Primary accent color (themeable). */
val AppPrimary: Color get() = ActiveAccent.primary

/** Gradient end tone matched to the active accent. */
val AppPrimaryGradientEnd: Color get() = ActiveAccent.primaryGradientEnd

/** Classic amber accent (kept for compatibility). */
val AppPrimaryAmber: Color get() = AccentThemes.first().primary

fun appDarkColorScheme(accent: AccentTheme) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    secondary = AppIntegrationPurple,
    onSecondary = Color.White,
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF1A1A1A),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFF9E9E9E),
    error = AppDestructive,
    onError = Color.White,
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF9E9E9E).copy(alpha = 0.3f),
    tertiary = AppSuccess,
    onTertiary = Color.White
)

fun appLightColorScheme(accent: AccentTheme) = lightColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    secondary = AppIntegrationPurple,
    onSecondary = Color.White,
    background = Color(0xFFFAFAF5),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE8E8E3),
    onSurfaceVariant = Color(0xFF6B7280),
    error = Color(0xFFE53935),
    onError = Color.White,
    outline = Color(0xFFD4D4D0),
    outlineVariant = Color(0xFFE8E8E3),
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color.White
)

val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 14.sp)
)

data class AppColors(
    val background: Color = AppBackground,
    val surface: Color = AppSurface,
    val surfaceVariant: Color = AppSurfaceVariant,
    val card: Color = AppCard,
    val border: Color = AppBorder,
    val divider: Color = AppDivider,
    val field: Color = AppField,
    val screenBg: Color = AppScreenBg,
    val primary: Color = AppPrimary,
    val primaryGradientEnd: Color = AppPrimaryGradientEnd,
    val success: Color = AppSuccess,
    val destructive: Color = AppDestructive,
    val integrationPurple: Color = AppIntegrationPurple,
    val muted: Color = AppMuted,
    val white: Color = AppWhite,
    val darkGray: Color = AppDarkGray
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

/**
 * App theme root. [themeMode] is "system" | "light" | "dark" and
 * [accentId] selects an accent theme from [AccentThemes].
 *
 * While this runs it also syncs [AppThemeMode] / [AppAccentId] and
 * [isDarkThemeActive], so global palette getters (AppPrimary,
 * AppBackground, ...) reflect the active theme everywhere.
 */
@Composable
fun DeepCodeTheme(
    themeMode: String = AppThemeMode,
    accentId: String = AppAccentId,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val accent = AccentThemes.firstOrNull { it.id == accentId } ?: AccentThemes.first()

    // Don't mutate global state during composition (was triggering extra recompositions).
    SideEffect {
        if (AppThemeMode != themeMode) AppThemeMode = themeMode
        if (AppAccentId != accentId) AppAccentId = accentId
        if (_darkActive != dark) _darkActive = dark
    }

    CompositionLocalProvider(LocalAppColors provides AppColors()) {
        MaterialTheme(
            colorScheme = if (dark) appDarkColorScheme(accent) else appLightColorScheme(accent),
            typography = AppTypography,
            content = content
        )
    }
}

object CardGradients {
    val primary get() = listOf(AppPrimary, AppPrimaryGradientEnd)
    val amber get() = listOf(Color(0xFFFF9F0A), Color(0xFFD4880F))
    val purple get() = listOf(Color(0xFFBF5AF2), Color(0xFF8E24AA))
    val blue get() = listOf(AppPrimary, AppPrimaryGradientEnd)
    val green get() = listOf(Color(0xFF30D158), Color(0xFF059669))
    val dark get() = listOf(Color(0xFF1D212E), Color(0xFF141721))
}