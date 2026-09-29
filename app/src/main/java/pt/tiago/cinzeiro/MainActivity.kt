package pt.tiago.cinzeiro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.tiago.cinzeiro.ui.components.DesignShowcase
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Barras do sistema sempre escuras (ícones claros), independentemente do tema do telemóvel.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // T1: ecrã temporário com o catálogo de design; o T4 substitui pela navegação.
            CinzeiroTheme { DesignShowcase() }
        }
    }
}
