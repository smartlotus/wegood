package com.wegood.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wegood.app.MainActivity
import com.wegood.app.R

object Notifications {
    private const val CH_HEARTS = "hearts"
    private const val CH_REMINDERS = "reminders"
    private const val CH_SERVICE = "service"
    private const val ID_HEART = 2001
    private const val ID_REMINDER = 2002
    const val ID_SERVICE = 1001

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun createChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_HEARTS, "爱心互动", NotificationManager.IMPORTANCE_HIGH).apply { description = "TA 给你发来的爱心与亲亲" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "纪念日提醒", NotificationManager.IMPORTANCE_HIGH).apply { description = "自定义的纪念日提醒" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "实时守护", NotificationManager.IMPORTANCE_LOW).apply { description = "保持与 TA 的实时连接" },
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun showHeart(context: Context, kind: String, fromName: String) {
        val emoji = mapOf("heart" to "❤️", "kiss" to "😘", "hug" to "🤗", "rose" to "🌹", "miss" to "💭")[kind] ?: "❤️"
        val n = NotificationCompat.Builder(context, CH_HEARTS)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setColor(0xFFFF2D55.toInt())
            .setContentTitle("$emoji $fromName 给你发来了爱心")
            .setContentText("点开看看吧")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        post(context, ID_HEART, n)
    }

    fun showMissedHearts(context: Context, count: Int) {
        val n = NotificationCompat.Builder(context, CH_HEARTS)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setColor(0xFFFF2D55.toInt())
            .setContentTitle("❤️ TA 想你啦")
            .setContentText("你们有 $count 条新互动，点开看看")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        post(context, ID_HEART, n)
    }

    fun showReminder(context: Context, name: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setColor(0xFFFF9500.toInt())
            .setContentTitle("⏰ 纪念日提醒")
            .setContentText(text.ifBlank { "「$name」就要到啦" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context))
            .build()
        post(context, ID_REMINDER, n)
    }

    fun serviceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setColor(0xFFFF2D55.toInt())
            .setContentTitle("WeGood 正在守护你们的心动")
            .setContentText("TA 发来爱心时会第一时间通知你")
            .setOngoing(true)
            .setContentIntent(contentIntent(context))
            .build()
}
