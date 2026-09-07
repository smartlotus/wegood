package com.wegood.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.wegood.app.ui.components.OnlineDot
import com.wegood.app.ui.components.ReactionRow
import com.wegood.app.ui.components.SectionHeader
import com.wegood.app.ui.theme.Indigo
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PinkGradient
import com.wegood.app.ui.theme.TextGray
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 首页（健康 App「摘要」页风格）：在一起天数 + 发送爱心 + 最近互动 */
@Composable
fun HomeScreen(state: com.wegood.app.data.MeResponse) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(if (sending) 0.88f else 1f, tween(160), label = "heartBtn")

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LargeTitle("WeGood")
            val fmt = remember { SimpleDateFormat("M月d日 EEEE", Locale.CHINA) }
            Text(
                fmt.format(Date()),
                fontSize = 13.sp, color = TextGray,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
        }

        // 在一起英雄卡
        item {
            val days = state.couple?.let { DateMath.dayCount(DateMath.toDateStr(it.createdAt)) } ?: 0
            MetricCard(
                title = "在一起",
                value = "$days",
                unit = " 天",
                caption = "每一天都值得纪念 · 和 ${state.partner?.name ?: "TA"}",
                icon = "💞",
                brush = PinkGradient,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // 发送爱心大按钮
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
                                Repo.sendHeart("heart")
                                kotlinx.coroutines.delay(220)
                                sending = false
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

@Composable
private fun FeedRow(ev: FeedEvent, state: com.wegood.app.data.MeResponse) {
    val mine = ev.fromName == state.device.name
    when (ev.kind) {
        "heart" -> ListCard(
            icon = HEART_EMOJI[ev.heartKind] ?: "❤️",
            iconColor = Pink.copy(alpha = 0.15f),
            title = if (mine) "你发出了${heartName(ev.heartKind)}" else "${ev.fromName} 发来了${heartName(ev.heartKind)}",
            subtitle = DateMath.relTime(ev.ts),
            onClick = {},
        )
        else -> ListCard(
            icon = "⭐️",
            iconColor = Indigo.copy(alpha = 0.15f),
            title = ev.text ?: "",
            subtitle = DateMath.relTime(ev.ts),
            onClick = {},
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
