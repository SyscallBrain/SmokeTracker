package pt.tiago.cinzeiro.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Ranges `[fromMs, toMs)`: start **inclusive**, end **exclusive** (a day = midnight to the
 * next midnight, without counting the same event on two days).
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

    /** Latest entry, one-shot read (widget, R4). */
    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms DESC, id DESC LIMIT 1")
    suspend fun last(): SmokeEventEntity?

    /** All entries ordered by instant (export). */
    @Query("SELECT * FROM smoke_event ORDER BY at_epoch_ms, id")
    suspend fun all(): List<SmokeEventEntity>

    /**
     * Deduplication keys `(at_epoch_ms, zone_id)` already present in `[fromMs, toMs]` (**both
     * inclusive**, unlike the other queries: it tests existence, not days).
     */
    @Query("SELECT at_epoch_ms, zone_id FROM smoke_event WHERE at_epoch_ms >= :fromMs AND at_epoch_ms <= :toMs")
    suspend fun keysBetween(fromMs: Long, toMs: Long): List<EventKey>

    /**
     * Import (R6): inserts, **in a single transaction**, the [events] whose key `(at_epoch_ms, zone_id)`
     * does not exist yet (neither in the DB nor earlier in the same batch). The received `id`s are ignored.
     * Returns how many were inserted. If anything fails, the transaction is rolled back — nothing is inserted.
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

/** Import deduplication key: instant to the millisecond + zone. */
data class EventKey(
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    @ColumnInfo(name = "zone_id") val zoneId: String,
)
