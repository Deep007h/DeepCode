package ai.deepcode.android.ui.components

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.deepcode.android.R
import ai.deepcode.android.ui.theme.ActiveAccent
import ai.deepcode.android.ui.theme.AppScreenBg
import ai.deepcode.android.ui.theme.AppWhite
import kotlinx.coroutines.delay

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

                // App Icon
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "DeepCode Logo",
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(22.dp))
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
