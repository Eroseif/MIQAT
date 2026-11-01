package com.miqat.app.zakat

import com.miqat.app.hijri.HijriCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Hawl = one complete lunar (Hijri) year, which is about 354 days, not a Gregorian year. */
object HawlMath {
    /** The Gregorian date on which the same Hijri day and month come round again, one Hijri year later. */
    fun dueDate(start: LocalDate, cal: HijriCalendar): LocalDate {
        val h = cal.fromGregorian(start)
        for (offset in 345L..365L) {
            val d = start.plusDays(offset)
            val x = cal.fromGregorian(d)
            if (x.year == h.year + 1 && x.month == h.month && x.day == h.day) return d
        }
        return start.plusDays(354)
    }

    fun isComplete(due: LocalDate, today: LocalDate): Boolean = !today.isBefore(due)

    fun daysRemaining(due: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, due)
}
