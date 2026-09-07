package com.wegood.app.data

import android.content.Context
import android.content.SharedPreferences

/** 本地凭证与小组件/提醒所需的快照数据 */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("wegood", Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = sp.getString("serverUrl", null) ?: DEFAULT_SERVER
        set(v) = sp.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    var deviceId: String get() = sp.getString("deviceId", "") ?: ""
        set(v) = sp.edit().putString("deviceId", v).apply()
    var secret: String get() = sp.getString("secret", "") ?: ""
        set(v) = sp.edit().putString("secret", v).apply()
    var myName: String get() = sp.getString("myName", "宝贝") ?: "宝贝"
        set(v) = sp.edit().putString("myName", v).apply()
    var myCode: String get() = sp.getString("myCode", "") ?: ""
        set(v) = sp.edit().putString("myCode", v).apply()

    var partnerName: String get() = sp.getString("partnerName", "") ?: ""
        set(v) = sp.edit().putString("partnerName", v).apply()
    var partnerOnline: Boolean get() = sp.getBoolean("partnerOnline", false)
        set(v) = sp.edit().putBoolean("partnerOnline", v).apply()
    var coupleCreatedAt: Long get() = sp.getLong("coupleCreatedAt", -1L)
        set(v) = sp.edit().putLong("coupleCreatedAt", v).apply()

    /** 纪念日 JSON 快照：小组件与开机重排提醒使用（无需网络） */
    var anniversariesJson: String get() = sp.getString("annJson", "[]") ?: "[]"
        set(v) = sp.edit().putString("annJson", v).apply()

    var todaySentDate: String get() = sp.getString("todaySentDate", "") ?: ""
        set(v) = sp.edit().putString("todaySentDate", v).apply()
    var todaySentCount: Int get() = if (todaySentDate == DateMath.todayStr()) sp.getInt("todaySentCount", 0) else 0
        set(v) = sp.edit().putInt("todaySentCount", v).putString("todaySentDate", DateMath.todayStr()).apply()

    /** 最近一条动态（小组件底部展示） */
    var latestFeedText: String get() = sp.getString("latestFeedText", "") ?: ""
        set(v) = sp.edit().putString("latestFeedText", v).apply()
    var latestFeedTs: Long get() = sp.getLong("latestFeedTs", 0L)
        set(v) = sp.edit().putLong("latestFeedTs", v).apply()

    /** 已通知到的动态水位（漏发爱心的兜底轮询用） */
    var lastFeedTs: Long get() = sp.getLong("lastFeedTs", 0L)
        set(v) = sp.edit().putLong("lastFeedTs", v).apply()

    /** 实时守护（前台服务保持 SSE 长连接） */
    var realtimeEnabled: Boolean get() = sp.getBoolean("realtime", true)
        set(v) = sp.edit().putBoolean("realtime", v).apply()

    val paired: Boolean get() = coupleCreatedAt > 0 && partnerName.isNotEmpty()

    /** 解绑：清除配对数据但保留设备凭证与服务器设置 */
    fun clearPairing() {
        sp.edit()
            .putString("partnerName", "").putBoolean("partnerOnline", false)
            .putLong("coupleCreatedAt", -1L).putString("annJson", "[]")
            .putString("latestFeedText", "").putLong("latestFeedTs", 0L)
            .putLong("lastFeedTs", 0L).putInt("todaySentCount", 0)
            .apply()
    }

    const val DEFAULT_SERVER = "http://10.0.2.2:3000"
}
