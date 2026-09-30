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
    fun `cigarettes at 23h59 and 00h01 fall on different days`() {
        val a = event("2026-09-28T23:59", LISBON)
        val b = event("2026-09-29T00:01", LISBON)

        assertEquals(LocalDate.of(2026, 9, 28), a.localDate())
        assertEquals(LocalDate.of(2026, 9, 29), b.localDate())
        assertNotEquals(a.localDate(), b.localDate())
    }

    @Test
    fun `DST change in Lisbon - 01h30 twice on the same day and order preserved`() {
        // 25-10-2026: at 02:00 WEST the clocks go back to 01:00 WET -> 01:30 exists twice.
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
    fun `event in another zone counts in the local day and hour of its own zone`() {
        // 2026-09-29T23:30Z: in Lisbon it is already 30-09 00:30; in New York it is still 29-09 19:30.
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

/** Event at local time `yyyy-MM-ddTHH:mm` in the given zone. */
internal fun event(local: String, zone: ZoneId = LISBON): SmokeEvent =
    SmokeEvent(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
