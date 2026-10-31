package com.miqat.app.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Foreground-only location updates. Collect only while the app is visible. */
class LocationTracker(private val ctx: Context) {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun updates(): Flow<Location> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Required on older Android versions")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        if (hasPermission()) {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        lm.getLastKnownLocation(p)?.let { trySend(it) }
                        lm.requestLocationUpdates(p, 30_000L, 200f, listener, Looper.getMainLooper())
                    }
                } catch (_: Exception) {
                }
            }
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}
