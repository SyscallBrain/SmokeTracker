package pt.tiago.cinzeiro.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountsTest {

    private val d = LocalDate.of(2026, 9, 28) // Monday

    @Test
    fun `dailyCounts includes days without events and the range is inclusive`() {
        val events = listOf(
            event("2026-09-28T08:00"),
            event("2026-09-28T09:00"),
            event("2026-09-30T23:59"),
            event("2026-10-01T00:00"), // outside the range
            event("2026-09-27T23:59"), // outside the range
        )

        val result = dailyCounts(events, d, d.plusDays(2))

        assertEquals(
            listOf(DayCount(d, 2), DayCount(d.plusDays(1), 0), DayCount(d.plusDays(2), 1)),
            result,
        )
    }

    @Test
    fun `dailyCounts for a period without events returns zeros`() {
        val result = dailyCounts(emptyList(), d, d.plusDays(6))

        assertEquals(7, result.size)
        assertEquals(List(7) { 0 }, result.map { it.count })
        assertEquals(d.plusDays(6), result.last().date)
    }

    @Test
    fun `dailyCounts puts both 01h30 of the DST change on the same day`() {
        val events = listOf(
            SmokeEvent(java.time.Instant.parse("2026-10-25T00:30:00Z"), LISBON),
            SmokeEvent(java.time.Instant.parse("2026-10-25T01:30:00Z"), LISBON),
        )
        val day = LocalDate.of(2026, 10, 25)

        assertEquals(listOf(DayCount(day, 2)), dailyCounts(events, day, day))
    }

    @Test
    fun `weeklyCounts groups by week starting on Monday`() {
        val events = listOf(
            event("2026-09-27T12:00"), // Sunday -> week of 21-09
            event("2026-09-28T00:10"), // Monday -> week of 28-09
            event("2026-10-04T23:50"), // Sunday -> week of 28-09
            event("2026-10-05T10:00"), // Monday -> week of 05-10
        )

        val result = weeklyCounts(events, LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 14))

        assertEquals(
            listOf(
                WeekCount(LocalDate.of(2026, 9, 21), 1),
                WeekCount(LocalDate.of(2026, 9, 28), 2),
                WeekCount(LocalDate.of(2026, 10, 5), 1),
                WeekCount(LocalDate.of(2026, 10, 12), 0),
            ),
            result,
        )
    }

    @Test
    fun `weeklyCounts only counts events within from to to`() {
        val events = listOf(
            event("2026-09-28T10:00"), // Monday, before from
            event("2026-09-30T10:00"),
        )

        val result = weeklyCounts(events, LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1))

        assertEquals(listOf(WeekCount(LocalDate.of(2026, 9, 28), 1)), result)
    }
}
