package pt.tiago.cinzeiro.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pt.tiago.cinzeiro.App
import pt.tiago.cinzeiro.feedback.FeedbackLog

/** Quem pediu o push (vai para o log de diagnóstico). */
enum class PushCaller(val tag: String) {
    ACTION("action"), APP("app"), MIDNIGHT("midnight"), ON_STOP("onStop")
}

/**
 * Único caminho para mudar o que o widget mostra (R4): calcula o [WidgetState] a partir do
 * repositório e, para **cada** widget, grava-o no estado Glance e pede o redesenho.
 *
 * Serializado com um [Mutex]: pushes concorrentes (app + widget + meia-noite) não se intercalam
 * e o último a terminar é sempre o que leu a BD por último.
 *
 * Nunca lança (excepto cancelamento): os erros vão para a linha `origem=widget-push` do log de
 * diagnóstico — o objectivo é ver, não rebentar.
 */
object WidgetUpdater {
    private val mutex = Mutex()

    suspend fun push(context: Context, caller: PushCaller) {
        val app = context.applicationContext as App
        val line = mutex.withLock { pushLocked(app, caller) }
        withContext(Dispatchers.IO) { runCatching { FeedbackLog.append(app, line) } }
    }

    private suspend fun pushLocked(app: App, caller: PushCaller): String {
        var ids = -1
        var state: WidgetState? = null
        val errors = mutableListOf<Throwable>()
        try {
            val repo = app.container.repository
            val glanceIds = GlanceAppWidgetManager(app).getGlanceIds(CinzeiroWidget::class.java)
            ids = glanceIds.size
            if (glanceIds.isNotEmpty()) {
                val s = loadWidgetState(repo.now(), repo.zone(), repo::countBetween, repo::lastEvent)
                state = s
                val widget = CinzeiroWidget()
                for (id in glanceIds) {
                    try {
                        updateAppWidgetState(app, PreferencesGlanceStateDefinition, id) { prefs ->
                            prefs.toMutablePreferences().apply { s.writeTo(this) }
                        }
                        widget.update(app, id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        errors += e // um widget com problemas não impede os outros
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            errors += e
        }
        return FeedbackLog.pushLine(caller.tag, ids, state, errors)
    }
}
