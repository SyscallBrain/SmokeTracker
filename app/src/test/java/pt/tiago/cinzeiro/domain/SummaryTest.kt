package pt.tiago.cinzeiro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SummaryTest {

    private val today = LocalDate.of(2026, 9, 29)
    private val yesterday = today.minusDays(1)

    @Test
    fun `Period has 7, 30, 90 and 365 days`() {
        assertEquals(listOf(7, 30, 90, 365), Period.entries.map { it.days })
    }

    @Test
    fun `period without events gives total 0, perDay 0 and avgInterval null`() {
        val s = summary(emptyList(), Period.D7, today, firstEventDate = null)

        assertEquals(0, s.total)
        assertEquals(0.0, s.perDay, 1e-9)
        assertNull(s.maxDay)
        assertNull(s.avgInterval)
        assertNull(s.deltaPct)
    }

    @Test
    fun `total counts only the N days ending at endInclusive`() {
        val events = listOf(
            event("2026-09-23T00:00"), // first day of D7
            event("2026-09-29T23:59"), // last day of D7
            event("2026-09-22T23:59"), // outside
            event("2026-09-30T00:00"), // outside
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 1, 1))

        assertEquals(2, s.total)
    }

    @Test
    fun `first week of use divides perDay by the actual days`() {
        // Started using 3 days ago (27, 28, 29): 6 cigarettes -> 2/day, not 6/30.
        val events = listOf("27", "27", "28", "28", "29", "29").mapIndexed { i, dd ->
            event("2026-09-${dd}T1$i:00")
        }

        val s = summary(events, Period.D30, today, firstEventDate = LocalDate.of(2026, 9, 27))

        assertEquals(6, s.total)
        assertEquals(2.0, s.perDay, 1e-9)
    }

    @Test
    fun `with long history perDay divides by N`() {
        val events = (0 until 7).map { event("2026-09-2${3 + it}T10:00") } // 1 per day, 23..29

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2025, 1, 1))

        assertEquals(1.0, s.perDay, 1e-9)
    }

    @Test
    fun `avgInterval ignores the overnight interval`() {
        val events = listOf(
            event("2026-09-28T10:00"),
            event("2026-09-28T11:00"), // +60 min
            event("2026-09-28T23:00"), // +720 min
            event("2026-09-29T07:00"), // overnight: ignored
            event("2026-09-29T07:30"), // +30 min
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        // (60 + 720 + 30) / 3 = 270 min
        assertEquals(Duration.ofMinutes(270), s.avgInterval)
    }

    @Test
    fun `avgInterval only uses events within the period`() {
        val events = listOf(
            event("2026-09-20T10:00"), event("2026-09-20T10:10"), // previous period: 10 min
            event("2026-09-28T10:00"), event("2026-09-28T12:00"), // current period: 120 min
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(Duration.ofMinutes(120), s.avgInterval)
    }

    @Test
    fun `avgInterval is null when no day has two events`() {
        val events = listOf(event("2026-09-27T23:00"), event("2026-09-28T01:00"), event("2026-09-29T09:00"))

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertNull(s.avgInterval)
    }

    @Test
    fun `avgInterval on DST change uses the real time between the two 01h30`() {
        val events = listOf(
            SmokeEvent(Instant.parse("2026-10-25T00:30:00Z"), LISBON),
            SmokeEvent(Instant.parse("2026-10-25T01:30:00Z"), LISBON),
        )

        val s = summary(events, Period.D7, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 1))

        assertEquals(Duration.ofHours(1), s.avgInterval)
    }

    @Test
    fun `maxDay is the day with the most cigarettes`() {
        val events = listOf(
            event("2026-09-25T10:00"),
            event("2026-09-26T10:00"), event("2026-09-26T11:00"), event("2026-09-26T12:00"),
            event("2026-09-28T10:00"), event("2026-09-28T11:00"),
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(DayCount(LocalDate.of(2026, 9, 26), 3), s.maxDay)
    }

    @Test
    fun `summary D7 with endInclusive yesterday ignores today's events`() {
        val events = (0 until 7).map { event("2026-09-2${2 + it}T10:00") } + // 22..28, 1 per day
            listOf(event("2026-09-29T08:00"), event("2026-09-29T09:00"))       // today

        val s = summary(events, Period.D7, yesterday, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(7, s.total)
        assertEquals(1.0, s.perDay, 1e-9)
        assertTrue(s.maxDay!!.date <= yesterday)
    }

    @Test
    fun `deltaPct compares perDay with the previous period of equal length`() {
        // Previous (16..22): 14 cigarettes = 2/day. Current (23..29): 21 = 3/day -> +50 %.
        val previous = (16..22).flatMap { dd -> List(2) { h -> event("2026-09-${dd}T1$h:00") } }
        val current = (23..29).flatMap { dd -> List(3) { h -> event("2026-09-${dd}T1$h:00") } }

        val s = summary(previous + current, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 16))

        assertNotNull(s.deltaPct)
        assertEquals(50.0, s.deltaPct!!, 1e-9)
    }

    @Test
    fun `deltaPct negative when smoking less`() {
        val previous = (16..22).flatMap { dd -> List(4) { h -> event("2026-09-${dd}T1$h:00") } }
        val current = (23..29).flatMap { dd -> List(3) { h -> event("2026-09-${dd}T1$h:00") } }

        val s = summary(previous + current, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(-25.0, s.deltaPct!!, 1e-9)
    }

    @Test
    fun `deltaPct is null when the previous period is not complete`() {
        // First event on 17-09: the previous period (16..22) starts before first use.
        val events = (17..29).map { dd -> event("2026-09-${dd}T10:00") }

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 17))

        assertNull(s.deltaPct)
    }

    @Test
    fun `deltaPct is null when the previous period had zero cigarettes`() {
        val events = listOf(event("2026-09-10T10:00"), event("2026-09-29T10:00"))

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 10))

        assertNull(s.deltaPct)
    }

    @Test
    fun `performance - summary over 3 years with 40 per day`() {
        val end = LocalDate.of(2026, 9, 29)
        val start = end.minusYears(3).plusDays(1)
        val events = ArrayList<SmokeEvent>()
        var day = start
        while (day <= end) {
            for (i in 0 until 40) {
                // 40 cigarettes between 07:00 and 23:40, every 25 min.
                val time = LocalTime.of(7, 0).plusMinutes(25L * i)
                events += SmokeEvent(day.atTime(time).atZone(LISBON).toInstant(), LISBON)
            }
            day = day.plusDays(1)
        }

        repeat(3) { Period.entries.forEach { p -> summary(events, p, end, start) } } // JIT warm-up

        val runs = 10
        val timings = Period.entries.associateWith { p ->
            val t0 = System.nanoTime()
            repeat(runs) { summary(events, p, end, start) }
            (System.nanoTime() - t0) / runs / 1_000_000.0
        }
        timings.forEach { (p, ms) ->
            println("PERF summary %s over %d events: %.2f ms (average of %d)".format(p, events.size, ms, runs))
        }

        assertEquals(40 * 365, summary(events, Period.Y1, end, start).total)
    }
}
