package pt.tiago.cinzeiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Sempre escuro (ADR §1): sem tema claro, sem dynamic color. */
private val CinzeiroColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = Background,
    tertiary = Tertiary,
    onTertiary = Background,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = Outline,
    error = Error,
    onError = Background,
    // Superfícies M3 derivadas (barra de navegação, folhas, diálogos) = surface; sem tonalidade.
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = SurfaceVariant,
    surfaceTint = Color.Transparent,
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = Secondary,
    inverseSurface = OnSurface,
    inverseOnSurface = Background,
    inversePrimary = Primary,
)

/** Cores fora do esquema M3. */
@Immutable
data class ExtendedColors(val good: Color)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors(good = Good) }

@Composable
fun CinzeiroTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides ExtendedColors(good = Good)) {
        MaterialTheme(
            colorScheme = CinzeiroColors,
            typography = CinzeiroTypography,
            content = content,
        )
    }
}

/** Acesso: `CinzeiroTheme.extended.good`. */
object CinzeiroTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
