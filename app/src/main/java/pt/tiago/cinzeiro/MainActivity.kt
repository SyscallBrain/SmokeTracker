package pt.tiago.cinzeiro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pt.tiago.cinzeiro.ui.nav.AppNav
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.widget.PushCaller
import pt.tiago.cinzeiro.widget.WidgetUpdater

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* estado lido nas Definições */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras do sistema sempre escuras (ícones claros), independentemente do tema do telemóvel.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        setContent {
            CinzeiroTheme { AppNav(container) }
        }
        if (savedInstanceState == null) askNotificationsOnce()
    }

    /**
     * R4: rede de segurança ao sair da app — volta a empurrar o estado para o widget. Corre no
     * `appScope` (não no `lifecycleScope`, que pode ser cancelado logo a seguir ao `onStop`).
     */
    override fun onStop() {
        super.onStop()
        val app = application as App
        app.appScope.launch { WidgetUpdater.push(app, PushCaller.ON_STOP) }
    }

    /**
     * R3.2: sem `POST_NOTIFICATIONS` (Android 13+) o sistema descarta o Toast do +1 do widget
     * (`NotificationManagerService.checkCanEnqueueToast`). Pede-se uma única vez, no primeiro
     * arranque, se «Mostrar mensagem» estiver ligado; depois só pelas Definições.
     */
    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val settings = (application as App).container.settings
        lifecycleScope.launch {
            if (settings.notificationRequests.first() > 0) return@launch
            if (!settings.current().showMessage) return@launch
            settings.countNotificationRequest()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
