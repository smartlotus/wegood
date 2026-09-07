package com.wegood.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wegood.app.data.Anniversary
import com.wegood.app.data.Api
import com.wegood.app.data.DateMath
import com.wegood.app.data.Prefs
import java.util.Calendar

/** 本地精确提醒：不依赖服务端在线，同步后重排；触发后按年重复续排 */
object ReminderScheduler {
    const val EXTRA_ID = "id"
    const val EXTRA_NAME = "name"
    const val EXTRA_DATE = "date"
    const val EXTRA_YEARLY = "yearly"

    private fun am(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, a: Anniversary): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, a.id)
            .putExtra(EXTRA_NAME, a.name)
            .putExtra(EXTRA_DATE, a.date)
            .putExtra(EXTRA_YEARLY, a.repeatYearly)
        return PendingIntent.getBroadcast(
            context, a.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun reschedule(context: Context, anns: List<Anniversary>) {
        for (a in anns) {
            cancel(context, a.id)
            val before = a.remindDaysBefore ?: continue
            val time = a.remindTime ?: continue
            val trigger = DateMath.triggerEpochMillis(a.date, a.repeatYearly, before, time)
            if (trigger <= System.currentTimeMillis()) continue
            val pi = pendingIntent(context, a)
            val alarm = am(context)
            if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                alarm.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, pi)
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        }
    }

    fun rescheduleFromPrefs(context: Context) {
        val anns = runCatching { Api.json.decodeFromString<List<Anniversary>>(Prefs.anniversariesJson) }.getOrDefault(emptyList())
        reschedule(context, anns)
    }

    fun cancel(context: Context, id: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        am(context).cancel(
            PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE),
        )
    }

    fun cancelAll(context: Context) {
        val anns = runCatching { Api.json.decodeFromString<List<Anniversary>>(Prefs.anniversariesJson) }.getOrDefault(emptyList())
        anns.forEach { cancel(context, it.id) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(ReminderScheduler.EXTRA_NAME) ?: return
        val date = intent.getStringExtra(ReminderScheduler.EXTRA_DATE) ?: return
        val yearly = intent.getBooleanExtra(ReminderScheduler.EXTRA_YEARLY, false)

        val days = DateMath.daysUntil(DateMath.nextOccurrence(date, yearly))
        val text = if (days <= 0) "「$name」就是今天！🎉" else "还有 $days 天就是「$name」啦"
        Notifications.showReminder(context, name, text)

        // 每年重复：续排下一年
        if (yearly) {
            val nextNow = Calendar.getInstance().apply { add(Calendar.DATE, 1) }
            val trigger = DateMath.triggerEpochMillis(date, true, 0, "09:00", nextNow)
            if (trigger > System.currentTimeMillis()) {
                val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = PendingIntent.getBroadcast(
                    context, "reschedule".hashCode(),
                    Intent(context, ReminderReceiver::class.java)
                        .putExtra(ReminderScheduler.EXTRA_NAME, name)
                        .putExtra(ReminderScheduler.EXTRA_DATE, date)
                        .putExtra(ReminderScheduler.EXTRA_YEARLY, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                    alarm.setWindow(AlarmManager.RTC_WAKEUP, trigger, 10 * 60_000L, pi)
                } else {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.rescheduleFromPrefs(context)
            com.wegood.app.widget.WidgetsUpdater.updateAll(context)
        }
    }
}
