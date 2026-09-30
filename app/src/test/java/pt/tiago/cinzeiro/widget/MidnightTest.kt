package pt.tiago.cinzeiro.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MidnightTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    @Test
    fun `normal day`() {
        // 13:32:05 in Lisbon (WEST) → 10 h 27 min 55 s until midnight.
        assertEquals(
            Duration.ofHours(10).plusMinutes(27).plusSeconds(55),
            durationUntilNextLocalMidnight(Instant.parse("2026-09-29T12:32:05Z"), lisbon),
        )
    }

    @Test
    fun `25 h day - clocks go back on 25-10-2026`() {
        // 00:30 WEST on 25 Oct → midnight of 26 Oct (WET) = 24 h 30 min real time.
        assertEquals(
            Duration.ofHours(24).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-10-24T23:30:00Z"), lisbon),
        )
    }

    @Test
    fun `23 h day - clocks go forward on 29-03-2026`() {
        // 00:30 WET on 29 Mar → midnight of 30 Mar (WEST, 23:00Z) = 22 h 30 min.
        assertEquals(
            Duration.ofHours(22).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-03-29T00:30:00Z"), lisbon),
        )
    }

    @Test
    fun `same instant in another zone gives another local day`() {
        // 23:30 in Lisbon = 07:30 the next day in Tokyo → 16 h 30 min until Tokyo midnight.
        assertEquals(
            Duration.ofHours(16).plusMinutes(30),
            durationUntilNextLocalMidnight(Instant.parse("2026-09-29T22:30:00Z"), ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun `zone where midnight does not exist starts the day at 01h`() {
        val santiago = ZoneId.of("America/Santiago")
        val now = Instant.parse("2026-09-05T16:00:00Z") // 12:00 on Saturday, -04
        val expectedStart = LocalDate.of(2026, 9, 6).atStartOfDay(santiago).toInstant()
        assertEquals(Duration.between(now, expectedStart), durationUntilNextLocalMidnight(now, santiago))
    }

    @Test
    fun `exactly at midnight waits the whole day`() {
        val now = LocalDate.of(2026, 9, 30).atStartOfDay(lisbon).toInstant()
        assertEquals(Duration.ofHours(24), durationUntilNextLocalMidnight(now, lisbon))
    }

    @Test
    fun `last event label in absolute time`() {
        val today = LocalDate.of(2026, 9, 29)
        assertEquals("último 13:32", lastLabel(Instant.parse("2026-09-29T12:32:05Z"), lisbon, today, lisbon))
        assertEquals("último 28 set", lastLabel(Instant.parse("2026-09-28T22:10:00Z"), lisbon, today, lisbon))
        assertEquals("sem registos", lastLabel(null, null, today, lisbon))
    }
}
