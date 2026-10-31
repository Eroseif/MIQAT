package com.miqat.app.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** azimuthDeg: degrees clockwise from MAGNETIC north (top of the phone, phone held flat). */
data class CompassReading(val azimuthDeg: Float, val accuracy: Int)

/** Uses the rotation-vector sensor, or accelerometer + magnetometer when that is missing. */
class CompassSensor(ctx: Context) {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val available: Boolean get() = (rotation != null && mag != null) || (accel != null && mag != null)

    fun readings(): Flow<CompassReading> = callbackFlow {
        val rm = FloatArray(9)
        val orient = FloatArray(3)
        var gravity: FloatArray? = null
        var geomag: FloatArray? = null
        var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        val useRotation = rotation != null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rm, e.values)
                        SensorManager.getOrientation(rm, orient)
                        trySend(CompassReading(norm(Math.toDegrees(orient[0].toDouble()).toFloat()), accuracy))
                    }
                    Sensor.TYPE_ACCELEROMETER -> gravity = smooth(gravity, e.values)
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        if (!useRotation) {
                            geomag = smooth(geomag, e.values)
                            val g = gravity
                            val m = geomag
                            if (g != null && m != null && SensorManager.getRotationMatrix(rm, null, g, m)) {
                                SensorManager.getOrientation(rm, orient)
                                trySend(CompassReading(norm(Math.toDegrees(orient[0].toDouble()).toFloat()), accuracy))
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, acc: Int) {
                // The magnetometer's accuracy is what tells us the compass needs calibrating.
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = acc
            }
        }

        if (available) {
            if (useRotation) sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
            else sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            sm.registerListener(listener, mag, SensorManager.SENSOR_DELAY_GAME)
        }
        awaitClose { sm.unregisterListener(listener) }
    }

    private fun smooth(prev: FloatArray?, new: FloatArray): FloatArray {
        val out = FloatArray(3)
        for (i in 0..2) out[i] = if (prev == null) new[i] else prev[i] + 0.15f * (new[i] - prev[i])
        return out
    }

    private fun norm(d: Float): Float = ((d % 360f) + 360f) % 360f
}
