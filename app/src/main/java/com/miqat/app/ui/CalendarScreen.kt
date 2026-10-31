package com.miqat.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.UiState
import com.miqat.app.events.CalEvents
import com.miqat.app.events.AlgerianHolidays
import com.miqat.app.events.EventKind
import com.miqat.app.events.UkHolidays
import com.miqat.app.events.UserEvent
import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.HijriDate
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
internal val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

internal fun fmtHijri(h: HijriDate) = "${h.day} ${HijriCalendar.monthName(h.month)} ${h.year} AH"

private val SelectedGrey = Color(0xFF3A3A3A)

@Composable
fun CalendarScreen(state: UiState, vm: AppViewModel, onAddEvent: (LocalDate?) -> Unit) {
    val userEvents by vm.events.collectAsState()

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
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()
    // When a day is tapped, scroll so the calendar and the day's details are both in view.
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(100)
            listState.animateScrollToItem(1)
        }
    }

    val cal = remember(state.calendarMethod, state.dayAdjustment) { HijriCalendar(state.calendarMethod, state.dayAdjustment) }
    val hToday = remember(cal, today) { cal.fromGregorian(today) }
    // Only events within the next 30 days are shown in the "coming up" box.
    val coming = remember(cal, today, userEvents) { CalEvents.upcoming(today, 30, cal, userEvents) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MiqatCard {
                Text(today.format(longDate), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(fmtHijri(hToday), fontSize = 18.sp, color = LightGrey)
            }
        }
        item {
            MiqatCard {
                Box(
                    Modifier
                        .clipToBounds()
                        .pointerInput(Unit) {
                            // Swipe left = next month, swipe right = previous month.
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    if (total > 120f) offset-- else if (total < -120f) offset++
                                    total = 0f
                                },
                                onDragCancel = { total = 0f },
                                onHorizontalDrag = { change, dx ->
                                    total += dx
                                    change.consume()
                                }
                            )
                        }
                ) {
                    AnimatedContent(
                        targetState = offset,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "month"
                    ) { off ->
                        MonthView(
                            off = off, today = today, cal = cal, userEvents = userEvents, selected = selected,
                            onPrev = { offset-- }, onNext = { offset++ }, onToday = { offset = 0 },
                            onSelect = { d -> selected = if (selected == d) null else d }
                        )
                    }
                }
            }
        }
        selected?.let { d ->
            item {
                MiqatCard {
                    Text(d.format(longDate), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(fmtHijri(cal.fromGregorian(d)), color = LightGrey, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    val list = CalEvents.forDate(d, cal, userEvents)
                    if (list.isEmpty()) {
                        Text("No events on this day.", color = LightGrey, fontSize = 14.sp)
                    } else {
                        list.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 6.dp))
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                    if (e.description.isNotBlank()) {
                                        Text(e.description, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                                    }
                                    val small = if (e.note.isBlank()) e.kind.label else "${e.kind.label} · ${e.note}"
                                    Text(small, color = LightGrey, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (e.kind == EventKind.USER && e.userId != null) {
                                    TextButton(onClick = { vm.deleteEvent(e.userId) }) { Text("Delete", color = White) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MiqatButton("Add event on this day") { onAddEvent(d) }
                }
            }
        }
        if (coming.isNotEmpty()) {
            item {
                MiqatCard {
                    Text("Coming up (next 30 days)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    coming.take(4).forEach { e ->
                        val days = ChronoUnit.DAYS.between(today, e.date)
                        val due = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
                        Spacer(Modifier.height(6.dp))
                        Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("$due · ${e.date.format(shortDate)} · ${e.kind.label}", color = LightGrey, fontSize = 12.sp)
                    }
                    if (coming.size > 4) {
                        Spacer(Modifier.height(6.dp))
                        Text("and ${coming.size - 4} more", color = LightGrey, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthView(
    off: Int,
    today: LocalDate,
    cal: HijriCalendar,
    userEvents: List<UserEvent>,
    selected: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val shown = YearMonth.from(today).plusMonths(off.toLong())
    val days = remember(shown) { (1..shown.lengthOfMonth()).map { shown.atDay(it) } }
    val hijriFor = remember(cal, shown) { days.associateWith { cal.fromGregorian(it) } }
    val userDays = remember(days, userEvents) { days.filter { d -> userEvents.any { CalEvents.userMatches(it, d) } }.toSet() }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous month", tint = White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${shown.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${shown.year}", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                val first = hijriFor.getValue(days.first())
                val last = hijriFor.getValue(days.last())
                val label = if (first.month == last.month && first.year == last.year)
                    "${HijriCalendar.monthName(first.month)} ${first.year} AH"
                else "${HijriCalendar.monthName(first.month)} ${first.year} – ${HijriCalendar.monthName(last.month)} ${last.year} AH"
                Text(label, color = LightGrey, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowRight, "Next month", tint = White) }
        }
        if (off != 0) {
            TextButton(onClick = onToday, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Back to today", color = White)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            for (dow in DayOfWeek.values()) {
                Text(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), color = LightGrey, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val lead = shown.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(lead) { null } + days
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    if (d == null) {
                        Box(Modifier.weight(1f).aspectRatio(0.8f))
                    } else {
                        val h = hijriFor.getValue(d)
                        DayCell(
                            date = d, hijri = h, isToday = d == today,
                            hasUser = d in userDays,
                            hasOther = cal.occasions(h).isNotEmpty() || UkHolidays.on(d).isNotEmpty() || AlgerianHolidays.on(d, cal).isNotEmpty(),
                            isSelected = d == selected,
                            onClick = { onSelect(d) }
                        )
                    }
                }
            }
        }
    }
}

/** Today = solid white circle. A day with one of your events = hollow white circle. Both can show together. */
@Composable
private fun RowScope.DayCell(
    date: LocalDate, hijri: HijriDate,
    isToday: Boolean, hasUser: Boolean, hasOther: Boolean, isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.weight(1f).padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) SelectedGrey else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(34.dp)
                .then(if (hasUser) Modifier.border(1.5.dp, White, CircleShape) else Modifier)
                .padding(3.dp)
                .background(if (isToday) White else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(date.dayOfMonth.toString(), color = if (isToday) Black else White, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        }
        Text(
            if (hijri.day == 1) "1 ${HijriCalendar.monthShort(hijri.month)}" else hijri.day.toString(),
            color = LightGrey, fontSize = 10.sp, maxLines = 1
        )
        Box(Modifier.size(4.dp).background(if (hasOther) White else Color.Transparent, CircleShape))
    }
}
