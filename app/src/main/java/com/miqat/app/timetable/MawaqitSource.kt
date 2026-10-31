package com.miqat.app.timetable

import com.miqat.app.core.Geo
import com.miqat.app.data.Mosque
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A pluggable source of a mosque's OFFICIAL published timetable. Add more implementations later. */
interface OfficialTimetableSource {
    val name: String

    /** Returns null when this source has no timetable for the mosque; throws IOException on network failure. */
    suspend fun fetch(mosque: Mosque, date: LocalDate): Timetable?
}

/**
 * Mawaqit (mawaqit.net) publishes timetables that mosques maintain themselves.
 * Uses the public nearest-mosque search, matches by location (and name), and reads today's times.
 * Unofficial use of a public endpoint: results are cached and requested only when a mosque is opened.
 */
class MawaqitSource(private val http: OkHttpClient) : OfficialTimetableSource {
    override val name = "Mawaqit (mawaqit.net)"

    override suspend fun fetch(mosque: Mosque, date: LocalDate): Timetable? {
        val url = "https://mawaqit.net/api/2.0/mosque/search?lat=${mosque.lat}&lon=${mosque.lon}"
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403 || r.code == 404) return null
            if (!r.isSuccessful) throw IOException("Mawaqit HTTP ${r.code}")
            r.body?.string() ?: throw IOException("Empty response")
        }
        val arr = parseArray(body)
        var best: JSONObject? = null
        var bestD = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val la = o.optDouble("latitude", Double.NaN)
            val lo = o.optDouble("longitude", Double.NaN)
            if (la.isNaN() || lo.isNaN()) continue
            val d = Geo.distanceMeters(mosque.lat, mosque.lon, la, lo)
            val sameName = sharesNameToken(mosque.name, o.optString("name") + " " + o.optString("label"))
            if ((d <= 120 || (d <= 300 && sameName)) && d < bestD) {
                best = o
                bestD = d
            }
        }
        val o = best ?: return null
        val times = o.optJSONArray("times") ?: return null
        if (times.length() < 6) return null
        val t = List(6) { times.optString(it) }
        if (t.any { !TIME.matches(it) }) return null

        val iqamaOn = o.optBoolean("iqamaEnabled", true)
        val iq = o.optJSONArray("iqama")
        fun iqama(idx: Int, start: String): String? =
            if (!iqamaOn || iq == null || idx >= iq.length()) null else parseIqama(start, iq.optString(idx))

        val rows = listOf(
            PrayerRow("Fajr", pad(t[0]), iqama(0, t[0])),
            PrayerRow("Sunrise", pad(t[1]), null),
            PrayerRow("Dhuhr", pad(t[2]), iqama(1, t[2])),
            PrayerRow("Asr", pad(t[3]), iqama(2, t[3])),
            PrayerRow("Maghrib", pad(t[4]), iqama(3, t[4])),
            PrayerRow("Isha", pad(t[5]), iqama(4, t[5]))
        )
        val jumuah = listOf("jumua", "jumua2", "jumua3").map { o.optString(it) }.filter { TIME.matches(it) }.map { pad(it) }
        val slug = o.optString("slug")
        return Timetable(
            TimetableKind.OFFICIAL, name,
            if (slug.isNotBlank()) "https://mawaqit.net/en/$slug" else mosque.website,
            date.toString(), rows, jumuah, System.currentTimeMillis()
        )
    }

    private fun parseArray(body: String): JSONArray {
        val s = body.trim()
        if (s.startsWith("[")) return JSONArray(s)
        val o = JSONObject(s)
        return o.optJSONArray("mosques") ?: o.optJSONArray("data") ?: JSONArray()
    }

    private fun pad(s: String) = if (s.length == 4) "0$s" else s

    private fun parseIqama(start: String, raw: String): String? {
        val r = raw.trim()
        if (r.isEmpty() || r == "null") return null
        if (TIME.matches(r)) return pad(r)
        val m = Regex("^\\+(\\d{1,3})$").matchEntire(r) ?: return null
        val base = LocalTime.parse(start, DateTimeFormatter.ofPattern("H:mm"))
        return base.plusMinutes(m.groupValues[1].toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun sharesNameToken(a: String, b: String): Boolean {
        fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 4 && it != "mosque" && it != "masjid" }.toSet()
        return tokens(a).intersect(tokens(b)).isNotEmpty()
    }

    companion object {
        private val TIME = Regex("^\\d{1,2}:\\d{2}$")
    }
}
