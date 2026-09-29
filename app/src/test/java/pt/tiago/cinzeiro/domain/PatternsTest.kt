package pt.tiago.cinzeiro.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PatternsTest {

    @Test
    fun `hourlyDistribution soma por hora local`() {
        val events = listOf(
            event("2026-09-28T08:05"),
            event("2026-09-29T08:59"),
            event("2026-09-29T23:00"),
            event("2026-09-29T00:00"),
        )

        val result = hourlyDistribution(events)

        val expected = IntArray(24).also { it[8] = 2; it[23] = 1; it[0] = 1 }
        assertArrayEquals(expected, result)
    }

    @Test
    fun `heatmap usa indice 0 para segunda e 6 para domingo`() {
        val events = listOf(
            event("2026-09-28T09:00"), // segunda
            event("2026-09-28T09:30"), // segunda
            event("2026-10-04T22:00"), // domingo
        )

        val grid = weekdayHourHeatmap(events)

        assertEquals(7, grid.size)
        assertEquals(24, grid[0].size)
        assertEquals(2, grid[0][9])
        assertEquals(1, grid[6][22])
        assertEquals(3, grid.sumOf { it.sum() })
    }

    @Test
    fun `movingAverage devolve null nos 6 primeiros dias`() {
        val start = LocalDate.of(2026, 9, 1)
        val daily = (0 until 9).map { DayCount(start.plusDays(it.toLong()), it) } // 0..8

        val result = movingAverage(daily)

        assertEquals(9, result.size)
        (0 until 6).forEach { assertNull(result[it]) }
        assertEquals(3.0, result[6]!!, 1e-9) // média de 0..6
        assertEquals(4.0, result[7]!!, 1e-9) // média de 1..7
        assertEquals(5.0, result[8]!!, 1e-9) // média de 2..8
    }

    @Test
    fun `movingAverage respeita a janela pedida`() {
        val start = LocalDate.of(2026, 9, 1)
        val daily = listOf(4, 2, 6).mapIndexed { i, c -> DayCount(start.plusDays(i.toLong()), c) }

        assertEquals(listOf(null, 3.0, 4.0), movingAverage(daily, window = 2))
    }
}
