package com.miqat.app.timetable

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Offline astronomical prayer times (fallback only). These are NOT a mosque's timetable
 * and are always labelled as calculated in the UI. Shown in the phone's time zone.
 */
object CalculatedTimes {
    const val SOURCE = "Calculated: Muslim World League (Fajr 18°, Isha 17°), standard Asr"

    fun compute(lat: Double, lon: Double, date: LocalDate): Timetable? = try {
        val params = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        params.madhab = Madhab.SHAFI
        val pt = PrayerTimes(Coordinates(lat, lon), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fun f(d: Date?): String? = d?.let { fmt.format(it) }
        Timetable(
            TimetableKind.CALCULATED, SOURCE, null, date.toString(),
            listOf(
                PrayerRow("Fajr", f(pt.fajr), null),
                PrayerRow("Sunrise", f(pt.sunrise), null),
                PrayerRow("Dhuhr", f(pt.dhuhr), null),
                PrayerRow("Asr", f(pt.asr), null),
                PrayerRow("Maghrib", f(pt.maghrib), null),
                PrayerRow("Isha", f(pt.isha), null)
            ),
            emptyList(),
            System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}
