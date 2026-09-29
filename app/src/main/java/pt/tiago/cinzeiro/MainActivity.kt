package pt.tiago.cinzeiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.tiago.cinzeiro.ui.nav.AppNav
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme

class MainActivity : ComponentActivity() {
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
    }
}
