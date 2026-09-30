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

/** Quem pediu o push (vai para o log de diagnóstico). */
enum class PushCaller(val tag: String) {
    ACTION("action"), APP("app"), MIDNIGHT("midnight"), ON_STOP("onStop"), SYSTEM("system")
}

/**
 * Único caminho para mudar o que o widget mostra: lê a BD, constrói as `RemoteViews`
 * ([WidgetViews.build]) e entrega-as de uma vez com `AppWidgetManager.updateAppWidget(ids, views)`
 * (R5: síncrono, sem sessão nem composição).
 *
 * Serializado com um [Mutex]: pushes concorrentes (app + widget + meia-noite) não se intercalam
 * e o último a terminar é sempre o que leu a BD por último.
 *
 * Nunca lança (excepto cancelamento): os erros vão para a linha `origem=widget-push` do log de
 * diagnóstico (`render=ok|<Classe:mensagem>` para o desenho, `erros=` para o resto).
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
