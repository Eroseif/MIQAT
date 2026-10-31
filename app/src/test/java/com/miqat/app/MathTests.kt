package com.miqat.app

import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.HijriDate
import com.miqat.app.qibla.QiblaMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathTests {
    @Test fun qiblaFromLondonIsAbout119Degrees() {
        assertEquals(119.0, QiblaMath.bearing(51.5074, -0.1278), 1.0)
    }

    @Test fun qiblaFromKidderminsterIsAbout117Degrees() {
        assertEquals(117.5, QiblaMath.bearing(52.39, -2.25), 1.0)
    }

    @Test fun angleDiffWrapsAround() {
        assertEquals(20.0, QiblaMath.angleDiff(10.0, 350.0), 0.001)
        assertEquals(-20.0, QiblaMath.angleDiff(350.0, 10.0), 0.001)
    }

    @Test fun civilCalendarEpoch() {
        assertEquals(HijriDate(1, 1, 1), HijriCalendar.civilFromJdn(1948440))
    }

    @Test fun civilCalendarAdvancesOneDayAtATime() {
        var prev = HijriCalendar.civilFromJdn(2461000)
        for (jd in 2461001L..2461400L) {
            val cur = HijriCalendar.civilFromJdn(jd)
            val nextDay = cur.day == prev.day + 1 && cur.month == prev.month && cur.year == prev.year
            val nextMonth = cur.day == 1 && (cur.month == prev.month + 1 || (cur.month == 1 && prev.month == 12))
            assertTrue("$prev -> $cur", nextDay || nextMonth)
            prev = cur
        }
    }
}
