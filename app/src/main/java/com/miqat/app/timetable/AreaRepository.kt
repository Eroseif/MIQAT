package com.miqat.app.timetable

import com.miqat.app.data.CacheDao
import com.miqat.app.data.CacheEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/** Finds official mosque timetables around a GPS position (the "area" view). Cached for 3 h, reused offline. */
class AreaRepository(private val dao: CacheDao, private val mawaqit: MawaqitSource) {

    class Result(val mosques: List<NearbyOfficial>, val stale: Boolean, val note: String?)

    suspend fun officialNearby(lat: Double, lon: Double): Result = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val cLat = Math.round(lat * 100) / 100.0
        val cLon = Math.round(lon * 100) / 100.0
        val key = String.format(Locale.US, "area:%.2f:%.2f:%s", cLat, cLon, today)
        val cached = dao.get(key)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < TTL_MS) {
            return@withContext Result(decode(cached.json), false, null)
        }
        try {
            val list = mawaqit.nearby(cLat, cLon, today, SEARCH_M, 5)
            dao.put(CacheEntry(key, encode(list), now))
            Result(list, false, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) Result(decode(cached.json), true, null)
            else Result(emptyList(), false, "Could not reach the timetable service (no internet connection?).")
        }
    }

    private fun encode(list: List<NearbyOfficial>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("n", it.name).put("la", it.lat).put("lo", it.lon).put("tt", it.timetable.toJson()))
        }
    }.toString()

    private fun decode(json: String): List<NearbyOfficial> {
        val a = JSONArray(json)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            NearbyOfficial(o.getString("n"), o.getDouble("la"), o.getDouble("lo"), Timetable.fromJson(o.getString("tt")))
        }
    }

    companion object {
        const val TTL_MS = 3L * 60 * 60 * 1000
        const val SEARCH_M = 16500.0
    }
}
