package pt.tiago.cinzeiro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** System font (Roboto); only the large number on the Today screen changes weight. */
val CinzeiroTypography: Typography = Typography().let { base ->
    base.copy(displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold))
}

/** Tabular figures: numbers do not "jump around" when they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
