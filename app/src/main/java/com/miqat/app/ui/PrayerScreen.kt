package com.miqat.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.TtState
import com.miqat.app.UiState
import com.miqat.app.core.Geo
import com.miqat.app.data.Mosque
import com.miqat.app.timetable.Timetable
import com.miqat.app.timetable.TimetableBundle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val HEADER_ITEMS = 3 // status column, map, section title

@Composable
fun PrayerScreen(state: UiState, vm: AppViewModel, onRequestPermission: () -> Unit) {
    val listState = rememberLazyListState()
    var showDialog by remember { mutableStateOf(false) }

    // Scroll to a mosque when it is chosen on the map.
    LaunchedEffect(state.expandedId) {
        val idx = state.mosques.indexOfFirst { it.id == state.expandedId }
        if (idx >= 0) listState.animateScrollToItem(HEADER_ITEMS + idx)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LocationCard(state, onChange = { showDialog = true }, onUseGps = { vm.useGps() })
                if (!state.permissionGranted && !state.manual) {
                    MiqatCard {
                        Text("Location permission is off", fontWeight = FontWeight.SemiBold)
                        Text("Allow location to find mosques near you, or choose a location manually.", color = LightGrey)
                        Spacer(Modifier.height(8.dp))
                        MiqatButton("Allow location", onClick = onRequestPermission)
                    }
                }
                if (state.stale && state.fetchedAt > 0) {
                    MiqatCard {
                        Text(
                            "No internet: showing saved mosques from ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.fetchedAt))}. They may be out of date.",
                            color = LightGrey
                        )
                    }
                }
                state.error?.let { msg ->
                    MiqatCard {
                        Text(msg, color = LightGrey)
                        Spacer(Modifier.height(8.dp))
                        MiqatButton("Retry", onClick = { vm.startSearch() })
                    }
                }
            }
        }
        item {
            val p = state.point
            if (p != null) {
                MosqueMap(
                    point = p, mosques = state.mosques, onSelect = { vm.selectMosque(it) },
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp))
                )
            } else {
                MiqatCard { Text("Waiting for your location…", color = LightGrey) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nearby mosques (within ${state.radiusKm} km)", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (state.loading) CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
        items(state.mosques, key = { it.id }) { m ->
            MosqueCard(m, expanded = state.expandedId == m.id, tt = state.timetables[m.id], onToggle = { vm.toggleMosque(m.id) }, onRetry = { vm.loadTimetable(m.id) })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.searched && !state.loading && state.mosques.isEmpty() && state.error == null) {
                    Text("No mosques are mapped in OpenStreetMap within ${state.radiusKm} km.", color = LightGrey)
                }
                if (state.point != null && state.radiusKm < 50) {
                    MiqatButton("Search further (${if (state.radiusKm == 10) 25 else 50} km)", onClick = { vm.widenSearch() })
                }
                Text("Mosque locations © OpenStreetMap contributors.", color = LightGrey, fontSize = 11.sp)
            }
        }
    }

    if (showDialog) {
        ManualLocationDialog(onDismiss = { showDialog = false }, onConfirm = { lat, lon ->
            showDialog = false
            vm.setManualLocation(lat, lon)
        })
    }
}

@Composable
private fun LocationCard(state: UiState, onChange: () -> Unit, onUseGps: () -> Unit) {
    MiqatCard {
        Text("Current location", color = LightGrey, fontSize = 13.sp)
        val p = state.point
        if (p == null) {
            Text("Not available yet", fontWeight = FontWeight.SemiBold)
        } else {
            state.placeName?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
            Text(
                String.format(Locale.US, "%.4f, %.4f  (%s)", p.lat, p.lon, if (state.manual) "chosen manually" else "GPS"),
                color = LightGrey, fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiqatButton("Choose location", onClick = onChange)
            if (state.manual && state.permissionGranted) MiqatButton("Use GPS", onClick = onUseGps)
        }
    }
}

@Composable
private fun MosqueCard(m: Mosque, expanded: Boolean, tt: TtState?, onToggle: () -> Unit, onRetry: () -> Unit) {
    MiqatCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(Geo.formatDistance(m.distanceM) + " away", color = LightGrey)
            }
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = White)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DividerGrey)
            Spacer(Modifier.height(10.dp))
            when (tt) {
                null, TtState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("  Looking for this mosque's published timetable…", color = LightGrey)
                }
                is TtState.Loaded -> TimetableView(tt.bundle, m, onRetry)
            }
        }
    }
}

@Composable
private fun TimetableView(b: TimetableBundle, m: Mosque, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    fun open(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (b.official != null) {
            Text("OFFICIAL MOSQUE TIMETABLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("Source: ${b.official.sourceName} · ${b.official.date}", color = LightGrey, fontSize = 12.sp)
            if (b.officialStale) Text("Saved copy — you are offline, times may be out of date.", color = LightGrey, fontSize = 12.sp)
            TimeTable(b.official)
        } else {
            Text("Official timetable unavailable", fontWeight = FontWeight.Bold)
            Text(b.officialNote, color = LightGrey, fontSize = 13.sp)
            if (b.calculated != null) {
                Spacer(Modifier.height(6.dp))
                Text("CALCULATED TIMES — NOT THIS MOSQUE'S OFFICIAL TIMETABLE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("${b.calculated.sourceName}. Shown in your phone's time zone. Actual mosque times and Iqamah will differ.", color = LightGrey, fontSize = 12.sp)
                TimeTable(b.calculated)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (b.official?.sourceUrl ?: m.website)?.let { url -> MiqatButton("Open timetable source", onClick = { open(url) }) }
            if (b.official == null) MiqatButton("Retry", onClick = onRetry)
        }
        val site = m.website
        if (site != null && site != b.official?.sourceUrl) {
            MiqatButton("Open mosque website", onClick = { open(site) })
        }
    }
}

@Composable
private fun TimeTable(t: Timetable) {
    val hasIqama = t.rows.any { it.iqama != null }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Prayer", color = LightGrey, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
            Text("Start", color = LightGrey, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (hasIqama) Text("Iqamah", color = LightGrey, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        t.rows.forEach { r ->
            HorizontalDivider(color = DividerGrey)
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(r.name, modifier = Modifier.weight(1.4f))
                Text(r.start ?: "—", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (hasIqama) Text(r.iqama ?: "—", modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = DividerGrey)
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
            Text("Jumu'ah", modifier = Modifier.weight(1.4f))
            Text(
                if (t.jumuah.isEmpty()) (if (t.kind == com.miqat.app.timetable.TimetableKind.OFFICIAL) "not published" else "not available") else t.jumuah.joinToString(", "),
                modifier = Modifier.weight(if (hasIqama) 2f else 1f)
            )
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
            TextButton(onClick = { if (valid) onConfirm(latV!!, lonV!!) }, enabled = valid) { Text("Use this location", color = if (valid) White else LightGrey) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = White) } }
    )
}
