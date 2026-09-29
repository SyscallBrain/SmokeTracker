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
    fun `Period tem 7, 30, 90 e 365 dias`() {
        assertEquals(listOf(7, 30, 90, 365), Period.entries.map { it.days })
    }

    @Test
    fun `periodo sem eventos da total 0, perDay 0 e avgInterval null`() {
        val s = summary(emptyList(), Period.D7, today, firstEventDate = null)

        assertEquals(0, s.total)
        assertEquals(0.0, s.perDay, 1e-9)
        assertNull(s.maxDay)
        assertNull(s.avgInterval)
        assertNull(s.deltaPct)
    }

    @Test
    fun `total conta so os N dias que terminam em endInclusive`() {
        val events = listOf(
            event("2026-09-23T00:00"), // primeiro dia do D7
            event("2026-09-29T23:59"), // último dia do D7
            event("2026-09-22T23:59"), // fora
            event("2026-09-30T00:00"), // fora
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 1, 1))

        assertEquals(2, s.total)
    }

    @Test
    fun `primeira semana de uso divide perDay pelos dias efectivos`() {
        // Começou a usar há 3 dias (27, 28, 29): 6 cigarros -> 2/dia, não 6/30.
        val events = listOf("27", "27", "28", "28", "29", "29").mapIndexed { i, dd ->
            event("2026-09-${dd}T1$i:00")
        }

        val s = summary(events, Period.D30, today, firstEventDate = LocalDate.of(2026, 9, 27))

        assertEquals(6, s.total)
        assertEquals(2.0, s.perDay, 1e-9)
    }

    @Test
    fun `com historico longo perDay divide por N`() {
        val events = (0 until 7).map { event("2026-09-2${3 + it}T10:00") } // 1 por dia, 23..29

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2025, 1, 1))

        assertEquals(1.0, s.perDay, 1e-9)
    }

    @Test
    fun `avgInterval ignora o intervalo da noite`() {
        val events = listOf(
            event("2026-09-28T10:00"),
            event("2026-09-28T11:00"), // +60 min
            event("2026-09-28T23:00"), // +720 min
            event("2026-09-29T07:00"), // noite: ignorado
            event("2026-09-29T07:30"), // +30 min
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        // (60 + 720 + 30) / 3 = 270 min
        assertEquals(Duration.ofMinutes(270), s.avgInterval)
    }

    @Test
    fun `avgInterval so usa eventos dentro do periodo`() {
        val events = listOf(
            event("2026-09-20T10:00"), event("2026-09-20T10:10"), // período anterior: 10 min
            event("2026-09-28T10:00"), event("2026-09-28T12:00"), // período actual: 120 min
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(Duration.ofMinutes(120), s.avgInterval)
    }

    @Test
    fun `avgInterval e null quando nenhum dia tem dois eventos`() {
        val events = listOf(event("2026-09-27T23:00"), event("2026-09-28T01:00"), event("2026-09-29T09:00"))

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertNull(s.avgInterval)
    }

    @Test
    fun `avgInterval na mudanca de hora usa o tempo real entre os dois 01h30`() {
        val events = listOf(
            SmokeEvent(Instant.parse("2026-10-25T00:30:00Z"), LISBON),
            SmokeEvent(Instant.parse("2026-10-25T01:30:00Z"), LISBON),
        )

        val s = summary(events, Period.D7, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 1))

        assertEquals(Duration.ofHours(1), s.avgInterval)
    }

    @Test
    fun `maxDay e o dia com mais cigarros`() {
        val events = listOf(
            event("2026-09-25T10:00"),
            event("2026-09-26T10:00"), event("2026-09-26T11:00"), event("2026-09-26T12:00"),
            event("2026-09-28T10:00"), event("2026-09-28T11:00"),
        )

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(DayCount(LocalDate.of(2026, 9, 26), 3), s.maxDay)
    }

    @Test
    fun `summary D7 com endInclusive ontem ignora os eventos de hoje`() {
        val events = (0 until 7).map { event("2026-09-2${2 + it}T10:00") } + // 22..28, 1 por dia
            listOf(event("2026-09-29T08:00"), event("2026-09-29T09:00"))       // hoje

        val s = summary(events, Period.D7, yesterday, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(7, s.total)
        assertEquals(1.0, s.perDay, 1e-9)
        assertTrue(s.maxDay!!.date <= yesterday)
    }

    @Test
    fun `deltaPct compara perDay com o periodo anterior de igual duracao`() {
        // Anterior (16..22): 14 cigarros = 2/dia. Actual (23..29): 21 = 3/dia -> +50 %.
        val previous = (16..22).flatMap { dd -> List(2) { h -> event("2026-09-${dd}T1$h:00") } }
        val current = (23..29).flatMap { dd -> List(3) { h -> event("2026-09-${dd}T1$h:00") } }

        val s = summary(previous + current, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 16))

        assertNotNull(s.deltaPct)
        assertEquals(50.0, s.deltaPct!!, 1e-9)
    }

    @Test
    fun `deltaPct negativo quando fuma menos`() {
        val previous = (16..22).flatMap { dd -> List(4) { h -> event("2026-09-${dd}T1$h:00") } }
        val current = (23..29).flatMap { dd -> List(3) { h -> event("2026-09-${dd}T1$h:00") } }

        val s = summary(previous + current, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 1))

        assertEquals(-25.0, s.deltaPct!!, 1e-9)
    }

    @Test
    fun `deltaPct e null quando o periodo anterior nao esta completo`() {
        // Primeiro evento a 17-09: o período anterior (16..22) começa antes do primeiro uso.
        val events = (17..29).map { dd -> event("2026-09-${dd}T10:00") }

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 17))

        assertNull(s.deltaPct)
    }

    @Test
    fun `deltaPct e null quando o periodo anterior teve zero cigarros`() {
        val events = listOf(event("2026-09-10T10:00"), event("2026-09-29T10:00"))

        val s = summary(events, Period.D7, today, firstEventDate = LocalDate.of(2026, 9, 10))

        assertNull(s.deltaPct)
    }

    @Test
    fun `desempenho - summary sobre 3 anos com 40 por dia`() {
        val end = LocalDate.of(2026, 9, 29)
        val start = end.minusYears(3).plusDays(1)
        val events = ArrayList<SmokeEvent>()
        var day = start
        while (day <= end) {
            for (i in 0 until 40) {
                // 40 cigarros entre as 07:00 e as 23:40, de 25 em 25 min.
                val time = LocalTime.of(7, 0).plusMinutes(25L * i)
                events += SmokeEvent(day.atTime(time).atZone(LISBON).toInstant(), LISBON)
            }
            day = day.plusDays(1)
        }

        repeat(3) { Period.entries.forEach { p -> summary(events, p, end, start) } } // aquecimento JIT

        val runs = 10
        val timings = Period.entries.associateWith { p ->
            val t0 = System.nanoTime()
            repeat(runs) { summary(events, p, end, start) }
            (System.nanoTime() - t0) / runs / 1_000_000.0
        }
        timings.forEach { (p, ms) ->
            println("PERF summary %s sobre %d eventos: %.2f ms (média de %d)".format(p, events.size, ms, runs))
        }

        assertEquals(40 * 365, summary(events, Period.Y1, end, start).total)
    }
}
