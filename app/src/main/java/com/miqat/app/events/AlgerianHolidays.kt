package com.miqat.app.events

import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.Occasion
import java.time.LocalDate

data class AlgDay(val name: String, val note: String)

/**
 * Algerian public holidays. Fixed-date holidays are exact. The Islamic holidays follow the selected Hijri
 * calendar, so they are estimates: Algeria confirms them by its own moon-sighting announcement.
 */
object AlgerianHolidays {
    private val fixed: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 1) to "New Year's Day",
        (1 to 12) to "Yennayer (Amazigh New Year)",
        (5 to 1) to "Labour Day",
        (7 to 5) to "Independence Day",
        (11 to 1) to "Revolution Day"
    )

    /** Islamic occasions that are public holidays in Algeria (shown as a note on the Islamic entry). */
    val islamicPublicHolidays: Set<Occasion> = setOf(
        Occasion.ISLAMIC_NEW_YEAR, Occasion.ASHURA, Occasion.MAWLID, Occasion.EID_AL_FITR, Occasion.EID_AL_ADHA
    )

    fun on(date: LocalDate, cal: HijriCalendar): List<AlgDay> {
        val out = ArrayList<AlgDay>()
        fixed[date.monthValue to date.dayOfMonth]?.let { out.add(AlgDay(it, "Public holiday in Algeria")) }
        val h = cal.fromGregorian(date)
        if (h.month == 10 && h.day == 2) {
            out.add(AlgDay("Eid al-Fitr, 2nd day", "Public holiday in Algeria · estimated · the government sometimes extends it"))
        }
        if (h.month == 12 && h.day == 11) {
            out.add(AlgDay("Eid al-Adha, 2nd day", "Public holiday in Algeria · estimated · the government sometimes extends it"))
        }
        return out
    }
}
