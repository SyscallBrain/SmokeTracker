package pt.tiago.cinzeiro.widget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.tiago.cinzeiro.data.LoggedEvent
import pt.tiago.cinzeiro.domain.SmokeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WidgetStateTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun `widgetState builds count and last from today`() {
        val last = Instant.parse("2026-09-30T12:32:00Z") // 13:32 WEST
        assertEquals(WidgetState(5, "último 13:32", today), widgetState(5, last, lisbon, today, lisbon))
    }

    @Test
    fun `widgetState with last from another day and with no entries`() {
        val ontem = Instant.parse("2026-09-29T21:00:00Z")
        assertEquals(WidgetState(0, "último 29 set", today), widgetState(0, ontem, lisbon, today, lisbon))
        assertEquals(WidgetState(0, "sem registos", today), widgetState(0, null, null, today, lisbon))
    }

    @Test
    fun `dayBounds goes from local midnight to midnight`() {
        val (from, to) = dayBounds(today, lisbon)
        assertEquals(Instant.parse("2026-09-29T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), to)
    }

    @Test
    fun `loggedToastText`() {
        assertEquals("Registado às 09:05 · 3 hoje", loggedToastText(LocalTime.of(9, 5, 59), 3))
    }

    // --- R4: pushed state ---

    @Test
    fun `loadWidgetState counts only the local day of now and uses the latest entry`() = runBlocking {
        val now = Instant.parse("2026-09-30T12:00:00Z") // 13:00 in Lisbon
        val events = listOf(
            Instant.parse("2026-09-29T22:59:59Z"), // 23:59:59 yesterday → outside
            Instant.parse("2026-09-29T23:00:00Z"), // 00:00 today → inside (inclusive start)
            Instant.parse("2026-09-30T11:30:00Z"), // 12:30 → inside
        )
        var asked: Pair<Instant, Instant>? = null
        val state = loadWidgetState(
            now, lisbon,
            countBetween = { from, to -> asked = from to to; events.count { it >= from && it < to } },
            last = { LoggedEvent(3, SmokeEvent(events.last(), lisbon)) },
        )
        assertEquals(WidgetState(2, "último 12:30", today), state)
        assertEquals(dayBounds(today, lisbon), asked)
    }

    @Test
    fun `loadWidgetState with no entries`() = runBlocking {
        val state = loadWidgetState(Instant.parse("2026-09-30T12:00:00Z"), lisbon, { _, _ -> 0 }, { null })
        assertEquals(WidgetState(0, "sem registos", today), state)
    }

    @Test
    fun `loadWidgetState right after midnight is already the new day`() = runBlocking {
        val ev = Instant.parse("2026-09-30T20:00:00Z")
        val state = loadWidgetState(
            Instant.parse("2026-09-30T23:00:05Z"), // 00:00:05 on 1 Oct in Lisbon
            lisbon,
            countBetween = { from, to -> listOf(ev).count { it >= from && it < to } },
            last = { LoggedEvent(1, SmokeEvent(ev, lisbon)) },
        )
        assertEquals(WidgetState(0, "último 30 set", LocalDate.of(2026, 10, 1)), state)
    }

    @Test
    fun `displayedOn shows the state for its own day and zero if from another day`() {
        val s = WidgetState(4, "último 21:00", today)
        assertEquals(s, s.displayedOn(today))
        assertEquals(WidgetState(0, "último 21:00", today), s.displayedOn(today.plusDays(1)))
    }

    // --- R5: widget texts (RemoteViews) ---

    @Test
    fun `widgetTexts shows count and last for its own day`() {
        assertEquals(WidgetTexts("7", "último 13:32"), widgetTexts(WidgetState(7, "último 13:32", today)))
    }

    @Test
    fun `widgetTexts for yesterday's state shows zero but keeps the last`() {
        assertEquals(
            WidgetTexts("0", "último 21:00"),
            widgetTexts(WidgetState(4, "último 21:00", today), today.plusDays(1)),
        )
    }

    @Test
    fun `widgetTexts with no entries`() {
        assertEquals(WidgetTexts("0", "sem registos"), widgetTexts(WidgetState(0, "sem registos", today)))
    }
}
