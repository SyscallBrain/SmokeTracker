package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import pt.tiago.cinzeiro.App

/** «+1» do widget: um toque, sem confirmação (ADR §7); depois redesenha todos os widgets. */
class LogCigaretteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as App).container
        container.repository.logNow()
        CinzeiroWidget().updateAll(context)
    }
}
