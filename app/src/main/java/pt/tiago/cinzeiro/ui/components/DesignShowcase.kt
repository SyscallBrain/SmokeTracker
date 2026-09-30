package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.ui.theme.Dimens
import pt.tiago.cinzeiro.ui.theme.tabular

/**
 * Design system catalog (T1): palette + all base components, to compare on the phone against
 * the mockup (no adb screenshots available). Only reachable in the debug APK.
 */
@Composable
fun DesignShowcase(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var period by remember { mutableStateOf(Period.D7) }
    Column(
        modifier
            .fillMaxSize()
            .background(cs.background)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.l),
    ) {
        Text("Sistema de design", style = MaterialTheme.typography.titleLarge, color = cs.onBackground)
        SectionHeader("Paleta")
        val swatches = listOf(
            "background" to cs.background, "surface" to cs.surface, "surfaceVariant" to cs.surfaceVariant,
            "onSurface" to cs.onSurface, "onSurfaceVariant" to cs.onSurfaceVariant, "outline" to cs.outline,
            "primary" to cs.primary, "secondary" to cs.secondary, "tertiary" to cs.tertiary,
            "error" to cs.error, "good" to CinzeiroTheme.extended.good,
        )
        swatches.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
                row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        SectionHeader("Número grande (displayLarge, tnum)")
        Text(
            "0123456789",
            style = MaterialTheme.typography.displayLarge.tabular(),
            color = cs.onBackground,
        )
        SectionHeader("StatCard")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.s)) {
            StatCard("média 7 dias", "11,4", Modifier.weight(1f), deltaPct = -8.0)
            StatCard("média/dia", "12,1", Modifier.weight(1f), deltaPct = 5.0)
        }
        StatCard("intervalo hoje", "1 h 26", Modifier.fillMaxWidth(0.5f), caption = "média")
        SectionHeader("PeriodSelector")
        PeriodSelector(period, { period = it })
        SectionHeader("Botão +1")
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(Dimens.plusButton).background(cs.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("+1", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = cs.onPrimary)
        }
        SectionHeader("EmptyState")
        EmptyState("Ainda sem registos hoje.")
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(bottom = Dimens.xs).size(height = 40.dp, width = 0.dp).background(color, Dimens.chipShape))
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(heightDp = 1400)
@Composable
private fun DesignShowcasePreview() = CinzeiroTheme { DesignShowcase() }
