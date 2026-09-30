package pt.tiago.cinzeiro.feedback

import android.content.Context
import android.os.Build
import pt.tiago.cinzeiro.CrashLog
import pt.tiago.cinzeiro.settings.Settings
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Diagnóstico do feedback do +1 (R3.0): uma linha por tentativa em
 * `Download/crashlogs/pt.tiago.cinzeiro-feedback.log` (mesmo mecanismo do [CrashLog]; só em builds
 * debuggable; guarda as últimas [MAX_LINES] linhas).
 */
object FeedbackLog {
    const val MAX_LINES = 200

    fun fileName(packageName: String) = "$packageName-feedback.log"

    fun append(context: Context, line: String) {
        CrashLog.appendLines(context, fileName(context.packageName), listOf(line), MAX_LINES)
    }

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
