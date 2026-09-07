package com.wegood.app.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import com.wegood.app.data.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 实时守护：前台服务保持 SSE 长连接，App 在后台时也能第一时间收到爱心通知 */
class SseService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(Notifications.ID_SERVICE, Notifications.serviceNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.ID_SERVICE, Notifications.serviceNotification(this))
        }
        Repo.startStream()
        scope.launch {
            Repo.uiEvents.collect { e ->
                if (!Repo.isForeground) {
                    when (e) {
                        is UiEvent.Heart -> Notifications.showHeart(this@SseService, e.kind, e.fromName)
                        is UiEvent.Reminder -> Notifications.showReminder(this@SseService, e.name, e.text)
                        is UiEvent.Unpaired -> stopSelf()
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        if (!Prefs.realtimeEnabled) Repo.stopStream()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            if (Prefs.realtimeEnabled && Prefs.paired) {
                ContextCompat.startForegroundService(context, Intent(context, SseService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SseService::class.java))
        }
    }
}
