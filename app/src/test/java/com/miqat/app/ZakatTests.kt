package com.miqat.app

import com.miqat.app.hijri.CalendarMethod
import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.zakat.HawlMath
import com.miqat.app.zakat.ZakatInputs
import com.miqat.app.zakat.ZakatMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ZakatTests {
    private fun bd(s: String) = BigDecimal(s)
    private val zero = BigDecimal.ZERO

    private fun inputs(
        cash: String = "0", bank: String = "0", home: String = "0", gold: String = "0", silver: String = "0",
        inventory: String = "0", receivable: String = "0", other: String = "0",
        jewellery: String = "0", jewelleryZakatable: Boolean = false, debts: String = "0"
    ) = ZakatInputs(bd(cash), bd(bank), bd(home), bd(gold), bd(silver), bd(inventory), bd(receivable), bd(other), bd(jewellery), jewelleryZakatable, bd(debts))

    @Test fun perGramFromOunce() {
        assertEquals(0, bd("1").compareTo(ZakatMath.perGram(31.1034768, 1.0).setScale(9, java.math.RoundingMode.HALF_UP)))
        assertEquals(0, bd("2").compareTo(ZakatMath.perGram(31.1034768, 2.0).setScale(9, java.math.RoundingMode.HALF_UP)))
    }

    @Test fun nisabIsGoldPricePerGramTimesWeight() {
        assertEquals(bd("7905.00"), ZakatMath.money(ZakatMath.nisab(bd("93"), bd("85"))))
    }

    @Test fun specExampleAboveNisab() {
        val r = ZakatMath.compute(inputs(cash = "10000"), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertTrue(r.nisabReached)
        assertEquals(bd("7905.00"), r.nisab)
        assertEquals(bd("10000.00"), r.net)
        assertEquals(bd("250.00"), r.zakat)
    }

    @Test fun specExampleWithDeductions() {
        val r = ZakatMath.compute(inputs(cash = "12000", debts = "2000"), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertEquals(bd("12000.00"), r.totalAssets)
        assertEquals(bd("2000.00"), r.deductions)
        assertEquals(bd("10000.00"), r.net)
        assertEquals(bd("250.00"), r.zakat)
    }

    @Test fun belowNisabMeansNoZakat() {
        val r = ZakatMath.compute(inputs(cash = "7000"), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertFalse(r.nisabReached)
        assertEquals(bd("0.00"), r.zakat)
    }

    @Test fun exactlyAtNisabCounts() {
        val r = ZakatMath.compute(inputs(cash = "7905"), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertTrue(r.nisabReached)
        assertEquals(bd("197.63"), r.zakat)
    }

    @Test fun debtsCannotExceedAssets() {
        val r = ZakatMath.compute(inputs(cash = "100", debts = "500"), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertEquals(bd("100.00"), r.deductions)
        assertEquals(bd("0.00"), r.net)
    }

    @Test fun goldAndSilverAreValuedAtMarketPrice() {
        val r = ZakatMath.compute(inputs(gold = "100", silver = "1000"), bd("93"), bd("1.2"), bd("85"), bd("0.025"))
        assertEquals(bd("9300.00"), r.goldValue)
        assertEquals(bd("1200.00"), r.silverValue)
        assertEquals(bd("10500.00"), r.totalAssets)
    }

    @Test fun jewelleryCountsOnlyWhenChosenAsSavings() {
        val left = ZakatMath.compute(inputs(jewellery = "50", jewelleryZakatable = false), bd("93"), bd("1"), bd("85"), bd("0.025"))
        val counted = ZakatMath.compute(inputs(jewellery = "50", jewelleryZakatable = true), bd("93"), bd("1"), bd("85"), bd("0.025"))
        assertEquals(bd("0.00"), left.jewelleryValue)
        assertEquals(bd("4650.00"), counted.jewelleryValue)
    }

    @Test fun nisabWeightIsConfigurable() {
        val r = ZakatMath.compute(inputs(cash = "9000"), bd("93"), bd("1"), bd("87.48"), bd("0.025"))
        assertEquals(bd("8135.64"), r.nisab)
        assertTrue(r.nisabReached)
    }

    @Test fun parseIgnoresBadInput() {
        assertEquals(0, zero.compareTo(ZakatMath.parse("abc")))
        assertEquals(0, zero.compareTo(ZakatMath.parse("-5")))
        assertEquals(0, bd("1234.5").compareTo(ZakatMath.parse("1,234.5")))
    }

    @Test fun currencyFollowsCountryUnlessOverridden() {
        assertEquals("GBP", ZakatMath.currencyFor("GB", null))
        assertEquals("DZD", ZakatMath.currencyFor("DZ", null))
        assertEquals("USD", ZakatMath.currencyFor("GB", "usd"))
    }

    @Test fun hawlIsAboutOneLunarYear() {
        val cal = HijriCalendar(CalendarMethod.UMM_AL_QURA, 0)
        val start = LocalDate.of(2026, 1, 1)
        val due = HawlMath.dueDate(start, cal)
        val days = ChronoUnit.DAYS.between(start, due)
        assertTrue("days=$days", days in 353L..355L)
        val h0 = cal.fromGregorian(start)
        val h1 = cal.fromGregorian(due)
        assertEquals(h0.year + 1, h1.year)
        assertEquals(h0.month, h1.month)
        assertEquals(h0.day, h1.day)
    }

    @Test fun hawlCompleteOnAndAfterDueDate() {
        val due = LocalDate.of(2026, 12, 21)
        assertFalse(HawlMath.isComplete(due, LocalDate.of(2026, 12, 20)))
        assertTrue(HawlMath.isComplete(due, LocalDate.of(2026, 12, 21)))
        assertTrue(HawlMath.isComplete(due, LocalDate.of(2027, 1, 5)))
        assertEquals(5L, HawlMath.daysRemaining(due, LocalDate.of(2026, 12, 16)))
    }
}
