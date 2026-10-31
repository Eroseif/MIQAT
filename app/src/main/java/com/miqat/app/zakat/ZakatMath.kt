package com.miqat.app.zakat

import java.util.Currency
import java.util.Locale

/** Which metal the nisab (minimum threshold for zakat) is measured in. */
enum class NisabBasis(val label: String, val grams: Double, val metal: String) {
    SILVER("Silver (595 g)", 595.0, "silver"),
    GOLD("Gold (85 g)", 85.0, "gold")
}

/** Pure zakat maths, no network or Android: easy to test. */
object ZakatMath {
    const val TROY_OUNCE_G = 31.1034768
    const val RATE = 0.025

    /** Price of one gram in local currency, from a USD price per troy ounce and a USD->local rate. */
    fun perGram(usdPerOunce: Double, usdToLocal: Double): Double = usdPerOunce / TROY_OUNCE_G * usdToLocal

    fun nisab(basis: NisabBasis, goldPerGram: Double, silverPerGram: Double): Double =
        basis.grams * (if (basis == NisabBasis.GOLD) goldPerGram else silverPerGram)

    /** 2.5% of savings, but only when savings reach the nisab. */
    fun zakatDue(savings: Double, nisab: Double): Double =
        if (savings > 0.0 && savings >= nisab) savings * RATE else 0.0

    /** Currency code for a country (or the user's override); falls back to the phone's own currency. */
    fun currencyFor(countryCode: String?, override: String?): String {
        override?.trim()?.takeIf { it.length == 3 }?.let { return it.uppercase(Locale.ROOT) }
        if (countryCode != null) {
            try {
                return Currency.getInstance(Locale.Builder().setRegion(countryCode).build()).currencyCode
            } catch (e: Exception) {
            }
        }
        return try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: Exception) {
            "USD"
        }
    }
}
