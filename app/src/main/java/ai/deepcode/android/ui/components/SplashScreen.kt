package ai.deepcode.android.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ai.deepcode.android.R
import ai.deepcode.android.ui.theme.ActiveAccent
import ai.deepcode.android.ui.theme.AppScreenBg
import ai.deepcode.android.ui.theme.AppWhite
import kotlinx.coroutines.delay

/**
 * Safely loads the application launcher icon as an [ImageBitmap] by rendering
 * the system [android.graphics.drawable.Drawable] (including AdaptiveIconDrawable)
 * to an in-memory bitmap. This avoids Compose's painterResource limitation which
 * only supports VectorDrawables and rasterized images (PNG/JPG).
 */
@Composable
fun rememberAppIconBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
            if (drawable != null) {
                val size = 256
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Renders the DeepCode app logo safely across all Android versions.
 */
@Composable
fun AppLogoImage(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    clipRadius: Dp = 16.dp
) {
    val iconBitmap = rememberAppIconBitmap()
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = "DeepCode Logo",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(clipRadius))
        )
    } else {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = "DeepCode Logo",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(clipRadius))
        )
    }
}

@Composable
fun SplashScreen(
    onAnimationFinished: () -> Unit = {}
) {
    val scale = remember { Animatable(0.65f) }
    val alpha = remember { Animatable(0f) }
    val glowTransition = rememberInfiniteTransition(label = "glowTransition")

    val haloPulse by glowTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    LaunchedEffect(Unit) {
        // Entrance animation
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.68f,
                stiffness = 280f
            )
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(300, easing = LinearEasing)
        )
        // Hold for a delightful moment (~600ms), then finish
        delay(600)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppScreenBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo container with pulsating ambient glow halo
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value),
                contentAlignment = Alignment.Center
            ) {
                // Outer ambient aura
                Box(
                    modifier = Modifier
                        .size(130.dp * haloPulse)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ActiveAccent.primary.copy(alpha = 0.35f * haloPulse),
                                    ActiveAccent.primaryGradientEnd.copy(alpha = 0.12f * haloPulse),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // App Icon safely rendered
                AppLogoImage(
                    size = 84.dp,
                    clipRadius = 22.dp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Brand Typography
            Text(
                text = "DeepCode",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppWhite,
                letterSpacing = 1.2.sp,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value.coerceIn(0f, 1f))
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Autonomous AI & Coding Assistant",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ActiveAccent.primary.copy(alpha = 0.85f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(alpha.value.coerceIn(0f, 1f))
            )
        }
    }
}
