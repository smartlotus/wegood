package com.wegood.app.data

import java.util.Calendar
import java.util.GregorianCalendar

/** 日期纯函数（与服务端 dates.js 逻辑一致，便于单元测试） */
object DateMath {
    const val DAY = 86_400_000L

    fun toDateStr(c: Calendar): String {
        val y = c.get(Calendar.YEAR)
        val m = c.get(Calendar.MONTH) + 1
        val d = c.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    /** epoch 毫秒 → 'yyyy-MM-dd'（本地时区） */
    fun toDateStr(millis: Long): String = toDateStr(Calendar.getInstance().apply { timeInMillis = millis })

    fun todayStr(now: Calendar = Calendar.getInstance()): String = toDateStr(now)

    /** 'yyyy-MM-dd' -> 当日本地零点 */
    fun parseDate(s: String): Long {
        val p = s.split("-").map { it.toInt() }
        return GregorianCalendar(p[0], p[1] - 1, p[2]).apply { zeroTime() }.timeInMillis
    }

    /** 过去日期距今天数（date=昨天 → 1，date=今天 → 0） */
    fun daysSince(dateStr: String, now: Calendar = Calendar.getInstance()): Int {
        val today = (now.clone() as Calendar).apply { zeroTime() }
        return (((today.timeInMillis - parseDate(dateStr)) / DAY)).toInt()
    }

    /** 未来日期剩余天数（date=明天 → 1，date=今天 → 0） */
    fun daysUntil(dateStr: String, now: Calendar = Calendar.getInstance()): Int {
        val today = (now.clone() as Calendar).apply { zeroTime() }
        return (((parseDate(dateStr) - today.timeInMillis) / DAY)).toInt()
    }

    /** "在一起第 N 天"：当天即第 1 天 */
    fun dayCount(dateStr: String, now: Calendar = Calendar.getInstance()): Int = daysSince(dateStr, now) + 1

    /** 每年重复时取今年（已过则明年）的日期；非重复原样返回。闌年 2/29 按日历滚动到 3/1 */
    fun nextOccurrence(dateStr: String, repeatYearly: Boolean, now: Calendar = Calendar.getInstance()): String {
        if (!repeatYearly) return dateStr
        val parts = dateStr.split("-")
        val y = now.get(Calendar.YEAR)
        val thisYear = "%04d-%02d-%02d".format(y, parts[1].toInt(), parts[2].toInt())
        return if (parseDate(thisYear) >= parseDate(todayStr(now))) thisYear
        else "%04d-%02d-%02d".format(y + 1, parts[1].toInt(), parts[2].toInt())
    }

    /** 提醒触发日 = 目标日提前 remindDaysBefore 天 */
    fun triggerDate(dateStr: String, repeatYearly: Boolean, remindDaysBefore: Int, now: Calendar = Calendar.getInstance()): String {
        val occ = nextOccurrence(dateStr, repeatYearly, now)
        return toDateStr(GregorianCalendar().apply { timeInMillis = parseDate(occ) - remindDaysBefore * DAY })
    }

    /** 触发时刻的 epoch 毫秒（triggerDate + remindTime 本地时间） */
    fun triggerEpochMillis(dateStr: String, repeatYearly: Boolean, remindDaysBefore: Int, remindTime: String, now: Calendar = Calendar.getInstance()): Long {
        val d = triggerDate(dateStr, repeatYearly, remindDaysBefore, now).split("-").map { it.toInt() }
        val t = remindTime.split(":").map { it.toInt() }
        val c = GregorianCalendar(d[0], d[1] - 1, d[2], t[0], t[1])
        return c.timeInMillis
    }

    fun nowHHmm(now: Calendar = Calendar.getInstance()): String =
        "%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))

    fun relTime(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - ts) / 1000
        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60} 分钟前"
            diff < 86400 -> "${diff / 3600} 小时前"
            diff < 86400 * 2 -> "昨天"
            else -> "${diff / 86400} 天前"
        }
    }

    // 注意：不能用 clear(field)——HOUR_OF_DAY 被 clear 后 GregorianCalendar 会回落到 AM_PM/HOUR，
    // 12:00 的日历清零后仍是 12:00；必须显式 set 0
    private fun Calendar.zeroTime() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}
