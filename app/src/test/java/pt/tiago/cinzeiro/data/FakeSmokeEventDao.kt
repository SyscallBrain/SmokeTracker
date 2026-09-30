package pt.tiago.cinzeiro.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** DAO em memória para testes de ViewModel (sem Room). */
class FakeSmokeEventDao : SmokeEventDao {
    val rows = MutableStateFlow<List<SmokeEventEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(event: SmokeEventEntity): Long {
        val id = nextId++
        rows.value = rows.value + event.copy(id = id)
        return id
    }

    override suspend fun insertAll(events: List<SmokeEventEntity>) = events.forEach { insert(it) }

    override suspend fun deleteById(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override fun observeBetween(fromMs: Long, toMs: Long): Flow<List<SmokeEventEntity>> =
        rows.map { l -> l.filter { it.atEpochMs in fromMs until toMs }.sortedWith(compareBy({ it.atEpochMs }, { it.id })) }

    override fun observeLast(): Flow<SmokeEventEntity?> =
        rows.map { l -> l.maxWithOrNull(compareBy({ it.atEpochMs }, { it.id })) }

    override suspend fun firstEventMs(): Long? = rows.value.minOfOrNull { it.atEpochMs }

    override fun observeFirstEventMs(): Flow<Long?> = rows.map { l -> l.minOfOrNull { it.atEpochMs } }

    override suspend fun countBetween(fromMs: Long, toMs: Long): Int =
        rows.value.count { it.atEpochMs in fromMs until toMs }

    override fun observeCountBetween(fromMs: Long, toMs: Long): Flow<Int> =
        rows.map { l -> l.count { it.atEpochMs in fromMs until toMs } }

    override suspend fun all(): List<SmokeEventEntity> = rows.value.sortedWith(compareBy({ it.atEpochMs }, { it.id }))
}
