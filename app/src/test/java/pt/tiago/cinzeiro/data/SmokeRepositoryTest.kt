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
}
