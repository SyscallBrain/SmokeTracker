package pt.tiago.cinzeiro.di

import android.content.Context
import pt.tiago.cinzeiro.data.AppDatabase
import pt.tiago.cinzeiro.data.SmokeRepository
import pt.tiago.cinzeiro.data.SystemZoneClock
import pt.tiago.cinzeiro.debug.DebugTools
import pt.tiago.cinzeiro.debug.createDebugTools
import pt.tiago.cinzeiro.settings.SettingsRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.Clock

/** Manual DI (ADR): one container per process, created in `App.onCreate`. */
class AppContainer(private val context: Context) {
    val clock: Clock = SystemZoneClock()
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val repository: SmokeRepository by lazy { SmokeRepository(database.smokeEventDao(), clock) }

    /** Single instance of the Settings DataStore in the process (app and widget). */
    val settings: SettingsRepository by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsRepository.FILE_NAME) },
        )
    }

    /** `null` in release: the seed code only exists in `src/debug`. */
    val debugTools: DebugTools? by lazy { createDebugTools(repository, clock) }
}
