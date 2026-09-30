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

    /** Lives as long as the process; only for work tied to the whole app (widget). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
        keepWidgetInSync()
    }

    /**
     * Any insert/delete in the repository (Today screen, History, undo, debug seed) redraws the
     * widget; and ensures the midnight worker if there are widgets (e.g. after rebooting the
     * phone or updating the app).
     */
    private fun keepWidgetInSync() {
        appScope.launch {
            runCatching { if (MidnightWorker.hasWidgets(this@App)) MidnightWorker.schedule(this@App) }
        }
        appScope.launch {
            // No silent runCatching (R4): push never throws and records errors in the diagnostic log.
            container.repository.changes.collect { WidgetUpdater.push(this@App, PushCaller.APP) }
        }
    }
}
