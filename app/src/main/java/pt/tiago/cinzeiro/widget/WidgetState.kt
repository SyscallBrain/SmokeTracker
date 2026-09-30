package pt.tiago.cinzeiro.widget

import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.ui.format.Formats
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the widget shows. Computed by [loadWidgetState] and drawn by [WidgetUpdater.push]
 * (R5: direct `RemoteViews`, see [WidgetViews]).
 *
 * [day] is the local day that [todayCount] refers to, so yesterday's count is not shown if the
 * midnight push is late ([displayedOn]).
 */
data class WidgetState(val todayCount: Int, val lastLabel: String, val day: LocalDate) {

    /** What to show on [today]: this state, or a count of 0 if it is from another day. */
    fun displayedOn(today: LocalDate): WidgetState = if (day == today) this else copy(todayCount = 0)
}

/**
 * "Latest" as an **absolute** time (ADR §6: the widget does not update every minute, so "há X min"
 * would go stale). Today → "último 13:32"; another day → "último 28 set"; no entries → "sem registos".
 */
fun lastLabel(last: Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): String {
    if (last == null) return "sem registos"
    val local = last.atZone(lastZone ?: zone)
    return if (last.atZone(zone).toLocalDate() == today) "último ${Formats.time(local.toLocalTime())}"
    else "último ${Formats.dayMonth(local.toLocalDate())}"
}

/** Builds the widget state (pure function, tested on the JVM). */
fun widgetState(count: Int, last: Instant?, lastZone: ZoneId?, today: LocalDate, zone: ZoneId): WidgetState =
    WidgetState(count, lastLabel(last, lastZone, today, zone), today)

/** Range `[midnight, next midnight)` of day [date] in [zone]. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/**
 * Current state from one-shot reads (no live Flows): count for the local day of [now] and the
 * latest entry. Parameters are functions so it can be tested without Room.
 */
suspend fun loadWidgetState(
    now: Instant,
    zone: ZoneId,
    countBetween: suspend (Instant, Instant) -> Int,
    last: suspend () -> LoggedEvent?,
): WidgetState {
    val today = now.atZone(zone).toLocalDate()
    val (from, to) = dayBounds(today, zone)
    val lastEvent = last()
    return widgetState(countBetween(from, to), lastEvent?.instant, lastEvent?.zone, today, zone)
}

/** Text of the widget's +1 Toast: "Registado às 13:32 · 5 hoje". */
fun loggedToastText(time: LocalTime, todayCount: Int): String =
    "Registado às ${Formats.time(time)} · $todayCount hoje"
