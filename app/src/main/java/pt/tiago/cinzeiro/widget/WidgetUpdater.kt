package pt.tiago.cinzeiro.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.feedback.FeedbackLog

/** Who requested the push (goes to the diagnostic log). */
enum class PushCaller(val tag: String) {
    ACTION("action"), APP("app"), MIDNIGHT("midnight"), ON_STOP("onStop"), SYSTEM("system")
}

/**
 * The only path to change what the widget shows: reads the DB, builds the `RemoteViews`
 * ([WidgetViews.build]) and delivers them at once with `AppWidgetManager.updateAppWidget(ids, views)`
 * (R5: synchronous, no session or composition).
 *
 * Serialized with a [Mutex]: concurrent pushes (app + widget + midnight) do not interleave
 * and the last one to finish is always the one that read the DB last.
 *
 * Never throws (except cancellation): errors go to the `origin=widget-push` line of the diagnostic
 * log (`render=ok|<Class:message>` for drawing, `errors=` for the rest).
 */
object WidgetUpdater {
    private val mutex = Mutex()

    fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, CinzeiroWidgetReceiver::class.java))

    suspend fun push(context: Context, caller: PushCaller) {
        val app = context.applicationContext as App
        val line = mutex.withLock { pushLocked(app, caller) }
        withContext(Dispatchers.IO) { runCatching { FeedbackLog.append(app, line) } }
    }

    private suspend fun pushLocked(app: App, caller: PushCaller): String {
        var ids = -1
        var state: WidgetState? = null
        var render: Result<Unit>? = null
        val errors = mutableListOf<Throwable>()
        try {
            val repo = app.container.repository
            val manager = AppWidgetManager.getInstance(app)
            val widgetIds = widgetIds(app)
            ids = widgetIds.size
            if (widgetIds.isNotEmpty()) {
                val s = loadWidgetState(repo.now(), repo.zone(), repo::countBetween, repo::lastEvent)
                state = s
                render = runCatching { manager.updateAppWidget(widgetIds, WidgetViews.build(app, s)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errors += e
        }
        return FeedbackLog.pushLine(caller.tag, ids, state, render, errors)
    }
}
