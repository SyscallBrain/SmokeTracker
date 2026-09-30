package pt.tiago.cinzeiro.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.feedback.Feedback
import pt.tiago.cinzeiro.feedback.FeedbackLog
import pt.tiago.cinzeiro.feedback.FeedbackOrigin

/**
 * Widget clássico (R5): `AppWidgetProvider` + `RemoteViews` ([WidgetViews]), sem Glance.
 *
 * O nome da classe (`pt.tiago.cinzeiro.widget.CinzeiroWidgetReceiver`) e o `appwidget-provider`
 * XML são os da 0.5.1: mudar o componente faria desaparecer o widget já colocado no ecrã inicial.
 *
 * Todo o trabalho assíncrono corre com `goAsync()` e termina com `finish()` em `finally`, dentro
 * de [BROADCAST_BUDGET_MS] (o sistema dá ~10 s a um broadcast antes de o considerar ANR).
 */
class CinzeiroWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LOG) {
            runAsync(context) { logFromWidget(it) }
            return
        }
        super.onReceive(context, intent)
    }

    /** Sistema pede redesenho (widget colocado, reinício do launcher, actualização da app). */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { WidgetUpdater.push(it, PushCaller.SYSTEM) }
    }

    /** Primeiro widget colocado: agenda a actualização da meia-noite. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightWorker.schedule(context)
    }

    /** Último widget removido: deixa de acordar à meia-noite. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightWorker.cancel(context)
    }

    private fun runAsync(context: Context, block: suspend (App) -> Unit) {
        val app = context.applicationContext as App
        // null quando o provider é chamado directamente (p.ex. Robolectric), não por broadcast.
        val pending: PendingResult? = goAsync()
        app.appScope.launch {
            try {
                withTimeoutOrNull(BROADCAST_BUDGET_MS) { block(app) }
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Acção do botão «+1» (PendingIntent explícito para este receiver). */
        const val ACTION_LOG = "pt.tiago.cinzeiro.action.LOG"

        /** Margem abaixo dos ~10 s do broadcast. */
        private const val BROADCAST_BUDGET_MS = 9_000L

        /**
         * «+1» do widget: um toque, sem confirmação (ADR §7). Grava, dá o feedback escolhido nas
         * Definições (vibração + Toast «Registado às HH:MM · N hoje», ver [Feedback]) e redesenha
         * ([WidgetUpdater.push]). Erros antes do push vão para o log de diagnóstico.
         */
        suspend fun logFromWidget(app: App) {
            try {
                val repo = app.container.repository
                repo.logNow()
                val zone = repo.zone()
                val now = repo.now()
                val (from, to) = dayBounds(now.atZone(zone).toLocalDate(), zone)
                val count = repo.countBetween(from, to)
                val text = loggedToastText(now.atZone(zone).toLocalTime(), count)
                val settings = app.container.settings.current()
                Feedback.performAndLog(app, settings, FeedbackOrigin.WIDGET, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                runCatching { FeedbackLog.append(app, FeedbackLog.actionErrorLine(e)) }
            }
            WidgetUpdater.push(app, PushCaller.ACTION)
        }
    }
}
