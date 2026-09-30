package pt.tiago.cinzeiro.widget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        assertEquals(WidgetState(5, "último 13:32"), widgetState(5, last, lisbon, today, lisbon))
    }

    @Test
    fun `widgetState com ultimo de outro dia e sem registos`() {
        val ontem = Instant.parse("2026-09-29T21:00:00Z")
        assertEquals(WidgetState(0, "último 29 set"), widgetState(0, ontem, lisbon, today, lisbon))
        assertEquals(WidgetState(0, "sem registos"), widgetState(0, null, null, today, lisbon))
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

    @Test
    fun `widgetStateFlow reemite quando a contagem muda`() = runTest {
        val start = Instant.parse("2026-09-30T12:00:00Z")
        val rows = MutableStateFlow(listOf<Instant>())
        val seen = mutableListOf<WidgetState>()
        val job = launch {
            widgetStateFlow(
                now = { start.plusMillis(testScheduler.currentTime) },
                zone = lisbon,
                observeCount = { from, to -> rows.map { l -> l.count { it >= from && it < to } } },
                observeLast = { rows.map { l -> l.maxOrNull()?.let { LoggedEvent(1, SmokeEvent(it, lisbon)) } } },
            ).collect { seen += it }
        }
        runCurrent()
        rows.value = listOf(start)
        runCurrent()
        rows.value = emptyList()
        runCurrent()
        job.cancel()
        assertEquals(
            listOf(WidgetState(0, "sem registos"), WidgetState(1, "último 13:00"), WidgetState(0, "sem registos")),
            seen,
        )
    }

    @Test
    fun `widgetStateFlow troca de dia a meia-noite local sem novo update`() = runTest {
        val start = Instant.parse("2026-09-30T22:59:00Z") // 23:59 em Lisboa
        val events = listOf(Instant.parse("2026-09-30T20:00:00Z")) // 21:00 de hoje
        val seen = mutableListOf<WidgetState>()
        val job = launch {
            widgetStateFlow(
                now = { start.plusMillis(testScheduler.currentTime) },
                zone = lisbon,
                observeCount = { from, to -> flowOf(events.count { it >= from && it < to }) },
                observeLast = { flowOf(LoggedEvent(1, SmokeEvent(events.last(), lisbon))) },
            ).collect { seen += it }
        }
        runCurrent()
        assertEquals(listOf(WidgetState(1, "último 21:00")), seen)
        advanceTimeBy(62_000) // passa a meia-noite (+ margem)
        runCurrent()
        job.cancel()
        assertEquals(WidgetState(0, "último 30 set"), seen.last())
        assertEquals(2, seen.size)
    }
}
