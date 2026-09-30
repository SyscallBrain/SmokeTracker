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

    @Test
    fun `exportar CSV escreve todos os registos e avisa com a contagem`() = runTest(dispatcher) {
        repo.replaceAll(listOf(at(0, 9, 40), at(100, 8, 0)))
        val vm = HistoryViewModel(repo, appVersion = "0.3.0", io = dispatcher)
        val events = mutableListOf<HistoryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        val out = java.io.ByteArrayOutputStream()
        vm.export(ExportFormat.CSV) { out }
        runCurrent()
        val lines = out.toString(Charsets.UTF_8).trimEnd().lines()
        assertEquals("id,instant_utc,zone_id,local_datetime", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].endsWith("Europe/Lisbon,2026-06-21T08:00:00"))
        assertEquals(HistoryEvent.Message("Exportados 2 registos"), events.last())
        assertEquals("smoketracker-2026-09-29.json", vm.suggestedFileName(ExportFormat.JSON))
    }

    @Test
    fun `exportar JSON leva app_version e falha com mensagem se o destino nao abrir`() = runTest(dispatcher) {
        repo.replaceAll(listOf(at(0, 9, 40)))
        val vm = HistoryViewModel(repo, appVersion = "0.3.0", io = dispatcher)
        val events = mutableListOf<HistoryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        val out = java.io.ByteArrayOutputStream()
        vm.export(ExportFormat.JSON) { out }
        runCurrent()
        val doc = pt.tiago.cinzeiro.domain.export.JsonExporter.read(out.toString(Charsets.UTF_8))
        assertEquals("0.3.0", doc.appVersion)
        assertEquals(1, doc.count)
        assertEquals("2026-09-29T12:32:05Z", doc.exportedAt)

        vm.export(ExportFormat.CSV) { null }
        runCurrent()
        assertEquals(HistoryEvent.Message("Falhou a exportação: destino indisponível"), events.last())
    }

    // --- Importação (R6) ---

    private fun fixtureStream() = javaClass.classLoader!!.getResourceAsStream("smoketracker-export-2026-09-30.json")!!

    private fun TestScope.importVm(): Pair<HistoryViewModel, MutableList<HistoryEvent>> {
        val vm = HistoryViewModel(repo, appVersion = "0.7.0", io = dispatcher)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val events = mutableListOf<HistoryEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        runCurrent()
        return vm to events
    }

    @Test
    fun `importar - preparar mostra N novos e M existentes, confirmar insere e avisa`() = runTest(dispatcher) {
        // Relógio do teste é 29-09 12:32Z; a fixture vai até 30-09 → usar um repo com relógio posterior.
        repo = SmokeRepository(FakeSmokeEventDao(), Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), zone))
        val (vm, events) = importVm()
        vm.prepareImport { fixtureStream() }
        runCurrent()
        val pending = vm.state.value.pendingImport!!
        assertEquals(13, pending.new)
        assertEquals(0, pending.existing)
        assertTrue(repo.allEvents().isEmpty()) // nada escrito antes de confirmar

        vm.confirmImport()
        runCurrent()
        assertNull(vm.state.value.pendingImport)
        assertEquals(13, repo.allEvents().size)
        assertEquals(HistoryEvent.Message("Importados 13 registos"), events.last())

        // O mesmo ficheiro outra vez: nada de diálogo, só a mensagem.
        vm.prepareImport { fixtureStream() }
        runCurrent()
        assertNull(vm.state.value.pendingImport)
        assertEquals(HistoryEvent.Message("Nada a importar: os 13 registos já existem"), events.last())
    }

    @Test
    fun `importar - apagar 2 e importar mostra 2 novos e 11 existentes`() = runTest(dispatcher) {
        repo = SmokeRepository(FakeSmokeEventDao(), Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), zone))
        val (vm, events) = importVm()
        vm.prepareImport { fixtureStream() }
        runCurrent()
        vm.confirmImport()
        runCurrent()
        val all = repo.allEvents()
        repo.delete(all[0].id)
        repo.delete(all[12].id)
        vm.prepareImport { fixtureStream() }
        runCurrent()
        assertEquals(PendingImport(vm.state.value.pendingImport!!.events, 2, 11), vm.state.value.pendingImport)
        vm.confirmImport()
        runCurrent()
        assertEquals(13, repo.allEvents().size)
        assertEquals(HistoryEvent.Message("Importados 2 registos"), events.last())
    }

    @Test
    fun `importar - cancelar nao escreve nada`() = runTest(dispatcher) {
        repo = SmokeRepository(FakeSmokeEventDao(), Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), zone))
        val (vm, _) = importVm()
        vm.prepareImport { fixtureStream() }
        runCurrent()
        vm.cancelImport()
        runCurrent()
        assertNull(vm.state.value.pendingImport)
        vm.confirmImport() // sem pendente: não faz nada
        runCurrent()
        assertTrue(repo.allEvents().isEmpty())
    }

    @Test
    fun `importar - ficheiro invalido da mensagem legivel com o indice e nao abre dialogo`() = runTest(dispatcher) {
        val (vm, events) = importVm()
        // O relógio do teste é 29-09 12:32:05Z; o 2.º registo fica no futuro.
        val text = """{"format":"cinzeiro-export","version":1,"exported_at":"2026-09-29T00:00:00Z","app_version":"0.6.0","count":2,
            "events":[{"id":1,"instant_utc":"2026-09-29T10:00:00Z","zone_id":"Europe/Lisbon","local_datetime":"x"},
                      {"id":2,"instant_utc":"2026-09-29T13:00:00Z","zone_id":"Europe/Lisbon","local_datetime":"x"}]}"""
        vm.prepareImport { text.byteInputStream() }
        runCurrent()
        assertNull(vm.state.value.pendingImport)
        assertEquals(
            HistoryEvent.Message("Ficheiro não importado: registo n.º 2: instante no futuro (2026-09-29T13:00:00Z)"),
            events.last(),
        )
        assertTrue(repo.allEvents().isEmpty())

        vm.prepareImport { "{}".byteInputStream() }
        runCurrent()
        assertTrue((events.last() as HistoryEvent.Message).text.startsWith("Ficheiro não importado: formato"))

        vm.prepareImport { null }
        runCurrent()
        assertEquals(HistoryEvent.Message("Ficheiro não importado: ficheiro indisponível"), events.last())
    }
}
