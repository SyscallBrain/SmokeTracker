package pt.tiago.cinzeiro.ui.stats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.tiago.cinzeiro.data.FakeSmokeEventDao
import pt.tiago.cinzeiro.data.SmokeRepository
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.domain.SmokeEvent
import pt.tiago.cinzeiro.domain.summary
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    // 2026-09-29 (terça) 13:32:05 em Lisboa.
    private val now = Instant.parse("2026-09-29T12:32:05Z")
    private val today = LocalDate.of(2026, 9, 29)
    private val clock = Clock.fixed(now, zone)
    private val dispatcher = StandardTestDispatcher()

    private lateinit var repo: SmokeRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = SmokeRepository(FakeSmokeEventDao(), clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newVm(): StatsViewModel {
        val vm = StatsViewModel(repo, compute = dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        runCurrent()
        return vm
    }

    /** [days] dias sintéticos até agora (semente fixa), 5–20 por dia, horas 7–23. */
    private fun synthetic(days: Int): List<Pair<Instant, ZoneId>> {
        val rnd = Random(7)
        val out = ArrayList<Pair<Instant, ZoneId>>()
        for (d in days - 1 downTo 0) {
            val date = today.minusDays(d.toLong())
            repeat(rnd.nextInt(5, 21)) {
                val t = date.atTime(rnd.nextInt(7, 24), rnd.nextInt(60)).atZone(zone).toInstant()
                if (!t.isAfter(now)) out += t to zone
            }
        }
        return out.sortedBy { it.first }
    }

    private fun events(raw: List<Pair<Instant, ZoneId>>) = raw.map { SmokeEvent(it.first, it.second) }

    @Test
    fun `BD vazia da resumo a zero e barras a zero`() = runTest(dispatcher) {
        val s = newVm().state.value
        assertEquals(false, s.loading)
        assertEquals(0, s.summary?.total)
        assertNull(s.summary!!.avgInterval)
        assertEquals(7, s.bars!!.values.size)
        assertTrue(s.bars!!.values.all { it == 0 })
    }

    @Test
    fun `cada periodo bate com summary sobre os mesmos dados (seed de 400 dias)`() = runTest(dispatcher) {
        val raw = synthetic(400)
        repo.replaceAll(raw)
        val all = events(raw)
        val first = raw.first().first.atZone(zone).toLocalDate()
        val vm = newVm()

        for (p in Period.entries) {
            vm.select(p)
            runCurrent()
            val s = vm.state.value
            assertEquals(p, s.period)
            assertEquals("summary $p", summary(all, p, today, first), s.summary)
            val bars = s.bars!!
            assertEquals("soma das barras $p", s.summary!!.total, bars.values.sum())
            assertEquals(bars.values.lastIndex, bars.highlightIndex)
            when (p) {
                Period.D7, Period.D30 -> {
                    assertEquals(Granularity.DAILY, bars.granularity)
                    assertEquals(p.days, bars.values.size)
                    assertEquals(today, bars.dates.last())
                }
                Period.D90, Period.Y1 -> {
                    assertEquals(Granularity.WEEKLY, bars.granularity)
                    assertTrue(bars.dates.all { it.dayOfWeek == DayOfWeek.MONDAY })
                    // 90 dias cobrem 13 ou 14 semanas; 365 cobrem 53 ou 54.
                    val expectedWeeks = if (p == Period.D90) 13..14 else 53..54
                    assertTrue("semanas ${bars.values.size}", bars.values.size in expectedWeeks)
                }
            }
        }
    }

    @Test
    fun `media movel so em 30 d, alinhada com as barras e com 30 pontos quando ha historico`() = runTest(dispatcher) {
        val raw = synthetic(60)
        repo.replaceAll(raw)
        val vm = newVm()
        vm.select(Period.D30)
        runCurrent()
        val bars = vm.state.value.bars!!
        assertEquals(30, bars.movingAvg.size)
        assertEquals((0 until 30).toList(), bars.movingAvg.map { it.first })
        // Último ponto = média dos últimos 7 dias.
        val last7 = bars.values.takeLast(7).sum() / 7.0
        assertEquals(last7, bars.movingAvg.last().second, 1e-9)

        vm.select(Period.D7)
        runCurrent()
        assertTrue(vm.state.value.bars!!.movingAvg.isEmpty())
    }

    @Test
    fun `media movel comeca no 7o dia de uso`() = runTest(dispatcher) {
        repo.replaceAll(synthetic(10))
        val vm = newVm()
        vm.select(Period.D30)
        runCurrent()
        val ma = vm.state.value.bars!!.movingAvg
        // 10 dias de uso → 4 pontos (dias 7..10), nos índices 26..29.
        assertEquals(listOf(26, 27, 28, 29), ma.map { it.first })
    }

    @Test
    fun `registo novo actualiza o total sem trocar de periodo`() = runTest(dispatcher) {
        val vm = newVm()
        repo.logNow()
        runCurrent()
        val s = vm.state.value
        assertEquals(Period.D7, s.period)
        assertEquals(1, s.summary!!.total)
        assertNotNull(s.summary!!.maxDay)
        assertEquals(today, s.summary!!.maxDay!!.date)
    }

    @Test
    fun `padroes respeitam o periodo e o pico do heatmap e a hora mais frequente`() = runTest(dispatcher) {
        // Hoje (terça) 3 às 9 h; há 10 dias (sábado, fora de 7 d) 5 às 22 h.
        val raw = buildList {
            repeat(3) { add(today.atTime(9, it).atZone(zone).toInstant() to zone) }
            repeat(5) { add(today.minusDays(10).atTime(22, it).atZone(zone).toInstant() to zone) }
        }
        repo.replaceAll(raw)
        val vm = newVm()

        var s = vm.state.value
        assertEquals(3, s.hourly.sum())
        assertEquals(3, s.hourly[9])
        assertEquals(3, s.heatmap[1][9]) // terça = índice 1
        assertEquals(3, s.heatmap.sumOf { it.sum() })
        assertEquals(13, s.currentHour)

        vm.select(Period.D30)
        runCurrent()
        s = vm.state.value
        assertEquals(8, s.hourly.sum())
        assertEquals(5, s.hourly[22])
        assertEquals(5, s.heatmap[5][22]) // sábado = índice 5
    }

    @Test
    fun `nivel do heatmap e 0 sem eventos e 1 no maximo`() {
        assertEquals(0f, pt.tiago.cinzeiro.ui.components.heatLevel(0, 10))
        assertEquals(0f, pt.tiago.cinzeiro.ui.components.heatLevel(3, 0))
        assertEquals(1f, pt.tiago.cinzeiro.ui.components.heatLevel(10, 10))
        assertTrue(pt.tiago.cinzeiro.ui.components.heatLevel(2, 10) < pt.tiago.cinzeiro.ui.components.heatLevel(5, 10))
    }
}
