package pt.tiago.cinzeiro

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.tiago.cinzeiro.di.AppContainer
import pt.tiago.cinzeiro.widget.MidnightWorker
import pt.tiago.cinzeiro.widget.PushCaller
import pt.tiago.cinzeiro.widget.WidgetUpdater

class App : Application() {
    lateinit var container: AppContainer
        private set

    /** Vive tanto quanto o processo; só para trabalho ligado à app inteira (widget). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        keepWidgetInSync()
    }

    /**
     * Qualquer inserção/remoção no repositório (ecrã Hoje, Histórico, desfazer, seed de debug)
     * redesenha o widget; e garante o worker da meia-noite se houver widgets (p.ex. depois de
     * reiniciar o telemóvel ou de actualizar a app).
     */
    private fun keepWidgetInSync() {
        appScope.launch {
            runCatching { if (MidnightWorker.hasWidgets(this@App)) MidnightWorker.schedule(this@App) }
        }
        appScope.launch {
            // Sem runCatching mudo (R4): push nunca lança e regista os erros no log de diagnóstico.
            container.repository.changes.collect { WidgetUpdater.push(this@App, PushCaller.APP) }
        }
    }
}
