package com.wegood.app

import com.wegood.app.data.DateMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class DateMathTest {
    private fun cal(y: Int, m: Int, d: Int, hh: Int = 12, mm: Int = 0): Calendar =
        GregorianCalendar(y, m - 1, d, hh, mm)

    @Test
    fun dayCount_countsTodayAsFirstDay() {
        assertEquals(1, DateMath.dayCount("2026-09-07", cal(2026, 9, 7)))
        assertEquals(2, DateMath.dayCount("2026-09-06", cal(2026, 9, 7)))
        assertEquals(366, DateMath.dayCount("2025-09-07", cal(2026, 9, 7)))
    }

    @Test
    fun daysUntil_and_daysSince() {
        assertEquals(1, DateMath.daysUntil("2026-09-08", cal(2026, 9, 7)))
        assertEquals(0, DateMath.daysUntil("2026-09-07", cal(2026, 9, 7)))
        assertEquals(1, DateMath.daysSince("2026-09-06", cal(2026, 9, 7)))
    }

    @Test
    fun yearly_occurrence_rolls_to_next_year_when_past() {
        assertEquals("2026-09-07", DateMath.nextOccurrence("2020-09-07", true, cal(2026, 9, 1)))
        // 当天（含 23:00）仍算今年的周年
        assertEquals("2026-09-07", DateMath.nextOccurrence("2020-09-07", true, cal(2026, 9, 7, 23, 0)))
        // 次日起滚到明年
        assertEquals("2027-09-07", DateMath.nextOccurrence("2020-09-07", true, cal(2026, 9, 8, 0, 30)))
        assertEquals("2020-09-07", DateMath.nextOccurrence("2020-09-07", false, cal(2026, 9, 7)))
    }

    @Test
    fun triggerDate_is_days_before_occurrence() {
        assertEquals("2026-09-06", DateMath.triggerDate("2026-09-07", false, 1, cal(2026, 9, 1)))
        assertEquals("2026-09-07", DateMath.triggerDate("2026-09-07", false, 0, cal(2026, 9, 1)))
    }

    @Test
    fun triggerEpochMillis_has_correct_hour() {
        val ms = DateMath.triggerEpochMillis("2026-09-07", false, 1, "09:30", cal(2026, 9, 1))
        val c = GregorianCalendar().apply { timeInMillis = ms }
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(8, c.get(Calendar.MONTH))
        assertEquals(6, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test
    fun relTime_buckets() {
        val now = 1_000_000_000_000L
        assertEquals("刚刚", DateMath.relTime(now - 30_000, now))
        assertEquals("5 分钟前", DateMath.relTime(now - 5 * 60_000, now))
        assertEquals("3 小时前", DateMath.relTime(now - 3 * 3_600_000, now))
        assertEquals("2 天前", DateMath.relTime(now - 2 * 86_400_000, now))
    }

    @Test
    fun timezoneIndependentMidnightMath() {
        // 今天中午 vs 零点附近：天数不应因时刻不同而漂移
        val noon = cal(2026, 9, 7, 12, 0)
        val early = cal(2026, 9, 7, 0, 30)
        assertEquals(DateMath.daysUntil("2026-09-10", noon), DateMath.daysUntil("2026-09-10", early))
        assertFalse(DateMath.daysUntil("2026-09-10", noon) != DateMath.daysUntil("2026-09-10", early))
        assertTrue(true)
    }
}
