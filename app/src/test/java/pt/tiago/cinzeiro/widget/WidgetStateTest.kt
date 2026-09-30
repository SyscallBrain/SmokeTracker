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
    fun `widgetState monta contagem e ultimo de hoje`() {
        val last = Instant.parse("2026-09-30T12:32:00Z") // 13:32 WEST
        assertEquals(WidgetState(5, "último 13:32", today), widgetState(5, last, lisbon, today, lisbon))
    }

    @Test
    fun `widgetState com ultimo de outro dia e sem registos`() {
        val ontem = Instant.parse("2026-09-29T21:00:00Z")
        assertEquals(WidgetState(0, "último 29 set", today), widgetState(0, ontem, lisbon, today, lisbon))
        assertEquals(WidgetState(0, "sem registos", today), widgetState(0, null, null, today, lisbon))
    }

    @Test
    fun `dayBounds vai de meia-noite a meia-noite local`() {
        val (from, to) = dayBounds(today, lisbon)
        assertEquals(Instant.parse("2026-09-29T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-09-30T23:00:00Z"), to)
    }

    @Test
    fun `loggedToastText`() {
        assertEquals("Registado às 09:05 · 3 hoje", loggedToastText(LocalTime.of(9, 5, 59), 3))
    }

    // --- R4: estado empurrado ---

    @Test
    fun `loadWidgetState conta so o dia local de now e usa o ultimo registo`() = runBlocking {
        val now = Instant.parse("2026-09-30T12:00:00Z") // 13:00 em Lisboa
        val events = listOf(
            Instant.parse("2026-09-29T22:59:59Z"), // 23:59:59 de ontem → fora
            Instant.parse("2026-09-29T23:00:00Z"), // 00:00 de hoje → dentro (início inclusivo)
            Instant.parse("2026-09-30T11:30:00Z"), // 12:30 → dentro
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
    fun `loadWidgetState sem registos`() = runBlocking {
        val state = loadWidgetState(Instant.parse("2026-09-30T12:00:00Z"), lisbon, { _, _ -> 0 }, { null })
        assertEquals(WidgetState(0, "sem registos", today), state)
    }

    @Test
    fun `loadWidgetState logo depois da meia-noite ja e o dia novo`() = runBlocking {
        val ev = Instant.parse("2026-09-30T20:00:00Z")
        val state = loadWidgetState(
            Instant.parse("2026-09-30T23:00:05Z"), // 00:00:05 de 1 out em Lisboa
            lisbon,
            countBetween = { from, to -> listOf(ev).count { it >= from && it < to } },
            last = { LoggedEvent(1, SmokeEvent(ev, lisbon)) },
        )
        assertEquals(WidgetState(0, "último 30 set", LocalDate.of(2026, 10, 1)), state)
    }

    @Test
    fun `displayedOn mostra o estado do proprio dia e zero se for de outro dia`() {
        val s = WidgetState(4, "último 21:00", today)
        assertEquals(s, s.displayedOn(today))
        assertEquals(WidgetState(0, "último 21:00", today), s.displayedOn(today.plusDays(1)))
    }

    // --- R5: textos do widget (RemoteViews) ---

    @Test
    fun `widgetTexts mostra contagem e ultimo do proprio dia`() {
        assertEquals(WidgetTexts("7", "último 13:32"), widgetTexts(WidgetState(7, "último 13:32", today)))
    }

    @Test
    fun `widgetTexts de estado de ontem mostra zero mas mantem o ultimo`() {
        assertEquals(
            WidgetTexts("0", "último 21:00"),
            widgetTexts(WidgetState(4, "último 21:00", today), today.plusDays(1)),
        )
    }

    @Test
    fun `widgetTexts sem registos`() {
        assertEquals(WidgetTexts("0", "sem registos"), widgetTexts(WidgetState(0, "sem registos", today)))
    }
}
