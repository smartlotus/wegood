package com.wegood.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.wegood.app.MainActivity
import com.wegood.app.R
import com.wegood.app.data.Anniversary
import com.wegood.app.data.Api
import com.wegood.app.data.DateMath
import com.wegood.app.data.HEART_EMOJI
import com.wegood.app.data.Prefs
import com.wegood.app.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** 统一刷新所有小组件：状态变化/收到爱心/上下线都会调用 */
object WidgetsUpdater {
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val mgr = AppWidgetManager.getInstance(appContext) ?: return
        LoveCardWidget.renderAll(appContext, mgr)
        HeartButtonWidget.renderAll(appContext, mgr)
        AnniversaryWidget.renderAll(appContext, mgr)
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 11,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sendAction(context: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        context, kind.hashCode(),
        Intent(context, WidgetActionReceiver::class.java)
            .setAction(WidgetActionReceiver.ACTION_SEND).putExtra(WidgetActionReceiver.EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** 小组件互动动作：免打开 App 直接发送爱心/亲亲/抱抱 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "heart"
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!Prefs.paired) {
                    toast(context, "先打开 WeGood 完成配对哦")
                    return@launch
                }
                val r = Repo.sendHeart(kind)
                toast(context, if (r.isSuccess) "已发给 ${Prefs.partnerName} ${HEART_EMOJI[kind] ?: "❤️"}" else r.exceptionOrNull()?.message ?: "发送失败")
            } finally {
                result.finish()
            }
        }
    }

    private fun toast(context: Context, msg: String) =
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    companion object {
        const val ACTION_SEND = "com.wegood.app.widget.SEND"
        const val EXTRA_KIND = "kind"
    }
}

/** 情侣卡（4x2）：在一起天数 + 在线状态 + 快捷互动按钮 + 最近动态 */
class LoveCardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = renderAll(context, mgr)

    companion object {
        fun renderAll(context: Context, mgr: AppWidgetManager) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, LoveCardWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_love_card)
            if (Prefs.coupleCreatedAt > 0) {
                val cal = Calendar.getInstance().apply { timeInMillis = Prefs.coupleCreatedAt }
                v.setTextViewText(R.id.widget_days_num, "${DateMath.dayCount(DateMath.toDateStr(cal))}")
            } else {
                v.setTextViewText(R.id.widget_days_num, "-")
            }
            v.setTextViewText(
                R.id.widget_partner,
                when {
                    Prefs.partnerName.isEmpty() -> "还没绑定 TA"
                    Prefs.partnerOnline -> "${Prefs.partnerName} 在线"
                    else -> "${Prefs.partnerName} 离线"
                },
            )
            v.setViewVisibility(R.id.widget_online_dot, if (Prefs.partnerOnline) View.VISIBLE else View.GONE)
            val latest = if (Prefs.latestFeedText.isBlank()) "绑定律动的心，开始你们的互动吧 ❤️"
            else "${Prefs.latestFeedText} · ${DateMath.relTime(Prefs.latestFeedTs)}"
            v.setTextViewText(R.id.widget_latest, latest)

            v.setOnClickPendingIntent(R.id.widget_left, WidgetsUpdater.openAppIntent(context))
            v.setOnClickPendingIntent(R.id.widget_latest, WidgetsUpdater.openAppIntent(context))
            v.setOnClickPendingIntent(R.id.widget_btn_heart, WidgetsUpdater.sendAction(context, "heart"))
            v.setOnClickPendingIntent(R.id.widget_btn_kiss, WidgetsUpdater.sendAction(context, "kiss"))
            v.setOnClickPendingIntent(R.id.widget_btn_hug, WidgetsUpdater.sendAction(context, "hug"))
            return v
        }
    }
}

/** 一键爱心（2x2）：点击直接发送 */
class HeartButtonWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = renderAll(context, mgr)

    companion object {
        fun renderAll(context: Context, mgr: AppWidgetManager) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, HeartButtonWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_heart_button)
            v.setTextViewText(R.id.widget_heart_count, "今日已发 ${Prefs.todaySentCount} 颗")
            v.setOnClickPendingIntent(R.id.widget_heart_root, WidgetsUpdater.sendAction(context, "heart"))
            return v
        }
    }
}

/** 纪念日倒计时（4x2）：最近的纪念日 / 倒计时 */
class AnniversaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = renderAll(context, mgr)

    companion object {
        fun renderAll(context: Context, mgr: AppWidgetManager) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, AnniversaryWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_anniversary)
            val anns = runCatching { Api.json.decodeFromString<List<Anniversary>>(Prefs.anniversariesJson) }.getOrDefault(emptyList())
            if (anns.isEmpty()) {
                v.setTextViewText(R.id.widget_ann_label, "纪念日")
                v.setTextViewText(R.id.widget_ann_name, "点我添加你们的纪念日")
                v.setTextViewText(R.id.widget_ann_num, "·")
                v.setTextViewText(R.id.widget_ann_unit, "")
                v.setTextViewText(R.id.widget_ann_date, "")
            } else {
                val future = anns
                    .map { it to DateMath.daysUntil(DateMath.nextOccurrence(it.date, it.repeatYearly)) }
                    .filter { it.second >= 0 }
                    .minByOrNull { it.second }
                val picked = future ?: anns
                    .map { it to DateMath.daysSince(it.date) }
                    .minByOrNull { it.second }!!
                v.setTextViewText(R.id.widget_ann_label, if (future != null) "纪念日 · 距离" else "纪念日 · 已经")
                v.setTextViewText(R.id.widget_ann_name, picked.first.name)
                v.setTextViewText(R.id.widget_ann_num, "${picked.second}")
                v.setTextViewText(R.id.widget_ann_unit, " 天")
                v.setTextViewText(R.id.widget_ann_date, picked.first.date)
            }
            v.setOnClickPendingIntent(R.id.widget_ann_root, WidgetsUpdater.openAppIntent(context))
            return v
        }
    }
}
