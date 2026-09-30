package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.feedback.Feedback
import pt.tiago.cinzeiro.feedback.FeedbackOrigin

/**
 * «+1» do widget: um toque, sem confirmação (ADR §7). Grava, dá o feedback escolhido nas
 * Definições (vibração com padrão explícito + Toast «Registado às HH:MM · N hoje», ver [Feedback])
 * e empurra o estado novo para o widget ([WidgetUpdater.push], R4).
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
        val settings = app.container.settings.current()
        Feedback.performAndLog(app, settings, FeedbackOrigin.WIDGET, text)
        WidgetUpdater.push(app, PushCaller.ACTION)
    }
}
