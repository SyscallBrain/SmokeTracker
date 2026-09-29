package pt.tiago.cinzeiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SmokeEventTest {

    @Test
    fun `cigarro as 23h59 e as 00h01 ficam em dias diferentes`() {
        val a = event("2026-09-28T23:59", LISBON)
        val b = event("2026-09-29T00:01", LISBON)

        assertEquals(LocalDate.of(2026, 9, 28), a.localDate())
        assertEquals(LocalDate.of(2026, 9, 29), b.localDate())
        assertNotEquals(a.localDate(), b.localDate())
    }

    @Test
    fun `mudanca de hora em Lisboa - 01h30 duas vezes no mesmo dia e ordem preservada`() {
        // 25-10-2026: às 02:00 WEST os relógios voltam a 01:00 WET -> 01:30 existe duas vezes.
        val first = SmokeEvent(Instant.parse("2026-10-25T00:30:00Z"), LISBON)  // 01:30 WEST (+01:00)
        val second = SmokeEvent(Instant.parse("2026-10-25T01:30:00Z"), LISBON) // 01:30 WET  (+00:00)

        assertEquals(LocalDate.of(2026, 10, 25), first.localDate())
        assertEquals(LocalDate.of(2026, 10, 25), second.localDate())
        assertEquals(1, first.localHour())
        assertEquals(1, second.localHour())
        assertTrue(first.instant < second.instant)
        assertEquals(listOf(first, second), listOf(second, first).sortedBy { it.instant })
    }

    @Test
    fun `evento noutra zona conta no dia e hora locais da zona dele`() {
        // 2026-09-29T23:30Z: em Lisboa já é 30-09 00:30; em Nova Iorque ainda é 29-09 19:30.
        val instant = Instant.parse("2026-09-29T23:30:00Z")
        val lisbon = SmokeEvent(instant, LISBON)
        val newYork = SmokeEvent(instant, ZoneId.of("America/New_York"))

        assertEquals(LocalDate.of(2026, 9, 30), lisbon.localDate())
        assertEquals(0, lisbon.localHour())
        assertEquals(LocalDate.of(2026, 9, 29), newYork.localDate())
        assertEquals(19, newYork.localHour())
    }
}

internal val LISBON: ZoneId = ZoneId.of("Europe/Lisbon")

/** Evento à hora local `yyyy-MM-ddTHH:mm` na zona indicada. */
internal fun event(local: String, zone: ZoneId = LISBON): SmokeEvent =
    SmokeEvent(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
