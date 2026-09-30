package pt.tiago.cinzeiro.feedback

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.tiago.cinzeiro.settings.Settings
import pt.tiago.cinzeiro.settings.VibrationPatterns
import pt.tiago.cinzeiro.settings.VibrationSpec

enum class FeedbackOrigin(val tag: String) { WIDGET("widget"), APP("app"), TEST("testar") }

/**
 * Feedback do +1 (app e widget) e do botão «Testar» das Definições. Cada chamada acrescenta uma
 * linha ao log de diagnóstico (R3.0, só debug) com o que foi pedido e o estado do sistema.
 *
 * Porque `USAGE_HARDWARE_FEEDBACK`: no AOSP, `VibrationSettings.shouldIgnoreVibration` devolve
 * `IGNORED_BACKGROUND` para uids fora do primeiro plano cujo uso não esteja em
 * `BACKGROUND_PROCESS_USAGE_ALLOWLIST` (RINGTONE, ALARM, NOTIFICATION, COMMUNICATION_REQUEST,
 * HARDWARE_FEEDBACK, PHYSICAL_EMULATION). O +1 do widget corre num BroadcastReceiver (fundo).
 * HARDWARE_FEEDBACK também está em `BATTERY_SAVER_USAGE_ALLOWLIST` e não é cortado pelo modo
 * silencioso (`shouldVibrateForRingerModeLocked` só corta RINGTONE/NOTIFICATION).
 *
 * Toast: `NotificationManagerService.checkCanEnqueueToast` suprime Toasts de apps que não estão em
 * `IMPORTANCE_FOREGROUND` **e** têm as notificações desligadas (`POST_NOTIFICATIONS` em falta no
 * Android 13+). Daí o pedido da permissão.
 */
object Feedback {

    /** Toca o padrão do tipo escolhido. Devolve a descrição do que foi pedido (para o log). */
    fun vibrate(context: Context, settings: Settings): VibrationOutcome {
        val vibrator = vibrator(context)
        val spec = VibrationPatterns.spec(settings.vibrationType)
        val base = VibrationOutcome(
            hasVibrator = vibrator.hasVibrator(),
            hasAmplitudeControl = vibrator.hasAmplitudeControl(),
            effect = VibrationPatterns.describe(spec),
            usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "HARDWARE_FEEDBACK" else "(sem atributos)",
        )
        if (!base.hasVibrator) return base.copy(requested = false)
        val effect = when (spec) {
            is VibrationSpec.OneShot -> VibrationEffect.createOneShot(spec.millis, VibrationEffect.DEFAULT_AMPLITUDE)
            is VibrationSpec.Waveform -> VibrationEffect.createWaveform(spec.timings.toLongArray(), -1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
        } else {
            vibrator.vibrate(effect)
        }
        return base.copy(requested = true)
    }

    /**
     * Vibra e/ou mostra o Toast (na thread principal) conforme [settings] e depois grava a linha de
     * diagnóstico (em IO). Nunca lança.
     */
    suspend fun performAndLog(context: Context, settings: Settings, origin: FeedbackOrigin, toastText: String? = null) {
        val line = withContext(Dispatchers.Main) { perform(context, settings, origin, toastText) }
        withContext(Dispatchers.IO) { runCatching { FeedbackLog.append(context.applicationContext, line) } }
    }

    /**
     * Feedback completo de uma origem. Chamar na thread principal (Toast). Nunca lança: os erros
     * vão para a linha de log devolvida.
     */
    fun perform(context: Context, settings: Settings, origin: FeedbackOrigin, toastText: String? = null): String {
        val app = context.applicationContext
        var outcome: VibrationOutcome? = null
        val errors = mutableListOf<String>()
        if (settings.vibrate) {
            runCatching { outcome = vibrate(app, settings) }
                .onFailure { errors += "vibrar: ${it.javaClass.simpleName}: ${it.message}" }
        }
        var toastRequested = false
        if (toastText != null && settings.showMessage) {
            runCatching { Toast.makeText(app, toastText, Toast.LENGTH_SHORT).show(); toastRequested = true }
                .onFailure { errors += "toast: ${it.javaClass.simpleName}: ${it.message}" }
        }
        return runCatching {
                FeedbackLog.line(
                    origin = origin.tag,
                    settings = settings,
                    outcome = outcome,
                    notificationsEnabled = NotificationManagerCompat.from(app).areNotificationsEnabled(),
                    powerSave = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode,
                    importance = myImportance(),
                    toastRequested = toastRequested,
                    errors = errors,
                )
        }.getOrElse { "${java.time.OffsetDateTime.now()} origem=${origin.tag} erroNoLog=${it.javaClass.simpleName}" }
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    /** Importância do processo (100 = FOREGROUND); o NMS compara a do uid com IMPORTANCE_FOREGROUND. */
    private fun myImportance(): Int =
        ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }.importance
}

data class VibrationOutcome(
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val effect: String,
    val usage: String,
    val requested: Boolean = false,
)
