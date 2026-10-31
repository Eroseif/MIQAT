package com.miqat.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miqat.app.AppViewModel
import com.miqat.app.UiState
import com.miqat.app.zakat.NisabBasis
import com.miqat.app.zakat.ZakatMath
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

private fun money(code: String, v: Double): String = try {
    val f = NumberFormat.getCurrencyInstance(Locale.getDefault())
    f.currency = Currency.getInstance(code)
    f.format(v)
} catch (e: Exception) {
    String.format(Locale.US, "%,.2f %s", v, code)
}

private fun countryName(cc: String?): String? = try {
    cc?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH) }?.ifBlank { null }
} catch (e: Exception) {
    null
}

/** Two cells: your savings on top, the zakat due below, priced in the currency of the country you are in. */
@Composable
fun ZakatScreen(state: UiState, vm: AppViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Keep gold and silver prices fresh while this tab is visible (cached for 5 minutes between fetches).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshPrices()
                delay(60_000)
            }
        }
    }

    val currency = ZakatMath.currencyFor(state.countryCode, state.zakatCurrency)
    val amount = input.toDoubleOrNull() ?: 0.0
    val prices = state.prices.prices
    val rate = prices?.rates?.get(currency)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MiqatCard(padding = 12.dp) {
            Text("Your savings", color = LightGrey, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { c -> c.isDigit() || c == '.' }.take(15) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                suffix = { Text(currency, color = LightGrey) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        MiqatCard(padding = 12.dp) {
            Text("Zakat due", color = LightGrey, fontSize = 12.sp)
            when {
                prices == null && state.prices.loading -> {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                prices == null -> {
                    Spacer(Modifier.height(6.dp))
                    Text("Gold and silver prices are unavailable. Check your internet connection.", color = LightGrey, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    MiqatButton("Retry") { vm.refreshPrices(force = true) }
                }
                rate == null -> {
                    Spacer(Modifier.height(6.dp))
                    Text("No exchange rate is available for $currency. Choose another currency in Settings.", color = LightGrey, fontSize = 14.sp)
                }
                else -> {
                    val goldG = ZakatMath.perGram(prices.goldUsdPerOunce, rate)
                    val silverG = ZakatMath.perGram(prices.silverUsdPerOunce, rate)
                    val nisab = ZakatMath.nisab(state.nisabBasis, goldG, silverG)
                    val zakat = ZakatMath.zakatDue(amount, nisab)
                    Text(money(currency, zakat), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    val note = when {
                        amount <= 0.0 -> "Enter your savings above"
                        amount < nisab -> "Below the nisab, so no zakat is due"
                        else -> "2.5% of your savings"
                    }
                    Text(note, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    val b: NisabBasis = state.nisabBasis
                    Text("Nisab (${b.metal} ${b.grams.toInt()} g): ${money(currency, nisab)}", color = LightGrey, fontSize = 12.sp)
                    Text("Gold ${money(currency, goldG)}/g · Silver ${money(currency, silverG)}/g", color = LightGrey, fontSize = 12.sp)
                    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prices.metalsAt))
                    val where = listOfNotNull(countryName(state.countryCode), currency).joinToString(" · ")
                    val stale = if (state.prices.stale) " · offline, saved prices" else ""
                    Text("$where · prices updated $time$stale", color = LightGrey, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Gold prices: gold-api.com · Exchange rates: ExchangeRate-API", color = LightGrey, fontSize = 9.sp)
                }
            }
        }
    }
}
