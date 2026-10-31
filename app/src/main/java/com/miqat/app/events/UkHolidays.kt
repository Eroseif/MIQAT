package com.miqat.app.events

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class UkDay(val name: String, val bankHoliday: Boolean)

/** UK (England & Wales) bank holidays and common observances, worked out by rule so they work offline for any year. */
object UkHolidays {
    private val cache = HashMap<Int, Map<LocalDate, List<UkDay>>>()

    fun on(date: LocalDate): List<UkDay> = forYear(date.year)[date] ?: emptyList()

    fun forYear(year: Int): Map<LocalDate, List<UkDay>> = cache.getOrPut(year) { build(year) }

    fun easter(y: Int): LocalDate {
        val a = y % 19
        val b = y / 100
        val c = y % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(y, month, day)
    }

    private fun build(y: Int): Map<LocalDate, List<UkDay>> {
        val out = LinkedHashMap<LocalDate, MutableList<UkDay>>()
        fun add(d: LocalDate, name: String, bank: Boolean) {
            out.getOrPut(d) { mutableListOf() }.add(UkDay(name, bank))
        }

        // New Year's Day (substitute day if it falls on a weekend)
        val ny = LocalDate.of(y, 1, 1)
        when (ny.dayOfWeek) {
            DayOfWeek.SATURDAY -> add(ny.plusDays(2), "New Year's Day (substitute day)", true)
            DayOfWeek.SUNDAY -> add(ny.plusDays(1), "New Year's Day (substitute day)", true)
            else -> add(ny, "New Year's Day", true)
        }

        val easter = easter(y)
        add(easter.minusDays(2), "Good Friday", true)
        add(easter, "Easter Sunday", false)
        add(easter.plusDays(1), "Easter Monday", true)
        add(easter.minusDays(21), "Mother's Day (UK)", false)

        add(LocalDate.of(y, 5, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)), "Early May bank holiday", true)
        add(LocalDate.of(y, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)), "Spring bank holiday", true)
        add(LocalDate.of(y, 8, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)), "Summer bank holiday", true)

        add(LocalDate.of(y, 2, 14), "Valentine's Day", false)
        add(LocalDate.of(y, 3, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY)), "Clocks go forward (UK)", false)
        add(LocalDate.of(y, 6, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY)).plusWeeks(2), "Father's Day (UK)", false)
        add(LocalDate.of(y, 10, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY)), "Clocks go back (UK)", false)
        add(LocalDate.of(y, 10, 31), "Halloween", false)
        add(LocalDate.of(y, 11, 5), "Bonfire Night", false)
        add(LocalDate.of(y, 11, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY)).plusWeeks(1), "Remembrance Sunday", false)

        // Christmas Day and Boxing Day, with substitute days
        val xmas = LocalDate.of(y, 12, 25)
        when (xmas.dayOfWeek) {
            DayOfWeek.FRIDAY -> {
                add(xmas, "Christmas Day", true)
                add(xmas.plusDays(3), "Boxing Day (substitute day)", true)
            }
            DayOfWeek.SATURDAY -> {
                add(xmas, "Christmas Day", false)
                add(xmas.plusDays(1), "Boxing Day", false)
                add(xmas.plusDays(2), "Christmas Day (substitute day)", true)
                add(xmas.plusDays(3), "Boxing Day (substitute day)", true)
            }
            DayOfWeek.SUNDAY -> {
                add(xmas, "Christmas Day", false)
                add(xmas.plusDays(1), "Boxing Day", true)
                add(xmas.plusDays(2), "Christmas Day (substitute day)", true)
            }
            else -> {
                add(xmas, "Christmas Day", true)
                add(xmas.plusDays(1), "Boxing Day", true)
            }
        }
        return out
    }
}
