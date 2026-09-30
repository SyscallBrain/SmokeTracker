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
 * R4: two writes to the diagnostic log → **one** file with two lines (0.5.0 created one
 * file per line because MediaStore renamed `.log` to `.log.txt`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CrashLogFileTest {
    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.applicationInfo.flags = app.applicationInfo.flags or ApplicationInfo.FLAG_DEBUGGABLE
    }

    /** Android 8–9: file in Android/data/<pkg>/files/crashlogs/. */
    @Test @Config(sdk = [28])
    fun two_writes_reuse_the_same_file_on_api_28() {
        CrashLog.appendLines(app, "x-feedback.txt", listOf("a"), 200)
        CrashLog.appendLines(app, "x-feedback.txt", listOf("b"), 200)
        val dir = File(app.getExternalFilesDir(null), "crashlogs")
        assertEquals(listOf("x-feedback.txt"), dir.list()!!.toList())
        assertEquals("a\nb\n", File(dir, "x-feedback.txt").readText())
    }

    // Android 10+ (MediaStore): not testable here — on Robolectric 4.17 (SDK 36) two inserts into
    // MediaStore.Downloads followed by a query by RELATIVE_PATH return 0 rows (tried in
    // R4). What caused the bug (.log name renamed to .log.txt) is covered by the test below.

    @Test @Config(sdk = [36])
    fun non_txt_name_is_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CrashLog.appendLines(app, "x-feedback.log", listOf("a"), 200)
        }
    }
}
