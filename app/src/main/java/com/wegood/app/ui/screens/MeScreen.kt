package com.wegood.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wegood.app.BuildConfig
import com.wegood.app.bt.BtLink
import com.wegood.app.data.DateMath
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.notify.ReminderScheduler
import com.wegood.app.notify.SseService
import com.wegood.app.ui.components.LargeTitle
import com.wegood.app.ui.components.ListCard
import com.wegood.app.ui.components.OnlineDot
import com.wegood.app.ui.components.SectionHeader
import com.wegood.app.ui.components.clickableNoIndication
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PinkGradient
import com.wegood.app.ui.theme.TextGray
import kotlinx.coroutines.launch

@Composable
fun MeScreen(state: com.wegood.app.data.MeResponse) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showNameEdit by remember { mutableStateOf(false) }
    var showServerEdit by remember { mutableStateOf(false) }
    var showUnbind by remember { mutableStateOf(false) }
    var realtime by remember { mutableStateOf(Prefs.realtimeEnabled) }
    var btMode by remember { mutableStateOf(Prefs.isBtMode) }
    val btLinkState by BtLink.state.collectAsState()
    val btPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            btMode = true
            Repo.setConnMode(true)
        }
    }
    val notifEnabled = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactAlarmOk = remember {
        Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("我的")

        // 资料卡
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PinkGradient),
                contentAlignment = Alignment.Center,
            ) { Text("💕", fontSize = 24.sp) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    "${Prefs.myName} ✏️",
                    fontSize = 20.sp, fontWeight = FontWeight(800),
                    modifier = Modifier.clickableNoIndication { showNameEdit = true },
                )
                Text(
                    "我的配对码 ${Prefs.myCode}（点击复制）",
                    fontSize = 13.sp, color = TextGray,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickableNoIndication {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("WeGood", Prefs.myCode))
                        },
                )
            }
        }

        // TA 的状态
        state.partner?.let { partner ->
            SectionHeader("我们", modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pink.copy(alpha = 0.10f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Text("💑", fontSize = 20.sp) }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(partner.name, fontSize = 17.sp, fontWeight = FontWeight(700))
                        Spacer(Modifier.size(8.dp))
                        OnlineDot(partner.online)
                        Text(
                            if (partner.online) "在线" else "离线",
                            fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    val days = state.couple?.let { DateMath.dayCount(DateMath.toDateStr(it.createdAt)) } ?: 0
                    Text("在一起 $days 天 · 心动实时同步中", fontSize = 13.sp, color = TextGray, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        SectionHeader("设置", modifier = Modifier.padding(top = 10.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListCard(
                icon = if (notifEnabled) "🔔" else "🔕",
                iconColor = if (notifEnabled) com.wegood.app.ui.theme.Green.copy(alpha = 0.15f) else Color(0x15FF3B30),
                title = "通知权限",
                subtitle = if (notifEnabled) "已开启，爱心与提醒都能送达" else "未开启，收不到 TA 的爱心哦",
                onClick = {
                    if (!notifEnabled) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            )
            if (!exactAlarmOk) {
                ListCard(
                    icon = "⏰",
                    iconColor = Color(0x15FF9500),
                    title = "精确闹钟权限",
                    subtitle = "开启后纪念日提醒更准时",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("实时守护", fontSize = 16.sp, fontWeight = FontWeight(600))
                    Text("后台保持连接，TA 发爱心秒级送达（更耗电）", fontSize = 13.sp, color = TextGray)
                }
                Switch(
                    checked = realtime,
                    onCheckedChange = {
                        realtime = it
                        Repo.setRealtime(it)
                        if (it) SseService.start(context) else SseService.stop(context)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pink),
                )
            }
            // 连接方式：互联网 / 蓝牙（用户自选）
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("连接方式", fontSize = 16.sp, fontWeight = FontWeight(600))
                    Text(
                        if (btMode) {
                            when (val s = btLinkState) {
                                is BtLink.State.Connected -> "蓝牙直连中 · ${s.peerName}（近距离免服务器）"
                                else -> "蓝牙直连：近距离免服务器，支持发爱心"
                            }
                        } else {
                            "互联网：远距离全功能（需可用的服务端）"
                        },
                        fontSize = 13.sp, color = TextGray,
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip("🌐 互联网", !btMode) {
                            if (btMode) {
                                btMode = false
                                Repo.setConnMode(false)
                            }
                        }
                        ModeChip("🔵 蓝牙", btMode) {
                            val granted = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
                                context, Manifest.permission.BLUETOOTH_CONNECT,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else if (!btMode) {
                                btMode = true
                                Repo.setConnMode(true)
                            }
                        }
                    }
                }
            }
            ListCard(
                icon = "🖥️",
                iconColor = Color(0x155E5CE6),
                title = "服务器地址",
                subtitle = Prefs.serverUrl,
                value = "修改",
                onClick = { showServerEdit = true },
            )
        }

        SectionHeader("其他", modifier = Modifier.padding(top = 10.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListCard(
                icon = "🔁",
                iconColor = Color(0x15AF52DE),
                title = "重排提醒",
                subtitle = "若提醒时间不准，点一下重新校准",
                onClick = { ReminderScheduler.reschedule(context, state.anniversaries) },
            )
            ListCard(
                icon = "💔",
                iconColor = Color(0x15FF3B30),
                title = "解除绑定",
                subtitle = "将清空共同的纪念日与游戏数据",
                onClick = { showUnbind = true },
            )
            Text(
                "WeGood v${BuildConfig.VERSION_NAME} · 为你们而做 ❤️",
                fontSize = 12.sp, color = TextGray,
                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(90.dp))
    }

    if (showNameEdit) {
        var newName by rememberSaveable { mutableStateOf(Prefs.myName) }
        AlertDialog(
            onDismissRequest = { showNameEdit = false },
            title = { Text("我的昵称") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it.take(20) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    showNameEdit = false
                    if (newName.isNotBlank()) scope.launch { Repo.updateName(newName.trim()) }
                }) { Text("保存", color = Pink) }
            },
            dismissButton = { TextButton(onClick = { showNameEdit = false }) { Text("取消") } },
        )
    }
    if (showServerEdit) {
        var server by rememberSaveable { mutableStateOf(Prefs.serverUrl) }
        var testing by remember { mutableStateOf(false) }
        var testResult by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showServerEdit = false },
            title = { Text("服务器地址") },
            text = {
                Column {
                    OutlinedTextField(value = server, onValueChange = { server = it; testResult = null }, singleLine = true)
                    Text(
                        "同一 WiFi：电脑运行 node server，手机填 http://电脑IP:3000\n异地使用请部署服务端（见项目 docs/DEPLOY.md）",
                        fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "测试连接",
                            color = Pink, fontWeight = FontWeight(600), fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Pink.copy(alpha = 0.1f))
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                                .clickableNoIndication {
                                    if (!testing) {
                                        testing = true; testResult = null
                                        scope.launch {
                                            val r = Repo.testServer(server)
                                            testing = false
                                            testResult = r.fold(
                                                onSuccess = { "✅ 服务器可达 · ${it}ms" },
                                                onFailure = { "❌ 连不上，请检查地址与网络" },
                                            )
                                        }
                                    }
                                },
                        )
                        if (testing) {
                            CircularProgressIndicator(Modifier.padding(start = 10.dp).size(16.dp), color = Pink, strokeWidth = 2.dp)
                        }
                    }
                    testResult?.let {
                        Text(
                            it,
                            fontSize = 13.sp, fontWeight = FontWeight(600),
                            color = if (it.startsWith("✅")) Color(0xFF30D158) else Color(0xFFFF3B30),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showServerEdit = false
                    Prefs.serverUrl = server
                    Repo.restartConnection()
                }) { Text("保存并重连", color = Pink) }
            },
            dismissButton = { TextButton(onClick = { showServerEdit = false }) { Text("取消") } },
        )
    }
    if (showUnbind) {
        AlertDialog(
            onDismissRequest = { showUnbind = false },
            title = { Text("解除绑定？") },
            text = { Text("解绑后你们的纪念日、游戏对局都会清空，双方都会收到解绑提示。确定要分开吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showUnbind = false
                    scope.launch { Repo.unpair() }
                }) { Text("确定解绑", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showUnbind = false }) { Text("再想想") } },
        )
    }
}

/** 连接方式分段选择的小胶囊 */
@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp, fontWeight = FontWeight(700),
        color = if (selected) Color.White else Pink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pink else Pink.copy(alpha = 0.1f))
            .clickableNoIndication(onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
