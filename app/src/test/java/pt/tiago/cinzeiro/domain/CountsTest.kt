package pt.tiago.cinzeiro.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountsTest {

    private val d = LocalDate.of(2026, 9, 28) // segunda-feira

    @Test
    fun `dailyCounts inclui dias sem eventos e o intervalo e inclusivo`() {
        val events = listOf(
            event("2026-09-28T08:00"),
            event("2026-09-28T09:00"),
            event("2026-09-30T23:59"),
            event("2026-10-01T00:00"), // fora do intervalo
            event("2026-09-27T23:59"), // fora do intervalo
        )

        val result = dailyCounts(events, d, d.plusDays(2))

        assertEquals(
            listOf(DayCount(d, 2), DayCount(d.plusDays(1), 0), DayCount(d.plusDays(2), 1)),
            result,
        )
    }

    @Test
    fun `dailyCounts de periodo sem eventos devolve zeros`() {
        val result = dailyCounts(emptyList(), d, d.plusDays(6))

        assertEquals(7, result.size)
        assertEquals(List(7) { 0 }, result.map { it.count })
        assertEquals(d.plusDays(6), result.last().date)
    }

    @Test
    fun `dailyCounts poe os dois 01h30 da mudanca de hora no mesmo dia`() {
        val events = listOf(
            SmokeEvent(java.time.Instant.parse("2026-10-25T00:30:00Z"), LISBON),
            SmokeEvent(java.time.Instant.parse("2026-10-25T01:30:00Z"), LISBON),
        )
        val day = LocalDate.of(2026, 10, 25)

        assertEquals(listOf(DayCount(day, 2)), dailyCounts(events, day, day))
    }

    @Test
    fun `weeklyCounts agrupa por semana que comeca a segunda`() {
        val events = listOf(
            event("2026-09-27T12:00"), // domingo -> semana de 21-09
            event("2026-09-28T00:10"), // segunda -> semana de 28-09
            event("2026-10-04T23:50"), // domingo -> semana de 28-09
            event("2026-10-05T10:00"), // segunda -> semana de 05-10
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
    fun `weeklyCounts so conta eventos dentro de from ate to`() {
        val events = listOf(
            event("2026-09-28T10:00"), // segunda, antes de from
            event("2026-09-30T10:00"),
        )

        val result = weeklyCounts(events, LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 1))

        assertEquals(listOf(WeekCount(LocalDate.of(2026, 9, 28), 1)), result)
    }
}
