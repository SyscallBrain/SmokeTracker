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
    fun `logNow inserts with the clock instant and zone and observeLast sees it`() = runBlocking {
        val id = repo.logNow()

        val last = repo.observeLast().first()!!
        assertEquals(id, last.id)
        assertEquals(now, last.instant)
        assertEquals(lisbon, last.zone)
    }

    @Test
    fun `insert and observe by range`() = runBlocking {
        repo.logAt(now.minusSeconds(60), lisbon)
        repo.logNow()

        val events = repo.observeBetween(now.minusSeconds(3600), now.plusSeconds(1)).first()
        assertEquals(2, events.size)
        assertTrue(events[0].instant < events[1].instant)
    }

    @Test
    fun `observeBetween includes the start and excludes the end`() = runBlocking {
        val from = now.minusSeconds(100)
        repo.logAt(from, lisbon) // == from → included
        repo.logAt(now, lisbon) // == to → excluded

        val events = repo.observeBetween(from, now).first()
        assertEquals(listOf(from), events.map { it.instant })
        assertEquals(1, repo.countBetween(from, now))
    }

    @Test
    fun `delete by id removes only that entry`() = runBlocking {
        val a = repo.logAt(now.minusSeconds(10), lisbon)
        val b = repo.logNow()

        assertTrue(repo.delete(a))
        assertFalse(repo.delete(a))

        val left = repo.observeBetween(now.minusSeconds(3600), now.plusSeconds(1)).first()
        assertEquals(listOf(b), left.map { it.id })
    }

    @Test
    fun `firstEventInstant is null with an empty DB`() = runBlocking {
        assertNull(repo.firstEventInstant())
        assertNull(dao.firstEventMs())
        assertNull(repo.observeLast().first())
    }

    @Test
    fun `firstEventInstant returns the oldest`() = runBlocking {
        repo.logNow()
        repo.logAt(now.minusSeconds(86_400), lisbon)
        assertEquals(now.minusSeconds(86_400), repo.firstEventInstant())
    }

    @Test
    fun `logAt rejects instants in the future`() = runBlocking {
        try {
            repo.logAt(now.plusMillis(1), lisbon)
            fail("should reject a future instant")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(0, repo.countBetween(Instant.EPOCH, now.plusSeconds(3600)))
    }

    @Test
    fun `logAt stores the entry zone and not the clock zone`() = runBlocking {
        val tokyo = ZoneId.of("Asia/Tokyo")
        repo.logAt(now.minusSeconds(5), tokyo)
        assertEquals(tokyo, repo.observeLast().first()!!.zone)
    }

    @Test
    fun `changes emits after insert and delete`() = runBlocking {
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
    fun `lastEvent returns the most recent by instant and null with no entries`() = runBlocking {
        assertNull(repo.lastEvent())
        val recent = repo.logNow()
        repo.logAt(now.minusSeconds(3600), ZoneId.of("Asia/Tokyo")) // inserted later, but older
        assertEquals(recent, repo.lastEvent()!!.id)
        repo.delete(recent)
        assertEquals(ZoneId.of("Asia/Tokyo"), repo.lastEvent()!!.zone)
    }

    @Test
    fun `allEvents returns everything ordered by instant then id`() = runBlocking {
        val b = repo.logAt(now.minusSeconds(60), lisbon)
        val a = repo.logAt(now.minusSeconds(3600), ZoneId.of("Asia/Tokyo"))
        val c = repo.logAt(now.minusSeconds(60), lisbon)
        val all = repo.allEvents()
        assertEquals(listOf(a, b, c), all.map { it.id })
        assertEquals(ZoneId.of("Asia/Tokyo"), all[0].zone)
    }

    // --- Import (R6) ---

    private fun fixtureEvents(): List<pt.tiago.cinzeiro.domain.SmokeEvent> {
        val text = javaClass.classLoader!!.getResource("sample-export-v1.json")!!.readText()
        val r = pt.tiago.cinzeiro.domain.export.JsonImporter.read(text, Instant.parse("2026-10-26T12:00:00Z"))
        assertTrue(r.errors.toString(), r.ok)
        return r.events
    }

    @Test
    fun `importing the synthetic fixture inserts 13 and importing again inserts 0`() = runBlocking {
        val repo = SmokeRepository(dao, Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), lisbon))
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
    fun `importing after deleting 2 restores only those 2 with new ids`() = runBlocking {
        val repo = SmokeRepository(dao, Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), lisbon))
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
    fun `dedupe is by instant to the ms and zone - same time in another zone goes in, duplicates within the batch do not`() = runBlocking {
        val t = now.minusSeconds(60)
        repo.logAt(t, lisbon)
        val batch = listOf(
            pt.tiago.cinzeiro.domain.SmokeEvent(t, lisbon), // already exists
            pt.tiago.cinzeiro.domain.SmokeEvent(t, ZoneId.of("Asia/Tokyo")), // same instant, other zone → new
            pt.tiago.cinzeiro.domain.SmokeEvent(t.plusMillis(1), lisbon), // +1 ms → new
            pt.tiago.cinzeiro.domain.SmokeEvent(t.plusMillis(1), lisbon), // duplicated within the batch itself
        )
        assertEquals(SmokeRepository.ImportPreview(new = 2, existing = 2), repo.previewImport(batch))
        assertEquals(2, repo.importEvents(batch))
        assertEquals(3, repo.allEvents().size)
    }

    @Test
    fun `DST change - both local 01h30 on 25-10-2026 go in`() = runBlocking {
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
    fun `importing with an event in the future inserts nothing`() = runBlocking {
        val batch = listOf(
            pt.tiago.cinzeiro.domain.SmokeEvent(now.minusSeconds(60), lisbon),
            pt.tiago.cinzeiro.domain.SmokeEvent(now.plusSeconds(60), lisbon),
        )
        try {
            repo.importEvents(batch)
            fail("should have been rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Registo n.º 2 no futuro"))
        }
        assertTrue(repo.allEvents().isEmpty())
    }

    @Test
    fun `insertMissing is a transaction - failure midway rolls everything back`() = runBlocking {
        // Trigger that aborts inserting a "Boom" zone: simulates a failure midway through the batch.
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
            fail("should have failed")
        } catch (e: Exception) {
            assertTrue(e.toString(), e.toString().contains("boom"))
        }
        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun `import emits changes only once at the end and nothing if none go in`() = runBlocking {
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
