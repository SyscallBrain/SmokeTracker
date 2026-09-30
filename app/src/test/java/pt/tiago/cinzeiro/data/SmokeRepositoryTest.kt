package pt.tiago.cinzeiro.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SmokeRepositoryTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-09-29T12:32:05Z")
    private val clock = Clock.fixed(now, lisbon)

    private lateinit var db: AppDatabase
    private lateinit var dao: SmokeEventDao
    private lateinit var repo: SmokeRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.smokeEventDao()
        repo = SmokeRepository(dao, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `logNow insere com o instante e a zona do relogio e observeLast ve-o`() = runBlocking {
        val id = repo.logNow()

        val last = repo.observeLast().first()!!
        assertEquals(id, last.id)
        assertEquals(now, last.instant)
        assertEquals(lisbon, last.zone)
    }

    @Test
    fun `inserir e observar pelo intervalo`() = runBlocking {
        repo.logAt(now.minusSeconds(60), lisbon)
        repo.logNow()

        val events = repo.observeBetween(now.minusSeconds(3600), now.plusSeconds(1)).first()
        assertEquals(2, events.size)
        assertTrue(events[0].instant < events[1].instant)
    }

    @Test
    fun `observeBetween inclui o inicio e exclui o fim`() = runBlocking {
        val from = now.minusSeconds(100)
        repo.logAt(from, lisbon) // == from → incluído
        repo.logAt(now, lisbon) // == to → excluído

        val events = repo.observeBetween(from, now).first()
        assertEquals(listOf(from), events.map { it.instant })
        assertEquals(1, repo.countBetween(from, now))
    }

    @Test
    fun `apagar pelo id remove so esse registo`() = runBlocking {
        val a = repo.logAt(now.minusSeconds(10), lisbon)
        val b = repo.logNow()

        assertTrue(repo.delete(a))
        assertFalse(repo.delete(a))

        val left = repo.observeBetween(now.minusSeconds(3600), now.plusSeconds(1)).first()
        assertEquals(listOf(b), left.map { it.id })
    }

    @Test
    fun `firstEventInstant e nulo com a BD vazia`() = runBlocking {
        assertNull(repo.firstEventInstant())
        assertNull(dao.firstEventMs())
        assertNull(repo.observeLast().first())
    }

    @Test
    fun `firstEventInstant devolve o mais antigo`() = runBlocking {
        repo.logNow()
        repo.logAt(now.minusSeconds(86_400), lisbon)
        assertEquals(now.minusSeconds(86_400), repo.firstEventInstant())
    }

    @Test
    fun `logAt rejeita instantes no futuro`() = runBlocking {
        try {
            repo.logAt(now.plusMillis(1), lisbon)
            fail("devia rejeitar o futuro")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, repo.countBetween(Instant.EPOCH, now.plusSeconds(3600)))
    }

    @Test
    fun `logAt guarda a zona do registo e nao a do relogio`() = runBlocking {
        val tokyo = ZoneId.of("Asia/Tokyo")
        repo.logAt(now.minusSeconds(5), tokyo)
        assertEquals(tokyo, repo.observeLast().first()!!.zone)
    }

    @Test
    fun `changes emite depois de inserir e apagar`() = runBlocking {
        val seen = mutableListOf<SmokeRepository.Change>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch {
            repo.changes.collect { seen += it }
        }
        val id = repo.logNow()
        repo.delete(id)
        job.cancel()
        assertEquals(listOf(SmokeRepository.Change.Inserted(id), SmokeRepository.Change.Deleted(id)), seen)
    }

    @Test
    fun `lastEvent devolve o mais recente por instante e null sem registos`() = runBlocking {
        assertNull(repo.lastEvent())
        val recent = repo.logNow()
        repo.logAt(now.minusSeconds(3600), ZoneId.of("Asia/Tokyo")) // inserido depois, mas mais antigo
        assertEquals(recent, repo.lastEvent()!!.id)
        repo.delete(recent)
        assertEquals(ZoneId.of("Asia/Tokyo"), repo.lastEvent()!!.zone)
    }

    @Test
    fun `allEvents devolve tudo por ordem de instante e depois id`() = runBlocking {
        val b = repo.logAt(now.minusSeconds(60), lisbon)
        val a = repo.logAt(now.minusSeconds(3600), ZoneId.of("Asia/Tokyo"))
        val c = repo.logAt(now.minusSeconds(60), lisbon)
        val all = repo.allEvents()
        assertEquals(listOf(a, b, c), all.map { it.id })
        assertEquals(ZoneId.of("Asia/Tokyo"), all[0].zone)
    }

    // --- Importação (R6) ---

    private fun fixtureEvents(): List<pt.tiago.cinzeiro.domain.SmokeEvent> {
        val text = javaClass.classLoader!!.getResource("smoketracker-export-2026-09-30.json")!!.readText()
        val r = pt.tiago.cinzeiro.domain.export.JsonImporter.read(text, Instant.parse("2026-09-30T12:00:00Z"))
        assertTrue(r.errors.toString(), r.ok)
        return r.events
    }

    @Test
    fun `importar a fixture real insere 13 e importar outra vez insere 0`() = runBlocking {
        val repo = SmokeRepository(dao, Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), lisbon))
        val events = fixtureEvents()
        assertEquals(SmokeRepository.ImportPreview(new = 13, existing = 0), repo.previewImport(events))
        assertEquals(13, repo.importEvents(events))
        assertEquals(13, repo.allEvents().size)
        assertEquals(events.map { it.instant }, repo.allEvents().map { it.instant })

        assertEquals(SmokeRepository.ImportPreview(new = 0, existing = 13), repo.previewImport(events))
        assertEquals(0, repo.importEvents(events))
        assertEquals(13, repo.allEvents().size)
    }

    @Test
    fun `importar depois de apagar 2 repoe so esses 2 com ids novos`() = runBlocking {
        val repo = SmokeRepository(dao, Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), lisbon))
        val events = fixtureEvents()
        repo.importEvents(events)
        val before = repo.allEvents()
        repo.delete(before[2].id)
        repo.delete(before[7].id)
        assertEquals(SmokeRepository.ImportPreview(new = 2, existing = 11), repo.previewImport(events))
        assertEquals(2, repo.importEvents(events))
        val after = repo.allEvents()
        assertEquals(before.map { it.instant to it.zone }, after.map { it.instant to it.zone })
        assertTrue(after.map { it.id }.max() > before.map { it.id }.max())
    }

    @Test
    fun `dedupe e por instante ao ms e zona - mesma hora noutra zona entra, repetidos no lote nao`() = runBlocking {
        val t = now.minusSeconds(60)
        repo.logAt(t, lisbon)
        val batch = listOf(
            pt.tiago.cinzeiro.domain.SmokeEvent(t, lisbon), // já existe
            pt.tiago.cinzeiro.domain.SmokeEvent(t, ZoneId.of("Asia/Tokyo")), // mesma instante, outra zona → novo
            pt.tiago.cinzeiro.domain.SmokeEvent(t.plusMillis(1), lisbon), // +1 ms → novo
            pt.tiago.cinzeiro.domain.SmokeEvent(t.plusMillis(1), lisbon), // repetido no próprio lote
        )
        assertEquals(SmokeRepository.ImportPreview(new = 2, existing = 2), repo.previewImport(batch))
        assertEquals(2, repo.importEvents(batch))
        assertEquals(3, repo.allEvents().size)
    }

    @Test
    fun `mudanca de hora - os dois 01h30 locais de 25-10-2026 entram ambos`() = runBlocking {
        val repo = SmokeRepository(dao, Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), lisbon))
        val batch = listOf(
            pt.tiago.cinzeiro.domain.SmokeEvent(Instant.parse("2026-10-25T00:30:00Z"), lisbon),
            pt.tiago.cinzeiro.domain.SmokeEvent(Instant.parse("2026-10-25T01:30:00Z"), lisbon),
        )
        assertEquals(2, repo.importEvents(batch))
        assertEquals(0, repo.importEvents(batch))
        assertEquals(2, repo.allEvents().size)
    }

    @Test
    fun `importar com evento no futuro nao insere nada`() = runBlocking {
        val batch = listOf(
            pt.tiago.cinzeiro.domain.SmokeEvent(now.minusSeconds(60), lisbon),
            pt.tiago.cinzeiro.domain.SmokeEvent(now.plusSeconds(60), lisbon),
        )
        try {
            repo.importEvents(batch)
            fail("devia recusar")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Registo n.º 2 no futuro"))
        }
        assertTrue(repo.allEvents().isEmpty())
    }

    @Test
    fun `insertMissing e uma transaccao - falha a meio reverte tudo`() = runBlocking {
        // Gatilho que aborta a inserção de uma zona «Boom»: simula uma falha a meio do lote.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER boom BEFORE INSERT ON smoke_event WHEN NEW.zone_id = 'Boom' BEGIN SELECT RAISE(ABORT, 'boom'); END"
        )
        val batch = listOf(
            SmokeEventEntity(atEpochMs = 1_000, zoneId = "Europe/Lisbon"),
            SmokeEventEntity(atEpochMs = 2_000, zoneId = "Europe/Lisbon"),
            SmokeEventEntity(atEpochMs = 3_000, zoneId = "Boom"),
        )
        try {
            dao.insertMissing(batch)
            fail("devia falhar")
        } catch (e: Exception) {
            assertTrue(e.toString(), e.toString().contains("boom"))
        }
        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun `importar emite changes uma so vez no fim e nada se nao entrar nenhum`() = runBlocking {
        val seen = mutableListOf<SmokeRepository.Change>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { repo.changes.collect { seen += it } }
        val batch = (1..5L).map { pt.tiago.cinzeiro.domain.SmokeEvent(now.minusSeconds(it * 60), lisbon) }
        assertEquals(5, repo.importEvents(batch))
        assertEquals(listOf<SmokeRepository.Change>(SmokeRepository.Change.Bulk), seen)
        assertEquals(0, repo.importEvents(batch))
        assertEquals(0, repo.importEvents(emptyList()))
        job.cancel()
        assertEquals(1, seen.size)
    }
}
