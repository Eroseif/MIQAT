package com.miqat.app.timetable

import org.json.JSONArray
import org.json.JSONObject

enum class TimetableKind { OFFICIAL, CALCULATED }

/** start = prayer start time; iqama = congregation time (only when the mosque publishes it). */
data class PrayerRow(val name: String, val start: String?, val iqama: String?)

data class Timetable(
    val kind: TimetableKind,
    val sourceName: String,
    val sourceUrl: String?,
    val date: String,
    val rows: List<PrayerRow>,
    val jumuah: List<String>,
    val fetchedAt: Long
) {
    fun toJson(): String = JSONObject().apply {
        put("kind", kind.name)
        put("sourceName", sourceName)
        put("sourceUrl", sourceUrl ?: JSONObject.NULL)
        put("date", date)
        put("fetchedAt", fetchedAt)
        put("rows", JSONArray().apply {
            rows.forEach {
                put(JSONObject().put("n", it.name).put("s", it.start ?: JSONObject.NULL).put("i", it.iqama ?: JSONObject.NULL))
            }
        })
        put("jumuah", JSONArray(jumuah))
    }.toString()

    companion object {
        fun fromJson(json: String): Timetable {
            val o = JSONObject(json)
            val rowsArr = o.getJSONArray("rows")
            val rows = (0 until rowsArr.length()).map {
                val r = rowsArr.getJSONObject(it)
                PrayerRow(r.getString("n"),
                    if (r.isNull("s")) null else r.getString("s"),
                    if (r.isNull("i")) null else r.getString("i"))
            }
            val j = o.optJSONArray("jumuah") ?: JSONArray()
            return Timetable(
                TimetableKind.valueOf(o.getString("kind")),
                o.getString("sourceName"),
                if (o.isNull("sourceUrl")) null else o.getString("sourceUrl"),
                o.getString("date"),
                rows,
                (0 until j.length()).map { j.getString(it) },
                o.optLong("fetchedAt")
            )
        }
    }
}

/** What the UI shows for one mosque: official times if found, otherwise a clear notice + calculated times. */
data class TimetableBundle(
    val date: String,
    val official: Timetable?,
    val officialStale: Boolean,
    val officialNote: String,
    val calculated: Timetable?
)
