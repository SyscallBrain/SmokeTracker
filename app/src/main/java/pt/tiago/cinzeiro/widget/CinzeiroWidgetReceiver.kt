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
 * Classic widget (R5): `AppWidgetProvider` + `RemoteViews` ([WidgetViews]), no Glance.
 *
 * The class name (`pt.tiago.cinzeiro.widget.CinzeiroWidgetReceiver`) and the `appwidget-provider`
 * XML are those of 0.5.1: changing the component would make the widget already placed on the home
 * screen disappear.
 *
 * All async work runs with `goAsync()` and ends with `finish()` in `finally`, within
 * [BROADCAST_BUDGET_MS] (the system gives a broadcast ~10 s before considering it an ANR).
 */
class CinzeiroWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LOG) {
            runAsync(context) { logFromWidget(it) }
            return
        }
        super.onReceive(context, intent)
    }

    /** System requests a redraw (widget placed, launcher restart, app update). */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { WidgetUpdater.push(it, PushCaller.SYSTEM) }
    }

    /** First widget placed: schedules the midnight update. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightWorker.schedule(context)
    }

    /** Last widget removed: stops waking up at midnight. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightWorker.cancel(context)
    }

    private fun runAsync(context: Context, block: suspend (App) -> Unit) {
        val app = context.applicationContext as App
        // null when the provider is called directly (e.g. Robolectric), not via broadcast.
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
        /** Action of the "+1" button (explicit PendingIntent to this receiver). */
        const val ACTION_LOG = "pt.tiago.cinzeiro.action.LOG"

        /** Margin below the broadcast's ~10 s. */
        private const val BROADCAST_BUDGET_MS = 9_000L

        /**
         * Widget "+1": one tap, no confirmation (ADR §7). Saves, gives the feedback chosen in
         * Settings (vibration + Toast "Registado às HH:MM · N hoje", see [Feedback]) and redraws
         * ([WidgetUpdater.push]). Errors before the push go to the diagnostic log.
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
