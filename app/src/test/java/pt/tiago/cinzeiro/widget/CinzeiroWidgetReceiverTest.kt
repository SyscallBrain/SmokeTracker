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
 * R5: o «+1» do widget (broadcast `ACTION_LOG` para o próprio receiver) grava um registo e entrega
 * `RemoteViews` novas com a contagem actualizada via `AppWidgetManager.updateAppWidget`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = App::class)
class CinzeiroWidgetReceiverTest {
    private lateinit var app: App
    private lateinit var shadowManager: ShadowAppWidgetManager
    private var widgetId = 0

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // onEnabled agenda o MidnightWorker: WorkManager síncrono em teste (o da app é o por omissão).
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

    /** O trabalho corre em `appScope` (Default) e o Toast passa pela main looper: avança ambos. */
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

    @Test fun widget_colocado_e_encontrado_pelo_updater() {
        assertArrayEquals(intArrayOf(widgetId), WidgetUpdater.widgetIds(app))
    }

    @Test fun mais_um_insere_registo_e_redesenha_com_a_contagem_nova() {
        assertEquals(0, dbCount())

        tapPlusOne()
        waitUntil("contagem 1 no widget") { countText() == "1" }
        assertEquals(1, dbCount())

        tapPlusOne()
        waitUntil("contagem 2 no widget") { countText() == "2" }
        assertEquals(2, dbCount())
    }

    @Test fun botao_mais_um_tem_descricao_de_acessibilidade() {
        val button = shadowManager.getViewFor(widgetId).findViewById<TextView>(R.id.widget_log)
        assertEquals("Registar um cigarro", button.contentDescription)
        assertEquals("+1", button.text.toString())
    }
}
