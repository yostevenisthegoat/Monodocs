package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MonoDocsLogo
import com.example.ui.components.MonoEcosystemLogo
import com.example.ui.components.MonoWaveBackground
import com.example.ui.theme.LocalMonoColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val mono = LocalMonoColors.current
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "splash_alpha",
    )

    val infinite = rememberInfiniteTransition(label = "spinner")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin_angle",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1200)
        onFinished()
    }

    MonoWaveBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .alpha(alpha),
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MonoDocsLogo(size = 86.dp)
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Mono Docs",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = mono.textPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Open Anything. Convert Anything.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = mono.textSecondary,
                )
                Spacer(Modifier.height(48.dp))

                // Minimalist segmented ring spinner matching reference splash
                Canvas(modifier = Modifier.size(34.dp)) {
                    drawArc(
                        color = mono.textMuted.copy(alpha = 0.25f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = mono.textPrimary,
                        startAngle = angle,
                        sweepAngle = 95f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "An application by",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textMuted,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoEcosystemLogo(size = 20.dp, tint = mono.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mono Ecosystem",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = mono.textPrimary,
                    )
                }
            }
        }
    }
}
