package com.miqat.app.timetable

import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Astronomical prayer times for any GPS position, using the method that is usual for the area.
 * These are NOT a mosque's timetable and are always labelled as calculated.
 * Times are shown in the phone's time zone.
 */
object CalculatedTimes {
    fun compute(lat: Double, lon: Double, date: LocalDate, choice: MethodChoice): Timetable? = try {
        val coords = Coordinates(lat, lon)
        val dc = DateComponents(date.year, date.monthValue, date.dayOfMonth)
        fun params(madhab: Madhab): CalculationParameters {
            val p = choice.method.parameters
            p.madhab = madhab
            // Far from the equator the twilight angles can fail in summer; use the 1/7th-of-night rule.
            if (abs(lat) > 48.0) p.highLatitudeRule = HighLatitudeRule.SEVENTH_OF_THE_NIGHT
            return p
        }
        val std = PrayerTimes(coords, dc, params(Madhab.SHAFI))
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fun f(d: Date?): String? = d?.let { fmt.format(it) }
        Timetable(
            TimetableKind.CALCULATED, "Calculated: ${choice.label}", null, date.toString(),
            listOf(
                PrayerRow("Fajr", f(std.fajr), null),
                PrayerRow("Sunrise", f(std.sunrise), null),
                PrayerRow("Dhuhr", f(std.dhuhr), null),
                PrayerRow("Asr", f(std.asr), null),
                PrayerRow("Maghrib", f(std.maghrib), null),
                PrayerRow("Isha", f(std.isha), null)
            ),
            emptyList(),
            System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}
