package pt.tiago.cinzeiro.debug

import pt.tiago.cinzeiro.data.SmokeRepository
import java.time.Clock

fun createDebugTools(repository: SmokeRepository, clock: Clock): DebugTools? = object : DebugTools {
    override suspend fun seed(days: Int): Int {
        val events = SyntheticData.generate(clock, days)
        repository.replaceAll(events)
        return events.size
    }

    override suspend fun clearAll() = repository.replaceAll(emptyList())
}
