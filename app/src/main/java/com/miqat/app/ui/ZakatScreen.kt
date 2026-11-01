package com.miqat.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miqat.app.AppViewModel
import com.miqat.app.UiState
import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.zakat.HawlMath
import com.miqat.app.zakat.ZakatHistory
import com.miqat.app.zakat.ZakatInputs
import com.miqat.app.zakat.ZakatMath
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import java.util.Date
import java.util.Locale

private val CURRENCY_CHOICES = listOf("GBP", "USD", "EUR", "DZD", "SAR", "AED")
private const val OUTDATED_AFTER_MS = 2L * 60 * 60 * 1000

private fun money(code: String, v: BigDecimal): String = try {
    val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
    f.currency = Currency.getInstance(code)
    f.format(v)
} catch (e: Exception) {
    String.format(Locale.US, "%,.2f %s", v, code)
}

private fun plain(v: BigDecimal): String = v.stripTrailingZeros().toPlainString()

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = LightGrey, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (selected) White else Color.Transparent)
            .border(BorderStroke(1.dp, White), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (selected) Black else White, fontSize = 13.sp)
    }
}

@Composable
private fun MoneyField(label: String, value: String, suffix: String?, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { c -> c.isDigit() || c == '.' }.take(14)) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        suffix = if (suffix != null) { { Text(suffix, color = LightGrey) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPopup(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK", color = White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = White) } }
    ) { DatePicker(state = st) }
}

/** Zakat Calculator (Maliki, gold-based nisab). Live market data is kept visibly separate from the religious calculation. */
@Composable
fun ZakatScreen(state: UiState, vm: AppViewModel, onToggleReminder: (Boolean) -> Unit) {
    val profile by vm.zakatProfile.collectAsState()
    val history by vm.zakatHistory.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pickingDate by remember { mutableStateOf(false) }

    // Keep prices fresh while this tab is visible (the repository only re-downloads after 5 minutes).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshPrices()
                delay(60_000)
            }
        }
    }

    val currency = if (state.zakatCurrency == "AUTO") ZakatMath.currencyFor(state.countryCode, null) else state.zakatCurrency
    val prices = state.prices.prices
    val fx = prices?.rates?.get(currency)
    val goldG = if (prices != null && fx != null) ZakatMath.perGram(prices.goldUsdPerOunce, fx) else null
    val silverG = if (prices != null && fx != null) ZakatMath.perGram(prices.silverUsdPerOunce, fx) else null

    // Religious rules (from Settings) - never changed by market prices.
    val nisabGrams = ZakatMath.parse(state.nisabGrams).takeIf { it.signum() > 0 } ?: ZakatMath.DEFAULT_NISAB_GRAMS
    val rate = ZakatMath.parse(state.ratePercent).divide(BigDecimal(100)).takeIf { it.signum() > 0 } ?: ZakatMath.DEFAULT_RATE

    val inputs = ZakatInputs(
        cash = ZakatMath.parse(profile.cash), bank = ZakatMath.parse(profile.bank), home = ZakatMath.parse(profile.home),
        goldGrams = ZakatMath.parse(profile.goldGrams), silverGrams = ZakatMath.parse(profile.silverGrams),
        inventory = ZakatMath.parse(profile.inventory), receivable = ZakatMath.parse(profile.receivable),
        other = ZakatMath.parse(profile.other),
        jewelleryGrams = ZakatMath.parse(profile.jewelleryGrams), jewelleryZakatable = profile.jewelleryUse == "INVEST",
        debts = ZakatMath.parse(profile.debts)
    )
    val result = if (goldG != null && silverG != null) ZakatMath.compute(inputs, goldG, silverG, nisabGrams, rate) else null

    val cal = remember(state.calendarMethod, state.dayAdjustment) { HijriCalendar(state.calendarMethod, state.dayAdjustment) }
    val today = remember { LocalDate.now() }
    val start = profile.hawlStartEpochDay?.let { LocalDate.ofEpochDay(it) }
    val due = start?.let { HawlMath.dueDate(it, cal) }
    val hawlComplete = due?.let { HawlMath.isComplete(it, today) }

    val outdated = prices != null && (state.prices.stale || System.currentTimeMillis() - prices.metalsAt > OUTDATED_AFTER_MS)

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Zakat Calculator", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

        // ---------- Live market data ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("LIVE MARKET DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                Spacer(Modifier.height(6.dp))
                val chips = (CURRENCY_CHOICES + listOf("AUTO")).let { base ->
                    if (state.zakatCurrency != "AUTO" && state.zakatCurrency !in base) listOf(state.zakatCurrency) + base else base
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { c ->
                        ChoiceChip(if (c == "AUTO") "Local" else c, selected = state.zakatCurrency == c) { vm.setZakatCurrency(c) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    prices == null && state.prices.loading -> CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    prices == null -> {
                        Text("Gold prices are unavailable and none are saved yet. Connect to the internet.", color = LightGrey, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        MiqatButton("Retry") { vm.refreshPrices(force = true) }
                    }
                    fx == null || goldG == null || silverG == null ->
                        Text("No exchange rate is available for $currency. Choose another currency.", color = LightGrey, fontSize = 13.sp)
                    else -> {
                        Line("Gold 24K, per gram", money(currency, ZakatMath.money(goldG)), bold = true)
                        Line("Silver, per gram", money(currency, ZakatMath.money(silverG)))
                        val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(prices.metalsAt))
                        Line("Last updated", stamp)
                        if (outdated) {
                            Spacer(Modifier.height(4.dp))
                            Text("This price is out of date (saved from your last successful update). It will refresh when you are online.", color = White, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Gold and silver prices: gold-api.com · Exchange rates: ExchangeRate-API", color = LightGrey, fontSize = 9.sp)
            }
        }

        // ---------- Wealth ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("YOUR WEALTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                Text("Stored only on this phone.", color = LightGrey, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Column8 {
                    MoneyField("Cash savings", profile.cash, currency) { v -> vm.updateProfile { it.copy(cash = v) } }
                    MoneyField("Money in bank accounts", profile.bank, currency) { v -> vm.updateProfile { it.copy(bank = v) } }
                    MoneyField("Cash held at home", profile.home, currency) { v -> vm.updateProfile { it.copy(home = v) } }
                    MoneyField("Gold owned (grams, 24K equivalent)", profile.goldGrams, "g") { v -> vm.updateProfile { it.copy(goldGrams = v) } }
                    MoneyField("Silver owned (grams)", profile.silverGrams, "g") { v -> vm.updateProfile { it.copy(silverGrams = v) } }
                    MoneyField("Business inventory (market value)", profile.inventory, currency) { v -> vm.updateProfile { it.copy(inventory = v) } }
                    MoneyField("Money owed to you, expected to be recovered", profile.receivable, currency) { v -> vm.updateProfile { it.copy(receivable = v) } }
                    MoneyField("Other zakatable assets", profile.other, currency) { v -> vm.updateProfile { it.copy(other = v) } }
                    MoneyField("Debts due immediately (deducted)", profile.debts, currency) { v -> vm.updateProfile { it.copy(debts = v) } }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Not counted: your main home, clothing, normal household furniture and a vehicle for personal use. Enter only debts that are due now.",
                    color = LightGrey, fontSize = 11.sp
                )
            }
        }

        // ---------- Jewellery (Maliki-specific treatment) ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("GOLD JEWELLERY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                Spacer(Modifier.height(6.dp))
                MoneyField("Jewellery (grams, 24K equivalent)", profile.jewelleryGrams, "g") { v -> vm.updateProfile { it.copy(jewelleryGrams = v) } }
                Spacer(Modifier.height(8.dp))
                Text("How is it used? Choose one:", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Personal adornment", profile.jewelleryUse == "ADORN") { vm.updateProfile { it.copy(jewelleryUse = "ADORN") } }
                    ChoiceChip("Savings, sale or hire", profile.jewelleryUse == "INVEST") { vm.updateProfile { it.copy(jewelleryUse = "INVEST") } }
                }
                Spacer(Modifier.height(6.dp))
                val gramsEntered = inputs.jewelleryGrams.signum() > 0
                Text(
                    when {
                        profile.jewelleryUse == "ADORN" -> "Counted as NOT zakatable: in the Maliki view, jewellery worn for lawful personal adornment is generally not subject to zakat."
                        profile.jewelleryUse == "INVEST" -> "Counted as zakatable at the 24K price: jewellery kept as savings, or for sale or hire, is treated as wealth."
                        gramsEntered -> "Choose how the jewellery is used. Until you do, it is left out of the calculation."
                        else -> "The Maliki treatment depends on use, so the app asks rather than assuming."
                    },
                    color = LightGrey, fontSize = 11.sp
                )
                Text("If unsure (for example damaged or inherited pieces), ask a qualified scholar.", color = LightGrey, fontSize = 11.sp)
            }
        }

        // ---------- Result ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("RELIGIOUS CALCULATION · MALIKI · GOLD NISAB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                Spacer(Modifier.height(6.dp))
                if (result == null || goldG == null) {
                    Text("The result appears once a gold price is available.", color = LightGrey, fontSize = 13.sp)
                } else {
                    Line("Nisab", "${plain(nisabGrams)} g × ${money(currency, ZakatMath.money(goldG))} = ${money(currency, result.nisab)}", bold = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (result.nisabReached) "Your eligible wealth has reached the Nisab threshold."
                        else "Your eligible wealth is currently below the Nisab threshold. Zakat is not currently due under this calculation.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = DividerGrey)
                    Spacer(Modifier.height(6.dp))
                    Line("Total eligible assets", money(currency, result.totalAssets))
                    if (result.goldValue.signum() > 0) Line("  of which gold", money(currency, result.goldValue))
                    if (result.silverValue.signum() > 0) Line("  of which silver", money(currency, result.silverValue))
                    if (result.jewelleryValue.signum() > 0) Line("  of which jewellery", money(currency, result.jewelleryValue))
                    Line("Eligible deductions", money(currency, result.deductions))
                    Line("Net zakatable wealth", money(currency, result.net), bold = true)
                    Line("Current Nisab", money(currency, result.nisab))
                    Line("Nisab reached", if (result.nisabReached) "Yes" else "No")
                    Line("Hawl (lunar year) complete", when (hawlComplete) { null -> "Date not set"; true -> "Yes"; false -> "Not yet" })
                    Line("Zakat rate", "${plain(rate.multiply(BigDecimal(100)))}%")
                    Spacer(Modifier.height(6.dp))
                    Line("Zakat payable", money(currency, result.zakat), bold = true)
                    if (result.nisabReached) {
                        Text(
                            "${money(currency, result.net)} × ${plain(rate.multiply(BigDecimal(100)))}% = ${money(currency, result.zakat)}",
                            color = LightGrey, fontSize = 12.sp
                        )
                        Text(
                            when (hawlComplete) {
                                null -> "Set the date your wealth first reached the Nisab below, so the lunar year can be tracked."
                                true -> "Your lunar year is complete, so this zakat is due."
                                false -> "Your lunar year is not complete yet. It completes on ${due!!.format(longDate)}."
                            },
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MiqatButton("Save this calculation") {
                        vm.saveCalculation(
                            ZakatHistory(
                                savedAt = System.currentTimeMillis(), currency = currency,
                                goldPerGram = goldG.toPlainString(), nisabGrams = nisabGrams.toPlainString(),
                                nisab = result.nisab.toPlainString(), totalAssets = result.totalAssets.toPlainString(),
                                deductions = result.deductions.toPlainString(), net = result.net.toPlainString(),
                                nisabReached = result.nisabReached, rate = rate.toPlainString(), zakat = result.zakat.toPlainString()
                            )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "A calculation aid, not a fatwa. Not every Maliki scholar uses exactly the same contemporary gold conversion; the nisab weight and rate can be changed in Settings on a scholar's advice.",
                    color = LightGrey, fontSize = 10.sp
                )
            }
        }

        // ---------- Hawl ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("HAWL · LUNAR YEAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                Spacer(Modifier.height(6.dp))
                Line("Wealth first reached Nisab", start?.format(longDate) ?: "Not set")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceChip("Choose date", false) { pickingDate = true }
                    ChoiceChip("Today", false) { vm.updateProfile { it.copy(hawlStartEpochDay = LocalDate.now().toEpochDay()) } }
                    if (start != null) ChoiceChip("Clear", false) { vm.updateProfile { it.copy(hawlStartEpochDay = null) } }
                }
                if (start != null && due != null) {
                    Spacer(Modifier.height(8.dp))
                    val h = cal.fromGregorian(due)
                    Line("Hawl completes", due.format(longDate), bold = true)
                    Text("${h.day} ${HijriCalendar.monthName(h.month)} ${h.year} AH (estimated)", color = LightGrey, fontSize = 12.sp)
                    val left = HawlMath.daysRemaining(due, today)
                    Text(if (left > 0) "$left days remaining" else "Lunar year complete", fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("A lunar year is about 354 days, shorter than a Gregorian year, so the due date comes about 11 days earlier each year. You can correct the start date at any time.", color = LightGrey, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me when zakat becomes due", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = profile.reminder,
                        onCheckedChange = { onToggleReminder(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Black, checkedTrackColor = White,
                            uncheckedThumbColor = White, uncheckedTrackColor = Black, uncheckedBorderColor = White
                        )
                    )
                }
            }
        }

        // ---------- History ----------
        item {
            MiqatCard(padding = 12.dp) {
                Text("PREVIOUS CALCULATIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                if (history.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("None saved yet.", color = LightGrey, fontSize = 13.sp)
                }
            }
        }
        items(history.take(10), key = { it.id }) { h ->
            MiqatCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(h.savedAt)), color = LightGrey, fontSize = 11.sp)
                        Text("Net ${money(h.currency, BigDecimal(h.net))} · Nisab ${money(h.currency, BigDecimal(h.nisab))}", fontSize = 13.sp)
                        Text(
                            if (h.nisabReached) "Zakat ${money(h.currency, BigDecimal(h.zakat))}" else "Below Nisab: no zakat due",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { vm.deleteCalculation(h.id) }) { Text("Delete", color = White) }
                }
            }
        }
    }

    if (pickingDate) {
        DatePickerPopup(
            initial = start ?: LocalDate.now(),
            onPick = { d -> vm.updateProfile { it.copy(hawlStartEpochDay = d.toEpochDay()) } },
            onDismiss = { pickingDate = false }
        )
    }
}

@Composable
private fun Column8(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}
