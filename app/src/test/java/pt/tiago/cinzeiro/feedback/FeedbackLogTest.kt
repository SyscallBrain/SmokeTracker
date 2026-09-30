package pt.tiago.cinzeiro.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.settings.VibrationType
import pt.tiago.cinzeiro.widget.WidgetState
import java.time.LocalDate
import java.time.OffsetDateTime

class FeedbackLogTest {
    private val at = OffsetDateTime.parse("2026-09-30T14:03:12+01:00")

    @Test fun file_name() = assertEquals("pt.tiago.cinzeiro-feedback.txt", FeedbackLog.fileName("pt.tiago.cinzeiro"))

    @Test fun full_line() {
        val line = FeedbackLog.line(
            origin = "widget",
            settings = Settings(vibrationType = VibrationType.DOUBLE),
            outcome = VibrationOutcome(true, false, "waveform(0/50/80/50ms)", "HARDWARE_FEEDBACK", requested = true),
            notificationsEnabled = false,
            powerSave = false,
            importance = 300,
            toastRequested = true,
            errors = emptyList(),
            now = at,
            sdk = 36,
        )
        assertEquals(
            "2026-09-30T14:03:12+01:00 origem=widget api=36 importance=300 vibrar=true tipo=DOUBLE " +
                "hasVibrator=true hasAmplitudeControl=false efeito=waveform(0/50/80/50ms) usage=HARDWARE_FEEDBACK " +
                "vibracaoPedida=true mostrarMensagem=true notificacoes=false powerSave=false toastPedido=true erros=-",
            line,
        )
    }

    @Test fun vibration_off_and_errors_on_one_line() {
        val line = FeedbackLog.line(
            origin = "app",
            settings = Settings(vibrate = false),
            outcome = null,
            notificationsEnabled = true,
            powerSave = true,
            importance = 100,
            toastRequested = false,
            errors = listOf("toast: RuntimeException: a\nb"),
            now = at,
            sdk = 36,
        )
        assertTrue(line, line.contains("vibrar=false tipo=SHORT hasVibrator=- "))
        assertTrue(line, line.endsWith("erros=\"toast: RuntimeException: a b\""))
        assertEquals(1, line.lines().size)
    }

    @Test fun push_line_sem_erros() = assertEquals(
        "2026-09-30T14:03:12+01:00 origem=widget-push chamador=app ids=1 count=5 dia=2026-09-30 erros=-",
        FeedbackLog.pushLine("app", 1, WidgetState(5, "último 14:03", LocalDate.of(2026, 9, 30)), emptyList(), at),
    )

    @Test fun push_line_com_erro_antes_de_saber_os_ids() {
        val line = FeedbackLog.pushLine("action", -1, null, listOf(IllegalStateException("a\n\"b\"")), at)
        assertEquals(
            "2026-09-30T14:03:12+01:00 origem=widget-push chamador=action ids=-1 count=- dia=- " +
                "erros=\"IllegalStateException:a 'b'\"",
            line,
        )
        assertEquals(1, line.lines().size)
    }

    @Test fun provide_line() = assertEquals(
        "2026-09-30T14:03:12+01:00 origem=widget-provide id=AppWidgetId(appWidgetId=12) count=3 dia=2026-09-30 semeado=true",
        FeedbackLog.provideLine("AppWidgetId(appWidgetId = 12)", WidgetState(3, "x", LocalDate.of(2026, 9, 30)), true, at),
    )
}
