package pt.tiago.cinzeiro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    /** Contagem em `[fromMs, toMs)` que o Room reemite sempre que a tabela muda (widget). */
    @Query("SELECT COUNT(*) FROM smoke_event WHERE at_epoch_ms >= :fromMs AND at_epoch_ms < :toMs")
    fun observeCountBetween(fromMs: Long, toMs: Long): Flow<Int>

    /** Todos os registos por ordem de instante (exportação). */
    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms, id")
    suspend fun all(): List<SmokeEventEntity>
}
