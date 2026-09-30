package pt.tiago.cinzeiro.feedback

import android.content.Context
import android.os.Build
import pt.tiago.cinzeiro.CrashLog
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.widget.WidgetState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnóstico do feedback do +1 (R3.0) e dos redesenhos do widget (R4/R5): uma linha por evento em
 * `Download/crashlogs/pt.tiago.cinzeiro-feedback.txt` (mesmo mecanismo do [CrashLog]; só em builds
 * debuggable; guarda as últimas [MAX_LINES] linhas). `.txt` e não `.log`: ver [CrashLog.appendLines].
 */
object FeedbackLog {
    const val MAX_LINES = 200

    fun fileName(packageName: String) = "$packageName-feedback.txt"

    fun append(context: Context, line: String) {
        CrashLog.appendLines(context, fileName(context.packageName), listOf(line), MAX_LINES)
    }

    /**
     * Uma linha por [pt.tiago.cinzeiro.widget.WidgetUpdater.push]:
     * `origem=widget-push chamador=<action|app|midnight|onStop|system> ids=<n> count=<n> dia=<d>
     * render=<ok|Classe:mensagem|-> erros=<classe:mensagem|->`.
     * `ids=-1`/`count=-` quando falhou antes de os saber; `render=-` quando não chegou a desenhar
     * (sem widgets ou erro antes). R5: `render` é o resultado de `AppWidgetManager.updateAppWidget`.
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
        "origem=widget-push",
        "chamador=$caller",
        "ids=$ids",
        "count=${state?.todayCount ?: "-"}",
        "dia=${state?.day ?: "-"}",
        "render=" + renderField(render),
        "erros=" + errorsField(errors),
    ).joinToString(" ")

    /** R5: erro do «+1» do widget antes do push (gravar, feedback); o push regista-se à parte. */
    fun actionErrorLine(error: Throwable, now: OffsetDateTime = OffsetDateTime.now()): String = listOf(
        now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "origem=widget-action",
        "erros=" + errorsField(listOf(error)),
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

    /** Linha `chave=valor` separada por espaços; valores sem espaços (erros entre aspas). */
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
        add("origem=$origin")
        add("api=$sdk")
        add("importance=$importance")
        add("vibrar=${settings.vibrate}")
        add("tipo=${settings.vibrationType.name}")
        add("hasVibrator=${outcome?.hasVibrator ?: "-"}")
        add("hasAmplitudeControl=${outcome?.hasAmplitudeControl ?: "-"}")
        add("efeito=${outcome?.effect ?: "-"}")
        add("usage=${outcome?.usage ?: "-"}")
        add("vibracaoPedida=${outcome?.requested ?: false}")
        add("mostrarMensagem=${settings.showMessage}")
        add("notificacoes=$notificationsEnabled")
        add("powerSave=$powerSave")
        add("toastPedido=$toastRequested")
        add("erros=" + if (errors.isEmpty()) "-" else "\"" + errors.joinToString(" | ").replace("\n", " ") + "\"")
    }.joinToString(" ")
}
