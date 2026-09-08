package com.wegood.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.bt.BtLink
import com.wegood.app.data.Conn
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.ui.components.clickableNoIndication
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PinkGradient
import kotlinx.coroutines.launch

/** 未配对时的全屏配对页：互联网配对 / 蓝牙直连 两页签 */
@Composable
fun PairScreen() {
    var tab by rememberSaveable { mutableStateOf(if (Prefs.isBtMode) "bt" else "net") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        val logoPulse = rememberInfiniteTransition(label = "logo")
        val logoScale by logoPulse.animateFloat(
            initialValue = 0.96f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "logoScale",
        )
        Box(
            Modifier
                .size(84.dp)
                .scale(logoScale)
                .clip(CircleShape)
                .background(PinkGradient),
            contentAlignment = Alignment.Center,
        ) { Text("💕", fontSize = 40.sp) }
        Text("WeGood", fontSize = 32.sp, fontWeight = FontWeight(800), modifier = Modifier.padding(top = 12.dp))
        Text("把两颗心连在一起", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        SegTabs(tab) { tab = it }
        Spacer(Modifier.height(18.dp))

        if (tab == "net") NetPairSection() else BtPairSection()
        Spacer(Modifier.height(40.dp))
    }
}

/** 页签切换（iOS 分段控件风格） */
@Composable
private fun SegTabs(tab: String, onTab: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x0F000000))
            .padding(4.dp),
    ) {
        SegItem("🌐 互联网配对", Modifier.weight(1f), tab == "net") { onTab("net") }
        SegItem("🔵 蓝牙直连", Modifier.weight(1f), tab == "bt") { onTab("bt") }
    }
}

@Composable
private fun SegItem(label: String, modifier: Modifier, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickableNoIndication(onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight(700),
            color = if (selected) Pink else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 互联网配对：我的配对码 + 绑定 TA + 服务器设置 */
@Composable
private fun NetPairSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conn by Repo.conn.collectAsState()
    var myCode by remember { mutableStateOf(Prefs.myCode.ifBlank { null }) }
    var partnerCode by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var server by rememberSaveable { mutableStateOf(Prefs.serverUrl) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(retry) {
        if (myCode == null) {
            runCatching { Repo.ensureRegistered() }
                .onSuccess { myCode = Prefs.myCode; error = null }
                .onFailure { error = "无法连接服务器，请检查下方服务器地址" }
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (conn == Conn.OFFLINE) {
            Text(
                "⚠️ 当前离线：配对码与绑定需要能连上服务器",
                fontSize = 12.sp, fontWeight = FontWeight(600), color = Color(0xFFB25000),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1FFF9500))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        // 我的配对码
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("我的配对码", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                myCode != null -> Text(
                    myCode!!,
                    fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = 8.sp, color = Pink,
                    modifier = Modifier.padding(top = 6.dp),
                )
                error != null -> {
                    Text("📶 离线中", fontSize = 26.sp, fontWeight = FontWeight(800), color = Color(0xFFB0B0B6), modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "点此重试",
                        color = Pink, fontSize = 14.sp, fontWeight = FontWeight(600),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Pink.copy(alpha = 0.1f))
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clickableNoIndication { retry++ },
                    )
                }
                else -> CircularProgressIndicator(Modifier.size(26.dp).padding(top = 8.dp), color = Pink, strokeWidth = 3.dp)
            }
            Text(
                "让 TA 在 App 里输入这串配对码",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
            if (myCode != null) {
                Text(
                    "复制",
                    color = Pink, fontSize = 14.sp, fontWeight = FontWeight(600),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Pink.copy(alpha = 0.1f))
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                        .clickableNoIndication {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("WeGood", myCode))
                        },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        // 绑定 TA
        OutlinedTextField(
            value = partnerCode,
            onValueChange = { partnerCode = it.uppercase().take(6); error = null },
            label = { Text("TA 的配对码") },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                letterSpacing = 6.sp, fontSize = 22.sp, fontWeight = FontWeight(700), textAlign = TextAlign.Center,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                if (partnerCode.length != 6) { error = "请输入 6 位配对码"; return@Button }
                busy = true
                scope.launch {
                    val r = Repo.pair(partnerCode)
                    busy = false
                    r.fold(onSuccess = {}, onFailure = { error = it.message })
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Pink),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("❤️  绑定 TA", fontSize = 17.sp, fontWeight = FontWeight(700))
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            if (showServer) "收起服务器设置" else "服务器设置",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickableNoIndication { showServer = !showServer },
        )
        if (showServer) {
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text("服务器地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "同一 WiFi：电脑运行 node server，填 http://电脑IP:3000",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "保存并重连",
                color = Pink, fontWeight = FontWeight(600), fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Pink.copy(alpha = 0.1f))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .clickableNoIndication {
                        Prefs.serverUrl = server
                        Repo.restartConnection()
                    },
            )
        }
    }
}

/** 蓝牙直连：状态卡 + 已配对设备列表 */
@Composable
private fun BtPairSection() {
    val linkState by BtLink.state.collectAsState()
    var permTick by remember { mutableStateOf(0) }
    var errMsg by rememberSaveable { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permTick++
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 状态卡
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = linkState) {
                is BtLink.State.Connected -> {
                    Text("💙", fontSize = 40.sp)
                    Text("已连接 ${s.peerName}", fontSize = 19.sp, fontWeight = FontWeight(800), modifier = Modifier.padding(top = 8.dp))
                    Text("蓝牙直连中，发爱心秒到 TA", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "断开",
                        color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight(600),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x15FF3B30))
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clickableNoIndication { BtLink.stop() },
                    )
                }
                BtLink.State.Waiting -> {
                    Text("👂", fontSize = 40.sp)
                    Text("等待 TA 连接…", fontSize = 19.sp, fontWeight = FontWeight(800), modifier = Modifier.padding(top = 8.dp))
                    Text("保持本页打开，让 TA 在列表里点你的手机", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                else -> {
                    Text("🔵", fontSize = 40.sp)
                    Text("蓝牙未连接", fontSize = 19.sp, fontWeight = FontWeight(800), modifier = Modifier.padding(top = 8.dp))
                    Text("近距离免服务器：发爱心、看在线状态", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "等待 TA 连接",
                        color = Pink, fontSize = 14.sp, fontWeight = FontWeight(600),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Pink.copy(alpha = 0.1f))
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                            .clickableNoIndication { BtLink.startWaiting { errMsg = it } },
                    )
                }
            }
        }
        errMsg?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(20.dp))

        if (BtLink.needsPermission()) {
            Button(
                onClick = { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("允许蓝牙权限", fontSize = 16.sp, fontWeight = FontWeight(700)) }
        } else {
            val devices = remember(permTick) { BtLink.bondedDevices() }
            if (devices.isEmpty()) {
                Text(
                    "还没有和 TA 配对过的蓝牙设备。\n请先在系统设置 → 蓝牙里，把两部手机互相配对一次，\n然后回到这里一键连接。",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            } else {
                Text("点 TA 的手机连接", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                devices.forEach { (addr, name) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("📱", fontSize = 22.sp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, fontSize = 16.sp, fontWeight = FontWeight(700))
                            Text(addr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                        }
                        Text(
                            "连接",
                            color = Pink, fontSize = 14.sp, fontWeight = FontWeight(600),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Pink.copy(alpha = 0.1f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickableNoIndication { BtLink.connect(addr) { errMsg = it } },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "蓝牙模式支持即时发爱心与在线状态；\n纪念日、游戏、每日一问请用互联网模式。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
