package pt.tiago.cinzeiro.debug

import pt.tiago.cinzeiro.data.SmokeRepository
import java.time.Clock

/** Release: no debug tools (neither seed nor "delete all"). */
@Suppress("UNUSED_PARAMETER")
fun createDebugTools(repository: SmokeRepository, clock: Clock): DebugTools? = null
