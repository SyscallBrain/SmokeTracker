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
 * Fonte de verdade dos registos. O relógio é injectado (testes com `Clock.fixed`).
 *
 * [changes] emite depois de cada inserção/remoção feita por este repositório — é o gancho
 * para quem vive fora do Compose (o widget do T8) se actualizar. Os ecrãs usam os `observe*`,
 * que o Room já reemite quando a tabela muda.
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

    /** Regista um cigarro agora, na zona actual. Devolve o id inserido (para «Desfazer»). */
    suspend fun logNow(): Long = insert(clock.instant(), clock.zone)

    /**
     * Registo retroactivo.
     * @throws IllegalArgumentException se [instant] estiver no futuro face ao relógio.
     */
    suspend fun logAt(instant: Instant, zone: ZoneId): Long {
        require(!instant.isAfter(clock.instant())) { "Registo no futuro: $instant" }
        return insert(instant, zone)
    }

    /** Apaga o registo com este id (não «o último»). Devolve true se existia. */
    suspend fun delete(id: Long): Boolean {
        val removed = dao.deleteById(id) > 0
        if (removed) _changes.tryEmit(Change.Deleted(id))
        return removed
    }

    /** Registos em `[from, to)`, ordenados por instante. */
    fun observeBetween(from: Instant, to: Instant): Flow<List<LoggedEvent>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toLogged() } }

    fun observeLast(): Flow<LoggedEvent?> = dao.observeLast().map { it?.toLogged() }

    fun observeFirstEventInstant(): Flow<Instant?> = dao.observeFirstEventMs().map { ms -> ms?.let(Instant::ofEpochMilli) }

    suspend fun firstEventInstant(): Instant? = dao.firstEventMs()?.let(Instant::ofEpochMilli)

    /** Contagem em `[from, to)` (para o widget). */
    suspend fun countBetween(from: Instant, to: Instant): Int =
        dao.countBetween(from.toEpochMilli(), to.toEpochMilli())

    /** Último registo, leitura única (widget, R4). */
    suspend fun lastEvent(): LoggedEvent? = dao.last()?.toLogged()

    /** Todos os registos, ordenados por instante (e id), para exportar. */
    suspend fun allEvents(): List<LoggedEvent> = dao.all().map { it.toLogged() }

    /** Quantos dos [events] seriam novos e quantos já existem (mesmo instante ao ms e mesma zona). */
    data class ImportPreview(val new: Int, val existing: Int)

    /** Pré-visualização da importação (diálogo de confirmação); não escreve nada. */
    suspend fun previewImport(events: List<SmokeEvent>): ImportPreview {
        if (events.isEmpty()) return ImportPreview(0, 0)
        val ms = events.map { it.instant.toEpochMilli() }
        val seen = dao.keysBetween(ms.min(), ms.max()).toHashSet()
        val new = events.count { seen.add(EventKey(it.instant.toEpochMilli(), it.zone.id)) }
        return ImportPreview(new = new, existing = events.size - new)
    }

    /**
     * Importa os [events] que ainda não existem, numa única transacção (tudo ou nada). Devolve o n.º
     * inserido. [changes] emite **uma** vez ([Change.Bulk]) no fim, e só se entrou alguma coisa.
     * @throws IllegalArgumentException se algum evento estiver no futuro (nada é inserido).
     */
    suspend fun importEvents(events: List<SmokeEvent>): Int {
        val now = clock.instant()
        events.forEachIndexed { i, e -> require(!e.instant.isAfter(now)) { "Registo n.º ${i + 1} no futuro: ${e.instant}" } }
        val inserted = dao.insertMissing(events.map { SmokeEventEntity(atEpochMs = it.instant.toEpochMilli(), zoneId = it.zone.id) })
        if (inserted > 0) _changes.tryEmit(Change.Bulk)
        return inserted
    }

    /** Só para as ferramentas de debug (seed / apagar tudo). */
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
