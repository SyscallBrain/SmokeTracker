package pt.tiago.cinzeiro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Always dark (ADR §1): no light theme, no dynamic color. */
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
    // Derived M3 surfaces (navigation bar, sheets, dialogs) = surface; no tonal tint.
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

/** Colours outside the M3 scheme. */
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

/** Access: `CinzeiroTheme.extended.good`. */
object CinzeiroTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
