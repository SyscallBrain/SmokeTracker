package pt.tiago.cinzeiro.feedback

import android.content.Context
import android.os.Build
import pt.tiago.cinzeiro.CrashLog
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.widget.WidgetState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnóstico do feedback do +1 (R3.0) e dos redesenhos do widget (R4): uma linha por evento em
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
     * R4: uma linha por [pt.tiago.cinzeiro.widget.WidgetUpdater.push]:
     * `origem=widget-push chamador=<action|app|midnight|onStop> ids=<n> count=<n> dia=<d> erros=<classe:mensagem|->`.
     * `ids=-1`/`count=-` quando falhou antes de os saber.
     */
    fun pushLine(
        caller: String,
        ids: Int,
        state: WidgetState?,
        errors: List<Throwable>,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): String = listOf(
        now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "origem=widget-push",
        "chamador=$caller",
        "ids=$ids",
        "count=${state?.todayCount ?: "-"}",
        "dia=${state?.day ?: "-"}",
        "erros=" + errorsField(errors),
    ).joinToString(" ")

    /** R4: uma linha por `provideGlance` (sessão Glance nova para o widget [glanceId]). */
    fun provideLine(
        glanceId: String,
        state: WidgetState?,
        seededNow: Boolean,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): String = listOf(
        now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        "origem=widget-provide",
        "id=" + glanceId.replace(" ", ""),
        "count=${state?.todayCount ?: "-"}",
        "dia=${state?.day ?: "-"}",
        "semeado=$seededNow",
    ).joinToString(" ")

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
