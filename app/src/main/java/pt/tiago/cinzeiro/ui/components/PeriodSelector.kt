package pt.tiago.cinzeiro.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import pt.tiago.cinzeiro.domain.Period
import pt.tiago.cinzeiro.ui.theme.CinzeiroTheme

val Period.label: String
    get() = when (this) {
        Period.D7 -> "7 d"
        Period.D30 -> "30 d"
        Period.D90 -> "90 d"
        Period.Y1 -> "1 a"
    }

/** 7 d · 30 d · 90 d · 1 y; selection in `secondary` on a translucent background. */
@Composable
fun PeriodSelector(selected: Period, onSelect: (Period) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = cs.secondary.copy(alpha = 0.22f),
        activeContentColor = cs.secondary,
        activeBorderColor = cs.outline,
        inactiveContainerColor = cs.surface,
        inactiveContentColor = cs.onSurfaceVariant,
        inactiveBorderColor = cs.outline,
    )
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = p == selected,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = Period.entries.size),
                colors = colors,
                icon = {},
                label = { Text(p.label) },
            )
        }
    }
}

@Preview(backgroundColor = 0xFF1A1B26, showBackground = true)
@Composable
private fun PeriodSelectorPreview() = CinzeiroTheme {
    var p by remember { mutableStateOf(Period.D30) }
    PeriodSelector(p, { p = it })
}
