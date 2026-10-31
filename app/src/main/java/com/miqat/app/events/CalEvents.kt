package com.miqat.app.events

import com.miqat.app.hijri.HijriCalendar
import com.miqat.app.hijri.Occasion
import java.time.LocalDate

enum class EventKind(val label: String) { ISLAMIC("Islamic"), UK("UK"), ALGERIA("Algeria"), USER("Yours") }

/** description = text the user wrote (or empty); note = small extra line such as "Estimated". */
data class CalEvent(
    val date: LocalDate,
    val title: String,
    val description: String,
    val kind: EventKind,
    val userId: Long? = null,
    val note: String = ""
)

/** Brings Islamic occasions, UK days, Algerian holidays and the user's own events together. */
object CalEvents {

    fun userMatches(e: UserEvent, date: LocalDate): Boolean {
        val d0 = LocalDate.ofEpochDay(e.dateEpochDay)
        if (!e.yearly) return d0 == date
        if (date.isBefore(d0)) return false
        if (d0.monthValue == 2 && d0.dayOfMonth == 29 && !date.isLeapYear) {
            return date.monthValue == 2 && date.dayOfMonth == 28
        }
        return d0.monthValue == date.monthValue && d0.dayOfMonth == date.dayOfMonth
    }

    fun userEventsOn(list: List<UserEvent>, date: LocalDate): List<UserEvent> = list.filter { userMatches(it, date) }

    private fun islamicNote(o: Occasion): String =
        "Estimated" + if (o in AlgerianHolidays.islamicPublicHolidays) " · public holiday in Algeria" else ""

    fun forDate(date: LocalDate, cal: HijriCalendar, user: List<UserEvent>): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        val h = cal.fromGregorian(date)
        for (o in cal.occasions(h)) {
            val title = if (o == Occasion.LAST_TEN) "Last ten nights of Ramadan" else o.short
            out.add(CalEvent(date, title, "", EventKind.ISLAMIC, note = islamicNote(o)))
        }
        for (u in UkHolidays.on(date)) {
            out.add(CalEvent(date, u.name, "", EventKind.UK, note = if (u.bankHoliday) "Bank holiday (England & Wales)" else "Observance"))
        }
        for (a in AlgerianHolidays.on(date, cal)) {
            out.add(CalEvent(date, a.name, "", EventKind.ALGERIA, note = a.note))
        }
        for (e in userEventsOn(user, date)) {
            out.add(CalEvent(date, e.title, e.description, EventKind.USER, e.id, if (e.yearly) "Repeats every year" else ""))
        }
        return out
    }

    /** Events from [from] over the next [days] days (inclusive), earliest first. */
    fun upcoming(from: LocalDate, days: Int, cal: HijriCalendar, user: List<UserEvent>): List<CalEvent> {
        val out = ArrayList<CalEvent>()
        for (i in 0..days) {
            val d = from.plusDays(i.toLong())
            val h = cal.fromGregorian(d)
            for (o in cal.occasions(h)) {
                if (o == Occasion.LAST_TEN && h.day != 21) continue
                val title = if (o == Occasion.LAST_TEN) "Laylat al-Qadr period begins" else o.short
                out.add(CalEvent(d, title, "", EventKind.ISLAMIC, note = islamicNote(o)))
            }
            for (u in UkHolidays.on(d)) out.add(CalEvent(d, u.name, "", EventKind.UK))
            for (a in AlgerianHolidays.on(d, cal)) out.add(CalEvent(d, a.name, "", EventKind.ALGERIA))
            for (e in userEventsOn(user, d)) out.add(CalEvent(d, e.title, e.description, EventKind.USER, e.id))
        }
        return out
    }
}
