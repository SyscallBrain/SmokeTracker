package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import pt.tiago.cinzeiro.ui.format.Formats
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme
import pt.tiago.cinzeiro.ui.theme.Dimens
import pt.tiago.cinzeiro.ui.theme.tabular

/**
 * Metric card. [deltaPct] > 0 = more cigarettes → ↑ in `error`; < 0 → ↓ in green.
 * Without a delta, [caption] (if any) is shown in `onSurfaceVariant`.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    deltaPct: Double? = null,
    caption: String? = null,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, Dimens.cardShape)
            .padding(Dimens.m),
    ) {
        Text(
            label.uppercase(Formats.PT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        val deltaText = deltaPct?.let { deltaLabel(it) }
        if (deltaText != null) {
            Text(
                deltaText,
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = deltaColor(deltaPct),
            )
        } else if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun deltaLabel(pct: Double): String {
    val arrow = when {
        pct > 0.5 -> "↑ "
        pct < -0.5 -> "↓ "
        else -> "= "
    }
    return arrow + Formats.percentAbs(pct) + " vs anterior"
}

@Composable
private fun deltaColor(pct: Double) = when {
    pct > 0.5 -> MaterialTheme.colorScheme.error
    pct < -0.5 -> CinzeiroTheme.extended.good
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun StatCardPreview() = CinzeiroTheme {
    Column {
        StatCard("média 7 dias", "11,4", deltaPct = -8.0)
        Spacer(Modifier.height(Dimens.s))
        StatCard("média/dia", "12,1", deltaPct = 5.2)
        Spacer(Modifier.height(Dimens.s))
        StatCard("intervalo hoje", "1 h 26", caption = "média")
    }
}
