package pt.tiago.cinzeiro.ui.today

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pt.tiago.cinzeiro.data.FakeSmokeEventDao
import pt.tiago.cinzeiro.data.SmokeRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    // 2026-09-29 13:32:05 em Lisboa (UTC+1).
    private val now = Instant.parse("2026-09-29T12:32:05Z")
    private val clock = Clock.fixed(now, zone)
    private val dispatcher = StandardTestDispatcher()

    private lateinit var dao: FakeSmokeEventDao
    private lateinit var repo: SmokeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dao = FakeSmokeEventDao()
        repo = SmokeRepository(dao, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(): TodayViewModel {
        val vm = TodayViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun localAt(date: LocalDate, h: Int, m: Int) = date.atTime(h, m).atZone(zone).toInstant()

    @Test
    fun `BD vazia da contagem 0, sem ultimo e sem media`() = runTest(dispatcher) {
        val s = newVm().state.value
        assertEquals(false, s.loading)
        assertEquals(LocalDate.of(2026, 9, 29), s.date)
        assertEquals(0, s.countToday)
        assertNull(s.lastInstant)
        assertNull(s.avg7PerDay)
        assertEquals(13, s.currentHour)
    }

    @Test
    fun `logNow insere agora, sobe a contagem e emite o id e a hora local`() = runTest(dispatcher) {
        val vm = newVm()
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.events.first() }

        vm.logNow()
        runCurrent()

        val logged = event.await() as TodayEvent.Logged
        assertEquals(LocalTime.of(13, 32, 5), logged.at)
        assertEquals(1, vm.state.value.countToday)
        assertEquals(now, vm.state.value.lastInstant)
        assertEquals(1, vm.state.value.hourly[13])
        assertEquals(now.toEpochMilli(), dao.rows.value.single { it.id == logged.id }.atEpochMs)
    }

    @Test
    fun `undo apaga o id inserido e nao o ultimo registo`() = runTest(dispatcher) {
        val vm = newVm()
        val event = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.events.first() }
        vm.logNow()
        runCurrent()
        val mine = (event.await() as TodayEvent.Logged).id

        // Entretanto o widget regista outro (mais recente).
        val widgetId = repo.logNow()
        runCurrent()
        assertEquals(2, vm.state.value.countToday)

        vm.undo(mine)
        runCurrent()

        assertEquals(1, vm.state.value.countToday)
        assertEquals(listOf(widgetId), dao.rows.value.map { it.id })
    }

    @Test
    fun `media 7 dias usa ate ontem e ignora hoje`() = runTest(dispatcher) {
        val today = LocalDate.of(2026, 9, 29)
        // 14 dias anteriores: 10/dia nos 7 mais antigos, 7/dia nos 7 até ontem.
        for (d in 1..14) {
            val n = if (d <= 7) 7 else 10
            repeat(n) { i -> repo.logAt(localAt(today.minusDays(d.toLong()), 8 + i, 0), zone) }
        }
        // Hoje: 2 (não pode puxar a média para baixo).
        repo.logAt(localAt(today, 9, 0), zone)
        repo.logAt(localAt(today, 10, 30), zone)

        val s = newVm().state.value
        assertEquals(2, s.countToday)
        assertEquals(7.0, s.avg7PerDay!!, 1e-9)
        assertEquals(-30.0, s.avg7DeltaPct!!, 1e-9)
        assertEquals(Duration.ofMinutes(90), s.avgIntervalToday)
    }

    @Test
    fun `evento de ontem nao conta em hoje`() = runTest(dispatcher) {
        repo.logAt(localAt(LocalDate.of(2026, 9, 28), 23, 59), zone)
        val s = newVm().state.value
        assertEquals(0, s.countToday)
        assertEquals(localAt(LocalDate.of(2026, 9, 28), 23, 59), s.lastInstant)
    }
}

/** DAO em memória (sem Room): o ViewModel só precisa do contrato. */
