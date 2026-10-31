package com.miqat.app.qibla

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Pure spherical-geometry Qibla maths. Works fully offline. */
object QiblaMath {
    const val KAABA_LAT = 21.4225
    const val KAABA_LON = 39.8262

    /** Initial great-circle bearing (degrees clockwise from TRUE north) from a point to the Kaaba. */
    fun bearing(lat: Double, lon: Double): Double {
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LON - lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun distanceKm(lat: Double, lon: Double): Double {
        val r = 6371.0
        val p1 = Math.toRadians(lat)
        val p2 = Math.toRadians(KAABA_LAT)
        val dp = p2 - p1
        val dl = Math.toRadians(KAABA_LON - lon)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)))
    }

    /** Signed shortest difference a - b in the range (-180, 180]. */
    fun angleDiff(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180) d -= 360.0
        if (d <= -180) d += 360.0
        return d
    }
}
