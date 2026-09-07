package com.wegood.app.ui.screens

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.ui.components.LargeTitle
import com.wegood.app.ui.components.MetricCard
import com.wegood.app.ui.theme.IndigoGradient
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PurpleGradient
import com.wegood.app.ui.theme.TealGradient
import com.wegood.app.ui.theme.TextGray
import com.wegood.app.ui.theme.YellowGradient
import kotlinx.coroutines.launch

/** 游戏大厅 */
@Composable
fun GamesScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LargeTitle("小游戏")
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                title = "井字棋 · 和 TA 对弈",
                value = "❌", unit = "",
                caption = "实时同步，谁先连成一条线？",
                icon = "🎮",
                brush = PurpleGradient,
                onClick = { onOpen("ttt") },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            MetricCard(
                title = "每日一问",
                value = "💬", unit = "",
                caption = "每天一个问题，两个答案互换",
                icon = "🫶",
                brush = TealGradient,
                onClick = { onOpen("daily") },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            MetricCard(
                title = "真心话大冒险",
                value = "🎲", unit = "",
                caption = "考验彼此的时候到了",
                icon = "🔥",
                brush = YellowGradient,
                onClick = { onOpen("td") },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** 双人井字棋：服务端同步对局 */
@Composable
fun TicTacToeScreen(state: com.wegood.app.data.MeResponse) {
    val scope = rememberCoroutineScope()
    val ttt = state.ttt
    val myMark = ttt?.players?.get(Prefs.deviceId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("井字棋")
        if (ttt == null || myMark == null) {
            Text("配对后才能对弈哦", color = TextGray, modifier = Modifier.padding(20.dp))
            return
        }
        val win = ttt.winner
        Text(
            when {
                win == "draw" -> "平局，心意相连 🤝"
                win == myMark -> "你赢啦 🎉"
                win != null -> "TA 赢了，下次扳回来 😤"
                ttt.turn == myMark -> "轮到你了（你是 $myMark）"
                else -> "等 TA 落子…（你是 $myMark）"
            },
            fontSize = 16.sp, fontWeight = FontWeight(600),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Box(Modifier.padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0..2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in 0..2) {
                            val idx = r * 3 + c
                            val inWinLine = ttt.winLine?.contains(idx) == true
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (inWinLine) Pink.copy(alpha = 0.18f) else Color.White)
                                    .border(
                                        width = 2.dp, color = if (inWinLine) Pink else Color(0xFFEFEFF4),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable(enabled = win == null && ttt.turn == myMark && ttt.board.getOrNull(idx) == null) {
                                        scope.launch { Repo.tttMove(idx) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                val mark = ttt.board.getOrNull(idx)
                                AnimatedMark(mark)
                            }
                        }
                    }
                }
            }
        }
        val names = state.allNames()
        val xName = ttt.players.filterValues { it == "X" }.keys.firstOrNull()?.let { names[it] } ?: "X"
        val oName = ttt.players.filterValues { it == "O" }.keys.firstOrNull()?.let { names[it] } ?: "O"
        Text(
            "❌ $xName  ·  ⭕️ $oName",
            fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { scope.launch { Repo.tttReset() } },
            colors = ButtonDefaults.buttonColors(containerColor = com.wegood.app.ui.theme.Purple),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(50.dp),
        ) { Text("再来一局", fontSize = 16.sp, fontWeight = FontWeight(700)) }
        Spacer(Modifier.height(90.dp))
    }
}

private fun com.wegood.app.data.MeResponse.allNames(): Map<String, String> =
    mapOf(device.id to device.name) + (partner?.let { mapOf(it.id to it.name) } ?: emptyMap())

