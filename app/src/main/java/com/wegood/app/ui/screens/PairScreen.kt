package com.wegood.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.ui.components.clickableNoIndication
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.PinkGradient
import kotlinx.coroutines.launch

/** 未配对时的全屏配对页：我的配对码 + 绑定 TA */
@Composable
fun PairScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var myCode by remember { mutableStateOf(Prefs.myCode.ifBlank { null }) }
    var partnerCode by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var server by rememberSaveable { mutableStateOf(Prefs.serverUrl) }
    var showServer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (myCode == null) {
            runCatching { Repo.ensureRegistered() }
                .onSuccess { myCode = Prefs.myCode }
                .onFailure { error = "无法连接服务器，请检查下方服务器地址" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(PinkGradient),
            contentAlignment = Alignment.Center,
        ) { Text("💕", fontSize = 40.sp) }
        Text("WeGood", fontSize = 32.sp, fontWeight = FontWeight(800), modifier = Modifier.padding(top = 12.dp))
        Text("把两颗心连在一起", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

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
            if (myCode == null) {
                CircularProgressIndicator(Modifier.size(26.dp).padding(top = 8.dp), color = Pink, strokeWidth = 3.dp)
            } else {
                Text(
                    myCode!!,
                    fontSize = 40.sp, fontWeight = FontWeight(800), letterSpacing = 8.sp, color = Pink,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
        Spacer(Modifier.height(40.dp))
    }
}
