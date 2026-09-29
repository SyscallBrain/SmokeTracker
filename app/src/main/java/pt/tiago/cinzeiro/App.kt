package pt.tiago.cinzeiro

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pt.tiago.cinzeiro.di.AppContainer
import pt.tiago.cinzeiro.widget.CinzeiroWidget
import pt.tiago.cinzeiro.widget.MidnightWorker

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
            container.repository.changes.collect {
                runCatching { CinzeiroWidget().updateAll(this@App) }
            }
        }
    }
}
