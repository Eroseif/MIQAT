package com.miqat.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miqat.app.AppViewModel
import com.miqat.app.UiState
import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.Occasion
import com.miqat.app.timetable.MethodPref
import com.miqat.app.zakat.NisabBasis
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate

@Composable
fun SettingsScreen(state: UiState, vm: AppViewModel) {
    val today = remember { LocalDate.now() }
    val cal = remember(state.calendarMethod, state.dayAdjustment) { HijriCalendar(state.calendarMethod, state.dayAdjustment) }
    val ramadan = remember(cal, today) { cal.ramadanInfo(today) }
    val upcoming = remember(cal, today) { cal.upcoming(today, 365) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold) }

        item {
            MiqatCard {
                Text("Prayer time calculation", fontWeight = FontWeight.SemiBold)
                Text(
                    "Automatic uses the method usual for the country you are in, and changes as you travel. Pick one only if your local mosques follow a different method.",
                    color = LightGrey, fontSize = 12.sp
                )
                MethodPref.values().forEach { m ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.methodPref == m,
                            onClick = { vm.setMethodPref(m) },
                            colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = White)
                        )
                        Text(m.label, fontSize = 14.sp)
                    }
                }
            }
        }

        item {
            var code by remember(state.zakatCurrency) { mutableStateOf(state.zakatCurrency ?: "") }
            MiqatCard {
                Text("Zakat", fontWeight = FontWeight.SemiBold)
                Text("Nisab is measured in", color = LightGrey, fontSize = 12.sp)
                NisabBasis.values().forEach { b ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.nisabBasis == b,
                            onClick = { vm.setNisabBasis(b) },
                            colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = White)
                        )
                        Text(b.label, fontSize = 14.sp)
                    }
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 6.dp))
                Text("Currency", color = LightGrey, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.zakatCurrency == null,
                        onClick = { code = ""; vm.setZakatCurrency(null) },
                        colors = RadioButtonDefaults.colors(selectedColor = White, unselectedColor = White)
                    )
                    Text("Automatic (country you are in)", fontSize = 14.sp)
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        val c = v.filter { ch -> ch.isLetter() }.take(3).uppercase()
                        code = c
                        if (c.length == 3) vm.setZakatCurrency(c)
                    },
                    label = { Text("Or a currency code (GBP, DZD, USD…)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
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
            }
        }

        item {
            MiqatCard {
                Text("Manual adjustment", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiqatButton("−1", onClick = { vm.setDayAdjustment(state.dayAdjustment - 1) })
                    Text(
                        (if (state.dayAdjustment > 0) "+" else "") + "${state.dayAdjustment} day" +
                            (if (state.dayAdjustment == 1 || state.dayAdjustment == -1) "" else "s"),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                    )
                    MiqatButton("+1", onClick = { vm.setDayAdjustment(state.dayAdjustment + 1) })
                }
                Text("Shifts every Hijri date by up to one day, to match your local moon-sighting announcement.", color = LightGrey, fontSize = 12.sp)
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
                Text("Islamic occasions, next 12 months (estimated)", fontWeight = FontWeight.SemiBold)
                if (upcoming.isEmpty()) {
                    Text("None found.", color = LightGrey)
                } else {
                    upcoming.forEach { u ->
                        HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(vertical = 6.dp))
                        Text(if (u.occasion == Occasion.LAST_TEN) Occasion.LAST_TEN.label else u.occasion.label, fontWeight = FontWeight.SemiBold)
                        Text("${u.date.format(shortDate)} · ${fmtHijri(u.hijri)}", color = LightGrey, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("The last ten nights begin on the evening before the date shown.", color = LightGrey, fontSize = 11.sp)
            }
        }

        item {
            MiqatCard {
                Text("About the calendar", fontWeight = FontWeight.SemiBold)
                Text("The Islamic day begins at sunset (Maghrib); the date shown changes at midnight.", color = LightGrey, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Hijri dates are computed on the phone, so the calendar works offline.", color = LightGrey, fontSize = 12.sp)
            }
        }
    }
}
