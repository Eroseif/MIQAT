package com.miqat.app.core

import android.content.Context
import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.timetable.MethodPref

data class SavedLocation(val lat: Double, val lon: Double, val manual: Boolean)

/** Small key-value store for the last location and calendar settings. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("miqat_prefs", Context.MODE_PRIVATE)

    fun loadLocation(): SavedLocation? {
        if (!sp.contains("lat")) return null
        return SavedLocation(
            Double.fromBits(sp.getLong("lat", 0L)),
            Double.fromBits(sp.getLong("lon", 0L)),
            sp.getBoolean("manual", false)
        )
    }

    fun saveLocation(l: SavedLocation) {
        sp.edit()
            .putLong("lat", l.lat.toBits())
            .putLong("lon", l.lon.toBits())
            .putBoolean("manual", l.manual)
            .apply()
    }

    var calendarMethod: CalendarMethod
        get() = runCatching {
            CalendarMethod.valueOf(sp.getString("cal_method", CalendarMethod.UMM_AL_QURA.name)!!)
        }.getOrDefault(CalendarMethod.UMM_AL_QURA)
        set(v) = sp.edit().putString("cal_method", v.name).apply()

    var dayAdjustment: Int
        get() = sp.getInt("cal_adjust", 0).coerceIn(-1, 1)
        set(v) = sp.edit().putInt("cal_adjust", v.coerceIn(-1, 1)).apply()

    var methodPref: MethodPref
        get() = runCatching { MethodPref.valueOf(sp.getString("method_pref", MethodPref.AUTOMATIC.name)!!) }
            .getOrDefault(MethodPref.AUTOMATIC)
        set(v) = sp.edit().putString("method_pref", v.name).apply()

    /** Country code of the last resolved location (so the right method is used offline). */
    var countryCode: String?
        get() = sp.getString("country", null)
        set(v) = sp.edit().putString("country", v).apply()

    /** Religious rule: weight of 24K gold that sets the nisab (default 85 g). Kept as text, parsed with BigDecimal. */
    var nisabGrams: String
        get() = sp.getString("nisab_grams", "85") ?: "85"
        set(v) = sp.edit().putString("nisab_grams", v).apply()

    /** Religious rule: zakat rate in percent (default 2.5). */
    var zakatRatePercent: String
        get() = sp.getString("zakat_rate_pct", "2.5") ?: "2.5"
        set(v) = sp.edit().putString("zakat_rate_pct", v).apply()

    /** "GBP" by default, any currency code, or "AUTO" for the currency of the country you are in. */
    var zakatCurrency: String
        get() = sp.getString("zakat_currency2", "GBP") ?: "GBP"
        set(v) = sp.edit().putString("zakat_currency2", v).apply()

    /** Epoch day of the hawl end we already notified about (Long.MIN_VALUE = none). */
    var zakatNotifiedDue: Long
        get() = sp.getLong("zakat_notified", Long.MIN_VALUE)
        set(v) = sp.edit().putLong("zakat_notified", v).apply()
}
