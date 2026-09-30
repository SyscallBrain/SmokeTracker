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
            "2026-09-30T14:03:12+01:00 origin=widget api=36 importance=300 vibrate=true type=DOUBLE " +
                "hasVibrator=true hasAmplitudeControl=false effect=waveform(0/50/80/50ms) usage=HARDWARE_FEEDBACK " +
                "vibrationRequested=true showMessage=true notifications=false powerSave=false toastRequested=true errors=-",
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
        assertTrue(line, line.contains("vibrate=false type=SHORT hasVibrator=- "))
        assertTrue(line, line.endsWith("errors=\"toast: RuntimeException: a b\""))
        assertEquals(1, line.lines().size)
    }

    @Test fun push_line_without_errors() = assertEquals(
        "2026-09-30T14:03:12+01:00 origin=widget-push caller=app ids=1 count=5 day=2026-09-30 render=ok errors=-",
        FeedbackLog.pushLine("app", 1, WidgetState(5, "último 14:03", LocalDate.of(2026, 9, 30)), Result.success(Unit), emptyList(), at),
    )

    @Test fun push_line_with_error_before_ids_are_known() {
        val line = FeedbackLog.pushLine("action", -1, null, null, listOf(IllegalStateException("a\n\"b\"")), at)
        assertEquals(
            "2026-09-30T14:03:12+01:00 origin=widget-push caller=action ids=-1 count=- day=- render=- " +
                "errors=\"IllegalStateException:a 'b'\"",
            line,
        )
        assertEquals(1, line.lines().size)
    }

    @Test fun push_line_with_render_error() = assertEquals(
        "2026-09-30T14:03:12+01:00 origin=widget-push caller=system ids=2 count=3 day=2026-09-30 " +
            "render=\"IllegalArgumentException:views too large\" errors=-",
        FeedbackLog.pushLine(
            "system", 2, WidgetState(3, "x", LocalDate.of(2026, 9, 30)),
            Result.failure(IllegalArgumentException("views too large")), emptyList(), at,
        ),
    )

    @Test fun push_line_without_widgets() = assertEquals(
        "2026-09-30T14:03:12+01:00 origin=widget-push caller=onStop ids=0 count=- day=- render=- errors=-",
        FeedbackLog.pushLine("onStop", 0, null, null, emptyList(), at),
    )

    @Test fun action_error_line() = assertEquals(
        "2026-09-30T14:03:12+01:00 origin=widget-action errors=\"IllegalStateException:bd\"",
        FeedbackLog.actionErrorLine(IllegalStateException("bd"), at),
    )
}
