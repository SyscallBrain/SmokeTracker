package pt.tiago.cinzeiro.ui.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.tiago.cinzeiro.data.FakeSmokeEventDao
import pt.tiago.cinzeiro.data.SmokeRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-09-29T12:32:05Z") // 13:32:05 em Lisboa
    private val today = LocalDate.of(2026, 9, 29)
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: SmokeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = SmokeRepository(FakeSmokeEventDao(), Clock.fixed(now, zone))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(): HistoryViewModel {
        val vm = HistoryViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun at(daysAgo: Long, h: Int, m: Int) = today.minusDays(daysAgo).atTime(h, m).atZone(zone).toInstant() to zone

    @Test
    fun `agrupa por dia, mais recente primeiro, e pagina em blocos de 30 dias`() = runTest(dispatcher) {
        repo.replaceAll(listOf(at(0, 9, 40), at(0, 13, 32), at(1, 23, 10), at(45, 10, 0), at(75, 8, 0)))
        val vm = newVm()
        var s = vm.state.value
        assertFalse(s.isEmpty)
        assertEquals(listOf(today, today.minusDays(1)), s.days.map { it.date })
        assertEquals(listOf(LocalTime.of(13, 32), LocalTime.of(9, 40)), s.days[0].events.map { it.instant.atZone(zone).toLocalTime() })
        assertTrue(s.canLoadMore)

        vm.loadMore()
        runCurrent()
        s = vm.state.value
        assertEquals(60, s.loadedDays)
        assertEquals(3, s.days.size)
        assertTrue(s.canLoadMore)

        vm.loadMore()
        runCurrent()
        s = vm.state.value
        assertEquals(4, s.days.size)
        assertFalse(s.canLoadMore)
        vm.loadMore() // sem efeito
        runCurrent()
        assertEquals(90, vm.state.value.loadedDays)
    }

    @Test
    fun `apagar e desfazer repoe o mesmo instante e a mesma zona`() = runTest(dispatcher) {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val instant = Instant.parse("2026-09-28T14:05:07.123Z")
        repo.logAt(instant, tokyo)
        val vm = newVm()
        val ev = vm.state.value.days.single().events.single()

        val events = mutableListOf<HistoryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.delete(ev)
        runCurrent()
        assertTrue(vm.state.value.days.isEmpty())
        assertEquals(HistoryEvent.Deleted(ev), events.single())

        vm.undoDelete(ev)
        runCurrent()
        val back = vm.state.value.days.single().events.single()
        assertEquals(instant, back.instant)
        assertEquals(tokyo, back.zone)
    }

    @Test
    fun `adicionar retroactivo no passado grava na zona actual e no futuro e recusado`() = runTest(dispatcher) {
        val vm = newVm()
        val events = mutableListOf<HistoryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.addPast(today.minusDays(3), LocalTime.of(22, 15))
        runCurrent()
        val added = repo.observeBetween(Instant.EPOCH, now.plusSeconds(1)).first().single()
        assertEquals(today.minusDays(3).atTime(22, 15).atZone(zone).toInstant(), added.instant)
        assertEquals(zone, added.zone)
        assertTrue(events.last() is HistoryEvent.Added)

        vm.addPast(today, LocalTime.of(13, 33)) // 1 min no futuro
        runCurrent()
        assertEquals(HistoryEvent.Message("Não é possível registar no futuro"), events.last())
        assertEquals(1, repo.observeBetween(Instant.EPOCH, now.plusSeconds(3600)).first().size)
    }

    @Test
    fun `validacao de futuro na fronteira`() {
        assertEquals(now.minusSeconds(5), pastInstantOrNull(today, LocalTime.of(13, 32), zone, now))
        assertNull(pastInstantOrNull(today, LocalTime.of(13, 33), zone, now))
        assertNull(pastInstantOrNull(today.plusDays(1), LocalTime.of(0, 0), zone, now))
    }

    @Test
    fun `BD vazia fica isEmpty sem paginas`() = runTest(dispatcher) {
        val s = newVm().state.value
        assertTrue(s.isEmpty)
        assertFalse(s.canLoadMore)
        assertTrue(s.days.isEmpty())
    }
}
