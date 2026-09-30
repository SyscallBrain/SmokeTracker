package pt.tiago.cinzeiro.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Definições do utilizador em `DataStore<Preferences>` (ficheiro `settings.preferences_pb`).
 * Uma instância por processo (o DataStore não admite duas sobre o mesmo ficheiro): criada no
 * `AppContainer` e usada pela app e pelo `LogCigaretteAction` do widget.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            vibrate = p[VIBRATE] ?: true,
            vibrationType = VibrationType.fromStored(p[VIBRATION_TYPE]),
            showMessage = p[SHOW_MESSAGE] ?: true,
        )
    }

    /** Quantas vezes a app já mostrou o diálogo de `POST_NOTIFICATIONS`. */
    val notificationRequests: Flow<Int> = store.data.map { it[NOTIFICATION_REQUESTS] ?: 0 }

    suspend fun current(): Settings = settings.first()

    suspend fun setVibrate(value: Boolean) = store.edit { it[VIBRATE] = value }

    suspend fun setVibrationType(value: VibrationType) = store.edit { it[VIBRATION_TYPE] = value.name }

    suspend fun setShowMessage(value: Boolean) = store.edit { it[SHOW_MESSAGE] = value }

    suspend fun countNotificationRequest() = store.edit {
        it[NOTIFICATION_REQUESTS] = (it[NOTIFICATION_REQUESTS] ?: 0) + 1
    }

    companion object {
        const val FILE_NAME = "settings"
        val VIBRATE = booleanPreferencesKey("vibrate")
        val VIBRATION_TYPE = stringPreferencesKey("vibration_type")
        val SHOW_MESSAGE = booleanPreferencesKey("show_message")
        val NOTIFICATION_REQUESTS = intPreferencesKey("notification_requests")
    }
}
