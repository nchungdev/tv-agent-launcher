package com.gathin.tvhome.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.ArrayList
import java.util.Collections

object LunarCalendar {

    const val TIME_ZONE = 7.0

    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    class Date(
        @JvmField val day: Int,
        @JvmField val month: Int,
        @JvmField val year: Int,
        @JvmField val leap: Boolean
    )

    enum class Holiday(val lunar: Boolean, val day: Int, val month: Int) {
        NEW_YEAR(false, 1, 1),
        TET(true, 1, 1),
        HUNG_KINGS(true, 10, 3),
        REUNIFICATION(false, 30, 4),
        LABOUR(false, 1, 5),
        MID_AUTUMN(true, 15, 8),
        NATIONAL_DAY(false, 2, 9),
        CHRISTMAS(false, 25, 12);

        fun onOrAfter(from: LocalDate): LocalDate? {
            for (year in from.year - 1..from.year + 1) {
                val d = if (lunar) toSolar(day, month, year, false) else LocalDate.of(year, month, day)
                if (d != null && !d.isBefore(from)) return d
            }
            return null
        }
    }

    class Upcoming(@JvmField val holiday: Holiday, @JvmField val days: Long)

    @JvmStatic
    fun nextHoliday(today: LocalDate): Upcoming? {
        val all = upcoming(today)
        return if (all.isEmpty()) null else all[0]
    }

    @JvmStatic
    fun upcoming(today: LocalDate): List<Upcoming> {
        val all = ArrayList<Upcoming>()
        for (h in Holiday.values()) {
            val d = h.onOrAfter(today)
            if (d != null) all.add(Upcoming(h, ChronoUnit.DAYS.between(today, d)))
        }
        all.sortWith { a, b -> a.days.compareTo(b.days) }
        return all
    }

    @JvmStatic
    fun canChi(lunarYear: Int): String {
        return CAN[(lunarYear + 6) % 10] + " " + CHI[(lunarYear + 8) % 12]
    }

    @JvmStatic
    fun toLunar(solar: LocalDate): Date {
        val dayNumber = jdFromDate(solar.dayOfMonth, solar.monthValue, solar.year)
        val k = Math.floor((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)
        val yy = solar.year
        var a11 = lunarMonth11(yy)
        var b11 = a11
        val lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = (monthStart - a11) / 29
        var leap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapMonthDiff = leapMonthOffset(a11)
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10
                if (diff == leapMonthDiff) leap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        var finalYear = lunarYear
        if (lunarMonth >= 11 && diff < 4) finalYear -= 1
        return Date(lunarDay, lunarMonth, finalYear, leap)
    }

    @JvmStatic
    fun toSolar(lunarDay: Int, lunarMonth: Int, lunarYear: Int, leap: Boolean): LocalDate? {
        val a11: Int
        val b11: Int
        if (lunarMonth < 11) {
            a11 = lunarMonth11(lunarYear - 1)
            b11 = lunarMonth11(lunarYear)
        } else {
            a11 = lunarMonth11(lunarYear)
            b11 = lunarMonth11(lunarYear + 1)
        }
        val k = Math.floor(0.5 + (a11 - 2415021.076998695) / 29.530588853).toInt()
        var off = lunarMonth - 11
        if (off < 0) off += 12
        if (b11 - a11 > 365) {
            val leapOff = leapMonthOffset(a11)
            var leapMonth = leapOff - 2
            if (leapMonth < 0) leapMonth += 12
            if (leap && lunarMonth != leapMonth) return null
            if (leap || off >= leapOff) off += 1
        } else if (leap) {
            return null
        }
        val monthStart = newMoonDay(k + off)
        return fromJd(monthStart + lunarDay - 1)
    }

    @JvmStatic
    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    private fun fromJd(jd: Int): LocalDate {
        val a: Int
        val b: Int
        val c: Int
        if (jd > 2299160) {
            a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - b * 146097 / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return LocalDate.of(year, month, day)
    }

    private fun newMoonDay(k: Int): Int {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m)
        c1 -= 0.4068 * Math.sin(mpr * dr) - 0.0161 * Math.sin(dr * 2 * mpr)
        c1 -= 0.0004 * Math.sin(dr * 3 * mpr)
        c1 += 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr))
        c1 -= 0.0074 * Math.sin(dr * (m - mpr)) - 0.0004 * Math.sin(dr * (2 * f + m))
        c1 -= 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr))
        c1 += 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m))
        val deltaT = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return Math.floor(jd1 + c1 - deltaT + 0.5 + TIME_ZONE / 24).toInt()
    }

    private fun sunLongitude(jdn: Int): Int {
        val t = (jdn - 2451545.5 - TIME_ZONE / 24) / 36525
        val t2 = t * t
        val dr = Math.PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= Math.PI * 2 * Math.floor(l / (Math.PI * 2))
        return Math.floor(l / Math.PI * 6).toInt()
    }

    private fun lunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021
        val k = Math.floor(off / 29.530588853).toInt()
        var nm = newMoonDay(k)
        if (sunLongitude(nm) >= 9) nm = newMoonDay(k - 1)
        return nm
    }

    private fun leapMonthOffset(a11: Int): Int {
        val k = Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = sunLongitude(newMoonDay(k + i))
        do {
            last = arc
            i++
            arc = sunLongitude(newMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }
}
