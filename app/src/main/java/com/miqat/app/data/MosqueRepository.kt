package com.miqat.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

data class Mosque(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val website: String?,
    val distanceM: Double = 0.0
)

/**
 * Finds mosques from OpenStreetMap (Overpass API): amenity=place_of_worship + religion=muslim.
 * Results are cached per ~1 km grid cell for 24 h and reused (marked stale) when offline.
 */
class MosqueRepository(private val dao: CacheDao, private val http: OkHttpClient) {

    class Result(val mosques: List<Mosque>, val stale: Boolean, val fetchedAt: Long)

    suspend fun search(lat: Double, lon: Double, radiusKm: Int): Result = withContext(Dispatchers.IO) {
        val cLat = Math.round(lat * 100) / 100.0
        val cLon = Math.round(lon * 100) / 100.0
        val key = String.format(Locale.US, "mosques:%.2f:%.2f:%d", cLat, cLon, radiusKm)
        val cached = dao.get(key)
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < TTL_MS) {
            return@withContext Result(decode(cached.json), false, cached.fetchedAt)
        }
        try {
            // Query a little wider than requested; the caller filters by real distance.
            val body = fetchOverpass(cLat, cLon, ((radiusKm + 1.5) * 1000).toInt())
            val list = parse(body)
            dao.put(CacheEntry(key, encode(list), now))
            Result(list, false, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) Result(decode(cached.json), true, cached.fetchedAt) else throw e
        }
    }

    private fun fetchOverpass(lat: Double, lon: Double, radiusM: Int): String {
        val parts = listOf("node", "way", "relation").joinToString("") {
            "$it[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:$radiusM,$lat,$lon);"
        }
        val query = "[out:json][timeout:25];($parts);out center tags;"
        var last: Exception = IOException("No Overpass server reachable")
        for (endpoint in ENDPOINTS) {
            try {
                val req = Request.Builder().url(endpoint).post(FormBody.Builder().add("data", query).build()).build()
                http.newCall(req).execute().use { r ->
                    if (r.isSuccessful) return r.body?.string() ?: throw IOException("Empty response")
                    last = IOException("Overpass HTTP ${r.code}")
                }
            } catch (e: IOException) {
                last = e
            }
        }
        throw last
    }

    private fun parse(body: String): List<Mosque> {
        val arr = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Mosque>()
        for (i in 0 until arr.length()) {
            val el = arr.getJSONObject(i)
            val type = el.optString("type")
            val id = el.optLong("id")
            val center = el.optJSONObject("center")
            val lat = if (el.has("lat")) el.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
            val lon = if (el.has("lon")) el.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
            if (lat.isNaN() || lon.isNaN()) continue
            val tags = el.optJSONObject("tags") ?: JSONObject()
            if (tags.optString("religion") != "muslim") continue
            val name = tags.optString("name").ifBlank { tags.optString("name:en") }
                .ifBlank { "Mosque (name not mapped)" }
            val site = tags.optString("website").ifBlank { tags.optString("contact:website") }
                .ifBlank { null }?.let { if (it.startsWith("http")) it else "https://$it" }
            out.add(Mosque("$type/$id", name, lat, lon, site))
        }
        return out
    }

    private fun encode(list: List<Mosque>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.lat).put("lon", it.lon)
                .put("web", it.website ?: JSONObject.NULL))
        }
    }.toString()

    private fun decode(json: String): List<Mosque> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Mosque(o.getString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"),
                if (o.isNull("web")) null else o.getString("web"))
        }
    }

    companion object {
        const val TTL_MS = 24L * 60 * 60 * 1000
        private val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
    }
}
