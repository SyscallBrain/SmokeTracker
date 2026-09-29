package pt.tiago.cinzeiro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Fonte do sistema (Roboto); só o número grande do ecrã Hoje muda de peso. */
val CinzeiroTypography: Typography = Typography().let { base ->
    base.copy(displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold))
}

/** Algarismos tabulares: os números não «dançam» quando mudam. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
