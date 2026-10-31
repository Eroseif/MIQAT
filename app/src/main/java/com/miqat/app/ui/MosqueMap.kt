package com.miqat.app.ui

import android.graphics.Color as AColor
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miqat.app.LatLon
import com.miqat.app.data.Mosque
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/** OpenStreetMap view (osmdroid) with tiles inverted to suit the permanent dark theme. */
@Composable
fun MosqueMap(point: LatLon, mosques: List<Mosque>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.5)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            // Let the map pan without the surrounding list stealing the gesture.
            setOnTouchListener { v, e ->
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                } else {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
            }
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            map.onDetach()
        }
    }
    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m ->
            m.overlays.clear()
            val density = ctx.resources.displayMetrics.density
            val size = (16 * density).toInt()
            m.overlays.add(Marker(m).apply {
                position = GeoPoint(point.lat, point.lon)
                icon = dot(false, size)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            })
            mosques.forEach { mq ->
                m.overlays.add(Marker(m).apply {
                    position = GeoPoint(mq.lat, mq.lon)
                    icon = dot(true, size)
                    title = mq.name
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { mk, _ -> mk.showInfoWindow(); true }
                })
            }
            if (m.tag != point) {
                m.controller.setCenter(GeoPoint(point.lat, point.lon))
                m.tag = point
            }
            m.invalidate()
        }
    )
}

// Solid white dot = mosque. Black dot with white ring = you.
private fun dot(filled: Boolean, sizePx: Int): Drawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(if (filled) AColor.WHITE else AColor.BLACK)
    setStroke(sizePx / 4, AColor.WHITE)
    setSize(sizePx, sizePx)
}
