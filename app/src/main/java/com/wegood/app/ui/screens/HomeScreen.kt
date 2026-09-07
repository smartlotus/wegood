package com.wegood.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.data.DateMath
import com.wegood.app.data.FeedEvent
import com.wegood.app.data.HEART_EMOJI
import com.wegood.app.data.Repo
import com.wegood.app.ui.components.LargeTitle
import com.wegood.app.ui.components.ListCard
import com.wegood.app.ui.components.MetricCard
import com.wegood.app.ui.components.ReactionRow
import com.wegood.app.ui.components.SectionHeader
import com.wegood.app.ui.theme.Indigo
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PinkGradient
import com.wegood.app.ui.theme.TextGray
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/** 首页（健康 App「摘要」页风格）：在一起天数 + 发送爱心 + 最近互动 */
@Composable
fun HomeScreen(state: com.wegood.app.data.MeResponse) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    var miniBurst by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(if (sending) 0.86f else 1f, spring(dampingRatio = 0.45f), label = "heartBtn")

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LargeTitle("WeGood")
            val fmt = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
            val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val greet = when (hour) {
                in 5..10 -> "早上好 ☀️"
                in 11..12 -> "中午好 🍱"
                in 13..17 -> "下午好 🌤️"
                else -> "晚上好 🌙"
            }
            Text(
                "$greet · ${fmt.format(Date())}",
                fontSize = 13.sp, color = TextGray,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
        }

        // 在一起英雄卡（天数滚动动画）
        item {
            val days = state.couple?.let { DateMath.dayCount(DateMath.toDateStr(it.createdAt)) } ?: 0
            val counter = remember { Animatable(0f) }
            LaunchedEffect(days) {
                counter.snapTo(0f)
                counter.animateTo(days.toFloat(), tween(900, easing = FastOutSlowInEasing))
            }
            MetricCard(
                title = "在一起",
                value = "${counter.value.roundToInt()}",
                unit = " 天",
                caption = "每一天都值得纪念 · 和 ${state.partner?.name ?: "TA"}",
                icon = "💞",
                brush = PinkGradient,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // 发送爱心大按钮（弹簧缩放 + 迷你爱心爆发）
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pink.copy(alpha = 0.10f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) {
                        if (!sending) {
                            sending = true
                            scope.launch {
                                val r = Repo.sendHeart("heart")
                                sending = false
                                if (r.isSuccess) {
                                    miniBurst = true
                                    delay(1000)
                                    miniBurst = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("❤️", fontSize = 46.sp, modifier = Modifier.scale(btnScale))
                    Text(
                        "点这里，给 ${state.partner?.name ?: "TA"} 发一颗爱心",
                        fontSize = 14.sp, color = Pink, fontWeight = FontWeight(600), modifier = Modifier.padding(top = 8.dp),
                    )
                }
                AnimatedVisibility(
                    visible = miniBurst,
                    enter = scaleIn(spring(dampingRatio = 0.4f)) + fadeIn(),
                    exit = fadeOut(),
                ) { MiniHeartBurst() }
            }
        }

        // 快捷互动
        item {
            Spacer(Modifier.height(6.dp))
            ReactionRow(
                kinds = listOf("kiss" to "😘", "hug" to "🤗", "rose" to "🌹", "miss" to "💭"),
                onSend = { kind -> scope.launch { Repo.sendHeart(kind) } },
            )
        }

        // 最近互动
        item { SectionHeader("最近互动", modifier = Modifier.padding(top = 8.dp)) }
        if (state.events.isEmpty()) {
            item {
                Text(
                    "还没有互动记录，发一颗爱心打破平静吧 ❤️",
                    fontSize = 13.sp, color = TextGray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        } else {
            items(state.events.sortedByDescending { it.ts }, key = { it.id }) { ev ->
                Box(Modifier.padding(horizontal = 20.dp)) {
                    FeedRow(ev, state)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** 按钮内迷你爱心粒子：发送成功后的即时反馈 */
@Composable
private fun MiniHeartBurst() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(5) { i ->
            val p = remember { Animatable(0f) }
            val x0 = remember { Random(i * 31).nextInt(-40, 40) }
            LaunchedEffect(Unit) {
                delay(i * 60L)
                p.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }
            Text(
                listOf("❤️", "🩷", "💕", "💖", "💗")[i],
                fontSize = (14 + i % 3 * 5).sp,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = (x0 * (1 - p.value)).dp, y = (34 * (1 - p.value) - 6).dp)
                    .alpha(if (p.value < 0.7f) 1f else (1f - (p.value - 0.7f) / 0.3f)),
            )
        }
    }
}

@Composable
private fun FeedRow(ev: FeedEvent, state: com.wegood.app.data.MeResponse) {
    val mine = ev.fromName == state.device.name
    when (ev.kind) {
        "heart" -> ListCard(
            icon = HEART_EMOJI[ev.heartKind] ?: "❤️",
            iconColor = Pink.copy(alpha = 0.15f),
            title = if (mine) "你发出了${heartName(ev.heartKind)}" else "${ev.fromName} 发来了${heartName(ev.heartKind)}",
            subtitle = DateMath.relTime(ev.ts),
        )
        else -> ListCard(
            icon = "⭐️",
            iconColor = Indigo.copy(alpha = 0.15f),
            title = ev.text ?: "",
            subtitle = DateMath.relTime(ev.ts),
        )
    }
}

private fun heartName(kind: String?): String = when (kind) {
    "kiss" -> "亲亲 😘"
    "hug" -> "抱抱 🤗"
    "rose" -> "玫瑰 🌹"
    "miss" -> "想念 💭"
    else -> "爱心 ❤️"
}
