package pt.tiago.cinzeiro.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var repo: SettingsRepository

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repo = SettingsRepository(newStore())
    }

    @After fun tearDown() = scope.cancel()

    private fun newStore() = PreferenceDataStoreFactory.create(scope = scope) {
        tmp.root.resolve("settings.preferences_pb")
    }

    @Test fun defaults_when_nothing_saved() = runBlocking {
        assertEquals(Settings(vibrate = true, vibrationType = VibrationType.SHORT, showMessage = true), repo.current())
        assertEquals(0, repo.notificationRequests.first())
    }

    @Test fun saves_and_reads_each_option() = runBlocking {
        repo.setVibrate(false)
        repo.setVibrationType(VibrationType.DOUBLE)
        repo.setShowMessage(false)
        assertEquals(Settings(vibrate = false, vibrationType = VibrationType.DOUBLE, showMessage = false), repo.current())
        repo.setVibrationType(VibrationType.STRONG)
        assertEquals(VibrationType.STRONG, repo.settings.first().vibrationType)
    }

    @Test fun values_survive_a_new_datastore_on_the_same_file() = runBlocking {
        repo.setVibrationType(VibrationType.STRONG)
        repo.setShowMessage(false)
        // cancel() não espera: a DataStore antiga só larga o ficheiro quando o scope termina.
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reopened = SettingsRepository(newStore())
        assertEquals(Settings(vibrate = true, vibrationType = VibrationType.STRONG, showMessage = false), reopened.current())
    }

    @Test fun counts_notification_requests() = runBlocking {
        repo.countNotificationRequest()
        repo.countNotificationRequest()
        assertEquals(2, repo.notificationRequests.first())
    }

    @Test fun keys_match_the_plan() {
        assertEquals("vibrate", SettingsRepository.VIBRATE.name)
        assertEquals("vibration_type", SettingsRepository.VIBRATION_TYPE.name)
        assertEquals("show_message", SettingsRepository.SHOW_MESSAGE.name)
    }

    @Test fun unknown_vibration_type_on_disk_reads_as_short() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("other.preferences_pb") }
        store.edit { it[SettingsRepository.VIBRATION_TYPE] = "TRIPLE" }
        assertEquals(VibrationType.SHORT, SettingsRepository(store).current().vibrationType)
    }
}
