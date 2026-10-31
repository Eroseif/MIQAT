package com.miqat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.UiState
import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.HijriDate
import com.miqat.app.hijri.Occasion
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun fmt(h: HijriDate) = "${h.day} ${HijriCalendar.monthName(h.month)} ${h.year} AH"

@Composable
fun CalendarScreen(state: UiState, vm: AppViewModel) {
    // Re-check the date regularly so the highlighted day follows time-zone and midnight changes.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val n = LocalDate.now()
            if (n != today) today = n
        }
    }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val shown = YearMonth.from(today).plusMonths(offset.toLong())
    val cal = remember(state.calendarMethod, state.dayAdjustment) { HijriCalendar(state.calendarMethod, state.dayAdjustment) }
    val hToday = remember(cal, today) { cal.fromGregorian(today) }
    val ramadan = remember(cal, today) { cal.ramadanInfo(today) }
    val days = remember(cal, shown) { (1..shown.lengthOfMonth()).map { shown.atDay(it) } }
    val hijriFor = remember(cal, shown) { days.associateWith { cal.fromGregorian(it) } }
    val events = remember(cal, shown) {
        days.flatMap { d -> cal.occasions(hijriFor.getValue(d)).map { d to it } }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MiqatCard {
                Text("Gregorian", color = LightGrey, fontSize = 13.sp)
                Text(today.format(longDate), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Hijri", color = LightGrey, fontSize = 13.sp)
                Text(fmt(hToday), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Month: ${HijriCalendar.monthName(hToday.month)} · Year: ${hToday.year} AH", color = LightGrey, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("The Islamic day begins at sunset (Maghrib); the date here changes at midnight.", color = LightGrey, fontSize = 11.sp)
            }
        }
        item {
            MiqatCard {
                Text("Ramadan (estimated)", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                when {
                    ramadan.inRamadan -> {
                        Text("Ramadan is under way: day ${ramadan.dayOfRamadan} (estimated).", fontSize = 16.sp)
                        ramadan.eidDate?.let { Text("Eid al-Fitr expected around ${it.format(shortDate)}.", color = LightGrey) }
                    }
                    ramadan.startDate != null && ramadan.daysRemaining == 0L ->
                        Text("Ramadan ${ramadan.hijriYear} is expected to begin today.", fontSize = 16.sp)
                    ramadan.startDate != null -> {
                        Text("${ramadan.daysRemaining} days remaining", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Ramadan ${ramadan.hijriYear} is expected to begin around ${ramadan.startDate.format(longDate)}.", color = LightGrey)
                    }
                    else -> Text("Ramadan date is not available for this method.", color = LightGrey)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "ESTIMATED, not officially confirmed. Ramadan and Eid are confirmed by moon sighting announcements, which this app cannot fetch. Check your local authority.",
                    color = LightGrey, fontSize = 11.sp
                )
            }
        }
        item {
            MiqatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { offset-- }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous month", tint = White) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${shown.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${shown.year}", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        val first = hijriFor.getValue(days.first())
                        val last = hijriFor.getValue(days.last())
                        val label = if (first.month == last.month && first.year == last.year)
                            "${HijriCalendar.monthName(first.month)} ${first.year} AH"
                        else "${HijriCalendar.monthName(first.month)} ${first.year} – ${HijriCalendar.monthName(last.month)} ${last.year} AH"
                        Text(label, color = LightGrey, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    IconButton(onClick = { offset++ }) { Icon(Icons.Filled.KeyboardArrowRight, "Next month", tint = White) }
                }
                if (offset != 0) {
                    TextButton(onClick = { offset = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Back to today", color = White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    for (dow in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                        Text(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), color = LightGrey, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                val lead = shown.atDay(1).dayOfWeek.value - 1
                val cells: List<LocalDate?> = List(lead) { null } + days
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val d = week.getOrNull(i)
                            if (d == null) Box(Modifier.weight(1f).aspectRatio(0.8f))
                            else {
                                val h = hijriFor.getValue(d)
                                DayCell(d, h, d == today, cal.occasions(h).isNotEmpty())
                            }
                        }
                    }
                }
            }
        }
        item {
            MiqatCard {
                Text("Islamic occasions this month (estimated)", fontWeight = FontWeight.SemiBold)
                if (events.isEmpty()) {
                    Text("None in this month.", color = LightGrey)
                } else {
                    // The last-ten-nights period spans many days: show it once as a range.
                    val lastTen = events.filter { it.second == Occasion.LAST_TEN }.map { it.first }
                    events.filter { it.second != Occasion.LAST_TEN }.forEach { (d, o) ->
                        HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 6.dp))
                        Text(o.label, fontWeight = FontWeight.SemiBold)
                        Text("${d.format(shortDate)} · ${fmt(hijriFor.getValue(d))}", color = LightGrey, fontSize = 13.sp)
                    }
                    if (lastTen.isNotEmpty()) {
                        HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 6.dp))
                        Text(Occasion.LAST_TEN.label, fontWeight = FontWeight.SemiBold)
                        Text("${lastTen.first().format(shortDate)} – ${lastTen.last().format(shortDate)} (21–30 Ramadan; the nights begin the evening before each date)", color = LightGrey, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            MiqatCard {
                Text("Hijri calendar authority", fontWeight = FontWeight.SemiBold)
                CalendarMethod.values().forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        RadioButton(
                            selected = state.calendarMethod == m,
                            onClick = { vm.setCalendarMethod(m) },
                            colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = White)
                        )
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(m.label)
                            Text(m.description, color = LightGrey, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 8.dp))
                Text("Manual adjustment", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiqatButton("−1", onClick = { vm.setDayAdjustment(state.dayAdjustment - 1) })
                    Text(
                        (if (state.dayAdjustment > 0) "+" else "") + "${state.dayAdjustment} day" + (if (state.dayAdjustment == 1 || state.dayAdjustment == -1) "" else "s"),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    MiqatButton("+1", onClick = { vm.setDayAdjustment(state.dayAdjustment + 1) })
                }
                Text("Shifts every Hijri date by up to one day, to match your local moon-sighting announcement.", color = LightGrey, fontSize = 12.sp)
                Text("Hijri dates are computed on the phone, so the calendar works offline.", color = LightGrey, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate, hijri: HijriDate, isToday: Boolean, hasOccasion: Boolean) {
    Column(Modifier.weight(1f).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(32.dp).background(if (isToday) White else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(date.dayOfMonth.toString(), color = if (isToday) Black else White, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        }
        Text(
            if (hijri.day == 1) "1 ${HijriCalendar.monthShort(hijri.month)}" else hijri.day.toString(),
            color = LightGrey, fontSize = 10.sp, maxLines = 1
        )
        Box(Modifier.size(4.dp).background(if (hasOccasion) White else Color.Transparent, CircleShape))
    }
}
