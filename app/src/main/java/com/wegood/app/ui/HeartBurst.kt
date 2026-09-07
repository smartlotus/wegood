package com.wegood.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.ui.theme.Pink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.random.Random

/** 收到爱心：全屏遮罩 + 中心光环扩散 + 心形粒子升腾 + 脉动大爱心 */
@Composable
fun HeartBurstOverlay(fromName: String, kindEmoji: String, onDone: () -> Unit) {
    val screenH = LocalConfiguration.current.screenHeightDp.toFloat()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "bigScale",
    )
    // 入场弹簧 + 中心光环扩散
    val entry = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { entry.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f)) }
        launch { ring.animateTo(1f, tween(700, easing = LinearEasing)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(onClick = onDone),
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size((80 + 260 * ring.value).dp)
                .alpha((0.35f * (1f - ring.value)).coerceIn(0f, 1f))
                .background(Pink, CircleShape),
        )
        repeat(14) { i -> FloatingHeart(i, screenH) }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(kindEmoji, fontSize = 96.sp, modifier = Modifier.scale(entry.value * pulseScale))
            Text(
                "$fromName 给你发来了爱心",
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight(700),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .alpha(entry.value)
                    .background(Pink.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text("点一下关闭", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }

    LaunchedEffect(Unit) {
        delay(2600)
        onDone()
    }
}

@Composable
private fun FloatingHeart(seed: Int, screenH: Float) {
    val rnd = remember(seed) { Random(seed * 7919) }
    val emoji = remember(seed) { listOf("❤️", "🩷", "💗", "💖", "💕", "😘", "🌹")[rnd.nextInt(7)] }
    val xRatio = remember(seed) { rnd.nextFloat() * 0.9f + 0.05f }
    val size = remember(seed) { 18f + rnd.nextInt(22) }
    val drift = remember(seed) { (rnd.nextFloat() - 0.5f) * 80f }
    val progress = remember(seed) { Animatable(0f) }

    LaunchedEffect(seed) {
        delay(seed * 110L)
        progress.animateTo(1f, animationSpec = tween(2200, easing = LinearEasing))
    }

    val p = progress.value
    val rise = screenH * (0.75f + 0.25f * (1f - p)) * p // 从底部升到约 60%~100% 高度
    Text(
        emoji,
        fontSize = size.sp,
        modifier = Modifier
            .offset(x = (LocalConfiguration.current.screenWidthDp * xRatio + drift * p).dp, y = screenH.dp - rise.dp)
            .alpha(if (p < 0.08f) p / 0.08f else 1f - ((p - 0.75f) / 0.25f).coerceIn(0f, 1f))
            .scale(0.8f + 0.4f * p),
    )
}
