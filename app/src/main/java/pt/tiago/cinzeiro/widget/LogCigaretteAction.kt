package pt.tiago.cinzeiro.widget

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.tiago.cinzeiro.App

/**
 * «+1» do widget: um toque, sem confirmação (ADR §7). Grava, dá feedback (vibração curta + Toast
 * «Registado às HH:MM · N hoje») e pede ao Glance para redesenhar; a contagem em si vem dos Flows
 * observados pela composição do widget.
 */
class LogCigaretteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as App
        val repo = app.container.repository
        repo.logNow()
        val zone = repo.zone()
        val now = repo.now()
        val (from, to) = dayBounds(now.atZone(zone).toLocalDate(), zone)
        val count = repo.countBetween(from, to)
        val text = loggedToastText(now.atZone(zone).toLocalTime(), count)
        withContext(Dispatchers.Main) {
            runCatching { vibrateClick(app) }
            Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
        }
        CinzeiroWidget().updateAll(app)
    }
}

/**
 * Vibração curta de confirmação. Com a app em segundo plano (é o caso: corre num
 * BroadcastReceiver), o sistema ignora vibrações de uso `TOUCH` — `EFFECT_CLICK` sem atributos é
 * promovido a `USAGE_TOUCH` — e só deixa passar os usos da lista branca de fundo; por isso, a partir
 * do API 33, pede-se `USAGE_HARDWARE_FEEDBACK` (feedback a uma interacção física do utilizador).
 */
private fun vibrateClick(context: Context) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (!vibrator.hasVibrator()) return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    } else {
        VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK))
    } else {
        vibrator.vibrate(effect)
    }
}
