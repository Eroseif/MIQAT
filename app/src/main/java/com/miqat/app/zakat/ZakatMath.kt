package com.miqat.app.zakat

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

data class ZakatInputs(
    val cash: BigDecimal,
    val bank: BigDecimal,
    val home: BigDecimal,
    val goldGrams: BigDecimal,
    val silverGrams: BigDecimal,
    val inventory: BigDecimal,
    val receivable: BigDecimal,
    val other: BigDecimal,
    val jewelleryGrams: BigDecimal,
    /** True only when the user chose "savings / for sale or hire" for their jewellery. */
    val jewelleryZakatable: Boolean,
    val debts: BigDecimal
)

data class ZakatResult(
    val goldValue: BigDecimal,
    val silverValue: BigDecimal,
    val jewelleryValue: BigDecimal,
    val totalAssets: BigDecimal,
    val deductions: BigDecimal,
    val net: BigDecimal,
    val nisab: BigDecimal,
    val nisabReached: Boolean,
    val rate: BigDecimal,
    val zakat: BigDecimal
)

/**
 * Zakat arithmetic with BigDecimal (no floating-point drift). The religious rules (nisab weight, rate)
 * are parameters supplied by the user's settings; market prices only feed the value of gold and silver.
 */
object ZakatMath {
    const val TROY_OUNCE_G = "31.1034768"
    val DEFAULT_NISAB_GRAMS: BigDecimal = BigDecimal("85")
    val DEFAULT_RATE: BigDecimal = BigDecimal("0.025")

    private val MC = MathContext(20, RoundingMode.HALF_UP)

    /** Reads a typed amount; anything invalid or negative counts as zero. */
    fun parse(s: String): BigDecimal =
        s.trim().replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: BigDecimal.ZERO

    fun money(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)

    /** Price of one gram in local currency, from a USD price per troy ounce and a USD->local rate. */
    fun perGram(usdPerOunce: Double, usdToLocal: Double): BigDecimal =
        BigDecimal.valueOf(usdPerOunce).divide(BigDecimal(TROY_OUNCE_G), MC).multiply(BigDecimal.valueOf(usdToLocal), MC)

    /** Gold price per gram x nisab weight. */
    fun nisab(goldPerGram: BigDecimal, nisabGrams: BigDecimal): BigDecimal = goldPerGram.multiply(nisabGrams, MC)

    fun compute(
        i: ZakatInputs,
        goldPerGram: BigDecimal,
        silverPerGram: BigDecimal,
        nisabGrams: BigDecimal,
        rate: BigDecimal
    ): ZakatResult {
        val goldValue = i.goldGrams.multiply(goldPerGram, MC)
        val silverValue = i.silverGrams.multiply(silverPerGram, MC)
        val jewelleryValue = if (i.jewelleryZakatable) i.jewelleryGrams.multiply(goldPerGram, MC) else BigDecimal.ZERO
        val total = i.cash + i.bank + i.home + goldValue + silverValue + i.inventory + i.receivable + i.other + jewelleryValue
        val deductions = i.debts.min(total)
        val net = total - deductions
        val nisab = nisab(goldPerGram, nisabGrams)
        val reached = nisab.signum() > 0 && net.compareTo(nisab) >= 0
        val zakat = if (reached) net.multiply(rate, MC) else BigDecimal.ZERO
        return ZakatResult(
            money(goldValue), money(silverValue), money(jewelleryValue),
            money(total), money(deductions), money(net), money(nisab),
            reached, rate, money(zakat)
        )
    }

    /** Currency code for a country; falls back to the phone's own currency. */
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