/** 落子缩放入场动画 */
@Composable
private fun AnimatedMark(mark: String?) {
    var shown by remember { mutableStateOf<String?>(null) }
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(mark) {
        if (mark != null) {
            shown = mark
            appear.snapTo(0f)
            appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }
    Box(Modifier.scale(appear.value)) {
        Text(
            when (shown) {
                "X" -> "❌"
                "O" -> "⭕️"
                else -> ""
            },
            fontSize = 34.sp,
        )
    }
}

/** 每日一问：双方各答一题，互相可见 */
@Composable
fun DailyQuestionScreen(state: com.wegood.app.data.MeResponse) {
    val scope = rememberCoroutineScope()
    val daily = state.daily
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("每日一问")
        if (daily == null) {
            Text("配对后开启吧", color = TextGray, modifier = Modifier.padding(20.dp))
            return
        }
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(TealGradient)
                .padding(18.dp),
        ) {
            Column {
                Text(daily.date, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Text(daily.question, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight(700), lineHeight = 28.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }

        var answer by rememberSaveable { mutableStateOf(daily.myAnswer ?: "") }
        val myAnswered = daily.myAnswer != null
        Spacer(Modifier.height(14.dp))
        Text(
            if (myAnswered) "我的答案" else "写下你的答案",
            fontWeight = FontWeight(700), fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = answer,
            onValueChange = { if (!myAnswered) answer = it.take(200) },
            enabled = !myAnswered,
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        if (!myAnswered) {
            Button(
                onClick = { if (answer.isNotBlank()) scope.launch { Repo.dailyAnswer(answer) } },
                colors = ButtonDefaults.buttonColors(containerColor = com.wegood.app.ui.theme.Teal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(48.dp),
            ) { Text("提交答案", fontWeight = FontWeight(700), fontSize = 15.sp) }
        }

        Spacer(Modifier.height(18.dp))
        Text("TA 的答案", fontWeight = FontWeight(700), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Box(
            Modifier
                .padding(20.dp, 8.dp, 20.dp, 0.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFE0F6FA))
                .padding(16.dp),
        ) {
            Text(
                when {
                    daily.partnerAnswer != null && daily.partnerAnswer != "hidden" -> daily.partnerAnswer
                    daily.partnerAnswered -> "TA 已经回答啦，先提交你的答案才能互看 😉"
                    else -> "TA 还没有回答，去催催 TA？"
                },
                fontSize = 15.sp, lineHeight = 23.sp,
                color = if (daily.partnerAnswer != null && daily.partnerAnswer != "hidden") Color.Black else TextGray,
            )
        }
        Spacer(Modifier.height(90.dp))
    }
}

/** 真心话大冒险（本地题库） */
@Composable
fun TruthDareScreen() {
    var mode by rememberSaveable { mutableStateOf<Int?>(null) } // 0=真心话 1=大冒险
    var item by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LargeTitle("真心话大冒险")
        Text("手拉手一起玩 · 敢不敢说真话", fontSize = 13.sp, color = TextGray)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { mode = 0; item = (TRUTHS.indices).random() },
                colors = ButtonDefaults.buttonColors(containerColor = com.wegood.app.ui.theme.Teal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("真心话 💬", fontWeight = FontWeight(700), fontSize = 16.sp) }
            Button(
                onClick = { mode = 1; item = (DARES.indices).random() },
                colors = ButtonDefaults.buttonColors(containerColor = com.wegood.app.ui.theme.Yellow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("大冒险 🎯", fontWeight = FontWeight(700), fontSize = 16.sp) }
        }
        if (mode != null && item != null) {
            val text = (if (mode == 0) TRUTHS else DARES)[item!!]
            Box(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (mode == 0) TealGradient else YellowGradient)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (mode == 0) "真心话" else "大冒险", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight(700))
                    Text(text, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight(700), lineHeight = 30.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Text(
                "换一个",
                color = Pink, fontWeight = FontWeight(700), fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pink.copy(alpha = 0.1f))
                    .padding(horizontal = 22.dp, vertical = 9.dp)
                    .clickable { item = ((if (mode == 0) TRUTHS else DARES).indices).random() },
            )
        } else {
            Text(
                "点上面的按钮抽一张\n轮到谁就谁来 👀",
                fontSize = 14.sp, color = TextGray, lineHeight = 22.sp,
                modifier = Modifier.padding(top = 40.dp),
            )
        }
        Spacer(Modifier.height(90.dp))
    }
}

private val TRUTHS = listOf(
    "第一次见 TA 时，你的第一印象是什么？",
    "TA 做过最让你心动的一件小事是什么？",
    "你偷偷观察过 TA 睡觉吗？",
    "你手机里有没有只关于 TA 的秘密相册？",
    "如果给我们的感情打分，你打几分？为什么？",
    "你什么时候开始确定 TA 就是对的人？",
    "说一个你为 TA 吃过最多的醋。",
    "TA 的哪个缺点你最想包容一辈子？",
    "你做过关于 TA 最甜的梦是什么？",
    "你为 TA 改变过自己哪个习惯？",
    "你最想带 TA 去哪里过余生？",
    "你藏了多少句没说出口的情话？说一句。",
    "如果重来一次，你还会选 TA 吗？",
    "TA 什么时候最像个小朋友？",
    "你和 TA 吵过最凶的一次是为了什么？",
    "你觉得我们之间最珍贵的共同回忆是什么？",
    "说出你最想和 TA 一起完成的一个心愿。",
    "TA 送你的礼物里你最喜欢哪个？",
    "如果 TA 变成动物，你觉得是什么？为什么？",
    "你有没有在 TA 面前偷偷哭过？为什么？",
)

private val DARES = listOf(
    "用最嗲的声音对 TA 说三遍“我爱你”。",
    "模仿 TA 最常用的一个表情或口头禅。",
    "给 TA 唱一段情歌（不许笑场）。",
    "和 TA 对视 30 秒，先笑的人做 10 个俯卧撑。",
    "让 TA 在你手机相册里随机选一张照片，并讲出背后的故事。",
    "向 TA 撒一个善意的小谎然后立刻承认，并说“但我最爱的是你”。",
    "背起 TA 或做 5 个公主抱起蹲（安全第一！）。",
    "用身体摆一个大爱心给 TA 看。",
    "夸 TA 十个优点，不许重复用词。",
    "让 TA 给你摆一个 pose，拍照留念。",
    "给 TA 按摩肩膀 2 分钟。",
    "学一段 TA 喜欢的手势舞（现场发挥也算）。",
    "深情朗读一段你发给 TA 的最早的聊天记录。",
    "让 TA 写一个词，你用这个词当场表白。",
    "闭眼让 TA 喂你吃一样东西，并猜是什么。",
)
