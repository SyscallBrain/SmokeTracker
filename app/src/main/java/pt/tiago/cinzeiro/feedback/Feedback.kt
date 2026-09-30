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

enum class FeedbackOrigin(val tag: String) { WIDGET("widget"), APP("app"), TEST("test") }

/**
 * Feedback for the +1 (app and widget) and for the "Testar" button in Settings. Each call appends a
 * line to the diagnostic log (R3.0, debug only) with what was requested and the system state.
 *
 * Why `USAGE_HARDWARE_FEEDBACK`: in AOSP, `VibrationSettings.shouldIgnoreVibration` returns
 * `IGNORED_BACKGROUND` for non-foreground uids whose usage is not in
 * `BACKGROUND_PROCESS_USAGE_ALLOWLIST` (RINGTONE, ALARM, NOTIFICATION, COMMUNICATION_REQUEST,
 * HARDWARE_FEEDBACK, PHYSICAL_EMULATION). The widget's +1 runs in a BroadcastReceiver (background).
 * HARDWARE_FEEDBACK is also in `BATTERY_SAVER_USAGE_ALLOWLIST` and is not cut by silent mode
 * (`shouldVibrateForRingerModeLocked` only cuts RINGTONE/NOTIFICATION).
 *
 * Toast: `NotificationManagerService.checkCanEnqueueToast` suppresses Toasts from apps that are not
 * at `IMPORTANCE_FOREGROUND` **and** have notifications disabled (`POST_NOTIFICATIONS` missing on
 * Android 13+). Hence the permission request.
 */
object Feedback {

    /** Plays the pattern of the chosen type. Returns the description of what was requested (for the log). */
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
     * Vibrates and/or shows the Toast (on the main thread) according to [settings], then writes the
     * diagnostic line (on IO). Never throws.
     */
    suspend fun performAndLog(context: Context, settings: Settings, origin: FeedbackOrigin, toastText: String? = null) {
        val line = withContext(Dispatchers.Main) { perform(context, settings, origin, toastText) }
        withContext(Dispatchers.IO) { runCatching { FeedbackLog.append(context.applicationContext, line) } }
    }

    /**
     * Full feedback for one origin. Call on the main thread (Toast). Never throws: errors go to
     * the returned log line.
     */
    fun perform(context: Context, settings: Settings, origin: FeedbackOrigin, toastText: String? = null): String {
        val app = context.applicationContext
        var outcome: VibrationOutcome? = null
        val errors = mutableListOf<String>()
        if (settings.vibrate) {
            runCatching { outcome = vibrate(app, settings) }
                .onFailure { errors += "vibrate: ${it.javaClass.simpleName}: ${it.message}" }
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
        }.getOrElse { "${java.time.OffsetDateTime.now()} origin=${origin.tag} logError=${it.javaClass.simpleName}" }
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

    /** Process importance (100 = FOREGROUND); the NMS compares the uid's with IMPORTANCE_FOREGROUND. */
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
