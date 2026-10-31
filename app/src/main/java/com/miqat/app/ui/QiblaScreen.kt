package com.miqat.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miqat.app.UiState
import com.miqat.app.qibla.CompassSensor
import com.miqat.app.qibla.QiblaMath
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun QiblaScreen(state: UiState) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sensor = remember { CompassSensor(ctx) }
    val point = state.point

    if (point == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Qibla needs your location.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Allow location access or choose a location manually on the Prayer Times tab. After that, the Qibla bearing works without internet.", color = LightGrey)
        }
        return
    }

    val qibla = remember(point) { QiblaMath.bearing(point.lat, point.lon) }
    val distanceKm = remember(point) { QiblaMath.distanceKm(point.lat, point.lon) }
    // Compass sensors give MAGNETIC north; Qibla bearing is from TRUE north, so correct by declination.
    val declination = remember(point) {
        GeomagneticField(point.lat.toFloat(), point.lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    var heading by remember { mutableFloatStateOf(0f) }
    var haveHeading by remember { mutableStateOf(false) }
    var accuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }

    LaunchedEffect(sensor, lifecycle, declination) {
        if (sensor.available) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sensor.readings().collect { r ->
                    val trueHeading = (((r.azimuthDeg + declination) % 360f) + 360f) % 360f
                    heading = if (!haveHeading) {
                        haveHeading = true
                        trueHeading
                    } else {
                        // Smooth around the circle so the dial does not jitter or jump at 0/360.
                        val d = QiblaMath.angleDiff(trueHeading.toDouble(), heading.toDouble()).toFloat()
                        (((heading + 0.25f * d) % 360f) + 360f) % 360f
                    }
                    accuracy = r.accuracy
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Qibla bearing", color = LightGrey)
        Text(String.format(Locale.US, "%.1f°", qibla), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text(
            String.format(Locale.US, "from true north · %,.0f km to the Kaaba, Makkah", distanceKm),
            color = LightGrey, fontSize = 13.sp, textAlign = TextAlign.Center
        )

        if (sensor.available) {
            CompassDial(heading = heading, qibla = qibla.toFloat(), modifier = Modifier.fillMaxWidth().aspectRatio(1f))
            val diff = QiblaMath.angleDiff(qibla, heading.toDouble())
            val msg = when {
                !haveHeading -> "Reading compass…"
                abs(diff) <= 3 -> "You are facing the Qibla"
                diff > 0 -> "Turn right ${diff.roundToInt()}°"
                else -> "Turn left ${abs(diff).roundToInt()}°"
            }
            Text(msg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (haveHeading) {
                Text(String.format(Locale.US, "Your heading: %.0f°", heading), color = LightGrey)
            }
            if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                MiqatCard {
                    Text("Compass needs calibration", fontWeight = FontWeight.SemiBold)
                    Text("Move your phone slowly in a figure-8 pattern a few times, and keep it away from magnets, metal and electronics.", color = LightGrey)
                }
            }
            Text("Hold the phone flat. The white arrow points to the Qibla.", color = LightGrey, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            MiqatCard {
                Text("No compass sensor on this phone", fontWeight = FontWeight.SemiBold)
                Text("The live compass cannot work here. Use the bearing above (degrees clockwise from true north) with a physical compass or a map.", color = LightGrey)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CompassDial(heading: Float, qibla: Float, modifier: Modifier = Modifier) {
    val labelPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.86f
        val stroke = 2.dp.toPx()
        labelPaint.textSize = r * 0.13f

        // Fixed marker at the top: the direction the phone is pointing.
        val marker = Path().apply {
            moveTo(c.x, c.y - r + 2f)
            lineTo(c.x - r * 0.06f, c.y - r - r * 0.10f)
            lineTo(c.x + r * 0.06f, c.y - r - r * 0.10f)
            close()
        }
        drawPath(marker, White)

        // The dial rotates opposite to the heading, so N always points to true north.
        rotate(-heading, pivot = c) {
            drawCircle(White, radius = r, style = Stroke(width = stroke))
            for (deg in 0 until 360 step 5) {
                val len = when {
                    deg % 90 == 0 -> r * 0.12f
                    deg % 30 == 0 -> r * 0.08f
                    else -> r * 0.04f
                }
                rotate(deg.toFloat(), pivot = c) {
                    drawLine(White, Offset(c.x, c.y - r), Offset(c.x, c.y - r + len), strokeWidth = stroke)
                }
            }
            val labels = listOf("N", "E", "S", "W")
            labels.forEachIndexed { i, s ->
                rotate(90f * i, pivot = c) {
                    drawIntoCanvas { it.nativeCanvas.drawText(s, c.x, c.y - r + r * 0.30f, labelPaint) }
                }
            }
            // Qibla arrow: drawn on the dial at the Qibla bearing, so it points at Makkah in the real world.
            rotate(qibla, pivot = c) {
                drawLine(White, Offset(c.x, c.y + r * 0.22f), Offset(c.x, c.y - r * 0.58f), strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
                val head = Path().apply {
                    moveTo(c.x, c.y - r * 0.82f)
                    lineTo(c.x - r * 0.11f, c.y - r * 0.56f)
                    lineTo(c.x + r * 0.11f, c.y - r * 0.56f)
                    close()
                }
                drawPath(head, White)
            }
        }
        drawCircle(White, radius = r * 0.035f, center = c)
    }
}
