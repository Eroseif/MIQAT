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
}
