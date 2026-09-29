package pt.tiago.cinzeiro.di

import android.content.Context
import pt.tiago.cinzeiro.data.AppDatabase
import pt.tiago.cinzeiro.data.SmokeRepository
import pt.tiago.cinzeiro.data.SystemZoneClock
import pt.tiago.cinzeiro.debug.DebugTools
import pt.tiago.cinzeiro.debug.createDebugTools
import java.time.Clock

/** DI manual (ADR): um contentor por processo, criado em `App.onCreate`. */
class AppContainer(context: Context) {
    val clock: Clock = SystemZoneClock()
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val repository: SmokeRepository by lazy { SmokeRepository(database.smokeEventDao(), clock) }

    /** `null` em release: o código do seed só existe em `src/debug`. */
    val debugTools: DebugTools? by lazy { createDebugTools(repository, clock) }
}
