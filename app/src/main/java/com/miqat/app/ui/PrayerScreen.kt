package com.miqat.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.AreaState
import com.miqat.app.UiState
import java.util.Locale

/** Location on top, prayer times for the area, map underneath. */
@Composable
fun PrayerScreen(state: UiState, vm: AppViewModel, onRequestPermission: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val p = state.point
    val label = state.placeName
        ?: p?.let { String.format(Locale.US, "%.3f, %.3f", it.lat, it.lon) }
        ?: "Choose location"

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showDialog = true }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Choose location", tint = White)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state.manual && state.permissionGranted) {
                IconButton(onClick = { vm.useGps() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Use GPS", tint = White)
                }
            }
        }
        if (!state.permissionGranted && p == null) {
            MiqatButton("Allow location", onClick = onRequestPermission)
        }
        if (p != null) {
            TimesCard(state.area)
            MosqueMap(
                point = p, mosques = state.mosques, onSelect = {},
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp))
            )
        }
    }

    if (showDialog) {
        ManualLocationDialog(onDismiss = { showDialog = false }, onConfirm = { lat, lon ->
            showDialog = false
            vm.setManualLocation(lat, lon)
        })
    }
}

/**
 * Today's times for the area: the nearest published mosque timetable if one was found, otherwise calculated.
 * One small grey line names the source so calculated times are never mistaken for a mosque's own.
 */
@Composable
private fun TimesCard(area: AreaState) {
    val official = area.official.firstOrNull()
    val t = official?.timetable ?: area.calculated
    MiqatCard(padding = 12.dp) {
        if (t == null) {
            if (area.officialLoading) {
                CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("—", color = LightGrey)
            }
        } else {
            val hasIqama = t.rows.any { it.iqama != null }
            if (hasIqama) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1.4f))
                    Spacer(Modifier.weight(1f))
                    Text("Iqamah", color = LightGrey, fontSize = 11.sp, modifier = Modifier.weight(1f))
                }
            }
            t.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 16.sp, modifier = Modifier.weight(1.4f))
                    Text(r.start ?: "—", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (hasIqama) Text(r.iqama ?: "—", fontSize = 16.sp, color = LightGrey, modifier = Modifier.weight(1f))
                }
            }
            val source = if (official != null) "Official timetable · ${official.name}" else t.sourceName
            Text(source, color = LightGrey, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ManualLocationDialog(onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit) {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    val latV = lat.trim().toDoubleOrNull()
    val lonV = lon.trim().toDoubleOrNull()
    val valid = latV != null && lonV != null && latV in -90.0..90.0 && lonV in -180.0..180.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardGrey,
        titleContentColor = White,
        textContentColor = White,
        title = { Text("Choose location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter coordinates in decimal degrees (for example 52.3900 and -2.2500).", color = LightGrey, fontSize = 13.sp)
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(lon, { lon = it }, label = { Text("Longitude") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(latV!!, lonV!!) }, enabled = valid) {
                Text("Use this location", color = if (valid) White else LightGrey)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = White) } }
    )
}
