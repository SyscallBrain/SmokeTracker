package pt.tiago.cinzeiro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import pt.tiago.cinzeiro.domain.SmokeEvent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Source of truth for entries. The clock is injected (tests use `Clock.fixed`).
 *
 * [changes] emits after every insert/delete made by this repository — it is the hook for
 * whatever lives outside Compose (the T8 widget) to update itself. Screens use the `observe*`
 * functions, which Room already re-emits when the table changes.
 */
class SmokeRepository(
    private val dao: SmokeEventDao,
    private val clock: Clock,
) {
    private val _changes = MutableSharedFlow<Change>(extraBufferCapacity = 16)
    val changes: SharedFlow<Change> = _changes.asSharedFlow()

    sealed interface Change {
        data class Inserted(val id: Long) : Change
        data class Deleted(val id: Long) : Change
        data object Bulk : Change
    }

    fun now(): Instant = clock.instant()
    fun zone(): ZoneId = clock.zone

    /** Logs a cigarette now, in the current zone. Returns the inserted id (for "Desfazer"). */
    suspend fun logNow(): Long = insert(clock.instant(), clock.zone)

    /**
     * Retroactive entry.
     * @throws IllegalArgumentException if [instant] is in the future relative to the clock.
     */
    suspend fun logAt(instant: Instant, zone: ZoneId): Long {
        require(!instant.isAfter(clock.instant())) { "Registo no futuro: $instant" }
        return insert(instant, zone)
    }

    /** Deletes the entry with this id (not "the latest"). Returns true if it existed. */
    suspend fun delete(id: Long): Boolean {
        val removed = dao.deleteById(id) > 0
        if (removed) _changes.tryEmit(Change.Deleted(id))
        return removed
    }

    /** Entries in `[from, to)`, ordered by instant. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<LoggedEvent>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toLogged() } }

    fun observeLast(): Flow<LoggedEvent?> = dao.observeLast().map { it?.toLogged() }

    fun observeFirstEventInstant(): Flow<Instant?> = dao.observeFirstEventMs().map { ms -> ms?.let(Instant::ofEpochMilli) }

    suspend fun firstEventInstant(): Instant? = dao.firstEventMs()?.let(Instant::ofEpochMilli)

    /** Count in `[from, to)` (for the widget). */
    suspend fun countBetween(from: Instant, to: Instant): Int =
        dao.countBetween(from.toEpochMilli(), to.toEpochMilli())

    /** Latest entry, one-shot read (widget, R4). */
    suspend fun lastEvent(): LoggedEvent? = dao.last()?.toLogged()

    /** All entries, ordered by instant (and id), for export. */
    suspend fun allEvents(): List<LoggedEvent> = dao.all().map { it.toLogged() }

    /** How many of the [events] would be new and how many already exist (same instant to the ms and same zone). */
    data class ImportPreview(val new: Int, val existing: Int)

    /** Import preview (confirmation dialog); writes nothing. */
    suspend fun previewImport(events: List<SmokeEvent>): ImportPreview {
        if (events.isEmpty()) return ImportPreview(0, 0)
        val ms = events.map { it.instant.toEpochMilli() }
        val seen = dao.keysBetween(ms.min(), ms.max()).toHashSet()
        val new = events.count { seen.add(EventKey(it.instant.toEpochMilli(), it.zone.id)) }
        return ImportPreview(new = new, existing = events.size - new)
    }

    /**
     * Imports the [events] that do not exist yet, in a single transaction (all or nothing). Returns the
     * number inserted. [changes] emits **once** ([Change.Bulk]) at the end, and only if something was added.
     * @throws IllegalArgumentException if any event is in the future (nothing is inserted).
     */
    suspend fun importEvents(events: List<SmokeEvent>): Int {
        val now = clock.instant()
        events.forEachIndexed { i, e -> require(!e.instant.isAfter(now)) { "Registo n.º ${i + 1} no futuro: ${e.instant}" } }
        val inserted = dao.insertMissing(events.map { SmokeEventEntity(atEpochMs = it.instant.toEpochMilli(), zoneId = it.zone.id) })
        if (inserted > 0) _changes.tryEmit(Change.Bulk)
        return inserted
    }

    /** Only for the debug tools (seed / delete all). */
    suspend fun replaceAll(events: List<Pair<Instant, ZoneId>>) {
        dao.deleteAll()
        if (events.isNotEmpty()) {
            dao.insertAll(events.map { (i, z) -> SmokeEventEntity(atEpochMs = i.toEpochMilli(), zoneId = z.id) })
        }
        _changes.tryEmit(Change.Bulk)
    }

    private suspend fun insert(instant: Instant, zone: ZoneId): Long {
        val id = dao.insert(SmokeEventEntity(atEpochMs = instant.toEpochMilli(), zoneId = zone.id))
        _changes.tryEmit(Change.Inserted(id))
        return id
    }
}
