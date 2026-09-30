package pt.tiago.cinzeiro

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * R4: duas escritas do log de diagnóstico → **um** ficheiro com duas linhas (a 0.5.0 criava um
 * ficheiro por linha porque o MediaStore renomeava `.log` para `.log.txt`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CrashLogFileTest {
    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    /** Android 8–9: ficheiro em Android/data/<pkg>/files/crashlogs/. */
    @Test @Config(sdk = [28])
    fun duas_escritas_reutilizam_o_mesmo_ficheiro_em_api_28() {
        CrashLog.appendLines(app, "x-feedback.txt", listOf("a"), 200)
        CrashLog.appendLines(app, "x-feedback.txt", listOf("b"), 200)
        val dir = File(app.getExternalFilesDir(null), "crashlogs")
        assertEquals(listOf("x-feedback.txt"), dir.list()!!.toList())
        assertEquals("a\nb\n", File(dir, "x-feedback.txt").readText())
    }

    // Android 10+ (MediaStore): não testável aqui — no Robolectric 4.17 (SDK 36) duas inserções em
    // MediaStore.Downloads seguidas de query por RELATIVE_PATH devolvem 0 linhas (experimentado na
    // R4). O que causava o bug (nome .log renomeado para .log.txt) fica coberto pelo teste abaixo.

    @Test @Config(sdk = [36])
    fun nome_que_nao_seja_txt_e_recusado() {
        assertThrows(IllegalArgumentException::class.java) {
            CrashLog.appendLines(app, "x-feedback.log", listOf("a"), 200)
        }
    }
}
