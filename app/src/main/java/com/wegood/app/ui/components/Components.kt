package com.wegood.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.ui.theme.Green
import com.wegood.app.ui.theme.TextGray
import com.wegood.app.ui.theme.PinkLight

/** 健康风大标题 */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
        fontSize = 34.sp, fontWeight = FontWeight(800), letterSpacing = 0.2.sp,
    )
}

/** 分组标题 + 可选右侧动作 */
@Composable
fun SectionHeader(title: String, actionText: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight(700))
        if (actionText != null) {
            Text(
                actionText,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAction,
                ),
            )
        }
    }
}

/**
 * 彩色指标卡（健康 App 签名组件）：渐变背景 + 左上图标位 + 大数字 + 说明
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    unit: String = "",
    valuePrefix: String = "",
    caption: String? = null,
    icon: String = "❤️",
    brush: Brush,
    height: Dp = 150.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.16f), ambientColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .clickable(enabled = onClick != null, interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick?.invoke() }
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 15.sp) }
            Text(title, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight(600))
        }
        when {
            content != null -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { content() }
            else -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (valuePrefix.isNotEmpty()) {
                        Text(valuePrefix, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight(600), modifier = Modifier.padding(bottom = 7.dp))
                    }
                    Text(value, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight(800), lineHeight = 44.sp)
                    if (unit.isNotEmpty()) Text(unit, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                }
                if (caption != null) {
                    Text(caption, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 2)
                }
            }
        }
    }
}

/** 白色列表卡：左侧彩色图标圆片 + 标题/副标题 + 右侧值 */
@Composable
fun ListCard(
    icon: String,
    iconColor: Color,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.10f), ambientColor = Color.Black.copy(alpha = 0.04f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(enabled = onClick != null, interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick?.invoke() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = 17.sp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight(600))
            if (subtitle != null) {
                Text(subtitle, fontSize = 13.sp, color = TextGray, modifier = Modifier.padding(top = 1.dp), maxLines = 1)
            }
        }
        if (value != null) {
            Text(value, fontSize = 14.sp, color = TextGray)
        }
        if (onClick != null) {
            Text("›", fontSize = 20.sp, color = Color(0xFFC7C7CC), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** 在线状态圆点 */
@Composable
fun OnlineDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(if (online) Green else Color(0xFFC7C7CC)),
    )
}

/** 互动表情快捷条（点按弹跳动效） */
@Composable
fun ReactionRow(kinds: List<Pair<String, String>>, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        kinds.forEach { (kind, emoji) ->
            var bump by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (bump) 0.78f else 1f,
                animationSpec = spring(stiffness = 900f),
                label = "bump",
            )
            LaunchedEffect(bump) { if (bump) { kotlinx.coroutines.delay(130); bump = false } }
            Box(
                Modifier
                    .weight(1f)
                    .shadow(3.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.10f), ambientColor = Color.Black.copy(alpha = 0.04f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickableNoIndication { bump = true; onSend(kind) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 22.sp, modifier = Modifier.scale(scale)) }
        }
    }
}

fun timeAgo(ts: Long): String = com.wegood.app.data.DateMath.relTime(ts)

/** 无涟漪点击（健康风卡片常用） */
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)
