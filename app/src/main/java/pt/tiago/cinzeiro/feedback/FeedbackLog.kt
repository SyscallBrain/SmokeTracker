package pt.tiago.cinzeiro.feedback

import android.content.Context
import android.os.Build
import pt.tiago.cinzeiro.CrashLog
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.widget.WidgetState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostics for the +1 feedback (R3.0) and for widget redraws (R4/R5): one line per event in
 * `Download/crashlogs/pt.tiago.cinzeiro-feedback.txt` (same mechanism as [CrashLog]; debuggable
 * builds only; keeps the last [MAX_LINES] lines). `.txt` rather than `.log`: see [CrashLog.appendLines].
 */
object FeedbackLog {
    const val MAX_LINES = 200

    fun fileName(packageName: String) = "$packageName-feedback.txt"

    fun append(context: Context, line: String) {
        CrashLog.appendLines(context, fileName(context.packageName), listOf(line), MAX_LINES)
    }

    /**
     * One line per [pt.tiago.cinzeiro.widget.WidgetUpdater.push]:
     * `origin=widget-push caller=<action|app|midnight|onStop|system> ids=<n> count=<n> day=<d>
     * render=<ok|Class:message|-> errors=<Class:message|->`.
     * `ids=-1`/`count=-` when it failed before knowing them; `render=-` when it never got to draw
     * (no widgets, or an earlier error). R5: `render` is the result of `AppWidgetManager.updateAppWidget`.
     */
    fun pushLine(
        caller: String,
        ids: Int,
        state: WidgetState?,
        render: Result<Unit>?,
        errors: List<Throwable>,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): String = listOf(
        now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "origin=widget-push",
        "caller=$caller",
        "ids=$ids",
        "count=${state?.todayCount ?: "-"}",
        "day=${state?.day ?: "-"}",
        "render=" + renderField(render),
        "errors=" + errorsField(errors),
    ).joinToString(" ")

    /** R5: error in the widget "+1" before the push (saving, feedback); the push is logged separately. */
    fun actionErrorLine(error: Throwable, now: OffsetDateTime = OffsetDateTime.now()): String = listOf(
        now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "origin=widget-action",
        "errors=" + errorsField(listOf(error)),
    ).joinToString(" ")

    private fun renderField(render: Result<Unit>?): String = when {
        render == null -> "-"
        render.isSuccess -> "ok"
        else -> errorsField(listOfNotNull(render.exceptionOrNull()))
    }

    private fun errorsField(errors: List<Throwable>): String =
        if (errors.isEmpty()) "-"
        else "\"" + errors.joinToString(" | ") { "${it.javaClass.simpleName}:${it.message}" }
            .replace("\n", " ").replace("\"", "'") + "\""

    /** Space-separated `key=value` line; values contain no spaces (errors are quoted). */
    fun line(
        origin: String,
        settings: Settings,
        outcome: VibrationOutcome?,
        notificationsEnabled: Boolean,
        powerSave: Boolean,
        importance: Int,
        toastRequested: Boolean,
        errors: List<String>,
        now: OffsetDateTime = OffsetDateTime.now(),
        sdk: Int = Build.VERSION.SDK_INT,
    ): String = buildList {
        add(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        add("origin=$origin")
        add("api=$sdk")
        add("importance=$importance")
        add("vibrate=${settings.vibrate}")
        add("type=${settings.vibrationType.name}")
        add("hasVibrator=${outcome?.hasVibrator ?: "-"}")
        add("hasAmplitudeControl=${outcome?.hasAmplitudeControl ?: "-"}")
        add("effect=${outcome?.effect ?: "-"}")
        add("usage=${outcome?.usage ?: "-"}")
        add("vibrationRequested=${outcome?.requested ?: false}")
        add("showMessage=${settings.showMessage}")
        add("notifications=$notificationsEnabled")
        add("powerSave=$powerSave")
        add("toastRequested=$toastRequested")
        add("errors=" + if (errors.isEmpty()) "-" else "\"" + errors.joinToString(" | ").replace("\n", " ") + "\"")
    }.joinToString(" ")
}
