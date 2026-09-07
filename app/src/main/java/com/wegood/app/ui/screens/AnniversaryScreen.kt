package com.wegood.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import com.wegood.app.data.Anniversary
import com.wegood.app.data.AnniversaryInput
import com.wegood.app.data.DateMath
import com.wegood.app.data.Repo
import com.wegood.app.ui.components.LargeTitle
import com.wegood.app.ui.components.MetricCard
import com.wegood.app.ui.components.clickableNoIndication
import com.wegood.app.ui.theme.Orange
import com.wegood.app.ui.theme.OrangeGradient
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.TextGray
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 纪念日/倒计时：双方同步可见，支持每年重复与提前提醒 */
@Composable
fun AnniversaryScreen(state: com.wegood.app.data.MeResponse) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Anniversary?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumnSafe(state) { ann ->
            editing = ann
            showEditor = true
        }
        FloatingActionButton(
            onClick = { editing = null; showEditor = true },
            containerColor = Orange,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
        ) { Text("＋", fontSize = 22.sp, fontWeight = FontWeight(700)) }
    }

    if (showEditor) {
        AnnEditor(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { input ->
                showEditor = false
                scope.launch { Repo.saveAnniversary(editing?.id, input) }
            },
            onDelete = editing?.let { ann ->
                {
                    showEditor = false
                    scope.launch { Repo.deleteAnniversary(ann.id) }
                }
            },
        )
    }
}

@Composable
private fun LazyColumnSafe(state: com.wegood.app.data.MeResponse, onEdit: (Anniversary) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LargeTitle("纪念日")
        if (state.anniversaries.isEmpty()) {
            Text(
                "还没有纪念日。记录在一起的日子、生日、旅行计划……\n过去的日期会累计天数，未来的日期会倒计时。",
                fontSize = 13.sp, color = TextGray, lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.anniversaries.sortedBy { it.date }.forEach { ann ->
                val past = DateMath.daysSince(ann.date) >= 0
                val nextOcc = DateMath.nextOccurrence(ann.date, ann.repeatYearly)
                val (prefix, days) = when {
                    !past -> "还有" to DateMath.daysUntil(ann.date)
                    ann.repeatYearly -> "距离下次" to DateMath.daysUntil(nextOcc)
                    else -> "已经" to DateMath.daysSince(ann.date)
                }
                val tip = buildString {
                    append(ann.date)
                    if (ann.repeatYearly) append(" · 每年重复")
                    if (ann.remindDaysBefore != null && ann.remindTime != null) {
                        append(" · 提前${if (ann.remindDaysBefore == 0) "当天" else "${ann.remindDaysBefore}天"} ${ann.remindTime} 提醒")
                    }
                }
                MetricCard(
                    title = ann.name,
                    value = "$days",
                    unit = " 天",
                    valuePrefix = prefix,
                    caption = tip,
                    icon = if (past) "📍" else "⏳",
                    brush = OrangeGradient,
                    onClick = { onEdit(ann) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnEditor(
    initial: Anniversary?,
    onDismiss: () -> Unit,
    onSave: (AnniversaryInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var date by rememberSaveable { mutableStateOf(initial?.date ?: "") }
    var yearly by rememberSaveable { mutableStateOf(initial?.repeatYearly ?: false) }
    var remindDays by rememberSaveable { mutableStateOf(initial?.remindDaysBefore?.toString() ?: "") }
    var time by rememberSaveable { mutableStateOf(initial?.remindTime ?: "09:00") }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                if (initial == null) "新建纪念日" else "编辑纪念日",
                fontSize = 20.sp, fontWeight = FontWeight(700),
                modifier = Modifier.padding(bottom = 14.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20); error = null },
                label = { Text("名称（如：在一起 / TA 的生日）") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("日期（过去=纪念日，未来=倒计时）") },
                    placeholder = { Text("选择一个日期") },
                    trailingIcon = { Text("📅") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .clickableNoIndication { showDatePicker = true },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("每年重复", fontSize = 16.sp, fontWeight = FontWeight(600))
                    Text("生日、周年类日期打开", fontSize = 12.sp, color = TextGray)
                }
                Switch(
                    checked = yearly,
                    onCheckedChange = { yearly = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Orange),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("提醒", fontSize = 16.sp, fontWeight = FontWeight(600), modifier = Modifier.padding(vertical = 6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("不提醒" to "", "当天" to "0", "提前1天" to "1", "提前3天" to "3", "提前7天" to "7").forEach { (label, v) ->
                    val selected = remindDays == v
                    Text(
                        label,
                        fontSize = 13.sp,
                        color = if (selected) Color.White else Color.Gray,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Orange else Color(0xFFEFEFF4))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clickableNoIndication { remindDays = v },
                    )
                }
            }
            if (remindDays.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("提醒时刻", fontSize = 15.sp)
                    Text(
                        time,
                        fontSize = 15.sp, fontWeight = FontWeight(700), color = Orange,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Orange.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                            .clickableNoIndication {
                                val parts = time.split(":").map { it.toInt() }
                                TimePickerDialog(context, { _, h, m ->
                                    time = "%02d:%02d".format(h, m)
                                }, parts[0], parts[1], true).show()
                            },
                    )
                }
                Text(
                    "到点会给你们双方都发提醒通知",
                    fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(top = 4.dp),
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (name.isBlank()) { error = "请填写名称"; return@Button }
                    if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) { error = "请选择日期"; return@Button }
                    onSave(
                        AnniversaryInput(
                            name = name.trim(), date = date, repeatYearly = yearly,
                            remindDaysBefore = remindDays.ifEmpty { null }?.toInt(),
                            remindTime = if (remindDays.isEmpty()) null else time,
                        ),
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight(700)) }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("删除这个纪念日", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDatePicker) {
        val initialUtc = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = dpState) }
    }
}
