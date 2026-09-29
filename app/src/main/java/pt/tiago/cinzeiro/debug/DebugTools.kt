package pt.tiago.cinzeiro.debug

/** Acções de desenvolvimento; a implementação real só existe em `src/debug`. */
interface DebugTools {
    /** Substitui tudo por [days] dias de dados sintéticos. Devolve o n.º de registos gerados. */
    suspend fun seed(days: Int = 90): Int
    suspend fun clearAll()
}
