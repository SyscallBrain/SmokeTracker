package pt.tiago.cinzeiro.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.cinzeiro.domain.SmokeEvent
import pt.tiago.cinzeiro.domain.dailyCounts
import pt.tiago.cinzeiro.domain.hourlyDistribution
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SyntheticDataTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-09-29T12:32:05Z")
    private val clock = Clock.fixed(now, zone)

    @Test
    fun `gera 90 dias, nenhum no futuro, ordenado e com picos nas horas esperadas`() {
        val data = SyntheticData.generate(clock, 90)
        val events = data.map { SmokeEvent(it.first, it.second) }

        assertTrue(events.all { !it.instant.isAfter(now) })
        assertEquals(events.sortedBy { it.instant }, events)

        val today = LocalDate.of(2026, 9, 29)
        val days = dailyCounts(events, today.minusDays(89), today)
        assertTrue(days.dropLast(1).all { it.count in 8..18 })
        assertEquals(today.minusDays(89), events.first().instant.atZone(zone).toLocalDate())

        val hours = hourlyDistribution(events)
        assertTrue(hours[8] > hours[3] && hours[21] > hours[4])
    }

    @Test
    fun `semente fixa da os mesmos dados`() {
        assertEquals(SyntheticData.generate(clock, 30), SyntheticData.generate(clock, 30))
    }
}
