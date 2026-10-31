package com.miqat.app

import com.miqat.app.zakat.NisabBasis
import com.miqat.app.zakat.ZakatMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZakatTests {
    @Test fun perGramFromOunce() {
        assertEquals(1.0, ZakatMath.perGram(ZakatMath.TROY_OUNCE_G, 1.0), 1e-9)
        assertEquals(2.0, ZakatMath.perGram(ZakatMath.TROY_OUNCE_G, 2.0), 1e-9)
    }

    @Test fun nisabUsesTheChosenMetal() {
        assertEquals(595.0 * 0.8, ZakatMath.nisab(NisabBasis.SILVER, 60.0, 0.8), 1e-9)
        assertEquals(85.0 * 60.0, ZakatMath.nisab(NisabBasis.GOLD, 60.0, 0.8), 1e-9)
    }

    @Test fun zakatOnlyAtOrAboveNisab() {
        assertEquals(25.0, ZakatMath.zakatDue(1000.0, 500.0), 1e-9)
        assertEquals(12.5, ZakatMath.zakatDue(500.0, 500.0), 1e-9)
        assertEquals(0.0, ZakatMath.zakatDue(400.0, 500.0), 1e-9)
        assertEquals(0.0, ZakatMath.zakatDue(0.0, 0.0), 1e-9)
    }

    @Test fun currencyFollowsCountryUnlessOverridden() {
        assertEquals("GBP", ZakatMath.currencyFor("GB", null))
        assertEquals("DZD", ZakatMath.currencyFor("DZ", null))
        assertEquals("USD", ZakatMath.currencyFor("GB", "usd"))
        assertTrue(ZakatMath.currencyFor(null, null).length == 3)
    }
}
