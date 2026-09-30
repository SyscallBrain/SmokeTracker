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

/** DI manual (ADR): um contentor por processo, criado em `App.onCreate`. */
class AppContainer(private val context: Context) {
    val clock: Clock = SystemZoneClock()
    val database: AppDatabase by lazy { AppDatabase.build(context) }
    val repository: SmokeRepository by lazy { SmokeRepository(database.smokeEventDao(), clock) }

    /** Única instância do DataStore das Definições no processo (app e widget). */
    val settings: SettingsRepository by lazy {
        SettingsRepository(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsRepository.FILE_NAME) },
        )
    }

    /** `null` em release: o código do seed só existe em `src/debug`. */
    val debugTools: DebugTools? by lazy { createDebugTools(repository, clock) }
}
