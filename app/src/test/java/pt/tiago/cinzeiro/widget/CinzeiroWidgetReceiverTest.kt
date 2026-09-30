package pt.tiago.cinzeiro.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppWidgetManager
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.R

/**
 * R5: the widget's "+1" (broadcast `ACTION_LOG` to the receiver itself) logs an event and delivers
 * new `RemoteViews` with the updated count via `AppWidgetManager.updateAppWidget`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = App::class)
class CinzeiroWidgetReceiverTest {
    private lateinit var app: App
    private lateinit var shadowManager: ShadowAppWidgetManager
    private var widgetId = 0

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // onEnabled schedules the MidnightWorker: synchronous WorkManager in tests (the app uses the default one).
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(app, Configuration.Builder().setExecutor { it.run() }.build())
        }
        shadowManager = shadowOf(AppWidgetManager.getInstance(app))
        widgetId = shadowManager.createWidget(CinzeiroWidgetReceiver::class.java, R.layout.widget_smoke)
    }

    @After fun tearDown() {
        app.container.database.close()
    }

    private fun countText(): String =
        shadowManager.getViewFor(widgetId).findViewById<TextView>(R.id.widget_count).text.toString()

    private fun dbCount(): Int = runBlocking {
        val repo = app.container.repository
        val (from, to) = dayBounds(repo.now().atZone(repo.zone()).toLocalDate(), repo.zone())
        repo.countBetween(from, to)
    }

    /** The work runs on `appScope` (Default) and the Toast goes through the main looper: advance both. */
    private fun waitUntil(what: String, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(20)
        }
        throw AssertionError("tempo esgotado à espera de: $what (count=${countText()}, bd=${dbCount()})")
    }

    private fun tapPlusOne() {
        val intent = Intent(app, CinzeiroWidgetReceiver::class.java).setAction(CinzeiroWidgetReceiver.ACTION_LOG)
        shadowManager.getAppWidgetProviderFor(widgetId).onReceive(app, intent)
    }

    @Test fun placed_widget_is_found_by_the_updater() {
        assertArrayEquals(intArrayOf(widgetId), WidgetUpdater.widgetIds(app))
    }

    @Test fun plus_one_inserts_an_event_and_redraws_with_the_new_count() {
        assertEquals(0, dbCount())

        tapPlusOne()
        waitUntil("contagem 1 no widget") { countText() == "1" }
        assertEquals(1, dbCount())

        tapPlusOne()
        waitUntil("contagem 2 no widget") { countText() == "2" }
        assertEquals(2, dbCount())
    }

    @Test fun plus_one_button_has_an_accessibility_description() {
        val button = shadowManager.getViewFor(widgetId).findViewById<TextView>(R.id.widget_log)
        assertEquals("Registar um cigarro", button.contentDescription)
        assertEquals("+1", button.text.toString())
    }
}
