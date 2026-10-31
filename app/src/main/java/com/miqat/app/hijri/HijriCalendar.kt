package com.miqat.app.hijri

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.temporal.ChronoField

enum class CalendarMethod(val label: String, val description: String) {
    UMM_AL_QURA(
        "Umm al-Qura (Saudi Arabia)",
        "Official Saudi civil calendar, calculated in advance."
    ),
    MOON_SIGHTING(
        "Local moon-sighting authority",
        "No offline source of local announcements exists, so this starts from Umm al-Qura. Use the day adjustment to match your local authority."
    ),
    CALCULATED(
        "Calculated astronomical calendar",
        "Tabular Islamic civil calendar (fixed 30-year cycle)."
    )
}

data class HijriDate(val year: Int, val month: Int, val day: Int)

enum class Occasion(val label: String, val short: String) {
    ISLAMIC_NEW_YEAR("Islamic New Year", "Islamic New Year"),
    ASHURA("Ashura", "Ashura"),
    MAWLID("Mawlid (observed by many; not an official holiday in Saudi Arabia)", "Mawlid"),
    RAMADAN_START("Start of Ramadan", "Start of Ramadan"),
    LAST_TEN("Last ten nights of Ramadan (Laylat al-Qadr period)", "Laylat al-Qadr period begins"),
    EID_AL_FITR("Eid al-Fitr", "Eid al-Fitr"),
    ARAFAH("Day of Arafah", "Day of Arafah"),
    EID_AL_ADHA("Eid al-Adha", "Eid al-Adha")
}

data class Upcoming(val date: LocalDate, val occasion: Occasion, val hijri: HijriDate, val daysAway: Long)

data class RamadanInfo(
    val inRamadan: Boolean,
    val dayOfRamadan: Int?,
    val startDate: LocalDate?,
    val eidDate: LocalDate?,
    val daysRemaining: Long,
    val hijriYear: Int
)

class HijriCalendar(val method: CalendarMethod, val adjustment: Int) {

    fun fromGregorian(date: LocalDate): HijriDate {
        val d = date.plusDays(adjustment.toLong())
        return if (method == CalendarMethod.CALCULATED) {
            civilFromJdn(d.toEpochDay() + EPOCH_DAY_TO_JDN)
        } else {
            try {
                val h = HijrahChronology.INSTANCE.date(d)
                HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
            } catch (e: DateTimeException) {
                civilFromJdn(d.toEpochDay() + EPOCH_DAY_TO_JDN)
            }
        }
    }

    fun occasions(h: HijriDate): List<Occasion> = buildList {
        if (h.month == 1 && h.day == 1) add(Occasion.ISLAMIC_NEW_YEAR)
        if (h.month == 1 && h.day == 10) add(Occasion.ASHURA)
        if (h.month == 3 && h.day == 12) add(Occasion.MAWLID)
        if (h.month == 9 && h.day == 1) add(Occasion.RAMADAN_START)
        if (h.month == 9 && h.day >= 21) add(Occasion.LAST_TEN)
        if (h.month == 10 && h.day == 1) add(Occasion.EID_AL_FITR)
        if (h.month == 12 && h.day == 9) add(Occasion.ARAFAH)
        if (h.month == 12 && h.day == 10) add(Occasion.EID_AL_ADHA)
    }

    /** Occasions from [from] over the next [days] days (the last-ten-nights period is listed once, at its start). */
    fun upcoming(from: LocalDate, days: Int): List<Upcoming> {
        val out = ArrayList<Upcoming>()
        for (i in 0..days) {
            val d = from.plusDays(i.toLong())
            val h = fromGregorian(d)
            for (o in occasions(h)) {
                if (o == Occasion.LAST_TEN && h.day != 21) continue
                out.add(Upcoming(d, o, h, i.toLong()))
            }
        }
        return out
    }

    fun nextEvent(from: LocalDate): Upcoming? = upcoming(from, 400).firstOrNull()

    /** Estimated, from the selected method. Never an official moon-sighting announcement. */
    fun ramadanInfo(today: LocalDate): RamadanInfo {
        val h = fromGregorian(today)
        if (h.month == 9) {
            var eid: LocalDate? = null
            for (i in 1..31) {
                val d = today.plusDays(i.toLong())
                if (fromGregorian(d).month == 10) { eid = d; break }
            }
            return RamadanInfo(true, h.day, today.minusDays((h.day - 1).toLong()), eid, 0, h.year)
        }
        for (i in 0..400) {
            val d = today.plusDays(i.toLong())
            val hd = fromGregorian(d)
            if (hd.month == 9 && hd.day == 1) return RamadanInfo(false, null, d, null, i.toLong(), hd.year)
        }
        return RamadanInfo(false, null, null, null, -1, h.year)
    }

    companion object {
        private const val EPOCH_DAY_TO_JDN = 2440588L

        val MONTH_NAMES = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
            "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )
        val MONTH_SHORT = listOf("Muh", "Saf", "Rab1", "Rab2", "Jum1", "Jum2", "Raj", "Sha", "Ram", "Shw", "DhQ", "DhH")

        fun monthName(m: Int) = MONTH_NAMES.getOrElse(m - 1) { "?" }
        fun monthShort(m: Int) = MONTH_SHORT.getOrElse(m - 1) { "?" }

        /** Tabular Islamic civil calendar from a Julian Day Number (integer-day form). */
        fun civilFromJdn(jd: Long): HijriDate {
            var l = jd - 1948440 + 10632
            val n = (l - 1) / 10631
            l = l - 10631 * n + 354
            val j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238)
            l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29
            val m = (24 * l) / 709
            val d = l - (709 * m) / 24
            val y = 30 * n + j - 30
            return HijriDate(y.toInt(), m.toInt(), d.toInt())
        }
    }
}
