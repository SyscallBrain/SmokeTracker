package pt.tiago.cinzeiro

import android.app.Application
import pt.tiago.cinzeiro.di.AppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container = AppContainer(this)
    }
}
