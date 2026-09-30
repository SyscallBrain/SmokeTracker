package pt.tiago.cinzeiro.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Intervalos `[fromMs, toMs)`: início **inclusivo**, fim **exclusivo** (um dia = meia-noite
 * até à meia-noite seguinte, sem contar o mesmo evento em dois dias).
 */
@Dao
interface SmokeEventDao {
    @Insert
    suspend fun insert(event: SmokeEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<SmokeEventEntity>)

    @Query("DELETE FROM smoke_event WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM smoke_event")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM smoke_event WHERE at_epoch_ms >= :fromMs AND at_epoch_ms < :toMs " +
            "ORDER BY at_epoch_ms, id"
    )
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<SmokeEventEntity>>

    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms DESC, id DESC LIMIT 1")
    fun observeLast(): Flow<SmokeEventEntity?>

    @Query("SELECT MIN(at_epoch_ms) FROM smoke_event")
    suspend fun firstEventMs(): Long?

    @Query("SELECT MIN(at_epoch_ms) FROM smoke_event")
    fun observeFirstEventMs(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM smoke_event WHERE at_epoch_ms >= :fromMs AND at_epoch_ms < :toMs")
    suspend fun countBetween(fromMs: Long, toMs: Long): Int

    /** Último registo, leitura única (widget, R4). */
    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms DESC, id DESC LIMIT 1")
    suspend fun last(): SmokeEventEntity?

    /** Todos os registos por ordem de instante (exportação). */
    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms, id")
    suspend fun all(): List<SmokeEventEntity>

    /**
     * Chaves de deduplicação `(at_epoch_ms, zone_id)` já existentes em `[fromMs, toMs]` (**ambos
     * inclusivos**, ao contrário das outras consultas: serve para testar existência, não para dias).
     */
    @Query("SELECT at_epoch_ms, zone_id FROM smoke_event WHERE at_epoch_ms >= :fromMs AND at_epoch_ms <= :toMs")
    suspend fun keysBetween(fromMs: Long, toMs: Long): List<EventKey>

    /**
     * Importação (R6): insere, **numa só transacção**, os [events] cuja chave `(at_epoch_ms, zone_id)`
     * ainda não existe (nem na BD nem repetida antes no próprio lote). Os `id` recebidos são ignorados.
     * Devolve quantos foram inseridos. Se algo falhar, a transacção é revertida — nada fica inserido.
     */
    @Transaction
    suspend fun insertMissing(events: List<SmokeEventEntity>): Int {
        if (events.isEmpty()) return 0
        val seen = keysBetween(events.minOf { it.atEpochMs }, events.maxOf { it.atEpochMs }).toHashSet()
        val fresh = events.filter { seen.add(EventKey(it.atEpochMs, it.zoneId)) }.map { it.copy(id = 0) }
        if (fresh.isNotEmpty()) insertAll(fresh)
        return fresh.size
    }
}

/** Chave de deduplicação da importação: instante ao milissegundo + zona. */
data class EventKey(
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)
