package com.miqat.app.timetable

import com.miqat.app.data.CacheDao
import com.miqat.app.data.CacheEntry
import com.miqat.app.data.Mosque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

class TimetableRepository(
    private val dao: CacheDao,
    private val sources: List<OfficialTimetableSource>
) {
    suspend fun load(m: Mosque): TimetableBundle = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val calculated = CalculatedTimes.compute(m.lat, m.lon, today)
        val key = "tt:${m.id}:$today"
        val cached = dao.get(key)
        val now = System.currentTimeMillis()
        var official: Timetable? = null
        var stale = false
        var note = ""

        if (cached != null && now - cached.fetchedAt < TTL_MS) {
            if (isNone(cached.json)) note = NONE_NOTE else official = Timetable.fromJson(cached.json)
        } else {
            try {
                var found: Timetable? = null
                for (s in sources) {
                    found = s.fetch(m, today)
                    if (found != null) break
                }
                if (found != null) {
                    dao.put(CacheEntry(key, found.toJson(), now))
                    official = found
                } else {
                    dao.put(CacheEntry(key, "{\"none\":true}", now))
                    note = NONE_NOTE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached != null && !isNone(cached.json)) {
                    official = Timetable.fromJson(cached.json)
                    stale = true
                } else {
                    note = "Could not reach the timetable source (no internet connection?)."
                }
            }
        }
        TimetableBundle(today.toString(), official, stale, note, calculated)
    }

    private fun isNone(json: String) = JSONObject(json).has("none")

    companion object {
        const val TTL_MS = 6L * 60 * 60 * 1000
        const val NONE_NOTE = "No official timetable for this mosque was found in the supported sources."
    }
}
