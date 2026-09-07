package com.wegood.app.data

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wegood.app.notify.ReminderScheduler
import com.wegood.app.notify.SseService
import com.wegood.app.widget.WidgetsUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** UI 层事件：爱心/提醒横幅、配对状态变化、轻提示 */
sealed class UiEvent {
    data class Heart(val kind: String, val fromName: String) : UiEvent()
    data class Reminder(val name: String, val text: String) : UiEvent()
    data class PartnerPresence(val online: Boolean) : UiEvent()
    data object Paired : UiEvent()
    data object Unpaired : UiEvent()
    data class Toast(val msg: String) : UiEvent()
}

/** 中央仓库：REST + SSE 长连接 + 状态流 + 快照分发（小组件/提醒） */
object Repo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    private val _state = MutableStateFlow<MeResponse?>(null)
    val state = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val uiEvents = _uiEvents.asSharedFlow()

    @Volatile var isForeground = false

    private var streamJob: Job? = null
    private var sseCall: okhttp3.Call? = null
    private var initJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        initJob = initJob ?: scope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        try {
            ensureRegistered()
            refresh()
            if (Prefs.realtimeEnabled && Prefs.paired) {
                SseService.start(appContext)
            }
        } catch (e: Exception) {
            _uiEvents.tryEmit(UiEvent.Toast("连接服务器失败：${e.message ?: "请检查网络与服务器地址"}"))
        }
        // 兜底轮询：App 被杀/守护关闭时，15 分钟内补发漏掉的爱心通知
        runCatching {
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                "wegood-sync", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<com.wegood.app.work.SyncWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }

    suspend fun ensureRegistered() {
        if (Prefs.deviceId.isNotBlank()) return
        val r = Api.register(Prefs.myName)
        Prefs.deviceId = r.deviceId
        Prefs.secret = r.secret
        Prefs.myCode = r.code
        Prefs.myName = r.name
    }

    /** 拉取全量状态并分发到小组件/提醒 */
    suspend fun refresh() {
        if (Prefs.deviceId.isBlank()) return
        val resp = Api.me()
        // 漏掉的爱心：以动态流水位为准，仅当不在前台时补系统通知
        val fresh = resp.events.filter { it.ts > Prefs.lastFeedTs && it.kind == "heart" && it.fromName != Prefs.myName }
        if (fresh.isNotEmpty() && !isForeground) {
            com.wegood.app.notify.Notifications.showMissedHearts(appContext, fresh.size)
        }
        resp.events.maxOfOrNull { it.ts }?.let { if (it > Prefs.lastFeedTs) Prefs.lastFeedTs = it }

        _state.value = resp
        persistSnapshot(resp)
        WidgetsUpdater.updateAll(appContext)
        ReminderScheduler.reschedule(appContext, resp.anniversaries)
    }

    private fun persistSnapshot(resp: MeResponse) {
        Prefs.myName = resp.device.name
        Prefs.myCode = resp.device.code
        Prefs.partnerName = resp.partner?.name ?: ""
        Prefs.partnerOnline = resp.partner?.online ?: false
        Prefs.coupleCreatedAt = resp.couple?.createdAt ?: -1L
        Prefs.anniversariesJson = Api.json.encodeToString(resp.anniversaries)
        resp.events.lastOrNull()?.let {
            Prefs.latestFeedText = describeEvent(it)
            Prefs.latestFeedTs = it.ts
        }
    }

    private fun describeEvent(e: FeedEvent): String = when (e.kind) {
        "heart" -> "${e.fromName ?: "TA"} 发来 ${HEART_EMOJI[e.heartKind] ?: "❤️"}"
        "sys" -> e.text ?: ""
        else -> ""
    }

    // ---------- SSE ----------
    fun startStream() {
        if (streamJob?.isActive == true) return
        streamJob = scope.launch { sseLoop() }
    }

    fun stopStream() {
        sseCall?.cancel()
        streamJob?.cancel()
        streamJob = null
    }

    private suspend fun sseLoop() {
        while (currentCoroutineContext().isActive) {
            if (Prefs.deviceId.isBlank()) { delay(3000); continue }
            try {
                val req = Request.Builder()
                    .url("${Prefs.serverUrl}/api/stream?id=${Prefs.deviceId}&token=${Prefs.secret}")
                    .build()
                val call = Api.sseClient.newCall(req)
                sseCall = call
                call.execute().use { resp ->
                    if (!resp.isSuccessful) { delay(5000); return@use }
                    val src = resp.body?.source() ?: return@use
                    while (currentCoroutineContext().isActive) {
                        val line = src.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            runCatching { handleSse(Api.json.decodeFromString<SseEvent>(line.removePrefix("data: "))) }
                        }
                    }
                }
            } catch (_: Exception) {
                // 断线重连
            }
            if (!currentCoroutineContext().isActive) break
            delay(3000)
        }
    }

    private suspend fun handleSse(e: SseEvent) {
        when (e.type) {
            "heart" -> {
                appendLocalFeed(FeedEvent(id = "sse-${e.ts}", ts = e.ts, kind = "heart", heartKind = e.kind, fromName = e.fromName))
                _uiEvents.emit(UiEvent.Heart(e.kind ?: "heart", e.fromName ?: "TA"))
            }
            "reminder" -> {
                appendLocalFeed(FeedEvent(id = "sse-r-${e.ts}", ts = e.ts, kind = "sys", text = "提醒：${e.text ?: ""}"))
                _uiEvents.emit(UiEvent.Reminder(e.name ?: "", e.text ?: ""))
            }
            "paired" -> { refresh(); _uiEvents.emit(UiEvent.Paired); SseService.start(appContext) }
            "unpaired" -> {
                Prefs.clearPairing(); _state.update { it?.copy(partner = null, couple = null, anniversaries = emptyList(), ttt = null, daily = null) }
                ReminderScheduler.cancelAll(appContext); WidgetsUpdater.updateAll(appContext)
                SseService.stop(appContext); _uiEvents.emit(UiEvent.Unpaired)
            }
            "presence" -> {
                _state.update { s -> s?.copy(partner = s.partner?.copy(online = e.online ?: false)) }
                Prefs.partnerOnline = e.online ?: false
                WidgetsUpdater.updateAll(appContext)
                _uiEvents.emit(UiEvent.PartnerPresence(e.online ?: false))
            }
            "ttt" -> _state.update { it?.copy(ttt = e.ttt) }
            "anniversary_changed", "daily_answer", "partner_updated" -> refresh()
        }
    }

    private fun appendLocalFeed(ev: FeedEvent) {
        _state.update { s ->
            s?.copy(events = (s.events + ev).sortedBy { it.ts }.takeLast(30))
        }
        Prefs.latestFeedText = describeEvent(ev)
        Prefs.latestFeedTs = ev.ts
        WidgetsUpdater.updateAll(appContext)
    }

    // ---------- 操作 ----------
    suspend fun pair(code: String): Result<Unit> = runCatching {
        Api.pair(code.uppercase().trim())
        refresh()
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "绑定失败")) }

    suspend fun unpair(): Result<Unit> = runCatching {
        Api.unpair()
        Prefs.clearPairing()
        _state.update { it?.copy(partner = null, couple = null, anniversaries = emptyList(), ttt = null, daily = null) }
        ReminderScheduler.cancelAll(appContext); WidgetsUpdater.updateAll(appContext); SseService.stop(appContext)
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "解绑失败")) }

    suspend fun sendHeart(kind: String, silentError: Boolean = false): Result<Unit> = runCatching {
        Api.sendHeart(kind)
        Prefs.todaySentCount = Prefs.todaySentCount + 1
        Prefs.lastSentTs = System.currentTimeMillis()
        appendLocalFeed(FeedEvent(id = "mine-${System.currentTimeMillis()}", ts = System.currentTimeMillis(), kind = "heart", heartKind = kind, fromName = Prefs.myName))
        WidgetsUpdater.updateAll(appContext)
    }.onFailure {
        if (!silentError) _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "发送失败，请检查网络"))
    }

    suspend fun updateName(name: String): Result<Unit> = runCatching {
        Api.updateName(name)
        Prefs.myName = name
        refresh()
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "修改失败")) }

    suspend fun saveAnniversary(id: String?, input: AnniversaryInput): Result<Unit> = runCatching {
        if (id == null) Api.addAnniversary(input) else Api.updateAnniversary(id, input)
        refresh()
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "保存失败")) }

    suspend fun deleteAnniversary(id: String): Result<Unit> = runCatching {
        Api.deleteAnniversary(id)
        refresh()
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "删除失败")) }

    suspend fun tttMove(index: Int): Result<Unit> = runCatching {
        val r = Api.tttMove(index)
        _state.update { it?.copy(ttt = r.ttt) }
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "落子失败")) }

    suspend fun tttReset(): Result<Unit> = runCatching {
        val r = Api.tttReset()
        _state.update { it?.copy(ttt = r.ttt) }
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "重开失败")) }

    suspend fun dailyAnswer(text: String): Result<Unit> = runCatching {
        val r = Api.dailyAnswer(text)
        _state.update { it?.copy(daily = r.daily) }
    }.onFailure { _uiEvents.tryEmit(UiEvent.Toast(it.message ?: "提交失败")) }

    /** 修改服务器地址后重连 */
    fun restartConnection() {
        stopStream()
        if (Prefs.realtimeEnabled && Prefs.paired) SseService.start(appContext)
        scope.launch {
            runCatching { ensureRegistered(); refresh() }
                .onFailure { _uiEvents.tryEmit(UiEvent.Toast("连接服务器失败：${it.message ?: "请检查地址"}")) }
        }
    }

    fun setRealtime(enabled: Boolean) {
        Prefs.realtimeEnabled = enabled
        if (enabled && Prefs.paired) SseService.start(appContext) else SseService.stop(appContext)
    }
}
