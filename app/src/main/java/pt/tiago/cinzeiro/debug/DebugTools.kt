package pt.tiago.cinzeiro.debug

/** Development actions; the real implementation only exists in `src/debug`. */
interface DebugTools {
    /** Replaces everything with [days] days of synthetic data. Returns the number of entries generated. */
    suspend fun seed(days: Int = 90): Int
    suspend fun clearAll()
}
