package com.miqat.app

import com.miqat.app.events.CalEvents
import com.miqat.app.events.UkHolidays
import com.miqat.app.events.UserEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayTests {
    private fun names(d: LocalDate) = UkHolidays.on(d).map { it.name }

    @Test fun easterDates() {
        assertEquals(LocalDate.of(2026, 4, 5), UkHolidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), UkHolidays.easter(2027))
    }

    @Test fun bankHolidays2026() {
        assertTrue("Good Friday" in names(LocalDate.of(2026, 4, 3)))
        assertTrue("Easter Monday" in names(LocalDate.of(2026, 4, 6)))
        assertTrue("Early May bank holiday" in names(LocalDate.of(2026, 5, 4)))
        assertTrue("Spring bank holiday" in names(LocalDate.of(2026, 5, 25)))
        assertTrue("Summer bank holiday" in names(LocalDate.of(2026, 8, 31)))
        assertTrue("Christmas Day" in names(LocalDate.of(2026, 12, 25)))
        assertTrue("Boxing Day (substitute day)" in names(LocalDate.of(2026, 12, 28)))
    }

    @Test fun christmasSubstitutes2027() {
        assertTrue("Christmas Day (substitute day)" in names(LocalDate.of(2027, 12, 27)))
        assertTrue("Boxing Day (substitute day)" in names(LocalDate.of(2027, 12, 28)))
    }

    @Test fun yearlyUserEventRepeatsOnlyAfterItsFirstDate() {
        val e = UserEvent(dateEpochDay = LocalDate.of(1990, 5, 12).toEpochDay(), title = "Birthday", yearly = true)
        assertTrue(CalEvents.userMatches(e, LocalDate.of(2027, 5, 12)))
        assertFalse(CalEvents.userMatches(e, LocalDate.of(1989, 5, 12)))
        assertFalse(CalEvents.userMatches(e, LocalDate.of(2027, 5, 13)))
    }

    @Test fun oneOffUserEventMatchesOnlyItsDate() {
        val e = UserEvent(dateEpochDay = LocalDate.of(2026, 12, 1).toEpochDay(), title = "Trip")
        assertTrue(CalEvents.userMatches(e, LocalDate.of(2026, 12, 1)))
        assertFalse(CalEvents.userMatches(e, LocalDate.of(2027, 12, 1)))
    }
}

class AlgerianHolidayTests {
    private val cal = com.miqat.app.hijri.HijriCalendar(com.miqat.app.hijri.CalendarMethod.UMM_AL_QURA, 0)
    private fun names(d: LocalDate) = com.miqat.app.events.AlgerianHolidays.on(d, cal).map { it.name }

    @Test fun fixedDateHolidays() {
        assertTrue("Independence Day" in names(LocalDate.of(2026, 7, 5)))
        assertTrue("Revolution Day" in names(LocalDate.of(2026, 11, 1)))
        assertTrue("Yennayer (Amazigh New Year)" in names(LocalDate.of(2027, 1, 12)))
        assertTrue("Labour Day" in names(LocalDate.of(2027, 5, 1)))
    }

    @Test fun ordinaryDayHasNoAlgerianHoliday() {
        assertTrue(names(LocalDate.of(2026, 11, 10)).isEmpty())
    }
}
